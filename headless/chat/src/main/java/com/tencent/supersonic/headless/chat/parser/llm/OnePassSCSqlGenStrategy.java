package com.tencent.supersonic.headless.chat.parser.llm;

import com.google.common.collect.Lists;
import com.tencent.supersonic.common.pojo.ChatApp;
import com.tencent.supersonic.common.pojo.ChatModelConfig;
import com.tencent.supersonic.common.pojo.Text2SQLExemplar;
import com.tencent.supersonic.common.pojo.enums.AppModule;
import com.tencent.supersonic.common.util.ChatAppManager;
import com.tencent.supersonic.headless.chat.parser.ParserConfig;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMReq;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMResp;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.tencent.supersonic.headless.chat.parser.ParserConfig.PARSER_FORMAT_JSON_TYPE;

/**
 * 通过大模型做语义解析生成 S2SQL
 */
@Service
@Slf4j
public class OnePassSCSqlGenStrategy extends SqlGenStrategy {

    private static final Logger keyPipelineLog = LoggerFactory.getLogger("keyPipeline");

    public static final String APP_KEY = "S2SQL_PARSER";

    @Autowired
    private ParserConfig parserConfig;

    /**
     * 角色：你是一名精通SQL语言的数据分析师。
     * 任务：用户会提出自然语言问题，你需要将其转换为SQL查询语句，以便通过执行该SQL查询从底层数据库中返回相关数据。
     * 规则：
     * SQL中的列名和取值必须严格依据提供的Schema，严禁自行虚构。
     * 涉及时间范围时，必须使用>、<、>=、<=等运算符明确指定。
     * 若问题中未明确提及时间范围，则where子句中不得包含时间条件。
     * 禁止使用函数计算日期范围（如DATEADD等）。
     * 若需嵌套聚合操作，必须使用with语句构建。
     * 通过AS命令定义的别名必须用下划线包围（如AS _别名_）。
     * AS命令创建的别名语言需与问题的语言一致（例如问题为中文，别名也需用中文）。
     * 示例：{{exemplar}}
     * 查询输入：问题：{{question}}，Schema：{{schema}}，附加信息：{{information}}
     */
    public static final String INSTRUCTION = "#Role: You are a data analyst experienced in SQL languages."
            + "\n#Task: You will be provided with a natural language question asked by users,"
            + "please convert it to a SQL query so that relevant data could be returned "
            + "by executing the SQL query against underlying database." + "\n#Rules:"
            + "\n1.SQL columns and values must be mentioned in the `Schema`, DO NOT hallucinate."
            + "\n2.ALWAYS specify time range using `>`,`<`,`>=`,`<=` operator."
            + "\n3.DO NOT include time range in the where clause if not explicitly expressed in the `Question`."
            + "\n4.DO NOT calculate date range using functions."
            + "\n5.ALWAYS use `with` statement if nested aggregation is needed."
            + "\n6.ALWAYS enclose alias declared by `AS` command in underscores."
            + "\n7.Alias created by `AS` command must be in the same language ast the `Question`."
            + "\n#Exemplars: {{exemplar}}"
            + "\n#Query: Question:{{question}},Schema:{{schema}},SideInfo:{{information}}";

    /**
     * 构造方法：初始化OnePassSCSqlGenStrategy并注册语义SQL解析应用
     * 
     * 核心逻辑：
     * 1. 在ChatAppManager中注册一个名为"语义SQL解析"的ChatApp
     * 2. 使用预定义的INSTRUCTION作为提示模板
     * 3. 该应用用于通过大模型将自然语言问题转换为SQL查询语句
     * 
     * 注册信息：
     * - 应用KEY: S2SQL_PARSER
     * - 应用名称: 语义SQL解析
     * - 应用模块: CHAT
     * - 应用描述: 通过大模型做语义解析生成S2SQL
     * - 启用状态: true
     */
    public OnePassSCSqlGenStrategy() {
        ChatAppManager.register(APP_KEY, ChatApp.builder().prompt(INSTRUCTION).name("语义SQL解析")
                .appModule(AppModule.CHAT).description("通过大模型做语义解析生成S2SQL").enable(true).build());
    }

    @Data
    static class SemanticSql {
        @Description("thought or remarks to tell users about the sql, make it short.")
        private String thought;

        @Description("sql to generate")
        private String sql;
    }

    /**
     * 语义SQL提取器接口
     * 
     * 核心功能：定义从文本中生成语义SQL的契约接口
     * 该接口与LangChain4j的AiServices配合使用，用于自动将LLM的输出解析为结构化的SemanticSql对象
     */
    interface SemanticSqlExtractor {
        /**
         * 从给定文本中生成语义SQL
         * 
         * @param text 包含问题、Schema和附加信息的提示文本
         * @return SemanticSql对象，包含SQL语句和思考过程
         */
        SemanticSql generateSemanticSql(String text);
    }

    /**
     * 生成SQL查询语句的核心方法（使用单次自洽性投票策略）
     * 
     * 核心逻辑：
     * 1. 召回少样本示例（Few-Shot Exemplars）：从示例库中检索与当前问题最相关的示例
     * 2. 生成提示模板：为每次自洽性推理生成SQL生成提示
     * 3. 并行推理：使用多个提示并行执行LLM推理，生成多个SQL候选
     * 4. 自洽性投票：对多个候选结果进行投票，选择出现频率最高的SQL作为最终结果
     * 5. 格式化响应：构建包含SQL、示例和投票结果的响应对象
     * 
     * 为什么使用自洽性策略：
     * - 通过多次采样和投票提高生成SQL的准确性和稳定性
     * - 减少单次推理可能产生的随机错误
     * - 提升复杂查询场景下的成功率
     * 
     * @param llmReq LLM请求对象，包含查询文本、Schema信息、配置等
     * @return LLMResp LLM响应对象，包含生成的SQL、投票结果、使用的示例等信息
     */
    @Override
    public LLMResp generate(LLMReq llmReq) {
        LLMResp llmResp = new LLMResp();
        llmResp.setQuery(llmReq.getQueryText());
        // 1.recall exemplars
        log.debug("OnePassSCSqlGenStrategy llmReq:\n{}", llmReq);
        List<List<Text2SQLExemplar>> exemplarsList = promptHelper.getFewShotExemplars(llmReq);

        // 2.generate sql generation prompt for each self-consistency inference
        ChatApp chatApp = llmReq.getChatAppConfig().get(APP_KEY);
        ChatModelConfig chatModelConfig = chatApp.getChatModelConfig();
        if (!StringUtils.isBlank(parserConfig.getParameterValue(PARSER_FORMAT_JSON_TYPE))) {
            chatModelConfig.setJsonFormat(true);
            chatModelConfig
                    .setJsonFormatType(parserConfig.getParameterValue(PARSER_FORMAT_JSON_TYPE));
        }
        ChatLanguageModel chatLanguageModel = getChatLanguageModel(chatModelConfig);
        SemanticSqlExtractor extractor = AiServices.create(SemanticSqlExtractor.class, chatLanguageModel);

        Map<Prompt, List<Text2SQLExemplar>> prompt2Exemplar = new HashMap<>();
        for (List<Text2SQLExemplar> exemplars : exemplarsList) {
            llmReq.setDynamicExemplars(exemplars);
            Prompt prompt = generatePrompt(llmReq, llmResp, chatApp);
            prompt2Exemplar.put(prompt, exemplars);
        }

        // 3.perform multiple self-consistency inferences parallelly
        Map<String, Prompt> output2Prompt = new ConcurrentHashMap<>();
        prompt2Exemplar.keySet().parallelStream().forEach(prompt -> {
            SemanticSql s2Sql = extractor.generateSemanticSql(prompt.toUserMessage().singleText());
            output2Prompt.put(s2Sql.getSql(), prompt);
            keyPipelineLog.info("OnePassSCSqlGenStrategy modelReq:\n{} \nmodelResp:\n{}",
                    prompt.text(), s2Sql);
        });

        // 4.format response.
        Pair<String, Map<String, Double>> sqlMapPair = ResponseHelper
                .selfConsistencyVote(Lists.newArrayList(output2Prompt.keySet()));
        llmResp.setSqlOutput(sqlMapPair.getLeft());
        List<Text2SQLExemplar> usedExemplars = prompt2Exemplar.get(output2Prompt.get(sqlMapPair.getLeft()));
        llmResp.setSqlRespMap(ResponseHelper.buildSqlRespMap(usedExemplars, sqlMapPair.getRight()));

        return llmResp;
    }

    /**
     * 生成LLM的提示模板
     * 
     * 核心逻辑：
     * 1. 构建示例字符串：将动态示例格式化为"Question-Schema-SideInfo-SQL"的形式
     * 2. 构建Schema字符串：提取并格式化数据库Schema信息
     * 3. 构建附加信息：提取查询相关的辅助信息
     * 4. 填充变量映射：将示例、问题、Schema、附加信息填充到模板变量中
     * 5. 应用提示模板：使用ChatApp中定义的提示模板生成最终的Prompt对象
     * 
     * 为什么这样设计：
     * - 使用少样本学习（Few-Shot Learning）提高LLM对特定领域问题的理解能力
     * - 提供Schema和附加信息确保生成的SQL符合实际数据库结构
     * - 支持自定义提示模板以适应不同场景和优化需求
     * 
     * @param llmReq  LLM请求对象，包含查询文本、动态示例等信息
     * @param llmResp LLM响应对象，用于记录Schema和附加信息（副作用）
     * @param chatApp 聊天应用配置，包含提示模板
     * @return Prompt 构建好的提示对象，可直接发送给LLM
     */
    private Prompt generatePrompt(LLMReq llmReq, LLMResp llmResp, ChatApp chatApp) {
        StringBuilder exemplars = new StringBuilder();
        for (Text2SQLExemplar exemplar : llmReq.getDynamicExemplars()) {
            String exemplarStr = String.format("\nQuestion:%s,Schema:%s,SideInfo:%s,SQL:%s",
                    exemplar.getQuestion(), exemplar.getDbSchema(), exemplar.getSideInfo(),
                    exemplar.getSql());
            exemplars.append(exemplarStr);
        }
        String dataSemantics = promptHelper.buildSchemaStr(llmReq);
        String sideInformation = promptHelper.buildSideInformation(llmReq);
        llmResp.setSchema(dataSemantics);
        llmResp.setSideInfo(sideInformation);

        Map<String, Object> variable = new HashMap<>();
        variable.put("exemplar", exemplars);
        variable.put("question", llmReq.getQueryText());
        variable.put("schema", dataSemantics);
        variable.put("information", sideInformation);

        // use custom prompt template if provided.
        String promptTemplate = chatApp.getPrompt();
        return PromptTemplate.from(promptTemplate).apply(variable);
    }

    /**
     * Spring Bean初始化后的回调方法
     * 
     * 核心逻辑：
     * 将当前策略实例注册到SqlGenStrategyFactory工厂中
     * 注册类型为ONE_PASS_SELF_CONSISTENCY（单次自洽性投票策略）
     * 
     * 为什么在这里注册：
     * - 实现InitializingBean接口，确保在Spring容器完成依赖注入后自动注册
     * - 使用工厂模式，便于根据不同的SQL生成类型动态选择合适的策略
     * - 解耦策略实现与使用方，提高系统的可扩展性
     * 
     * @throws Exception 如果属性设置过程中发生异常
     */
    @Override
    public void afterPropertiesSet() {
        SqlGenStrategyFactory
                .addSqlGenerationForFactory(LLMReq.SqlGenType.ONE_PASS_SELF_CONSISTENCY, this);
    }
}

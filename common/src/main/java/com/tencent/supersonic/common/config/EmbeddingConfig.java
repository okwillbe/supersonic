package com.tencent.supersonic.common.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class EmbeddingConfig {

    // 向量数据库里面存的记忆集合前缀
    @Value("${s2.embedding.memory.collection.prefix:memory_}")
    private String memoryCollectionPrefix;

    @Value("${s2.embedding.preset.collection:preset_query_collection}")
    private String presetCollection;

    // 向量数据库里面存的元数据集合表名meta_collection
    @Value("${s2.embedding.meta.collection:meta_collection}")
    private String metaCollectionName;

    // 内嵌向量数据库召回结果数量 默认为1
    @Value("${s2.embedding.nResult:1}")
    private int nResult;

    @Value("${s2.embedding.metric.analyzeQuery.collection:solved_query_collection}")
    private String metricAnalyzeQueryCollection;

    // 向量数据库里面存的text2sql集合表名text2dsl_agent_collection
    @Value("${text2sql.collection.name:text2dsl_agent_collection}")
    private String text2sqlCollectionName;

    // 指标分析的embedding召回数量 默认为5
    @Value("${s2.embedding.metric.analyzeQuery.nResult:5}")
    private int metricAnalyzeQueryResultNum;

    public String getMemoryCollectionName(Integer agentId) {
        return memoryCollectionPrefix + agentId;
    }
}

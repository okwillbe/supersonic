package com.tencent.supersonic.chat.server.pojo;

import com.tencent.supersonic.chat.api.pojo.request.ChatParseReq;
import com.tencent.supersonic.chat.api.pojo.response.ChatParseResp;
import com.tencent.supersonic.chat.server.agent.Agent;
import lombok.Data;

import java.util.Objects;

@Data
public class ParseContext {
    // 用户的解析请求
    private ChatParseReq request;
    // 解析结果
    private ChatParseResp response;
    // 助手 用户解析请求里面会包含助手id这里将找到的助手放的上下文里面
    private Agent agent;

    public ParseContext(ChatParseReq request, ChatParseResp response) {
        this.request = request;
        this.response = response;
    }

    // 是否启用nl2sql ，条件是助手包含数据集工具且没有选择解析结果
    public boolean enableNL2SQL() {
        return Objects.nonNull(agent) && agent.containsDatasetTool()
                && response.getSelectedParses().size() == 0;
    }

    // 是否启用llm 请求中设置了不启用llm则不启用
    public boolean enableLLM() {
        return !request.isDisableLLM();
    }

    // 是否需要反馈 ，条件是助手包含反馈工具且请求解析为null且有多个解析结果，这里需要多个解析结果才需要反馈
    public boolean needFeedback() {
        return agent.enableFeedback() && (Objects.isNull(request.getSelectedParse())
                && response.getSelectedParses().size() > 1);
    }

    // 是否需要llm解析 ，条件是llm可用且请求解析为null且有选择解析结果，这里主要有解析结果
    public boolean needLLMParse() {
        return enableLLM() && (Objects.nonNull(request.getSelectedParse())
                || !response.getSelectedParses().isEmpty());
    }
}

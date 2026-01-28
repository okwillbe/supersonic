package com.tencent.supersonic.chat.api.pojo.request;

import com.tencent.supersonic.common.pojo.User;
import com.tencent.supersonic.headless.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.headless.api.pojo.request.QueryFilters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatParseReq {
    /*
     * 用户输入的问题
     */
    private String queryText;
    /*
     * 对话id
     */
    private Integer chatId;
    /*
     * 助手id
     */
    private Integer agentId;
    /*
     * 数据集id
     */
    private Long dataSetId;
    /*
     * 用户信息
     */
    private User user;
    /*
     * 过滤条件
     */
    private QueryFilters queryFilters;
    /*
     * 是否保存回答
     */
    private boolean saveAnswer = true;
    /*
     * 是否禁用大模型
     */
    private boolean disableLLM = false;
    /*
     * 查询id
     */
    private Long queryId;
    /*
     * 选中的解析
     */
    private SemanticParseInfo selectedParse;
}

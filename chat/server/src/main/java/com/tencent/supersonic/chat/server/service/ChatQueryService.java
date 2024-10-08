package com.tencent.supersonic.chat.server.service;

import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.chat.api.pojo.request.ChatExecuteReq;
import com.tencent.supersonic.chat.api.pojo.request.ChatParseReq;
import com.tencent.supersonic.chat.api.pojo.request.ChatQueryDataReq;
import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.headless.api.pojo.request.DimensionValueReq;
import com.tencent.supersonic.headless.api.pojo.response.ParseResp;
import com.tencent.supersonic.headless.api.pojo.response.SearchResult;

import java.util.List;

public interface ChatQueryService {

    List<SearchResult> search(ChatParseReq chatParseReq);

    ParseResp performParsing(ChatParseReq chatParseReq);

    QueryResult performExecution(ChatExecuteReq chatExecuteReq) throws Exception;

    QueryResult parseAndExecute(int chatId, int agentId, String queryText);

    Object queryData(ChatQueryDataReq chatQueryDataReq, User user) throws Exception;

    Object queryDimensionValue(DimensionValueReq dimensionValueReq, User user) throws Exception;
}

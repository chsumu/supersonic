package com.tencent.supersonic.chat.server.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageInfo;
import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.auth.api.authentication.utils.UserHolder;
import com.tencent.supersonic.chat.api.pojo.request.ChatMemoryUpdateReq;
import com.tencent.supersonic.chat.api.pojo.request.MemoryUpdateReq;
import com.tencent.supersonic.chat.api.pojo.request.PageMemoryReq;
import com.tencent.supersonic.chat.server.persistence.dataobject.ChatMemoryDO;
import com.tencent.supersonic.chat.server.service.MemoryService;
import com.tencent.supersonic.common.pojo.Text2SQLExemplar;
import com.tencent.supersonic.common.service.impl.ExemplarServiceImpl;
import com.tencent.supersonic.common.util.JsonUtil;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping({"/api/chat/memory"})
public class MemoryController {

    private final ObjectMapper objectMapper = JsonUtil.INSTANCE.getObjectMapper();

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private ExemplarServiceImpl exemplarService;

    @PostMapping("/updateMemory")
    public Boolean updateMemory(@RequestBody ChatMemoryUpdateReq chatMemoryUpdateReq,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        User user = UserHolder.findUser(request, response);
        memoryService.updateMemory(chatMemoryUpdateReq, user);
        return true;
    }

    @SneakyThrows
    @PostMapping("/updateMemoryByFile")
    public Boolean updateMemoryByFile(MemoryUpdateReq memoryUpdateReq) {
        TypeReference<List<Text2SQLExemplar>> valueTypeRef = new TypeReference<List<Text2SQLExemplar>>() {};
        List<Text2SQLExemplar> exemplars = objectMapper.readValue(memoryUpdateReq.getFile().getInputStream(),
                valueTypeRef);
        exemplars.stream().forEach(e -> exemplarService.storeExemplar(memoryUpdateReq.getCollection(), e));
        return true;
    }

    @RequestMapping("/pageMemories")
    public PageInfo<ChatMemoryDO> pageMemories(@RequestBody PageMemoryReq pageMemoryReq) {
        return memoryService.pageMemories(pageMemoryReq);
    }

}

package com.tencent.supersonic.chat.api.pojo.request;


import com.tencent.supersonic.common.pojo.RecordInfo;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class MemoryUpdateReq extends RecordInfo {

    MultipartFile file;

    private String collection;

}

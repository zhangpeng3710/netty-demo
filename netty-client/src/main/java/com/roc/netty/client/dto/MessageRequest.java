package com.roc.netty.client.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 消息请求DTO
 */
@Data
public class MessageRequest {
    @NotBlank(message = "客户端ID不能为空")
    private String clientId;  // 客户端ID
    
    @NotBlank(message = "消息内容不能为空")
    private String content;   // 消息内容
}

package com.roc.netty.client.controller;

import com.roc.netty.client.dto.ApiResponse;
import com.roc.netty.client.dto.MessageRequest;
import com.roc.netty.client.netty.NettyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 消息控制器
 */
@Slf4j
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@Validated
public class MessageController {

    private final NettyClient nettyClient;

    /**
     * 发送消息到服务器
     */
    @PostMapping("/send")
    public ApiResponse<String> sendMessage(@Valid @RequestBody MessageRequest request) {
        if (!nettyClient.isConnected()) {
            return ApiResponse.error(500, "客户端未连接到服务器");
        }

        boolean success = nettyClient.sendMessage(request.getContent());
        if (success) {
            return ApiResponse.success("消息发送成功");
        } else {
            return ApiResponse.error(500, "消息发送失败");
        }
    }

    /**
     * 获取客户端连接状态
     */
    @GetMapping("/status")
    public ApiResponse<Boolean> getStatus() {
        return ApiResponse.success("获取状态成功", nettyClient.isConnected());
    }
}

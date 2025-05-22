package com.roc.netty.client.controller;

import com.roc.netty.client.dto.ApiResponse;
import com.roc.netty.client.dto.MessageRequest;
import com.roc.netty.client.protocol.MessageProtocol;
import com.roc.netty.client.service.ClientConnectionService;
import io.netty.channel.Channel;
import io.netty.util.CharsetUtil;
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
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Validated
public class MessageController {

    private final ClientConnectionService clientConnectionService;

    /**
     * 向指定客户端发送消息
     */
    @PostMapping("/send")
    public ApiResponse<String> sendMessage(@Valid @RequestBody MessageRequest request) {
        String clientId = request.getClientId();
        String content = request.getContent();
        
        // 获取客户端连接
        Channel channel = clientConnectionService.getChannel(clientId);
        if (channel == null || !channel.isActive()) {
            return ApiResponse.error(404, "客户端未连接或已断开: " + clientId);
        }
        
        try {
            // 创建消息协议对象
            MessageProtocol message = new MessageProtocol();
            message.setType((byte) 0); // 业务消息
            byte[] contentBytes = content.getBytes(CharsetUtil.UTF_8);
            message.setLength(contentBytes.length);
            message.setContent(contentBytes);
            
            // 发送消息
            channel.writeAndFlush(message);
            
            log.info("向客户端 {} 发送消息: {}", clientId, content);
            return ApiResponse.success("消息发送成功");
            
        } catch (Exception e) {
            log.error("向客户端发送消息失败: {}", clientId, e);
            return ApiResponse.error(500, "消息发送失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有已连接的客户端ID
     */
    @GetMapping("/clients")
    public ApiResponse<String[]> getConnectedClients() {
        String[] clientIds = clientConnectionService.getAllClientIds();
        return ApiResponse.success("获取客户端列表成功", clientIds);
    }
    
    /**
     * 获取当前连接数
     */
    @GetMapping("/count")
    public ApiResponse<Integer> getConnectionCount() {
        int count = clientConnectionService.getConnectionCount();
        return ApiResponse.success("获取连接数成功", count);
    }
}

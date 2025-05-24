package com.roc.netty.server.controller;

import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Netty HTTP 控制器
 */
@RestController
@RequestMapping("/api/netty")
public class NettyController {

    
    // 用于保存所有连接的channel
    public static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "Netty server is running");
        result.put("activeConnections", channels.size());
        return result;
    }
    
    @PostMapping("/broadcast")
    public Map<String, Object> broadcastMessage(@RequestBody Map<String, String> message) {
        String content = message.get("message");
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        
        // 广播消息给所有连接的客户端
        channels.writeAndFlush(content);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Message broadcasted successfully");
        result.put("recipients", channels.size());
        return result;
    }
}

package com.roc.netty.server.controller;

import com.roc.netty.server.service.ClientConnectionService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Netty HTTP 控制器
 */
@RestController
@RequestMapping("/api/netty")
public class NettyController {


    @Resource
    private ClientConnectionService service;

    
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "Netty server is running");
        result.put("activeConnections", service.getConnectionCount());
        return result;
    }
    
    @PostMapping("/broadcast")
    public Map<String, Object> broadcastMessage(@RequestBody Map<String, String> message) {
        String content = message.get("message");
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        
        // 广播消息给所有连接的客户端
        service.broadcast(content);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Message broadcasted successfully");
        result.put("recipients", service.getConnectionCount());
        return result;
    }
}

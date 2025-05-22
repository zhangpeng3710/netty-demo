package com.roc.netty.server.handler;

import com.roc.netty.server.protocol.MessageProtocol;
import com.roc.netty.server.service.ClientConnectionService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 服务端业务处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerBusinessHandler extends SimpleChannelInboundHandler<MessageProtocol> {
    
    private final ClientConnectionService clientConnectionService;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MessageProtocol msg) throws Exception {
        // 只处理业务消息类型
        if (msg.getType() == 0) {
            String content = new String(msg.getContent(), StandardCharsets.UTF_8);
            log.info("服务端收到消息: {}", content);
            
            // 回复消息
            String responseContent = "Server received: " + content;
            byte[] responseBytes = responseContent.getBytes(StandardCharsets.UTF_8);
            
            MessageProtocol response = new MessageProtocol();
            response.setType((byte) 0);  // 业务消息类型
            response.setLength(1 + responseBytes.length);  // 类型字段(1字节) + 内容长度
            response.setContent(responseBytes);
            
            ctx.writeAndFlush(response);
        }
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        String clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
        
        // 注册客户端连接
        clientConnectionService.addConnection(clientId, channel);
        
        log.info("客户端连接成功: {}, 分配ID: {}", channel.remoteAddress(), clientId);
        log.info("当前连接数: {}", clientConnectionService.getConnectionCount());
        
        // 发送欢迎消息和客户端ID
        String welcomeMsg = "Welcome! Your client ID is: " + clientId;
        byte[] content = welcomeMsg.getBytes(StandardCharsets.UTF_8);
        
        MessageProtocol message = new MessageProtocol();
        message.setType((byte) 0);
        message.setLength(content.length);
        message.setContent(content);
        
        channel.writeAndFlush(message);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        log.info("客户端断开连接: {}", channel.remoteAddress());
        
        // 从连接服务中移除
        clientConnectionService.removeConnection(channel);
        log.info("当前连接数: {}", clientConnectionService.getConnectionCount());
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("服务端异常", cause);
        ctx.close();
    }
}

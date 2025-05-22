package com.roc.netty.client.handler;

import com.roc.netty.client.protocol.MessageProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 客户端业务处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientBusinessHandler extends SimpleChannelInboundHandler<MessageProtocol> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MessageProtocol msg) throws Exception {
        // 只处理业务消息类型
        if (msg.getType() == 0) {
            String content = new String(msg.getContent(), StandardCharsets.UTF_8);
            log.info("客户端收到消息: {}", content);
        }
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("连接到服务器成功");
        
        // 连接成功后发送一条测试消息
        String content = "Hello from client!";
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        
        MessageProtocol message = new MessageProtocol();
        message.setType((byte) 0);  // 业务消息类型
        message.setLength(1 + contentBytes.length);  // 类型字段(1字节) + 内容长度
        message.setContent(contentBytes);
        
        ctx.writeAndFlush(message);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("与服务器断开连接");
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("客户端异常", cause);
        ctx.close();
    }
}

package com.roc.netty.server.handler;

import com.roc.netty.server.protocol.MessageProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 心跳检测处理器
 */
@Slf4j
@Component
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {
    
    private int lostHeartbeatCount = 0;
    
    @Value("${netty.max-lost-heartbeat:3}")
    private int maxLostHeartbeat;
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 发送心跳消息
            MessageProtocol heartbeat = new MessageProtocol();
            heartbeat.setType((byte) 1); // 心跳请求类型
            heartbeat.setLength(1); // 只有类型字段，没有内容
            heartbeat.setContent(new byte[0]);
            
            ctx.writeAndFlush(heartbeat);
            
            lostHeartbeatCount++;
            log.info("发送心跳消息，当前心跳丢失次数: {}", lostHeartbeatCount);
            
            if (lostHeartbeatCount > maxLostHeartbeat) {
                log.error("心跳超时，关闭连接");
                ctx.channel().close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof MessageProtocol) {
            MessageProtocol message = (MessageProtocol) msg;
            
            // 处理心跳响应
            if (message.getType() == 2) {
                lostHeartbeatCount = 0;
                if (log.isDebugEnabled()) {
                    log.debug("收到心跳响应: {}", ctx.channel().remoteAddress());
                }
                return;
            }
            
            // 处理心跳请求，回复心跳响应
            if (message.getType() == 1) {
                MessageProtocol heartbeatResp = new MessageProtocol();
                heartbeatResp.setType((byte) 2); // 心跳响应类型
                heartbeatResp.setLength(1); // 只有类型字段，没有内容
                heartbeatResp.setContent(new byte[0]);
                
                ctx.writeAndFlush(heartbeatResp);
                if (log.isDebugEnabled()) {
                    log.debug("收到心跳请求，发送心跳响应: {}", ctx.channel().remoteAddress());
                }
                return;
            }
        }
        
        // 传递给下一个处理器
        ctx.fireChannelRead(msg);
    }
}

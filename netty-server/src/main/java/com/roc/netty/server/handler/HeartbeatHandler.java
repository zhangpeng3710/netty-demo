package com.roc.netty.server.handler;

import com.roc.netty.server.config.NettyConfig;
import com.roc.netty.server.constant.Constants;
import com.roc.netty.server.protocol.MessageProtocol;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 心跳检测处理器
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {


    @Resource
    private NettyConfig nettyConfig;

    private int lostHeartbeatCount = 0;


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 发送心跳消息
            MessageProtocol heartbeat = new MessageProtocol();
            heartbeat.setType(Constants.HEARTBEAT_REQUEST); // 心跳请求类型
            heartbeat.setLength(1); // 只有类型字段，没有内容
            heartbeat.setContent(new byte[0]);

            ctx.writeAndFlush(heartbeat);

            log.info("发送心跳消息，当前心跳丢失次数: {}", lostHeartbeatCount);
            lostHeartbeatCount++;

            if (lostHeartbeatCount > 3) {
                log.error("心跳超时，关闭连接");
                ctx.channel().close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof MessageProtocol) {
            MessageProtocol message = (MessageProtocol) msg;
            // 处理心跳响应
            if (message.getType() == Constants.HEARTBEAT_RESPONSE) {
                lostHeartbeatCount = 0;
                if (log.isDebugEnabled()) {
                    log.debug("收到心跳响应: {}", ctx.channel().remoteAddress());
                }
                return;
            }
            
            // 处理心跳请求，回复心跳响应
            if (message.getType() == Constants.HEARTBEAT_REQUEST) {
                MessageProtocol heartbeatResp = new MessageProtocol();
                heartbeatResp.setType(Constants.HEARTBEAT_RESPONSE); // 心跳响应类型
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

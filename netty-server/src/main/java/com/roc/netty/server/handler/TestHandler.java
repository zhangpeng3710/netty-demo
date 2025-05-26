package com.roc.netty.server.handler;

import com.roc.netty.server.protocol.MessageProtocol;
import io.netty.channel.ChannelHandler.Sharable;
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
@Sharable
public class TestHandler extends SimpleChannelInboundHandler<MessageProtocol> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MessageProtocol msg) {
        // 只处理业务消息类型
        if (msg.getType() == 9) {
            String content = "";
            if (msg.getContent() != null) {
                content = new String(msg.getContent(), StandardCharsets.UTF_8);
            }
            log.info("服务端收到响应 - 类型: {}, 长度: {}, 内容: {}",
                    msg.getType(), msg.getLength(), content);

        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端异常", cause);
        ctx.close();
    }
}

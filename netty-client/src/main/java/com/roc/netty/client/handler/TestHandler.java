package com.roc.netty.client.handler;

import com.roc.netty.client.protocol.MessageProtocol;
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
    protected void channelRead0(ChannelHandlerContext ctx, MessageProtocol msg) throws Exception {
        // 只处理业务消息类型
        if (msg.getType() == 9) {
            String content = "";
            if (msg.getContent() != null) {
                content = new String(msg.getContent(), StandardCharsets.UTF_8);
            }
            log.info("客户端收到消息 - 类型: {}, 长度: {}, 内容: {}",
                    msg.getType(), msg.getLength(), content);

            String responseContent = "Client received: " + content;
            MessageProtocol message = new MessageProtocol();
            message.setType((byte) 9);  // 业务消息类型
            message.setLength(1 + responseContent.getBytes().length);  // 类型字段(1字节) + 内容长度
            message.setContent(responseContent.getBytes());

            ctx.writeAndFlush(message);

            // 打印接收到的字节数
            if (ctx.channel() != null && ctx.channel().isActive()) {
                log.info("Channel is active. Local: {}, Remote: {}",
                        ctx.channel().localAddress(), ctx.channel().remoteAddress());
            } else {
                log.warn("Channel is not active");
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("客户端异常", cause);
        ctx.close();
    }
}

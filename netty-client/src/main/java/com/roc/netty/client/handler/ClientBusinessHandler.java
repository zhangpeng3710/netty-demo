package com.roc.netty.client.handler;

import com.roc.netty.client.constant.Constants;
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
public class ClientBusinessHandler extends SimpleChannelInboundHandler<MessageProtocol> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MessageProtocol msg) throws Exception {
        String content = "";
        if (msg.getContent() != null) {
            content = new String(msg.getContent(), StandardCharsets.UTF_8);
        }
        log.info("客户端收到消息 - 类型: {}, 消息ID: {}, 长度: {}, 内容: {}", msg.getType(), msg.getMsgId(), msg.getLength(), content);
        switch (msg.getType()) {
            case Constants.WELCOME_MESSAGE_TYPE:
                log.info("客户端收到欢迎消息 - 类型: {}, 消息ID: {}, 长度: {}, 内容: {}",
                        msg.getType(), msg.getMsgId(), msg.getLength(), msg.getContent());
                break;
            case Constants.BUSINESS_MESSAGE_REQUEST:
                log.info("客户端收到业务请求 - 类型: {}, 消息ID: {}, 长度: {}, 内容: {}",
                        msg.getType(), msg.getMsgId(), msg.getLength(), msg.getContent());
                break;
            case Constants.BUSINESS_MESSAGE_RESPONSE:
                log.info("客户端收到业务响应 - 类型: {}, 消息ID: {}, 长度: {}, 内容: {}",
                        msg.getType(), msg.getMsgId(), msg.getLength(), msg.getContent());
                break;
            case Constants.FILE_SEND_TO_SERVER_RESPONSE:
                log.info("准备发送消息到客户端 - 类型: {}, 消息ID: {}, 长度: {}, 内容: {}",
                        msg.getType(), msg.getMsgId(), msg.getLength(), msg.getContent());
                break;
            case Constants.FILE_SEND_TO_CLIENT_REQUEST:
                String responseContent = "Client received: " + content;
                MessageProtocol message = new MessageProtocol();
                message.setType(Constants.FILE_SEND_TO_CLIENT_RESPONSE);  // 业务消息类型
                message.setLength(1 + responseContent.getBytes().length);  // 类型字段(1字节) + 内容长度
                message.setContent(responseContent.getBytes());

                ctx.writeAndFlush(message);
                break;
            default:
                log.warn("客户端收到未知消息类型: {}, 消息ID: {}, 长度: {}, 内容: {}", msg.getType(), msg.getMsgId(), msg.getLength(), content);
        }
        // 打印接收到的字节数
        if (ctx.channel() != null && ctx.channel().isActive()) {
            log.info("Channel is active. Local: {}, Remote: {}",
                    ctx.channel().localAddress(), ctx.channel().remoteAddress());
        } else {
            log.warn("Channel is not active");
        }

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("连接到服务器成功. Local: {}, Remote: {}",
                ctx.channel().localAddress(), ctx.channel().remoteAddress());

        // 连接成功后发送一条测试消息
        String content = "Hello from client!";
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        MessageProtocol message = new MessageProtocol();
        message.setType(Constants.WELCOME_MESSAGE_TYPE);  // 业务消息类型
        message.setLength(1 + contentBytes.length);  // 类型字段(1字节) + 内容长度
        message.setContent(contentBytes);

        log.info("客户端准备发送消息 - 类型: {}, 消息ID: {}, 长度: {}, 内容: {}",
                message.getType(), message.getMsgId(), message.getLength(), content);

        // 添加监听器来检查是否发送成功
        ctx.writeAndFlush(message).addListener(future -> {
            if (future.isSuccess()) {
                log.info("客户端消息发送成功");
            } else {
                log.error("客户端消息发送失败: {}", future.cause().getMessage(), future.cause());
            }
        });
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

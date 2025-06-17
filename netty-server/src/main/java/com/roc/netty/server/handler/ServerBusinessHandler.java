package com.roc.netty.server.handler;

import com.roc.netty.server.constant.Constants;
import com.roc.netty.server.protocol.MessageProtocol;
import com.roc.netty.server.service.ClientConnectionService;
import com.roc.netty.server.service.FileService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 服务端业务处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ChannelHandler.Sharable
public class ServerBusinessHandler extends SimpleChannelInboundHandler<MessageProtocol> {

    private final ClientConnectionService clientConnectionService;
    private final FileService fileService;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MessageProtocol msg) throws IOException {
        MessageProtocol response = new MessageProtocol();
        String responseContent = "";
        String content = "";
        if (msg.getContent() != null && msg.getType() != 8) {
            content = new String(msg.getContent(), StandardCharsets.UTF_8);
        }
        // 只处理业务消息类型
        switch (msg.getType()) {
            case Constants.WELCOME_MESSAGE_TYPE:
                log.info("服务端收到欢迎消息 - 类型: {}, 消息ID: {}, 长度: {}, 内容: {}", msg.getType(), msg.getMsgId(), msg.getLength(), content);
                break;
            case Constants.BUSINESS_MESSAGE_REQUEST:
                log.info("服务端收到消息 - 类型: {}, 消息ID: {}, 长度: {}, 内容: {}", msg.getType(), msg.getMsgId(), msg.getLength(), content);

                // 回复消息
                responseContent = "Server received: " + content;
                byte[] responseBytes = responseContent.getBytes(StandardCharsets.UTF_8);

                response.setType(Constants.BUSINESS_MESSAGE_RESPONSE);  // 业务消息类型
                response.setLength(1 + responseBytes.length);  // 类型字段(1字节) + 内容长度
                response.setContent(responseBytes);

                log.info("准备发送消息到客户端 - 类型: {}, 消息ID: {}, 长度: {}, 内容: {}",
                        response.getType(), response.getMsgId(), response.getLength(), responseContent);

                // 添加监听器来检查是否发送成功
                ctx.writeAndFlush(response);
                break;
            case Constants.FILE_SEND_TO_SERVER_REQUEST:
                log.info("服务端收到文件 - 类型: {}, 消息ID: {}, 长度: {}",
                        response.getType(), response.getMsgId(), response.getLength());
                try {
                    // 使用FileService处理文件上传
                    String result = fileService.processUploadedFile(msg.getContent());

                    // 发送成功响应
                    response.setType(Constants.FILE_SEND_TO_SERVER_RESPONSE);
                    response.setContent(result.getBytes(StandardCharsets.UTF_8));
                    response.setLength(1 + result.getBytes(StandardCharsets.UTF_8).length);
                    ctx.writeAndFlush(response);

                } catch (Exception e) {
                    log.error("Error processing uploaded file: {}", e.getMessage(), e);
                    String errorResponse = "Error processing file: " + e.getMessage();
                    response.setType((byte) 8);
                    response.setContent(errorResponse.getBytes(StandardCharsets.UTF_8));
                    response.setLength(1 + errorResponse.getBytes(StandardCharsets.UTF_8).length);
                    ctx.writeAndFlush(response);
                }
                break;
            case Constants.FILE_SEND_TO_CLIENT_REQUEST:
                log.info("服务端发送文件到客户端 - 类型: {}, 消息ID: {}, 长度: {}",
                        msg.getType(), msg.getMsgId(), msg.getLength());
                break;
            case Constants.FILE_SEND_TO_CLIENT_RESPONSE:
                log.info("服务端收到响应 - 类型: {}, 消息ID: {}, 长度: {}, 内容: {}",
                        msg.getType(), msg.getMsgId(), msg.getLength(), content);
                break;
            default:
                log.warn("未知消息类型: {}", msg.getType());
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
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
        message.setType(Constants.WELCOME_MESSAGE_TYPE);
        message.setLength(1 + content.length);
        message.setContent(content);
        channel.writeAndFlush(message);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        log.info("客户端断开连接: {}", channel.remoteAddress());

        // 从连接服务中移除
        clientConnectionService.removeConnection(channel);
        log.info("当前连接数: {}", clientConnectionService.getConnectionCount());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("服务端异常", cause);
        ctx.close();
    }
}

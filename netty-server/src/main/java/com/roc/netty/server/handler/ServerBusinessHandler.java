package com.roc.netty.server.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roc.netty.server.protocol.MessageProtocol;
import com.roc.netty.server.service.ClientConnectionService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * 服务端业务处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ChannelHandler.Sharable
public class ServerBusinessHandler extends SimpleChannelInboundHandler<MessageProtocol> {

    private final ClientConnectionService clientConnectionService;

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
            case 0:
                log.info("服务端收到消息: {}", content);

                // 回复消息
                responseContent = "Server received: " + content;
                byte[] responseBytes = responseContent.getBytes(StandardCharsets.UTF_8);

                response.setType((byte) 0);  // 业务消息类型
                response.setLength(1 + responseBytes.length);  // 类型字段(1字节) + 内容长度
                response.setContent(responseBytes);

                log.info("准备发送消息到客户端 - 类型: {}, 长度: {}, 内容: {}",
                        response.getType(), response.getLength(), responseContent);

                // 添加监听器来检查是否发送成功
                ctx.writeAndFlush(response);
                break;
            case 8:
                GZIPInputStream gzipIn = null;
                ByteArrayOutputStream bos = null;
                ByteArrayInputStream bis = null;
                try {
                    HashMap fileInfo = new ObjectMapper().readValue(msg.getContent(), HashMap.class);
                    // Decode base64 data
                    byte[] compressedData = Base64.getDecoder().decode(fileInfo.get("content").toString());

                    // Decompress the data
                    bis = new ByteArrayInputStream(compressedData);
                    gzipIn = new GZIPInputStream(bis);
                    bos = new ByteArrayOutputStream();

                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = gzipIn.read(buffer)) > 0) {
                        bos.write(buffer, 0, len);
                    }

                    byte[] decompressedData = bos.toByteArray();

                    // Create directory if not exists
                    String savePath = "logs/uploaded/" + fileInfo.get("filename");
                    Path saveDir = Paths.get("logs/uploaded");
                    if (!Files.exists(saveDir)) {
                        Files.createDirectories(saveDir);
                    }

                    // Save file
                    Files.write(java.nio.file.Paths.get(savePath),
                            Base64.getDecoder().decode((String) fileInfo.get("content")));

                    log.info("File saved successfully: {}", savePath);

                    // Send response
                    responseContent = "File received and saved: " + savePath;
                    response.setType((byte) 8);
                    response.setContent(responseContent.getBytes(StandardCharsets.UTF_8));
                    response.setLength(1 + responseContent.getBytes(StandardCharsets.UTF_8).length);
                    ctx.writeAndFlush(response);


                } catch (Exception e) {
                    log.error("Error processing uploaded file: {}", e.getMessage(), e);
                    String errorResponse = "Error processing file: " + e.getMessage();
                    response.setType((byte) 8);
                    response.setContent(errorResponse.getBytes(StandardCharsets.UTF_8));
                    response.setLength(1 + errorResponse.getBytes(StandardCharsets.UTF_8).length);
                    ctx.writeAndFlush(response);
                } finally {
                    // Close resources
                    gzipIn.close();
                    bos.close();
                    bis.close();
                }
                break;
            case 9:
                log.info("服务端收到响应 - 类型: {}, 长度: {}, 内容: {}",
                        msg.getType(), msg.getLength(), content);
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
        message.setType((byte) 0);
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

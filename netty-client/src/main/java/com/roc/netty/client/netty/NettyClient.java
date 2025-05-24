package com.roc.netty.client.netty;

import com.roc.netty.client.codec.MessageDecoder;
import com.roc.netty.client.codec.MessageEncoder;
import com.roc.netty.client.handler.ClientBusinessHandler;
import com.roc.netty.client.handler.HeartbeatHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Netty客户端Channel初始化
 */
@Slf4j
@Component
public class NettyClient extends ChannelInitializer<SocketChannel> {


    private final ClientBusinessHandler clientBusinessHandler;
    private final HeartbeatHandler heartbeatHandler;

    @Value("${netty.writer-idle-time-seconds:40}")
    private int writerIdleTimeSeconds;

    @Autowired
    public NettyClient(ClientBusinessHandler clientBusinessHandler, HeartbeatHandler heartbeatHandler) {
        this.clientBusinessHandler = clientBusinessHandler;
        this.heartbeatHandler = heartbeatHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(
                // 添加编解码器
                new MessageEncoder(),
                new MessageDecoder(),
                // 添加空闲状态处理器
//            new IdleStateHandler(0, writerIdleTimeSeconds, 0, TimeUnit.SECONDS),
                // 添加心跳处理器
                heartbeatHandler,
                // 添加业务处理器
                clientBusinessHandler
        );
    }
}

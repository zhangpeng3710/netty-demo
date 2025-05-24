package com.roc.netty.client.netty;

import com.roc.netty.client.codec.MessageDecoder;
import com.roc.netty.client.codec.MessageEncoder;
import com.roc.netty.client.config.NettyConfig;
import com.roc.netty.client.handler.ClientBusinessHandler;
import com.roc.netty.client.handler.HeartbeatHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Netty客户端Channel初始化
 */
@Slf4j
@Component
public class NettyClient extends ChannelInitializer<SocketChannel> {


    private final ClientBusinessHandler clientBusinessHandler;
    private final HeartbeatHandler heartbeatHandler;
    private final NettyConfig nettyConfig;


    @Autowired
    public NettyClient(NettyConfig nettyConfig, ClientBusinessHandler clientBusinessHandler, HeartbeatHandler heartbeatHandler, NettyConfig nettyConfig1) {
        this.clientBusinessHandler = clientBusinessHandler;
        this.heartbeatHandler = heartbeatHandler;
        this.nettyConfig = nettyConfig;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(
                // 添加编解码器
                new MessageEncoder(),
                new MessageDecoder(),
                // 添加空闲状态处理器
                new IdleStateHandler(
                        nettyConfig.getClient().getReaderIdleTimeSeconds(),
                        nettyConfig.getClient().getWriterIdleTimeSeconds(),
                        nettyConfig.getClient().getAllIdleTimeSeconds(),
                        TimeUnit.SECONDS
                ),
                // 添加心跳处理器
                heartbeatHandler,
                // 添加业务处理器
                clientBusinessHandler
        );
    }
}

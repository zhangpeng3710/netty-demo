package com.roc.netty.server.netty;

import com.roc.netty.server.codec.MessageDecoder;
import com.roc.netty.server.codec.MessageEncoder;
import com.roc.netty.server.config.NettyConfig;
import com.roc.netty.server.handler.HeartbeatHandler;
import com.roc.netty.server.handler.ServerBusinessHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * Netty服务器核心类，负责服务器启动和Channel初始化
 */
@Slf4j
@Component
public class NettyServer {
    // 业务线程池，用于处理耗时的业务逻辑
    private static final EventExecutorGroup BUSINESS_GROUP = new DefaultEventExecutorGroup(16);

    // 最大帧长度
    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1MB
    private static final int LENGTH_FIELD_OFFSET = 0;
    private static final int LENGTH_FIELD_LENGTH = 4;
    private static final int LENGTH_ADJUSTMENT = 0;
    private static final int INITIAL_BYTES_TO_STRIP = 4;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;
    private final NettyConfig nettyConfig;
    private final ServerBusinessHandler serverBusinessHandler;


    public NettyServer(NettyConfig nettyConfig, ServerBusinessHandler serverBusinessHandler) {
        this.nettyConfig = nettyConfig;
        this.serverBusinessHandler = serverBusinessHandler;
    }


    @PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup(nettyConfig.getBossThreads(), new DefaultThreadFactory(nettyConfig.getBossNamePrefix()));
        workerGroup = new NioEventLoopGroup(nettyConfig.getWorkerThreads(), new DefaultThreadFactory(nettyConfig.getWorkerNamePrefix()));

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, nettyConfig.getBacklog())
                    .childOption(ChannelOption.SO_KEEPALIVE, nettyConfig.isKeepAlive())
                    // 开启Nagle算法，要求高实时性时关闭
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            // 添加日志处理器
                            if (log.isDebugEnabled()) {
                                p.addLast(new LoggingHandler(LogLevel.DEBUG));
                            }
                            // 添加基于长度的帧解码器，解决TCP粘包/拆包问题
                            // 参数说明：
                            // maxFrameLength: 最大帧长度
                            // lengthFieldOffset: 长度字段的偏移量
                            // lengthFieldLength: 长度字段的长度
                            // lengthAdjustment: 长度调整值
                            // initialBytesToStrip: 需要跳过的字节数
                            p.addLast(new LengthFieldBasedFrameDecoder(
                                    MAX_FRAME_LENGTH,
                                    LENGTH_FIELD_OFFSET,
                                    LENGTH_FIELD_LENGTH,
                                    LENGTH_ADJUSTMENT,
                                    INITIAL_BYTES_TO_STRIP));
                            // 添加长度字段编码器
                            p.addLast(new LengthFieldPrepender(4));
                            // 添加空闲状态处理器

                            // 添加编解码器
                            p.addLast(new MessageEncoder());
                            p.addLast(new MessageDecoder());
                            p.addLast(new IdleStateHandler(
                                    nettyConfig.getReaderIdleTimeSeconds(),
                                    nettyConfig.getWriterIdleTimeSeconds(),
                                    nettyConfig.getAllIdleTimeSeconds(),
                                    TimeUnit.SECONDS
                            ));
                            p.addLast(new HeartbeatHandler());
                            // 添加业务处理器
                            p.addLast(BUSINESS_GROUP, serverBusinessHandler);

                            log.debug("Channel initialized: {}", ch);
                        }
                    });

            // 绑定端口，开始接收进来的连接
            channelFuture = b.bind(nettyConfig.getHost(), nettyConfig.getPort()).sync();
            log.info("Netty server started on port: {}", nettyConfig.getPort());

            // 等待服务器 socket 关闭
            channelFuture.channel().closeFuture().addListener(future -> {
                log.info("Netty server stopped");
            });

        } catch (Exception e) {
            log.error("Netty server error", e);
            stop();
        }
    }

    @PreDestroy
    public void stop() {
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        log.info("Netty server resources released");
    }
}

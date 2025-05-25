package com.roc.netty.client.netty;

import com.roc.netty.client.codec.MessageDecoder;
import com.roc.netty.client.codec.MessageEncoder;
import com.roc.netty.client.config.NettyConfig;
import com.roc.netty.client.handler.ClientBusinessHandler;
import com.roc.netty.client.handler.HeartbeatHandler;
import com.roc.netty.client.protocol.MessageProtocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Netty客户端实现
 */
@Slf4j
@Component
public class NettyClient {
    // 最大帧长度
    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1MB
    private static final int LENGTH_FIELD_OFFSET = 0;
    private static final int LENGTH_FIELD_LENGTH = 4;
    private static final int LENGTH_ADJUSTMENT = 0;
    private static final int INITIAL_BYTES_TO_STRIP = 4;

    private final ClientBusinessHandler clientBusinessHandler;
    private final HeartbeatHandler heartbeatHandler;
    private final NettyConfig nettyConfig;
    private final NioEventLoopGroup workerGroup;
    private final ExecutorService connectionExecutor;

    private volatile Channel channel;
    private volatile boolean running = true;

    public NettyClient(NettyConfig nettyConfig, ClientBusinessHandler clientBusinessHandler, HeartbeatHandler heartbeatHandler) {
        this.nettyConfig = nettyConfig;
        this.clientBusinessHandler = clientBusinessHandler;
        this.heartbeatHandler = heartbeatHandler;

        // 初始化工作线程组
        this.workerGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("netty-client-worker"));
        this.connectionExecutor = Executors.newSingleThreadExecutor(new DefaultThreadFactory("netty-client-connector"));
    }

    /**
     * 启动连接循环
     */
    @PostConstruct
    private void startConnectionLoop() {
        connectionExecutor.execute(() -> {
            NettyConfig.ClientConfig config = nettyConfig.getClient();

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    connectToServer(config);
                    if (running) {
                        TimeUnit.SECONDS.sleep(config.getReconnectDelay());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Connection attempt failed: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * 连接到服务器
     */
    private void connectToServer(NettyConfig.ClientConfig config) throws InterruptedException {
        try {
            Bootstrap bootstrap = new Bootstrap();
            ChannelFuture future = bootstrap
                    .group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()

                                    .addLast(new LengthFieldBasedFrameDecoder(
                                            MAX_FRAME_LENGTH,
                                            LENGTH_FIELD_OFFSET,
                                            LENGTH_FIELD_LENGTH,
                                            LENGTH_ADJUSTMENT,
                                            INITIAL_BYTES_TO_STRIP))
                                    .addLast(new LengthFieldPrepender(4))
                                    .addLast(new MessageEncoder())
                                    .addLast(new MessageDecoder())
                                    .addLast(new IdleStateHandler(
                                            config.getReaderIdleTimeSeconds(),
                                            config.getWriterIdleTimeSeconds(),
                                            config.getAllIdleTimeSeconds(),
                                            TimeUnit.SECONDS))
                                    .addLast(heartbeatHandler)
                                    .addLast(clientBusinessHandler);
                        }
                    })
                    .connect(config.getHost(), config.getPort())
                    .sync();

            log.info("Connected to server: {}:{}", config.getHost(), config.getPort());
            this.channel = future.channel();

            // 等待连接关闭
            channel.closeFuture().sync();
            log.warn("Disconnected from server");

        } catch (Exception e) {
            if (running) {
                throw new RuntimeException("Connection failed", e);
            }
        }
    }

    /**
     * 发送消息到服务器
     *
     * @param message 消息内容
     * @return 是否发送成功
     */
    public boolean sendMessage(String message) {
        if (!isConnected()) {
            log.warn("Cannot send message: not connected to server");
            return false;
        }

        try {
            byte[] content = message.getBytes(StandardCharsets.UTF_8);
            MessageProtocol msg = new MessageProtocol();
            msg.setType((byte) 0);
            msg.setLength(1 + content.length);
            msg.setContent(content);

            channel.writeAndFlush(msg);
            log.debug("Message sent: {}", message);
            return true;
        } catch (Exception e) {
            log.error("Failed to send message", e);
            return false;
        }
    }

    /**
     * 检查客户端是否已连接
     */
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    /**
     * 关闭客户端
     */
    @PreDestroy
    public void shutdown() {
        running = false;

        if (channel != null) {
            channel.close();
        }

        connectionExecutor.shutdownNow();

        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS)
                    .addListener(future -> log.info("Netty client resources released"));
        }

        log.info("Netty client shutdown complete");
    }
}

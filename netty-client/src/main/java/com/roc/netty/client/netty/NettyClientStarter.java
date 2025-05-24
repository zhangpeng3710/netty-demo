package com.roc.netty.client.netty;

import com.roc.netty.client.config.NettyConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Netty客户端启动类
 */
@Slf4j
@Component
public class NettyClientStarter implements CommandLineRunner {

    private final NettyConfig nettyConfig;
    private final NettyClient nettyClient;
    private EventLoopGroup group;
    private ChannelFuture channelFuture;

    @Autowired
    public NettyClientStarter(NettyConfig nettyConfig, NettyClient nettyClient) {
        this.nettyConfig = nettyConfig;
        this.nettyClient = nettyClient;
    }

    private volatile boolean running = true;
    
    @Override
    public void run(String... args) throws Exception {
        NettyConfig.ClientConfig clientConfig = nettyConfig.getClient();
        NettyConfig.ThreadPoolConfig threadConfig = nettyConfig.getThread();
        
        // 配置客户端NIO线程组
        ThreadFactory threadFactory = new DefaultThreadFactory(threadConfig.getClientNamePrefix());
        group = new NioEventLoopGroup(1, threadFactory);
        
        // 连接服务器
        connectToServer();
        
        // 添加JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }
    
    /**
     * 连接到服务器
     */
    private void connectToServer() {
        NettyConfig.ClientConfig clientConfig = nettyConfig.getClient();
        
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(group)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientConfig.getConnectTimeout())
                        .handler(nettyClient);
                
                log.info("Connecting to server: {}:{}", clientConfig.getHost(), clientConfig.getPort());
                channelFuture = bootstrap.connect(clientConfig.getHost(), clientConfig.getPort()).sync();
                
                log.info("Netty client connected to server: {}:{}", clientConfig.getHost(), clientConfig.getPort());
                
                // 等待连接关闭
                channelFuture.channel().closeFuture().sync();
                
                if (running) {
                    log.warn("Connection to server lost, will try to reconnect in {} seconds...", 
                            clientConfig.getReconnectDelay());
                    TimeUnit.SECONDS.sleep(clientConfig.getReconnectDelay());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Netty client was interrupted");
                break;
            } catch (Exception e) {
                if (running) {
                    log.error("Failed to connect to server, will retry in {} seconds", 
                            clientConfig.getReconnectDelay(), e);
                    try {
                        TimeUnit.SECONDS.sleep(clientConfig.getReconnectDelay());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
        }
    }
    }

    /**
     * 关闭客户端
     */
    @PreDestroy
    public void destroy() {
        shutdown();
    }
    
    /**
     * 关闭资源
     */
    private void shutdown() {
        log.info("Shutting down Netty client...");
        running = false;
        
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        
        if (group != null) {
            try {
                group.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while shutting down client");
            }
        }
        
        log.info("Netty client shutdown complete.");
    }
}

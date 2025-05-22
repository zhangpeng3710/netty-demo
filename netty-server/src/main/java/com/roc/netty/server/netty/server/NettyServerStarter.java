package com.roc.netty.server.netty.server;

import com.roc.netty.server.config.NettyConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.concurrent.ThreadFactory;

/**
 * Netty服务器启动类
 */
@Slf4j
@Component
public class NettyServerStarter implements CommandLineRunner {

    private final NettyConfig nettyConfig;
    private final NettyServer nettyServer;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    @Autowired
    public NettyServerStarter(NettyConfig nettyConfig, NettyServer nettyServer) {
        this.nettyConfig = nettyConfig;
        this.nettyServer = nettyServer;
    }

    @Override
    public void run(String... args) throws Exception {
        NettyConfig.ServerConfig serverConfig = nettyConfig.getServer();
        NettyConfig.ThreadPoolConfig threadConfig = nettyConfig.getThread();
        
        // 配置服务端的NIO线程组
        ThreadFactory bossThreadFactory = new DefaultThreadFactory(threadConfig.getBossNamePrefix());
        ThreadFactory workerThreadFactory = new DefaultThreadFactory(threadConfig.getWorkerNamePrefix());
        
        bossGroup = new NioEventLoopGroup(serverConfig.getBossThreads(), bossThreadFactory);
        workerGroup = new NioEventLoopGroup(serverConfig.getWorkerThreads(), workerThreadFactory);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // 服务器可连接队列大小
                    .option(ChannelOption.SO_BACKLOG, serverConfig.getBacklog())
                    // 开启TCP底层心跳机制
                    .childOption(ChannelOption.SO_KEEPALIVE, serverConfig.isKeepAlive())
                    // 开启Nagle算法，要求高实时性时关闭
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(nettyServer);

            // 绑定地址和端口
            InetSocketAddress address = new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort());
            
            // 绑定端口，同步等待成功
            ChannelFuture future = bootstrap.bind(address).sync();
            
            log.info("Netty server started on {}:{}", serverConfig.getHost(), serverConfig.getPort());
            log.info("Server configuration: {}", serverConfig);

            // 等待服务端监听端口关闭
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("Netty server was interrupted", e);
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            log.error("Failed to start Netty server", e);
            throw e;
        } finally {
            // 优雅退出，释放线程池资源
            shutdownGracefully();
        }
    }

    /**
     * 优雅关闭服务器
     */
    @PreDestroy
    public void destroy() {
        log.info("Shutting down Netty server...");
        shutdownGracefully();
        log.info("Netty server shutdown complete.");
    }
    
    /**
     * 优雅关闭资源
     */
    private void shutdownGracefully() {
        if (bossGroup != null) {
            log.info("Shutting down boss group...");
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            log.info("Shutting down worker group...");
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
    }
}

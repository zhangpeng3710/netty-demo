package com.roc.netty.server.netty.server;

import com.roc.netty.server.codec.MessageEncoder;
import com.roc.netty.server.codec.MessageDecoder;
import com.roc.netty.server.handler.HeartbeatHandler;
import com.roc.netty.server.handler.ServerBusinessHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Netty服务端Channel初始化
 */
@Slf4j
@Component
public class NettyServer extends ChannelInitializer<SocketChannel> {
    
    // 业务线程池，用于处理耗时的业务逻辑
    private static final EventExecutorGroup BUSINESS_GROUP = new DefaultEventExecutorGroup(16);
    
    private final ServerBusinessHandler serverBusinessHandler;
    private final HeartbeatHandler heartbeatHandler;
    
    @Value("${netty.server.reader-idle-time-seconds:5}")
    private int readerIdleTimeSeconds;
    
    @Value("${netty.server.writer-idle-time-seconds:4}")
    private int writerIdleTimeSeconds;
    
    @Value("${netty.server.all-idle-time-seconds:0}")
    private int allIdleTimeSeconds;
    
    // 最大帧长度
    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1MB
    
    @Autowired
    public NettyServer(ServerBusinessHandler serverBusinessHandler, HeartbeatHandler heartbeatHandler) {
        this.serverBusinessHandler = serverBusinessHandler;
        this.heartbeatHandler = heartbeatHandler;
    }
    
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        
        // 添加日志处理器
        if (log.isDebugEnabled()) {
            pipeline.addLast("logging", new LoggingHandler(LogLevel.DEBUG));
        }
        
        // 添加基于长度的帧解码器，解决TCP粘包/拆包问题
        // 参数说明：
        // maxFrameLength: 最大帧长度
        // lengthFieldOffset: 长度字段的偏移量
        // lengthFieldLength: 长度字段的长度
        // lengthAdjustment: 长度调整值
        // initialBytesToStrip: 需要跳过的字节数
        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                MAX_FRAME_LENGTH, 
                0, 4, 0, 4));
        
        // 添加长度字段编码器
        pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
        
        // 添加自定义协议编解码器
        pipeline.addLast("decoder", new MessageDecoder());
        pipeline.addLast("encoder", new MessageEncoder());
        
        // 添加空闲状态处理器
        pipeline.addLast("idleStateHandler", new IdleStateHandler(
                readerIdleTimeSeconds,
                writerIdleTimeSeconds,
                allIdleTimeSeconds,
                TimeUnit.SECONDS));
        
        // 添加心跳处理器
        pipeline.addLast("heartbeatHandler", heartbeatHandler);
        
        // 添加业务处理器，使用业务线程池处理耗时的业务逻辑
        pipeline.addLast(BUSINESS_GROUP, "businessHandler", serverBusinessHandler);
        
        log.debug("Channel initialized: {}", ch);
    }
}

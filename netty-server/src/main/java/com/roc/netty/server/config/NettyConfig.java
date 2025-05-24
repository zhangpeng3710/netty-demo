package com.roc.netty.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Netty配置属性
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "netty", ignoreUnknownFields = false)
public class NettyConfig {

    @NotBlank
    private String host = "0.0.0.0";  // 服务器绑定地址

    @NotNull
    @Min(1024)
    @Max(65535)
    private int port = 8888;  // 服务器端口

    @Min(1)
    private int bossThreads = 1;  // boss线程数

    @Min(0)
    private int workerThreads = 0;  // worker线程数，0表示使用Netty默认值

    private boolean keepAlive = true;  // 是否保持长连接

    @Min(128)
    private int backlog = 128;  // 最大等待连接数

    @Min(0)
    private int readerIdleTimeSeconds = 5;  // 读空闲时间(秒)

    @Min(0)
    private int writerIdleTimeSeconds = 4;  // 写空闲时间(秒)

    @Min(0)
    private int allIdleTimeSeconds = 0;  // 所有类型空闲时间(秒)

    @Min(1)
    private int maxLostHeartbeat = 3;  // 最大丢失心跳次数

    private String bossNamePrefix = "netty-boss-";

    private String workerNamePrefix = "netty-worker-";

}

package com.roc.netty.client.config;

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

    @Valid
    private ClientConfig client = new ClientConfig();

    @Valid
    private ThreadPoolConfig thread = new ThreadPoolConfig();


    /**
     * 客户端配置
     */
    @Data
    @Validated
    public static class ClientConfig {
        @NotBlank
        private String host = "127.0.0.1";  // 服务器地址

        @NotNull
        @Min(1024)
        @Max(65535)
        private int port = 8888;  // 服务器端口

        @Min(1)
        private int reconnectDelay = 5;  // 重连延迟(秒)

        @Min(1000)
        private int connectTimeout = 5000;  // 连接超时(毫秒)

        @Min(0)
        private int readerIdleTimeSeconds = 0;  // 读空闲时间(秒)

        @Min(0)
        private int writerIdleTimeSeconds = 30;  // 写空闲时间(秒)

        @Min(0)
        private int allIdleTimeSeconds = 0;  // 所有类型空闲时间(秒)

        @Min(1)
        private int maxLostHeartbeat = 3;  // 最大丢失心跳次数

    }

    /**
     * 线程池配置
     */
    @Data
    public static class ThreadPoolConfig {
        private String bossNamePrefix = "netty-boss-";
        private String workerNamePrefix = "netty-worker-";
        private String clientNamePrefix = "netty-client-";
    }
}

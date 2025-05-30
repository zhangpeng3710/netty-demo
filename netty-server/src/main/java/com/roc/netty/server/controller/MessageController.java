package com.roc.netty.server.controller;

import com.roc.netty.server.dto.ApiResponse;
import com.roc.netty.server.dto.MessageRequest;
import com.roc.netty.server.protocol.MessageProtocol;
import com.roc.netty.server.service.ClientConnectionService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 消息控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Validated
public class MessageController {

    private final ClientConnectionService clientConnectionService;


    /**
     * 向指定客户端发送消息-并发
     */
    @PostMapping("/send/concurrently")
    public ApiResponse<String> sendMessageConcurrently(@Valid @RequestBody MessageRequest request) {
        String clientId = request.getClientId();
        String content = request.getContent();

        // 创建固定大小的线程池
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        try {
            // 创建10个并发任务
            for (int i = 0; i < 10; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // 创建消息协议对象
                        MessageProtocol message = new MessageProtocol();
                        message.setType((byte) 9); // 测试消息
                        String messageContent = content + " [Thread: " + Thread.currentThread().getName() + "]";
                        byte[] contentBytes = messageContent.getBytes(CharsetUtil.UTF_8);
                        message.setLength(1 + contentBytes.length);
                        message.setContent(contentBytes);

                        // 发送消息
                        Thread.sleep(100);
                        clientConnectionService.getChannel(clientId).writeAndFlush(message);
                        log.info("向客户端 {} 发送消息: {}", clientId, messageContent);
                    } catch (Exception e) {
                        log.error("发送消息时发生异常", e);
                        throw new RuntimeException(e);
                    }
                }, executorService);

                futures.add(future);

            }
            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            return ApiResponse.success("10条并发消息发送成功");

        } catch (Exception e) {
            log.error("向客户端发送消息失败: {}", clientId, e);
            return ApiResponse.error(500, "消息发送失败: " + e.getMessage());
        } finally {
            // 关闭线程池
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 向指定客户端发送消息-单线程顺序
     */
    @PostMapping("/send/sequentially")
    public ApiResponse<String> sendMessageSequentially(@Valid @RequestBody MessageRequest request) {
        String clientId = request.getClientId();
        String content = request.getContent();
        Channel channel = clientConnectionService.getChannel(clientId);


        try {
            // 使用Channel的eventLoop来确保消息按顺序发送
            channel.eventLoop().execute(() -> {
                for (int i = 0; i < 10; i++) {
                    try {
                        // 创建消息协议对象
                        MessageProtocol message = new MessageProtocol();
                        message.setType((byte) 9); // 测试消息
                        String messageContent = String.format("%s [Seq:%d]", content, i + 1);
                        byte[] contentBytes = messageContent.getBytes(CharsetUtil.UTF_8);
                        message.setLength(1 + contentBytes.length);
                        message.setContent(contentBytes);

                        // 发送消息并等待发送完成
                        ChannelFuture future = channel.writeAndFlush(message).sync();
                        if (future.isSuccess()) {
                            log.info("向客户端 {} 发送消息: {}", clientId, messageContent);
                        } else {
                            log.error("向客户端 {} 发送消息失败: {}", clientId, future.cause().getMessage());
                        }
                    } catch (Exception e) {
                        log.error("发送消息时发生异常", e);
                    }
                }
            });

            return ApiResponse.success("10条并发消息发送成功");
        } catch (Exception e) {
            log.error("向客户端发送消息失败: {}", clientId, e);
            return ApiResponse.error(500, "消息发送失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有已连接的客户端ID
     */
    @GetMapping("/clients")
    public ApiResponse<String[]> getConnectedClients() {
        String[] clientIds = clientConnectionService.getAllClientIds();
        return ApiResponse.success("获取客户端列表成功", clientIds);
    }

    /**
     * 获取当前连接数
     */
    @GetMapping("/count")
    public ApiResponse<Integer> getConnectionCount() {
        int count = clientConnectionService.getConnectionCount();
        return ApiResponse.success("获取连接数成功", count);
    }
}

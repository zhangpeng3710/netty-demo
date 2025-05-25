package com.roc.netty.server.service;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端连接服务
 */
@Service
public class ClientConnectionService {
    // 存储所有客户端连接
    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    // 存储客户端ID与Channel的映射关系
    private final Map<String, ChannelId> clientChannelMap = new ConcurrentHashMap<>();
    // 存储Channel与客户端ID的映射关系
    private final Map<ChannelId, String> channelClientMap = new ConcurrentHashMap<>();

    /**
     * 添加客户端连接
     */
    public void addConnection(String clientId, Channel channel) {
        if (clientId != null && channel != null) {
            channels.add(channel);
            clientChannelMap.put(clientId, channel.id());
            channelClientMap.put(channel.id(), clientId);
        }
    }

    /**
     * 移除客户端连接
     */
    public void removeConnection(Channel channel) {
        if (channel != null) {
            String clientId = channelClientMap.remove(channel.id());
            if (clientId != null) {
                clientChannelMap.remove(clientId);
            }
            channels.remove(channel);
        }
    }

    /**
     * 根据客户端ID获取Channel
     */
    public Channel getChannel(String clientId) {
        ChannelId channelId = clientChannelMap.get(clientId);
        if (channelId != null) {
            return channels.find(channelId);
        }
        return null;
    }

    /**
     * 获取所有连接的客户端ID
     */
    public String[] getAllClientIds() {
        return clientChannelMap.keySet().toArray(new String[0]);
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return clientChannelMap.size();
    }

    public void broadcast(String content) {
        channels.writeAndFlush(content);
    }
}

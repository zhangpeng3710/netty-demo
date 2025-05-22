package com.roc.netty.server.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import lombok.Data;

/**
 * 自定义消息协议，用于解决粘包拆包问题
 * 协议格式：
 * +--------+------+---------+
 * | Length | Type | Content |
 * | 4字节  | 1字节 | N字节   |
 * +--------+------+---------+
 */
@Data
public class MessageProtocol {
    // 消息类型
    public static final byte TYPE_BUSINESS = 0;  // 业务消息
    public static final byte TYPE_HEARTBEAT_REQ = 1;  // 心跳请求
    public static final byte TYPE_HEARTBEAT_RESP = 2; // 心跳响应

    /**
     * 消息长度 (不包括自身的4个字节)
     */
    private int length;

    /**
     * 消息类型
     */
    private byte type;

    /**
     * 消息内容
     */
    private byte[] content;

    /**
     * 创建业务消息
     */
    public static MessageProtocol createBusinessMessage(String content) {
        return createMessage(TYPE_BUSINESS, content);
    }

    /**
     * 创建心跳请求消息
     */
    public static MessageProtocol createHeartbeatRequest() {
        return createMessage(TYPE_HEARTBEAT_REQ, "");
    }

    /**
     * 创建心跳响应消息
     */
    public static MessageProtocol createHeartbeatResponse() {
        return createMessage(TYPE_HEARTBEAT_RESP, "");
    }

    /**
     * 创建消息
     */
    private static MessageProtocol createMessage(byte type, String content) {
        MessageProtocol message = new MessageProtocol();
        message.setType(type);
        if (content != null && !content.isEmpty()) {
            byte[] contentBytes = content.getBytes(CharsetUtil.UTF_8);
            message.setContent(contentBytes);
            // 长度 = 类型(1字节) + 内容长度
            message.setLength(1 + contentBytes.length);
        } else {
            message.setContent(new byte[0]);
            // 只有类型，没有内容
            message.setLength(1);
        }
        return message;
    }

    /**
     * 将消息转换为ByteBuf
     */
    public ByteBuf toByteBuf() {
        // 分配缓冲区：长度(4) + 类型(1) + 内容
        ByteBuf buffer = Unpooled.buffer(4 + 1 + (content != null ? content.length : 0));
        buffer.writeInt(length);
        buffer.writeByte(type);
        if (content != null && content.length > 0) {
            buffer.writeBytes(content);
        }
        return buffer;
    }

    /**
     * 获取消息内容为字符串
     */
    public String getContentAsString() {
        if (content == null || content.length == 0) {
            return "";
        }
        return new String(content, CharsetUtil.UTF_8);
    }
}

package com.roc.netty.server.protocol;

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
     * 获取消息内容为字符串
     */
    public String getContentAsString() {
        if (content == null || content.length == 0) {
            return "";
        }
        return new String(content, CharsetUtil.UTF_8);
    }
}

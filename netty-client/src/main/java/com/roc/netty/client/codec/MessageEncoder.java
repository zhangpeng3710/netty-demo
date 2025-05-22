package com.roc.netty.client.codec;

import com.roc.netty.client.protocol.MessageProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息编码器
 * 将MessageProtocol对象编码为字节流
 */
@Slf4j
public class MessageEncoder extends MessageToByteEncoder<MessageProtocol> {

    @Override
    protected void encode(ChannelHandlerContext ctx, MessageProtocol msg, ByteBuf out) throws Exception {
        if (msg == null) {
            log.warn("Message is null, skip encoding");
            return;
        }

        try {
            // 写入消息长度 (4字节)
            out.writeInt(msg.getLength());
            
            // 写入消息类型 (1字节)
            out.writeByte(msg.getType());
            
            // 写入消息内容
            if (msg.getContent() != null && msg.getContent().length > 0) {
                out.writeBytes(msg.getContent());
            }
            
            if (log.isDebugEnabled()) {
                log.debug("Encoded message - Type: {}, Length: {}, Content: {}", 
                        msg.getType(), msg.getLength(), msg.getContentAsString());
            }
        } catch (Exception e) {
            log.error("Encode message error: {}", e.getMessage(), e);
            throw e;
        }
    }
}

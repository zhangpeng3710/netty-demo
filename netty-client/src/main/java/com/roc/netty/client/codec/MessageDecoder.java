package com.roc.netty.client.codec;

import com.roc.netty.client.protocol.MessageProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 消息解码器
 * 将字节流解码为MessageProtocol对象
 */
@Slf4j
public class MessageDecoder extends ByteToMessageDecoder {

    // 最小长度：长度字段(4) + 类型(1)
    private static final int MIN_LENGTH = 5;
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 可读长度小于最小长度，等待更多数据
        if (in.readableBytes() < MIN_LENGTH) {
            return;
        }
        
        // 标记当前读取位置
        in.markReaderIndex();
        
        // 读取消息长度 (不包括长度字段自身的4个字节)
        int length = in.readInt();
        
        // 检查消息是否完整
        if (in.readableBytes() < length) {
            // 数据不完整，重置读取位置，等待更多数据
            in.resetReaderIndex();
            return;
        }
        
        try {
            // 读取消息类型
            byte type = in.readByte();
            
            // 读取消息内容
            byte[] content = null;
            int contentLength = length - 1; // 减去类型字段的1个字节
            if (contentLength > 0) {
                content = new byte[contentLength];
                in.readBytes(content);
            }
            
            // 构建消息对象
            MessageProtocol message = new MessageProtocol();
            message.setLength(length);
            message.setType(type);
            message.setContent(content);
            
            // 添加到输出列表，传递给下一个handler
            out.add(message);
            
            if (log.isDebugEnabled()) {
                log.debug("Decoded message - Type: {}, Length: {}, Content: {}", 
                        type, length, message.getContentAsString());
            }
            
        } catch (Exception e) {
            log.error("Decode message error: {}", e.getMessage(), e);
            throw e;
        }
    }
}

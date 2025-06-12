package com.roc.netty.client.constant;

/**
 * @Description
 * @Author: Zhang Peng
 * @Date: 2025/6/11
 */
public class Constants {
    public static final byte WELCOME_MESSAGE_TYPE = 0;
    public static final byte HEARTBEAT_REQUEST = 1;
    public static final byte HEARTBEAT_RESPONSE = 2;
    public static final byte BUSINESS_MESSAGE_REQUEST = 3;
    public static final byte BUSINESS_MESSAGE_RESPONSE = 4;
    public static final byte FILE_SEND_TO_SERVER_REQUEST = 5;
    public static final byte FILE_SEND_TO_SERVER_RESPONSE = 6;
    public static final byte FILE_SEND_TO_CLIENT_REQUEST = 7;
    public static final byte FILE_SEND_TO_CLIENT_RESPONSE = 8;
}


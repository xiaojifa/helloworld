package com.hzy.blog.entity;

import lombok.Data;

/**
 * @author Fa Xiaoji 14439
 * @date 2024/5/4 13:59
 */
@Data
public class SocketMsg {
    /**
     * 聊天类型 0 群聊 1 单聊
     **/
    private int type;
    /**
     * 发送者
     **/
    private String sendOutUser;
    /**
     * 接受者
     **/
    private String receiveUser;
    /**
     * 消息
     **/
    private String msg;
}

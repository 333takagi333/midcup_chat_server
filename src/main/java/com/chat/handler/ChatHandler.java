package com.chat.handler;

import com.chat.protocol.ChatPrivateReceive;
import com.chat.protocol.ChatPrivateSend;
import com.chat.utils.OnlineUserManager;
import com.google.gson.Gson;

import java.io.PrintWriter;

public class ChatHandler {

    private final Gson gson = new Gson();

    /**
     * 处理私聊消息（协议：chat_private_send）
     * - 入参：ChatPrivateSend（from/to/content/timestamp）
     * - 行为：向目标用户在线连接推送 ChatPrivateReceive
     * - 返回：是否处理成功
     */
    public boolean handle(ChatPrivateSend chatRequest) {
        if (chatRequest == null) return false;
        String from = chatRequest.getFrom();
        String to = chatRequest.getTo();
        String content = chatRequest.getContent();

        if (from == null || from.isEmpty() || to == null || to.isEmpty() || content == null || content.isEmpty()) {
            System.out.println("[CHAT] 无效的消息字段：from/to/content 不能为空");
            return false;
        }

        System.out.println("发送者 " + from + " 到 " + to
                + " : " + content + "，已转发");

        // 若接收方在线则推送
        PrintWriter targetOut = OnlineUserManager.getUserOutput(to);
        if (targetOut != null) {
            ChatPrivateReceive rec = new ChatPrivateReceive(from, to, content, System.currentTimeMillis());
            targetOut.println(gson.toJson(rec));
        }

        return true;
    }
}
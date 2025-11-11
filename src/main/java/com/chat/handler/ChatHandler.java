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
     * - 入参：ChatPrivateSend（fromUserId/toUserId/content/contentType/timestamp）
     * - 行为：向目标用户在线连接推送 ChatPrivateReceive
     * - 返回：是否处理成功
     */
    public boolean handle(ChatPrivateSend chatRequest) {
        if (chatRequest == null) return false;
        Long fromUserId = chatRequest.getFromUserId();
        Long toUserId = chatRequest.getToUserId();
        String content = chatRequest.getContent();
        String contentType = chatRequest.getContentType();

        if (fromUserId == null || toUserId == null || content == null || content.isEmpty()) {
            System.out.println("[CHAT] 无效的消息字段：fromUserId/toUserId/content 不能为空");
            return false;
        }

        System.out.println("发送者 " + fromUserId + " 到 " + toUserId
                + " : " + content + "，已转发");

        // 若接收方在线则推送
        PrintWriter targetOut = OnlineUserManager.getUserOutput(toUserId);
        if (targetOut != null) {
            ChatPrivateReceive rec = new ChatPrivateReceive(fromUserId, toUserId, content, contentType, System.currentTimeMillis());
            targetOut.println(gson.toJson(rec));
        }

        return true;
    }
}
package com.chat.handler;

import com.chat.protocol.ChatPrivateReceive;
import com.chat.protocol.ChatPrivateSend;
import com.chat.core.ChatService;
import com.chat.utils.OnlineUserManager;
import com.google.gson.Gson;

import java.io.PrintWriter;

public class ChatHandler {

    private final Gson gson = new Gson();
    private final ChatService chatService = new ChatService(); // 新增ChatService依赖

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
        Long timestamp = chatRequest.getTimestamp(); // 使用Long类型

        if (fromUserId == null || toUserId == null || content == null || content.isEmpty()) {
            System.out.println("[CHAT] 无效的消息字段：fromUserId/toUserId/content 不能为空");
            return false;
        }

        System.out.println("发送者 " + fromUserId + " 到 " + toUserId
                + " : " + content + "，已转发");

        // ========== 修复点：保存消息到数据库 ==========
        try {
            boolean saved = chatService.savePrivateMessage(
                    fromUserId,
                    toUserId,
                    content,
                    contentType != null ? contentType : "text",
                    chatRequest.getFileUrl(),
                    chatRequest.getFileSize(),
                    chatRequest.getFileName(),
                    timestamp != null ? timestamp : System.currentTimeMillis() // 修复这里
            );

            if (!saved) {
                System.out.println("[CHAT] 警告：保存消息到数据库失败，但继续推送");
            } else {
                System.out.println("[CHAT] 消息已保存到数据库");
            }
        } catch (Exception e) {
            System.err.println("[CHAT] 保存消息异常: " + e.getMessage());
            e.printStackTrace();
        }
        // ========== 修复结束 ==========

        // 若接收方在线则推送
        PrintWriter targetOut = OnlineUserManager.getUserOutput(toUserId);
        if (targetOut != null) {
            // 使用timestamp变量，它已经是Long类型
            Long sendTimestamp = timestamp != null ? timestamp : System.currentTimeMillis();
            ChatPrivateReceive rec = new ChatPrivateReceive(fromUserId, toUserId, content,
                    contentType != null ? contentType : "text", sendTimestamp);
            targetOut.println(gson.toJson(rec));
        }

        return true;
    }
}
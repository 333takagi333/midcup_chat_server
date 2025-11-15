package com.chat.handler;

import com.chat.protocol.ChatGroupReceive;
import com.chat.protocol.ChatGroupSend;
import com.chat.utils.DatabaseManager;
import com.chat.utils.OnlineUserManager;
import com.google.gson.Gson;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 群聊消息处理器
 */
public class ChatGroupHandler {

    private final Gson gson = new Gson();

    /**
     * 处理群聊消息发送
     */
    public boolean handle(ChatGroupSend chatRequest) {
        if (chatRequest == null) return false;

        Long fromUserId = chatRequest.getFromUserId();
        Long groupId = chatRequest.getGroupId();
        String content = chatRequest.getContent();
        String contentType = chatRequest.getContentType();

        if (fromUserId == null || groupId == null || content == null || content.isEmpty()) {
            System.out.println("[GROUP_CHAT] 无效的消息字段：fromUserId/groupId/content 不能为空");
            return false;
        }

        // 检查用户是否在群组中
        GroupHandler groupHandler = new GroupHandler();
        if (!groupHandler.isUserInGroup(fromUserId, groupId)) {
            System.out.println("[GROUP_CHAT] 用户 " + fromUserId + " 不在群组 " + groupId + " 中");
            return false;
        }

        // 保存消息到数据库
        Long messageId = saveGroupMessage(chatRequest);
        if (messageId == null) {
            System.out.println("[GROUP_CHAT] 保存群聊消息失败");
            return false;
        }

        System.out.println("[GROUP_CHAT] 群组 " + groupId + " 收到用户 " + fromUserId + " 的消息: " + content);

        // 向群组所有在线成员广播消息
        broadcastToGroupMembers(groupId, fromUserId, chatRequest, messageId);

        return true;
    }

    /**
     * 保存群聊消息到数据库
     */
    private Long saveGroupMessage(ChatGroupSend chatRequest) {
        String sql = "INSERT INTO message (sender_id, group_id, content, content_type, " +
                "file_url, file_size, file_name, timestamp, is_read) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, chatRequest.getFromUserId());
            stmt.setLong(2, chatRequest.getGroupId());
            stmt.setString(3, chatRequest.getContent());
            stmt.setString(4, chatRequest.getContentType());
            stmt.setString(5, chatRequest.getFileUrl());

            if (chatRequest.getFileSize() != null) {
                stmt.setLong(6, chatRequest.getFileSize());
            } else {
                stmt.setNull(6, java.sql.Types.BIGINT);
            }

            stmt.setString(7, chatRequest.getFileName());
            stmt.setLong(8, chatRequest.getTimestamp());

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[SAVE_GROUP_MSG] SQL error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 向群组所有在线成员广播消息
     */
    private void broadcastToGroupMembers(Long groupId, Long fromUserId,
                                         ChatGroupSend originalMessage, Long messageId) {
        // 获取群组所有成员
        String sql = "SELECT user_id FROM group_member WHERE group_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long memberId = rs.getLong("user_id");

                    // 不向发送者自己发送（可选，根据需求调整）
                    if (memberId.equals(fromUserId)) {
                        continue;
                    }

                    // 向在线成员发送消息
                    PrintWriter memberOut = OnlineUserManager.getUserOutput(memberId);
                    if (memberOut != null) {
                        ChatGroupReceive receiveMsg = createReceiveMessage(originalMessage, messageId);
                        memberOut.println(gson.toJson(receiveMsg));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[BROADCAST_GROUP] SQL error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建接收消息对象
     */
    private ChatGroupReceive createReceiveMessage(ChatGroupSend sendMsg, Long messageId) {
        ChatGroupReceive receiveMsg = new ChatGroupReceive();
        receiveMsg.setType("chat_group_receive");
        receiveMsg.setGroupId(sendMsg.getGroupId());
        receiveMsg.setFromUserId(sendMsg.getFromUserId());
        receiveMsg.setContent(sendMsg.getContent());
        receiveMsg.setContentType(sendMsg.getContentType());
        receiveMsg.setFileUrl(sendMsg.getFileUrl());
        receiveMsg.setFileSize(sendMsg.getFileSize());
        receiveMsg.setFileName(sendMsg.getFileName());
        receiveMsg.setTimestamp(sendMsg.getTimestamp());
        receiveMsg.setId(messageId);
        receiveMsg.setIsRead(0); // 默认未读

        return receiveMsg;
    }
}
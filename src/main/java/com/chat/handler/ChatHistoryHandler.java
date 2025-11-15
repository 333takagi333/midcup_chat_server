package com.chat.handler;

import com.chat.protocol.ChatHistoryRequest;
import com.chat.protocol.ChatHistoryResponse;
import com.chat.protocol.MessageType;
import com.chat.utils.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天历史记录处理器
 */
public class ChatHistoryHandler {

    /**
     * 处理聊天历史记录请求
     */
    public ChatHistoryResponse handle(ChatHistoryRequest request, Long currentUid) {
        ChatHistoryResponse response = new ChatHistoryResponse();
        response.setType(MessageType.CHAT_HISTORY_RESPONSE);

        if (request == null || currentUid == null) {
            response.setSuccess(false);
            response.setMessage("请求数据无效");
            return response;
        }

        String chatType = request.getChatType();
        if (chatType == null || (!chatType.equals("private") && !chatType.equals("group"))) {
            response.setSuccess(false);
            response.setMessage("聊天类型无效");
            return response;
        }

        try {
            List<ChatHistoryResponse.HistoryMessageItem> messages;

            if ("private".equals(chatType)) {
                messages = getPrivateChatHistory(request, currentUid);
            } else {
                messages = getGroupChatHistory(request, currentUid);
            }

            response.setMessages(messages);
            response.setChatType(chatType);
            response.setSuccess(true);
            response.setMessage("获取历史消息成功");

        } catch (Exception e) {
            System.err.println("[CHAT_HISTORY] Error: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("获取历史消息失败");
        }

        return response;
    }

    /**
     * 获取私聊历史记录
     */
    private List<ChatHistoryResponse.HistoryMessageItem> getPrivateChatHistory(
            ChatHistoryRequest request, Long currentUid) throws SQLException {

        List<ChatHistoryResponse.HistoryMessageItem> messages = new ArrayList<>();

        String sql = "SELECT id, sender_id, receiver_id, content, content_type, " +
                "file_url, file_size, file_name, timestamp, is_read " +
                "FROM message " +
                "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) " +
                "AND group_id IS NULL ";

        if (request.getBeforeTimestamp() != null) {
            sql += "AND timestamp < ? ";
        }

        sql += "ORDER BY timestamp DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int paramIndex = 1;
            stmt.setLong(paramIndex++, currentUid);
            stmt.setLong(paramIndex++, request.getTargetUserId());
            stmt.setLong(paramIndex++, request.getTargetUserId());
            stmt.setLong(paramIndex++, currentUid);

            if (request.getBeforeTimestamp() != null) {
                stmt.setLong(paramIndex++, request.getBeforeTimestamp());
            }

            stmt.setInt(paramIndex, request.getLimit() != null ? request.getLimit() : 50);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChatHistoryResponse.HistoryMessageItem message = new ChatHistoryResponse.HistoryMessageItem();
                    message.setId(rs.getLong("id"));
                    message.setSenderId(rs.getLong("sender_id"));
                    message.setReceiverId(rs.getLong("receiver_id"));
                    message.setContent(rs.getString("content"));
                    message.setContentType(rs.getString("content_type"));
                    message.setFileUrl(rs.getString("file_url"));

                    Long fileSize = rs.getLong("file_size");
                    if (!rs.wasNull()) {
                        message.setFileSize(fileSize);
                    }

                    message.setFileName(rs.getString("file_name"));
                    message.setTimestamp(rs.getLong("timestamp"));
                    message.setIsRead(rs.getInt("is_read"));

                    messages.add(message);
                }
            }
        }

        return messages;
    }

    /**
     * 获取群聊历史记录
     */
    private List<ChatHistoryResponse.HistoryMessageItem> getGroupChatHistory(
            ChatHistoryRequest request, Long currentUid) throws SQLException {

        List<ChatHistoryResponse.HistoryMessageItem> messages = new ArrayList<>();

        // 先检查用户是否在群组中
        GroupHandler groupHandler = new GroupHandler();
        if (!groupHandler.isUserInGroup(currentUid, request.getGroupId())) {
            throw new SecurityException("用户不在该群组中");
        }

        String sql = "SELECT id, sender_id, group_id, content, content_type, " +
                "file_url, file_size, file_name, timestamp, is_read " +
                "FROM message " +
                "WHERE group_id = ? ";

        if (request.getBeforeTimestamp() != null) {
            sql += "AND timestamp < ? ";
        }

        sql += "ORDER BY timestamp DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int paramIndex = 1;
            stmt.setLong(paramIndex++, request.getGroupId());

            if (request.getBeforeTimestamp() != null) {
                stmt.setLong(paramIndex++, request.getBeforeTimestamp());
            }

            stmt.setInt(paramIndex, request.getLimit() != null ? request.getLimit() : 50);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChatHistoryResponse.HistoryMessageItem message = new ChatHistoryResponse.HistoryMessageItem();
                    message.setId(rs.getLong("id"));
                    message.setSenderId(rs.getLong("sender_id"));
                    message.setGroupId(rs.getLong("group_id"));
                    message.setContent(rs.getString("content"));
                    message.setContentType(rs.getString("content_type"));
                    message.setFileUrl(rs.getString("file_url"));

                    Long fileSize = rs.getLong("file_size");
                    if (!rs.wasNull()) {
                        message.setFileSize(fileSize);
                    }

                    message.setFileName(rs.getString("file_name"));
                    message.setTimestamp(rs.getLong("timestamp"));
                    message.setIsRead(rs.getInt("is_read"));

                    messages.add(message);
                }
            }
        }

        return messages;
    }
}
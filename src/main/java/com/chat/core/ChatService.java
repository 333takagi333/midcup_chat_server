package com.chat.core;

import com.chat.protocol.ChatGroupSend;
import com.chat.protocol.ChatHistoryResponse;
import com.chat.utils.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天服务 - 处理聊天消息相关数据操作
 */
public class ChatService {

    /**
     * 获取私聊历史记录
     */
    public List<ChatHistoryResponse.HistoryMessageItem> getPrivateChatHistory(
            Long currentUid, Long targetUserId, Long beforeTimestamp, Integer limit) throws SQLException {

        List<ChatHistoryResponse.HistoryMessageItem> messages = new ArrayList<>();

        String sql = "SELECT id, sender_id, receiver_id, content, content_type, " +
                "file_url, file_size, file_name, timestamp, is_read " +
                "FROM message " +
                "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) " +
                "AND group_id IS NULL ";

        if (beforeTimestamp != null) {
            sql += "AND timestamp < ? ";
        }

        sql += "ORDER BY timestamp DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int paramIndex = 1;
            stmt.setLong(paramIndex++, currentUid);
            stmt.setLong(paramIndex++, targetUserId);
            stmt.setLong(paramIndex++, targetUserId);
            stmt.setLong(paramIndex++, currentUid);

            if (beforeTimestamp != null) {
                stmt.setLong(paramIndex++, beforeTimestamp);
            }

            stmt.setInt(paramIndex, limit != null ? limit : 50);

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
    public List<ChatHistoryResponse.HistoryMessageItem> getGroupChatHistory(
            Long groupId, Long beforeTimestamp, Integer limit) throws SQLException {

        List<ChatHistoryResponse.HistoryMessageItem> messages = new ArrayList<>();

        String sql = "SELECT id, sender_id, group_id, content, content_type, " +
                "file_url, file_size, file_name, timestamp, is_read " +
                "FROM message " +
                "WHERE group_id = ? ";

        if (beforeTimestamp != null) {
            sql += "AND timestamp < ? ";
        }

        sql += "ORDER BY timestamp DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int paramIndex = 1;
            stmt.setLong(paramIndex++, groupId);

            if (beforeTimestamp != null) {
                stmt.setLong(paramIndex++, beforeTimestamp);
            }

            stmt.setInt(paramIndex, limit != null ? limit : 50);

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

    /**
     * 保存私聊消息
     */
    public boolean savePrivateMessage(Long senderId, Long receiverId, String content,
                                      String contentType, String fileUrl, Long fileSize,
                                      String fileName, Long timestamp) {
        String sql = "INSERT INTO message (sender_id, receiver_id, content, content_type, " +
                "file_url, file_size, file_name, timestamp, is_read) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, senderId);
            stmt.setLong(2, receiverId);
            stmt.setString(3, content);
            stmt.setString(4, contentType);
            stmt.setString(5, fileUrl);

            if (fileSize != null) {
                stmt.setLong(6, fileSize);
            } else {
                stmt.setNull(6, java.sql.Types.BIGINT);
            }

            stmt.setString(7, fileName);
            stmt.setLong(8, timestamp);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[SAVE_PRIVATE_MESSAGE] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 保存群聊消息
     */
    public boolean saveGroupMessage(Long senderId, Long groupId, String content,
                                    String contentType, String fileUrl, Long fileSize,
                                    String fileName, Long timestamp) {
        String sql = "INSERT INTO message (sender_id, group_id, content, content_type, " +
                "file_url, file_size, file_name, timestamp, is_read) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, senderId);
            stmt.setLong(2, groupId);
            stmt.setString(3, content);
            stmt.setString(4, contentType);
            stmt.setString(5, fileUrl);

            if (fileSize != null) {
                stmt.setLong(6, fileSize);
            } else {
                stmt.setNull(6, java.sql.Types.BIGINT);
            }

            stmt.setString(7, fileName);
            stmt.setLong(8, timestamp);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[SAVE_GROUP_MESSAGE] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 保存群聊消息到数据库（基于 ChatGroupSend 对象）
     */
    public Long saveGroupMessage(ChatGroupSend chatRequest) {
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
     * 获取群组成员列表
     */
    public List<Long> getGroupMembers(Long groupId) {
        List<Long> memberIds = new ArrayList<>();

        String sql = "SELECT user_id FROM group_member WHERE group_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    memberIds.add(rs.getLong("user_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[GET_GROUP_MEMBERS] SQL error: " + e.getMessage());
            e.printStackTrace();
        }

        return memberIds;
    }

    /**
     * 获取未读消息数量
     */
    public int getUnreadMessageCount(Long userId) {
        String sql = "SELECT COUNT(*) as unread_count FROM message " +
                "WHERE receiver_id = ? AND is_read = 0 AND group_id IS NULL";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("unread_count");
                }
            }
        } catch (SQLException e) {
            System.err.println("[GET_UNREAD_COUNT] SQL error: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 标记消息为已读
     */
    public boolean markMessageAsRead(Long messageId, Long userId) {
        String sql = "UPDATE message SET is_read = 1 WHERE id = ? AND receiver_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, messageId);
            stmt.setLong(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[MARK_AS_READ] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 标记用户所有私聊消息为已读
     */
    public boolean markAllPrivateMessagesAsRead(Long userId, Long targetUserId) {
        String sql = "UPDATE message SET is_read = 1 " +
                "WHERE receiver_id = ? AND sender_id = ? AND group_id IS NULL AND is_read = 0";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, targetUserId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[MARK_ALL_AS_READ] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
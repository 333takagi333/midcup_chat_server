package com.chat.core;

import com.chat.protocol.ChatGroupSend;
import com.chat.protocol.ChatHistoryResponse;
import com.chat.utils.DatabaseManager;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天服务 - 处理聊天消息相关数据操作
 */
public class ChatService {

    // 日期格式化器，用于将Timestamp格式化为字符串
    private static final SimpleDateFormat datetimeFormatter =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // ===================== 添加缺少的方法 =====================

    /**
     * 保存群聊消息到数据库（基于 ChatGroupSend 对象） - 这个方法需要添加
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
            stmt.setString(4, chatRequest.getContentType() != null ? chatRequest.getContentType() : "text");
            stmt.setString(5, chatRequest.getFileUrl());

            if (chatRequest.getFileSize() != null) {
                stmt.setLong(6, chatRequest.getFileSize());
            } else {
                stmt.setNull(6, java.sql.Types.BIGINT);
            }

            stmt.setString(7, chatRequest.getFileName());
            stmt.setTimestamp(8, new Timestamp(chatRequest.getTimestamp()));

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
     * 保存群聊消息 - 简化版（客户端使用）
     */
    public boolean saveGroupMessage(Long senderId, Long groupId, String content,
                                    String contentType, String fileUrl, Long fileSize,
                                    String fileName, Long timestamp) {

        // 创建一个临时的ChatGroupSend对象
        ChatGroupSend chatRequest = new ChatGroupSend();
        chatRequest.setFromUserId(senderId);
        chatRequest.setGroupId(groupId);
        chatRequest.setContent(content);
        chatRequest.setContentType(contentType != null ? contentType : "text");
        chatRequest.setFileUrl(fileUrl);
        chatRequest.setFileSize(fileSize);
        chatRequest.setFileName(fileName);
        chatRequest.setTimestamp(timestamp != null ? timestamp : System.currentTimeMillis());

        return saveGroupMessage(chatRequest) != null;
    }

    /**
     * 获取私聊历史记录 - 修复版
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
                stmt.setTimestamp(paramIndex++, new Timestamp(beforeTimestamp));
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

                    Timestamp dbTimestamp = rs.getTimestamp("timestamp");
                    if (dbTimestamp != null) {
                        message.setTimestamp(formatTimestamp(dbTimestamp));
                    } else {
                        message.setTimestamp(getCurrentDatetimeString());
                    }

                    message.setIsRead(rs.getInt("is_read"));

                    messages.add(message);
                }
            }
        }

        return messages;
    }

    /**
     * 获取群聊历史记录 - 修复版
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
                stmt.setTimestamp(paramIndex++, new Timestamp(beforeTimestamp));
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

                    Timestamp dbTimestamp = rs.getTimestamp("timestamp");
                    if (dbTimestamp != null) {
                        message.setTimestamp(formatTimestamp(dbTimestamp));
                    } else {
                        message.setTimestamp(getCurrentDatetimeString());
                    }

                    message.setIsRead(rs.getInt("is_read"));

                    messages.add(message);
                }
            }
        }

        return messages;
    }

    // ===================== 以下是修复客户端响应问题的方案 =====================

    /**
     * 专门用于客户端历史消息请求的方法（避免重复处理）
     */
    public List<ChatHistoryResponse.HistoryMessageItem> getChatHistoryForClient(
            String chatType, Long targetId, Long currentUid, Long beforeTimestamp, Integer limit)
            throws Exception {

        if ("private".equals(chatType)) {
            return getPrivateChatHistory(currentUid, targetId, beforeTimestamp, limit);

        } else if ("group".equals(chatType)) {
            GroupService groupService = new GroupService();
            if (!groupService.isUserInGroup(currentUid, targetId)) {
                throw new SecurityException("用户不在该群组中");
            }
            return getGroupChatHistory(targetId, beforeTimestamp, limit);

        } else {
            throw new IllegalArgumentException("无效的聊天类型: " + chatType);
        }
    }

    // ===================== 以下是优化版本，避免SQL错误 =====================

    /**
     * 安全的获取私聊历史记录（带参数校验）
     */
    public List<ChatHistoryResponse.HistoryMessageItem> getPrivateChatHistorySafely(
            Long currentUid, Long targetUserId, Long beforeTimestamp, Integer limit) {

        try {
            return getPrivateChatHistory(currentUid, targetUserId, beforeTimestamp, limit);
        } catch (SQLException e) {
            System.err.println("[ERROR] 获取私聊历史记录失败: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 安全的获取群聊历史记录（带参数校验）
     */
    public List<ChatHistoryResponse.HistoryMessageItem> getGroupChatHistorySafely(
            Long groupId, Long beforeTimestamp, Integer limit) {

        try {
            return getGroupChatHistory(groupId, beforeTimestamp, limit);
        } catch (SQLException e) {
            System.err.println("[ERROR] 获取群聊历史记录失败: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // 其他方法保持不变...

    /**
     * 格式化Timestamp为字符串
     */
    private String formatTimestamp(Timestamp timestamp) {
        return datetimeFormatter.format(timestamp);
    }

    /**
     * 获取当前时间的字符串格式
     */
    private String getCurrentDatetimeString() {
        return datetimeFormatter.format(new Timestamp(System.currentTimeMillis()));
    }

    /**
     * 将Long时间戳转换为MySQL datetime格式的字符串
     */
    private String timestampToDatetimeString(long timestamp) {
        return datetimeFormatter.format(new Timestamp(timestamp));
    }

    // ===================== 需要在ChatHistoryHandler中调用的方法 =====================

    /**
     * 处理历史消息请求的完整方法
     */
    public ChatHistoryResponse processHistoryRequest(String chatType, Long targetId,
                                                     Long currentUid, Long beforeTimestamp,
                                                     Integer limit) {

        ChatHistoryResponse response = new ChatHistoryResponse();
        response.setChatType(chatType);

        try {
            List<ChatHistoryResponse.HistoryMessageItem> messages;

            if ("private".equals(chatType)) {
                messages = getPrivateChatHistorySafely(currentUid, targetId, beforeTimestamp, limit);
            } else if ("group".equals(chatType)) {
                GroupService groupService = new GroupService();
                if (!groupService.isUserInGroup(currentUid, targetId)) {
                    response.setSuccess(false);
                    response.setMessage("用户不在该群组中，无法查看历史消息");
                    return response;
                }
                messages = getGroupChatHistorySafely(targetId, beforeTimestamp, limit);
            } else {
                response.setSuccess(false);
                response.setMessage("无效的聊天类型");
                return response;
            }

            response.setMessages(messages);
            response.setSuccess(true);
            response.setMessage("获取历史消息成功");

        } catch (Exception e) {
            response.setSuccess(false);
            response.setMessage("获取历史消息失败: " + e.getMessage());
        }

        return response;
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

    /**
     * 保存私聊消息（确保这个方法存在）
     */
    public boolean savePrivateMessage(Long senderId, Long receiverId, String content,
                                      String contentType, String fileUrl, Long fileSize,
                                      String fileName, Long timestamp) {

        // 注意：这个方法需要返回消息ID，但这里保持原来的boolean返回值
        // 如果需要消息ID，可以修改这个方法
        String sql = "INSERT INTO message (sender_id, receiver_id, content, content_type, " +
                "file_url, file_size, file_name, timestamp, is_read) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, senderId);
            stmt.setLong(2, receiverId);
            stmt.setString(3, content);
            stmt.setString(4, contentType != null ? contentType : "text");
            stmt.setString(5, fileUrl);

            if (fileSize != null) {
                stmt.setLong(6, fileSize);
            } else {
                stmt.setNull(6, java.sql.Types.BIGINT);
            }

            stmt.setString(7, fileName);

            if (timestamp != null) {
                stmt.setTimestamp(8, new Timestamp(timestamp));
            } else {
                stmt.setTimestamp(8, new Timestamp(System.currentTimeMillis()));
            }

            int affected = stmt.executeUpdate();
            return affected > 0;

        } catch (SQLException e) {
            System.err.println("[SAVE_PRIVATE_MESSAGE] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
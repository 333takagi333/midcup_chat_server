package com.chat.core;

import com.chat.utils.DatabaseManager;
import java.sql.*;

/**
 * 文件服务 - 处理文件相关的数据库操作（支持Base64传输）
 */
public class FileService {

    /**
     * 保存文件信息到数据库
     */
    public boolean saveFileInfo(String fileId, String fileName, long fileSize,
                                String fileType, Long senderId, Long receiverId,
                                Long groupId, String filePath) {
        String sql = "INSERT INTO message (sender_id, receiver_id, group_id, content_type, " +
                "file_url, file_size, file_name, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, senderId);

            if (receiverId != null) {
                stmt.setLong(2, receiverId);
            } else {
                stmt.setNull(2, Types.BIGINT);
            }

            if (groupId != null) {
                stmt.setLong(3, groupId);
            } else {
                stmt.setNull(3, Types.BIGINT);
            }

            stmt.setString(4, "file"); // content_type设为'file'
            stmt.setString(5, filePath); // 存储文件路径
            stmt.setLong(6, fileSize);
            stmt.setString(7, fileName);

            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("[FILE_SERVICE] 保存文件信息失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取文件信息
     */
    public FileInfo getFileInfo(String fileId, Long userId) {
        String sql = "SELECT id, sender_id, receiver_id, group_id, file_url, file_size, file_name, " +
                "content_type, timestamp FROM message " +
                "WHERE file_url LIKE ? AND (sender_id = ? OR receiver_id = ? OR " +
                "(group_id IS NOT NULL AND ? IN (SELECT user_id FROM group_member WHERE group_id = message.group_id)))";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + fileId + "%");
            stmt.setLong(2, userId);
            stmt.setLong(3, userId);
            stmt.setLong(4, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    FileInfo info = new FileInfo();
                    info.setId(rs.getLong("id"));
                    info.setFileId(fileId);
                    info.setSenderId(rs.getLong("sender_id"));

                    Object receiverObj = rs.getObject("receiver_id");
                    info.setReceiverId(receiverObj != null ? ((Number) receiverObj).longValue() : null);

                    Object groupObj = rs.getObject("group_id");
                    info.setGroupId(groupObj != null ? ((Number) groupObj).longValue() : null);

                    info.setFileName(rs.getString("file_name"));
                    info.setFileSize(rs.getLong("file_size"));
                    info.setFileType(rs.getString("content_type"));
                    info.setDownloadUrl(rs.getString("file_url"));
                    info.setTimestamp(rs.getTimestamp("timestamp"));
                    return info;
                }
            }
        } catch (SQLException e) {
            System.err.println("[FILE_SERVICE] 获取文件信息失败: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 验证用户是否有权限下载文件
     */
    public boolean hasFilePermission(String fileId, Long userId) {
        // 简化验证：文件存在即可下载
        FileInfo info = getFileInfo(fileId, userId);
        if (info == null) {
            return false;
        }

        // 验证用户是否有权限
        if (info.getSenderId().equals(userId)) {
            return true; // 发送者可以下载
        }

        if (info.getReceiverId() != null && info.getReceiverId().equals(userId)) {
            return true; // 接收者可以下载
        }

        if (info.getGroupId() != null) {
            // 如果是群聊文件，需要检查用户是否是群成员
            return isGroupMember(info.getGroupId(), userId);
        }

        return false;
    }

    /**
     * 检查用户是否是群成员
     */
    private boolean isGroupMember(Long groupId, Long userId) {
        String sql = "SELECT COUNT(*) FROM group_member WHERE group_id = ? AND user_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, groupId);
            stmt.setLong(2, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("[FILE_SERVICE] 检查群成员失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 文件信息类
     */
    public static class FileInfo {
        private Long id;
        private String fileId;
        private String fileName;
        private long fileSize;
        private String fileType;
        private Long senderId;
        private Long receiverId;
        private Long groupId;
        private String downloadUrl;
        private Timestamp timestamp;

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public Long getSenderId() { return senderId; }
        public void setSenderId(Long senderId) { this.senderId = senderId; }
        public Long getReceiverId() { return receiverId; }
        public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
        public Timestamp getTimestamp() { return timestamp; }
        public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
    }
}
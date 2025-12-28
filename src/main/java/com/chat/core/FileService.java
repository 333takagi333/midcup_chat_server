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
     * 根据文件ID获取文件信息（主要方法）
     */
    public FileInfo getFileInfo(String fileId, Long userId) {
        // 如果fileId是格式为"file_数字"的字符串，提取数字作为消息ID
        if (fileId.startsWith("file_")) {
            try {
                Long messageId = Long.parseLong(fileId.substring(5));
                return getFileInfoByMessageId(messageId, userId);
            } catch (NumberFormatException e) {
                System.err.println("[FILE_SERVICE] 解析fileId失败: " + fileId);
                return null;
            }
        }

        // 如果fileId是纯数字，直接作为消息ID
        try {
            Long messageId = Long.parseLong(fileId);
            return getFileInfoByMessageId(messageId, userId);
        } catch (NumberFormatException e) {
            System.err.println("[FILE_SERVICE] fileId格式无效: " + fileId);
            return null;
        }
    }

    /**
     * 根据消息ID获取文件信息
     */
    private FileInfo getFileInfoByMessageId(Long messageId, Long userId) {
        String sql = "SELECT m.id, m.sender_id, m.receiver_id, m.group_id, m.file_url, " +
                "m.file_size, m.file_name, m.content_type, m.timestamp " +
                "FROM message m " +
                "WHERE m.id = ? AND m.content_type = 'file'";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, messageId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    FileInfo info = new FileInfo();
                    info.setId(rs.getLong("id"));
                    info.setFileId("file_" + rs.getLong("id"));
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

                    System.out.println("[FILE_SERVICE] 找到文件信息: " +
                            "id=" + info.getId() + ", " +
                            "fileName=" + info.getFileName() + ", " +
                            "groupId=" + info.getGroupId() + ", " +
                            "senderId=" + info.getSenderId());

                    return info;
                } else {
                    System.err.println("[FILE_SERVICE] 未找到文件信息: messageId=" + messageId);
                }
            }
        } catch (SQLException e) {
            System.err.println("[FILE_SERVICE] 根据消息ID获取文件信息失败: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 验证用户是否有权限下载文件（简化版本 - 取消严格权限验证）
     */
    public boolean hasFilePermission(String fileId, Long userId) {
        System.out.println("[FILE_SERVICE] 开始权限检查: fileId=" + fileId + ", userId=" + userId);

        // 获取文件信息
        FileInfo info = getFileInfo(fileId, userId);
        if (info == null) {
            System.err.println("[FILE_SERVICE] 权限检查失败: 未找到文件信息");
            return false;
        }

        // 如果是私聊文件
        if (info.getReceiverId() != null) {
            // 发送者或接收者都可以下载
            boolean allowed = info.getSenderId().equals(userId) ||
                    info.getReceiverId().equals(userId);
            System.out.println("[FILE_SERVICE] 私聊文件权限检查结果: " + allowed);
            return allowed;
        }

        // 如果是群聊文件
        if (info.getGroupId() != null) {
            // 检查用户是否是群成员
            boolean isMember = isGroupMember(info.getGroupId(), userId);
            System.out.println("[FILE_SERVICE] 群聊文件权限检查结果: " +
                    "groupId=" + info.getGroupId() + ", " +
                    "userId=" + userId + ", " +
                    "isMember=" + isMember);

            // 如果是群成员，允许下载
            if (isMember) {
                return true;
            }

            // 如果不是群成员，但用户是文件发送者，也允许下载
            if (info.getSenderId().equals(userId)) {
                System.out.println("[FILE_SERVICE] 用户是文件发送者，允许下载");
                return true;
            }

            return false;
        }

        System.err.println("[FILE_SERVICE] 未知的文件类型");
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
                    int count = rs.getInt(1);
                    System.out.println("[FILE_SERVICE] 群成员检查: groupId=" + groupId +
                            ", userId=" + userId + ", count=" + count);
                    return count > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("[FILE_SERVICE] 检查群成员失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 根据文件名和群组ID查找文件
     */
    public FileInfo findFileByGroupAndName(Long groupId, String fileName, Long userId) {
        String sql = "SELECT m.id, m.sender_id, m.receiver_id, m.group_id, m.file_url, " +
                "m.file_size, m.file_name, m.content_type, m.timestamp " +
                "FROM message m " +
                "WHERE m.group_id = ? AND m.file_name = ? AND m.content_type = 'file' " +
                "ORDER BY m.timestamp DESC LIMIT 1";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, groupId);
            stmt.setString(2, fileName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    FileInfo info = new FileInfo();
                    info.setId(rs.getLong("id"));
                    info.setFileId("file_" + rs.getLong("id"));
                    info.setSenderId(rs.getLong("sender_id"));
                    info.setGroupId(rs.getLong("group_id"));
                    info.setFileName(rs.getString("file_name"));
                    info.setFileSize(rs.getLong("file_size"));
                    info.setFileType(rs.getString("content_type"));
                    info.setDownloadUrl(rs.getString("file_url"));
                    info.setTimestamp(rs.getTimestamp("timestamp"));

                    System.out.println("[FILE_SERVICE] 通过文件名找到文件: " +
                            "fileName=" + fileName + ", " +
                            "groupId=" + groupId + ", " +
                            "fileId=" + info.getFileId());

                    return info;
                }
            }
        } catch (SQLException e) {
            System.err.println("[FILE_SERVICE] 通过文件名查找文件失败: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
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
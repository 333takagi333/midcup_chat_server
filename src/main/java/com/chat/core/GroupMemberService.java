package com.chat.core;

import com.chat.utils.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 群成员服务 - 处理群成员管理相关数据操作
 */
public class GroupMemberService {

    /**
     * 添加群成员
     */
    public boolean addGroupMember(Long groupId, Long userId, Long operatorId) throws SQLException {

        // 1. 检查操作者是否是群主
        if (!isGroupOwner(groupId, operatorId)) {
            throw new SecurityException("只有群主可以添加成员");
        }

        // 2. 检查用户是否已在群中
        if (isUserInGroup(groupId, userId)) {
            throw new IllegalArgumentException("用户已在群聊中");
        }

        // 3. 检查用户是否存在
        if (!userExists(userId)) {
            throw new IllegalArgumentException("用户不存在");
        }

        // 4. 添加成员
        String sql = "INSERT INTO group_member (group_id, user_id) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, groupId);
            stmt.setLong(2, userId);

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                // 记录群成员变动（可选）
                recordGroupMemberChange(groupId, userId, operatorId, "ADD");
                return true;
            }
        }

        return false;
    }

    /**
     * 批量添加群成员
     */
    public int addGroupMembers(Long groupId, List<Long> userIds, Long operatorId) throws SQLException {
        int successCount = 0;

        // 检查操作者权限
        if (!isGroupOwner(groupId, operatorId)) {
            throw new SecurityException("只有群主可以添加成员");
        }

        String sql = "INSERT INTO group_member (group_id, user_id) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (Long userId : userIds) {
                try {
                    // 检查用户是否已在群中
                    if (!isUserInGroup(groupId, userId) && userExists(userId)) {
                        stmt.setLong(1, groupId);
                        stmt.setLong(2, userId);
                        stmt.addBatch();
                        successCount++;
                    }
                } catch (SQLException e) {
                    System.err.println("添加用户 " + userId + " 到群组失败: " + e.getMessage());
                }
            }

            if (successCount > 0) {
                stmt.executeBatch();

                // 记录批量添加操作
                for (Long userId : userIds) {
                    if (isUserInGroup(groupId, userId)) {
                        recordGroupMemberChange(groupId, userId, operatorId, "ADD");
                    }
                }
            }
        }

        return successCount;
    }

    /**
     * 检查用户是否是群主
     */
    public boolean isGroupOwner(Long groupId, Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM group_info WHERE id = ? AND owner_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, groupId);
            stmt.setLong(2, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * 检查用户是否在群中
     */
    public boolean isUserInGroup(Long groupId, Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM group_member WHERE group_id = ? AND user_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, groupId);
            stmt.setLong(2, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * 获取群成员列表
     */
    public List<Long> getGroupMembers(Long groupId) throws SQLException {
        List<Long> members = new ArrayList<>();

        String sql = "SELECT user_id FROM group_member WHERE group_id = ? ORDER BY id";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, groupId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    members.add(rs.getLong("user_id"));
                }
            }
        }

        return members;
    }

    /**
     * 获取群成员详细信息（包含用户名）
     */
    public List<Object[]> getGroupMemberDetails(Long groupId) throws SQLException {
        List<Object[]> members = new ArrayList<>();

        String sql = "SELECT ua.uid, ua.username, up.avatar_url " +
                "FROM user_auth ua " +
                "LEFT JOIN user_profile up ON ua.uid = up.user_id " +
                "INNER JOIN group_member gm ON ua.uid = gm.user_id " +
                "WHERE gm.group_id = ? " +
                "ORDER BY gm.id";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, groupId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Object[] memberInfo = new Object[3];
                    memberInfo[0] = rs.getLong("uid");
                    memberInfo[1] = rs.getString("username");
                    memberInfo[2] = rs.getString("avatar_url");
                    members.add(memberInfo);
                }
            }
        }

        return members;
    }

    /**
     * 移除群成员
     */
    public boolean removeGroupMember(Long groupId, Long userId, Long operatorId) throws SQLException {
        // 检查权限：只有群主可以移除成员
        if (!isGroupOwner(groupId, operatorId)) {
            throw new SecurityException("只有群主可以移除成员");
        }

        // 不能移除自己
        if (userId.equals(operatorId)) {
            throw new IllegalArgumentException("群主不能移除自己");
        }

        String sql = "DELETE FROM group_member WHERE group_id = ? AND user_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, groupId);
            stmt.setLong(2, userId);

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                recordGroupMemberChange(groupId, userId, operatorId, "REMOVE");
                return true;
            }
        }

        return false;
    }

    /**
     * 检查用户是否存在
     */
    private boolean userExists(Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM user_auth WHERE uid = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * 记录群成员变动（可扩展为系统消息）
     */
    private void recordGroupMemberChange(Long groupId, Long userId, Long operatorId, String action) {
        // 这里可以记录成员变动日志，或者发送系统消息
        System.out.println(String.format("[GroupMember] 群组 %d: 用户 %d %s 了用户 %d",
                groupId, operatorId, action, userId));
    }

    /**
     * 获取群组成员数量
     */
    public int getGroupMemberCount(Long groupId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM group_member WHERE group_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, groupId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    /**
     * 检查用户是否可以添加成员到群组
     */
    public boolean canAddMember(Long groupId, Long userId) throws SQLException {
        // 群主可以添加成员，普通成员不能添加
        return isGroupOwner(groupId, userId);
    }
}
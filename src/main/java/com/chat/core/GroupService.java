package com.chat.core;

import com.chat.protocol.GroupListResponse;
import com.chat.utils.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 群组服务 - 处理群组相关数据操作
 */
public class GroupService {

    /**
     * 创建新的群聊
     */
    public Long createGroup(String groupName, Long ownerId) {
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);

            // 1. 检查群聊名称是否已存在
            String checkSql = "SELECT id FROM group_info WHERE name = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, groupName);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        return null; // 群聊名称已存在
                    }
                }
            }

            // 2. 插入群聊信息
            String insertGroupSql = "INSERT INTO group_info (name, owner_id) VALUES (?, ?)";
            Long groupId = null;

            try (PreparedStatement insertStmt = conn.prepareStatement(insertGroupSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, groupName);
                insertStmt.setLong(2, ownerId);
                int affected = insertStmt.executeUpdate();

                if (affected > 0) {
                    try (ResultSet rs = insertStmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            groupId = rs.getLong(1);
                        }
                    }
                }
            }

            if (groupId == null) {
                conn.rollback();
                return null;
            }

            // 3. 将创建者加入群聊成员
            String insertMemberSql = "INSERT INTO group_member (group_id, user_id) VALUES (?, ?)";
            try (PreparedStatement memberStmt = conn.prepareStatement(insertMemberSql)) {
                memberStmt.setLong(1, groupId);
                memberStmt.setLong(2, ownerId);
                memberStmt.executeUpdate();
            }

            conn.commit();
            return groupId;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("[ROLLBACK_ERROR] " + ex.getMessage());
                }
            }
            System.err.println("[CREATE_GROUP] SQL error: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("[CLOSE_CONN_ERROR] " + e.getMessage());
                }
            }
        }
    }

    /**
     * 获取群聊信息
     */
    public GroupListResponse.GroupItem getGroupInfo(Long groupId) {
        String sql = "SELECT id, name, avatar FROM group_info WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, groupId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    GroupListResponse.GroupItem group = new GroupListResponse.GroupItem();
                    group.setId(rs.getLong("id"));
                    group.setName(rs.getString("name"));
                    group.setAvatar(rs.getString("avatar"));
                    return group;
                }
            }
        } catch (SQLException e) {
            System.err.println("[GET_GROUP_INFO] SQL error: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 获取用户所在的群组列表
     */
    public List<GroupListResponse.GroupItem> getGroupList(Long userId) {
        List<GroupListResponse.GroupItem> groups = new ArrayList<>();

        String sql = "SELECT g.id, g.name, g.avatar " +
                "FROM group_info g " +
                "INNER JOIN group_member gm ON g.id = gm.group_id " +
                "WHERE gm.user_id = ? " +
                "ORDER BY g.name";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    GroupListResponse.GroupItem group = new GroupListResponse.GroupItem();
                    group.setId(rs.getLong("id"));
                    group.setName(rs.getString("name"));
                    group.setAvatar(rs.getString("avatar"));
                    groups.add(group);
                }
            }
        } catch (SQLException e) {
            System.err.println("[GET_GROUPS] SQL error: " + e.getMessage());
            e.printStackTrace();
        }

        return groups;
    }

    /**
     * 检查用户是否在群组中
     */
    public boolean isUserInGroup(Long userId, Long groupId) {
        String sql = "SELECT id FROM group_member WHERE user_id = ? AND group_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[CHECK_GROUP_MEMBER] SQL error: " + e.getMessage());
            return false;
        }
    }
}
package com.chat.core;

import com.chat.protocol.GroupListResponse;
import com.chat.utils.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 群组服务 - 处理群组相关数据操作
 */
public class GroupService {

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
package com.chat.core;

import com.chat.protocol.ExitGroupResponse;
import com.chat.protocol.GroupDetailResponse;
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

            // 2. 获取创建者的头像
            String avatarUrl = null;
            String getAvatarSql = "SELECT avatar_url FROM user_profile WHERE user_id = ?";
            try (PreparedStatement avatarStmt = conn.prepareStatement(getAvatarSql)) {
                avatarStmt.setLong(1, ownerId);
                try (ResultSet rs = avatarStmt.executeQuery()) {
                    if (rs.next()) {
                        avatarUrl = rs.getString("avatar_url");
                    }
                }
            }

            // 如果没有找到头像，使用默认头像
            if (avatarUrl == null || avatarUrl.isEmpty()) {
                avatarUrl = "default_group_avatar.png"; // 默认群头像
            }

            // 3. 插入群聊信息（包含头像）
            String insertGroupSql = "INSERT INTO group_info (name, owner_id, avatar) VALUES (?, ?, ?)";
            Long groupId = null;

            try (PreparedStatement insertStmt = conn.prepareStatement(insertGroupSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, groupName);
                insertStmt.setLong(2, ownerId);
                insertStmt.setString(3, avatarUrl); // 使用创建者头像
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

            // 4. 将创建者加入群聊成员
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

    // 在 GroupService 类中添加以下方法：

    /**
     * 获取群聊详细信息
     */
    public GroupDetailResponse getGroupDetail(Long groupId, Long userId) {
        GroupDetailResponse response = new GroupDetailResponse();

        // 1. 验证用户是否在群中
        if (!isUserInGroup(userId, groupId)) {
            response.setSuccess(false);
            response.setMessage("你不在该群聊中");
            return response;
        }

        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();

            // 2. 获取群基本信息
            String groupSql = "SELECT id, name, avatar FROM group_info WHERE id = ?";
            try (PreparedStatement groupStmt = conn.prepareStatement(groupSql)) {
                groupStmt.setLong(1, groupId);

                try (ResultSet rs = groupStmt.executeQuery()) {
                    if (rs.next()) {
                        response.setSuccess(true);
                        response.setGroupId(rs.getLong("id"));
                        response.setGroupName(rs.getString("name"));
                        response.setAvatarUrl(rs.getString("avatar"));

                        // 群公告（根据你的表结构，可能需要额外字段，这里假设从group_info获取）
                        response.setNotice("暂无群公告"); // 默认值
                    } else {
                        response.setSuccess(false);
                        response.setMessage("群聊不存在");
                        return response;
                    }
                }
            }

            // 3. 获取成员数量
            String countSql = "SELECT COUNT(*) as count FROM group_member WHERE group_id = ?";
            try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                countStmt.setLong(1, groupId);
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        response.setMemberCount(rs.getInt("count"));
                    }
                }
            }

            // 4. 获取当前用户在群中的昵称（从user_auth获取用户名）
            String userSql = "SELECT username FROM user_auth WHERE uid = ?";
            try (PreparedStatement userStmt = conn.prepareStatement(userSql)) {
                userStmt.setLong(1, userId);
                try (ResultSet rs = userStmt.executeQuery()) {
                    if (rs.next()) {
                        response.setMyNickname(rs.getString("username"));
                    }
                }
            }

            // 5. 获取当前用户的角色（群主或成员）
            String roleSql = "SELECT " +
                    "CASE WHEN g.owner_id = ? THEN 2 ELSE 0 END as role " +
                    "FROM group_info g " +
                    "WHERE g.id = ?";
            try (PreparedStatement roleStmt = conn.prepareStatement(roleSql)) {
                roleStmt.setLong(1, userId);
                roleStmt.setLong(2, groupId);
                try (ResultSet rs = roleStmt.executeQuery()) {
                    if (rs.next()) {
                        response.setRole(rs.getInt("role"));
                    }
                }
            }

            // 6. 获取群成员列表（简化版）
            List<GroupDetailResponse.GroupMember> members = getGroupMembers(groupId);
            response.setMembers(members);

            // 7. 获取群文件列表（从message表中获取文件类型的消息）
            List<GroupDetailResponse.GroupFile> files = getGroupFiles(groupId);
            response.setFiles(files);

        } catch (SQLException e) {
            System.err.println("[GET_GROUP_DETAIL] SQL error: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("数据库查询失败: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("[CLOSE_CONN_ERROR] " + e.getMessage());
                }
            }
        }

        return response;
    }

    /**
     * 获取群成员列表
     */
    private List<GroupDetailResponse.GroupMember> getGroupMembers(Long groupId) {
        List<GroupDetailResponse.GroupMember> members = new ArrayList<>();

        String sql = "SELECT ua.uid, ua.username, up.avatar_url, " +
                "CASE WHEN gi.owner_id = ua.uid THEN 2 ELSE 0 END as role " +
                "FROM group_member gm " +
                "JOIN user_auth ua ON gm.user_id = ua.uid " +
                "LEFT JOIN user_profile up ON ua.uid = up.user_id " +
                "JOIN group_info gi ON gm.group_id = gi.id " +
                "WHERE gm.group_id = ? " +
                "ORDER BY role DESC, ua.username";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, groupId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    GroupDetailResponse.GroupMember member = new GroupDetailResponse.GroupMember();
                    member.setUserId(rs.getLong("uid"));
                    member.setUsername(rs.getString("username"));
                    member.setAvatarUrl(rs.getString("avatar_url"));
                    member.setNickname(rs.getString("username")); // 默认使用用户名作为群昵称
                    member.setRole(rs.getInt("role"));
                    member.setStatus(0); // 默认离线状态，实际可以结合在线用户管理

                    members.add(member);
                }
            }
        } catch (SQLException e) {
            System.err.println("[GET_GROUP_MEMBERS] SQL error: " + e.getMessage());
            e.printStackTrace();
        }

        return members;
    }

    /**
     * 获取群文件列表
     */
    private List<GroupDetailResponse.GroupFile> getGroupFiles(Long groupId) {
        List<GroupDetailResponse.GroupFile> files = new ArrayList<>();

        // 从message表中查找文件类型的消息
        String sql = "SELECT m.file_name, m.file_size, m.timestamp, ua.username as uploader " +
                "FROM message m " +
                "JOIN user_auth ua ON m.sender_id = ua.uid " +
                "WHERE m.group_id = ? AND m.content_type = 'file' " +
                "ORDER BY m.timestamp DESC " +
                "LIMIT 20"; // 限制最多20个文件

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, groupId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    GroupDetailResponse.GroupFile file = new GroupDetailResponse.GroupFile();
                    file.setFileName(rs.getString("file_name"));
                    file.setFileSize(formatFileSize(rs.getLong("file_size")));
                    file.setUploadTime(rs.getTimestamp("timestamp").toString());
                    file.setUploader(rs.getString("uploader"));

                    files.add(file);
                }
            }
        } catch (SQLException e) {
            System.err.println("[GET_GROUP_FILES] SQL error: " + e.getMessage());
            e.printStackTrace();
        }

        return files;
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 用户退出群聊
     */
    public ExitGroupResponse exitGroup(Long groupId, Long userId) {
        ExitGroupResponse response = new ExitGroupResponse();

        // 1. 验证用户是否在群中
        if (!isUserInGroup(userId, groupId)) {
            response.setSuccess(false);
            response.setMessage("你不在该群聊中");
            return response;
        }

        // 2. 检查是否是群主（群主不能直接退出，需要先转让群主）
        boolean isOwner = false;
        String checkOwnerSql = "SELECT id FROM group_info WHERE id = ? AND owner_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkOwnerSql)) {
            stmt.setLong(1, groupId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                isOwner = rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[CHECK_OWNER] SQL error: " + e.getMessage());
            response.setSuccess(false);
            response.setMessage("检查群主身份失败: " + e.getMessage());
            return response;
        }

        if (isOwner) {
            response.setSuccess(false);
            response.setMessage("群主不能直接退出，请先转让群主");
            return response;
        }

        // 3. 执行退出操作
        String deleteSql = "DELETE FROM group_member WHERE group_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setLong(1, groupId);
            stmt.setLong(2, userId);

            int rowsDeleted = stmt.executeUpdate();
            if (rowsDeleted > 0) {
                response.setSuccess(true);
                response.setMessage("成功退出群聊");
                response.setGroupId(groupId);
                response.setUserId(userId);
            } else {
                response.setSuccess(false);
                response.setMessage("退出群聊失败");
            }
        } catch (SQLException e) {
            System.err.println("[EXIT_GROUP] SQL error: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("数据库操作失败: " + e.getMessage());
        }

        return response;
    }
}
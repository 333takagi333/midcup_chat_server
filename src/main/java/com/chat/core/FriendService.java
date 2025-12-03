package com.chat.core;

import com.chat.protocol.FriendListResponse;
import com.chat.protocol.FriendRequestListResponse;
import com.chat.utils.DatabaseManager;
import com.chat.utils.OnlineUserManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 好友服务 - 处理好友关系相关数据操作
 */
public class FriendService {

    /**
     * 获取指定用户的好友请求列表（收到的请求）
     */
    public List<FriendRequestListResponse.FriendRequestItem> getFriendRequests(Long userId) {
        List<FriendRequestListResponse.FriendRequestItem> requests = new ArrayList<>();

        String sql = "SELECT fr.id, fr.from_user, fr.status, ua.username " +
                "FROM friend_request fr " +
                "JOIN user_auth ua ON fr.from_user = ua.uid " +
                "WHERE fr.to_user = ? AND fr.status = 0 " +
                "ORDER BY fr.id DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FriendRequestListResponse.FriendRequestItem item =
                            new FriendRequestListResponse.FriendRequestItem();
                    item.setRequestId(rs.getLong("id"));
                    item.setFromUserId(rs.getLong("from_user"));
                    item.setFromUsername(rs.getString("username"));
                    item.setStatus(rs.getInt("status"));

                    requests.add(item);
                }
            }
        } catch (SQLException e) {
            System.err.println("[GET_FRIEND_REQUESTS] SQL error: " + e.getMessage());
            e.printStackTrace();
        }

        return requests;
    }

    /**
     * 处理好友请求（同意或拒绝）
     */
    public boolean processFriendRequest(Long requestId, Long currentUid, boolean accept) {
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);

            // 1. 验证请求存在且属于当前用户
            String checkSql = "SELECT id, from_user, to_user, status FROM friend_request " +
                    "WHERE id = ? AND to_user = ? AND status = 0";

            Long fromUserId = null;
            Long toUserId = null;

            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setLong(1, requestId);
                checkStmt.setLong(2, currentUid);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        return false; // 请求不存在或已处理
                    }
                    fromUserId = rs.getLong("from_user");
                    toUserId = rs.getLong("to_user");
                }
            }

            // 2. 更新请求状态
            String updateSql = "UPDATE friend_request SET status = ? WHERE id = ?";
            int newStatus = accept ? 1 : 2; // 1同意，2拒绝

            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setInt(1, newStatus);
                updateStmt.setLong(2, requestId);
                updateStmt.executeUpdate();
            }

            // 3. 如果同意，建立双向好友关系
            if (accept && fromUserId != null && toUserId != null) {
                // 检查是否已存在好友关系（防止重复）
                String checkFriendship = "SELECT id FROM friendship " +
                        "WHERE (user_id = ? AND friend_id = ?) " +
                        "   OR (user_id = ? AND friend_id = ?)";

                boolean alreadyFriends = false;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkFriendship)) {
                    checkStmt.setLong(1, fromUserId);
                    checkStmt.setLong(2, toUserId);
                    checkStmt.setLong(3, toUserId);
                    checkStmt.setLong(4, fromUserId);

                    try (ResultSet rs = checkStmt.executeQuery()) {
                        alreadyFriends = rs.next();
                    }
                }

                if (!alreadyFriends) {
                    // 建立双向好友关系
                    String insertSql = "INSERT INTO friendship (user_id, friend_id) VALUES (?, ?), (?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setLong(1, fromUserId);
                        insertStmt.setLong(2, toUserId);
                        insertStmt.setLong(3, toUserId);
                        insertStmt.setLong(4, fromUserId);
                        insertStmt.executeUpdate();
                    }
                }
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("[ROLLBACK_ERROR] " + ex.getMessage());
                }
            }
            System.err.println("[PROCESS_REQUEST] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
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

    // 以下是您原有的方法，保持不动
    public boolean userExists(Long userId) {
        String sql = "SELECT uid FROM user_auth WHERE uid = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[CHECK_USER] SQL error: " + e.getMessage());
            return false;
        }
    }

    public boolean isFriend(Long userId1, Long userId2) {
        String sql = "SELECT id FROM friendship WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId1);
            stmt.setLong(2, userId2);
            stmt.setLong(3, userId2);
            stmt.setLong(4, userId1);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[CHECK_FRIEND] SQL error: " + e.getMessage());
            return false;
        }
    }

    public boolean hasPendingRequest(Long fromUser, Long toUser) {
        String sql = "SELECT id FROM friend_request WHERE from_user = ? AND to_user = ? AND status = 0";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, fromUser);
            stmt.setLong(2, toUser);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[CHECK_REQUEST] SQL error: " + e.getMessage());
            return false;
        }
    }

    public Long createFriendRequest(Long fromUser, Long toUser) {
        String sql = "INSERT INTO friend_request (from_user, to_user, status) VALUES (?, ?, 0)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, fromUser);
            stmt.setLong(2, toUser);
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[CREATE_REQUEST] SQL error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public List<FriendListResponse.FriendItem> getFriendList(Long userId) {
        List<FriendListResponse.FriendItem> friends = new ArrayList<>();

        String sql = "SELECT u.uid, u.username, p.avatar_url " +
                "FROM user_auth u " +
                "LEFT JOIN user_profile p ON u.uid = p.user_id " +
                "WHERE u.uid IN (" +
                "    SELECT friend_id FROM friendship WHERE user_id = ? " +
                "    UNION " +
                "    SELECT user_id FROM friendship WHERE friend_id = ?" +
                ")";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FriendListResponse.FriendItem friend = new FriendListResponse.FriendItem();
                    friend.setUid(rs.getLong("uid"));
                    friend.setUsername(rs.getString("username"));
                    friend.setAvatarUrl(rs.getString("avatar_url"));
                    friends.add(friend);
                }
            }
        } catch (SQLException e) {
            System.err.println("[GET_FRIENDS] SQL error: " + e.getMessage());
            e.printStackTrace();
        }

        return friends;
    }
}
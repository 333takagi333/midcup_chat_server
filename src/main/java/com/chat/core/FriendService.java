package com.chat.core;

import com.chat.protocol.FriendListResponse;
import com.chat.utils.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 好友服务 - 处理好友关系相关数据操作
 */
public class FriendService {

    /**
     * 检查用户是否存在
     */
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

    /**
     * 检查是否已经是好友
     */
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

    /**
     * 检查是否有待处理的好友请求
     */
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

    /**
     * 创建好友请求
     */
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

    /**
     * 获取好友列表
     */
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
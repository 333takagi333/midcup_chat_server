package com.chat.handler;

import com.chat.protocol.FriendAddRequest;
import com.chat.protocol.FriendAddResponse;
import com.chat.protocol.FriendListRequest;
import com.chat.protocol.FriendListResponse;
import com.chat.protocol.MessageType;
import com.chat.utils.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 好友关系处理器
 */
public class FriendHandler {

    /**
     * 处理添加好友请求
     */
    public FriendAddResponse handleFriendAdd(FriendAddRequest request, Long currentUid) {
        FriendAddResponse response = new FriendAddResponse();
        response.setType(MessageType.FRIEND_ADD_RESPONSE);

        if (request == null || currentUid == null) {
            response.setSuccess(false);
            response.setMessage("请求数据无效");
            return response;
        }

        Long toUserId = request.getToUserId();
        if (toUserId == null || toUserId.equals(currentUid)) {
            response.setSuccess(false);
            response.setMessage("目标用户ID无效");
            return response;
        }

        // 检查目标用户是否存在
        if (!userExists(toUserId)) {
            response.setSuccess(false);
            response.setMessage("目标用户不存在");
            return response;
        }

        // 检查是否已经是好友
        if (isFriend(currentUid, toUserId)) {
            response.setSuccess(false);
            response.setMessage("已经是好友关系");
            return response;
        }

        // 检查是否已有待处理的请求
        if (hasPendingRequest(currentUid, toUserId)) {
            response.setSuccess(false);
            response.setMessage("已发送过好友请求，请等待对方处理");
            return response;
        }

        // 创建好友请求
        Long requestId = createFriendRequest(currentUid, toUserId);
        if (requestId != null) {
            response.setSuccess(true);
            response.setRequestId(requestId);
            response.setStatus(0); // 待处理状态
            response.setMessage("好友请求发送成功");
        } else {
            response.setSuccess(false);
            response.setMessage("发送好友请求失败");
        }

        return response;
    }

    /**
     * 处理好友列表请求
     */
    public FriendListResponse handleFriendList(FriendListRequest request, Long currentUid) {
        FriendListResponse response = new FriendListResponse();
        response.setType(MessageType.FRIEND_LIST_RESPONSE);

        if (currentUid == null) {
            return response; // 返回空列表
        }

        List<FriendListResponse.FriendItem> friends = getFriendList(currentUid);
        response.setFriends(friends);
        return response;
    }

    private boolean userExists(Long userId) {
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

    private boolean isFriend(Long userId1, Long userId2) {
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

    private boolean hasPendingRequest(Long fromUser, Long toUser) {
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

    private Long createFriendRequest(Long fromUser, Long toUser) {
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

    private List<FriendListResponse.FriendItem> getFriendList(Long userId) {
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
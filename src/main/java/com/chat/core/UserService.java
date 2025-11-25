package com.chat.core;

import com.chat.utils.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 用户信息服务 - 处理用户资料相关操作
 */
public class UserService {

    /**
     * 获取用户资料
     */
    public UserProfile getUserProfile(Long userId) {
        if (userId == null) {
            return null;
        }

        String sql = "SELECT u.uid, u.username, p.avatar_url, p.gender, p.birthday, p.tele " +
                "FROM user_auth u LEFT JOIN user_profile p ON u.uid = p.user_id " +
                "WHERE u.uid = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    UserProfile profile = new UserProfile();
                    profile.setUid(rs.getLong("uid"));
                    profile.setUsername(rs.getString("username"));
                    profile.setAvatarUrl(rs.getString("avatar_url"));
                    profile.setGender(rs.getInt("gender"));

                    // 处理生日日期
                    java.sql.Date birthday = rs.getDate("birthday");
                    if (birthday != null) {
                        profile.setBirthday(birthday.toString());
                    }

                    profile.setTele(rs.getString("tele"));
                    return profile;
                }
            }
        } catch (SQLException e) {
            System.err.println("[USER_PROFILE] SQL error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 更新用户资料
     */
    public boolean updateUserProfile(UserProfile profile) {
        if (profile == null || profile.getUid() == null) {
            return false;
        }

        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false); // 开启事务

            // 1. 更新 user_auth 表中的用户名
            if (profile.getUsername() != null && !profile.getUsername().trim().isEmpty()) {
                String updateAuthSql = "UPDATE user_auth SET username = ? WHERE uid = ?";
                try (PreparedStatement authStmt = conn.prepareStatement(updateAuthSql)) {
                    authStmt.setString(1, profile.getUsername().trim());
                    authStmt.setLong(2, profile.getUid());
                    int authRows = authStmt.executeUpdate();
                    if (authRows == 0) {
                        conn.rollback();
                        return false;
                    }
                }
            }

            // 2. 检查是否存在用户资料记录
            String checkSql = "SELECT user_id FROM user_profile WHERE user_id = ?";
            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setLong(1, profile.getUid());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    exists = rs.next();
                }
            }

            // 3. 插入或更新 user_profile 表
            String profileSql;
            if (exists) {
                profileSql = "UPDATE user_profile SET avatar_url = ?, gender = ?, birthday = ?, tele = ? WHERE user_id = ?";
            } else {
                profileSql = "INSERT INTO user_profile (user_id, avatar_url, gender, birthday, tele) VALUES (?, ?, ?, ?, ?)";
            }

            try (PreparedStatement profileStmt = conn.prepareStatement(profileSql)) {
                if (exists) {
                    profileStmt.setString(1, profile.getAvatarUrl());
                    profileStmt.setInt(2, profile.getGender() != null ? profile.getGender() : 0);

                    // 处理生日字段
                    if (profile.getBirthday() != null && !profile.getBirthday().trim().isEmpty()) {
                        profileStmt.setDate(3, java.sql.Date.valueOf(profile.getBirthday()));
                    } else {
                        profileStmt.setNull(3, java.sql.Types.DATE);
                    }

                    profileStmt.setString(4, profile.getTele());
                    profileStmt.setLong(5, profile.getUid());
                } else {
                    profileStmt.setLong(1, profile.getUid());
                    profileStmt.setString(2, profile.getAvatarUrl());
                    profileStmt.setInt(3, profile.getGender() != null ? profile.getGender() : 0);

                    // 处理生日字段
                    if (profile.getBirthday() != null && !profile.getBirthday().trim().isEmpty()) {
                        profileStmt.setDate(4, java.sql.Date.valueOf(profile.getBirthday()));
                    } else {
                        profileStmt.setNull(4, java.sql.Types.DATE);
                    }

                    profileStmt.setString(5, profile.getTele());
                }

                int profileRows = profileStmt.executeUpdate();
                if (profileRows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("[UPDATE_PROFILE] Rollback error: " + rollbackEx.getMessage());
                }
            }
            System.err.println("[UPDATE_PROFILE] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    System.err.println("[UPDATE_PROFILE] Close connection error: " + closeEx.getMessage());
                }
            }
        }
    }

    /**
     * 检查用户名是否已存在（排除当前用户）
     */
    public boolean isUsernameExists(String username, Long excludeUserId) {
        String sql = "SELECT COUNT(*) FROM user_auth WHERE username = ? AND uid != ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setLong(2, excludeUserId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("[CHECK_USERNAME] SQL error: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 用户资料数据类
     */
    public static class UserProfile {
        private Long uid;
        private String username;
        private String avatarUrl;
        private Integer gender;
        private String birthday;
        private String tele;

        // Getters and Setters
        public Long getUid() { return uid; }
        public void setUid(Long uid) { this.uid = uid; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public Integer getGender() { return gender; }
        public void setGender(Integer gender) { this.gender = gender; }
        public String getBirthday() { return birthday; }
        public void setBirthday(String birthday) { this.birthday = birthday; }
        public String getTele() { return tele; }
        public void setTele(String tele) { this.tele = tele; }
    }
}
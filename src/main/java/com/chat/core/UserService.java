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

        // 先检查是否存在用户资料记录
        String checkSql = "SELECT user_id FROM user_profile WHERE user_id = ?";
        String insertSql = "INSERT INTO user_profile (user_id, avatar_url, gender, birthday, tele) VALUES (?, ?, ?, ?, ?)";
        String updateSql = "UPDATE user_profile SET avatar_url = ?, gender = ?, birthday = ?, tele = ? WHERE user_id = ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setLong(1, profile.getUid());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    exists = rs.next();
                }
            }

            String sql = exists ? updateSql : insertSql;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (exists) {
                    stmt.setString(1, profile.getAvatarUrl());
                    stmt.setInt(2, profile.getGender() != null ? profile.getGender() : 0);
                    stmt.setString(3, profile.getBirthday());
                    stmt.setString(4, profile.getTele());
                    stmt.setLong(5, profile.getUid());
                } else {
                    stmt.setLong(1, profile.getUid());
                    stmt.setString(2, profile.getAvatarUrl());
                    stmt.setInt(3, profile.getGender() != null ? profile.getGender() : 0);
                    stmt.setString(4, profile.getBirthday());
                    stmt.setString(5, profile.getTele());
                }
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[UPDATE_PROFILE] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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
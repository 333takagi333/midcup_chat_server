package com.chat.core;

import com.chat.utils.DatabaseManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 认证服务 - 处理用户登录、注册、密码重置等
 */
public class AuthService {

    /**
     * 用户认证并获取UID
     */
    public Long authenticateAndGetUid(String username, String password) {
        if (username == null || password == null) {
            return null;
        }

        String sql = "SELECT uid, password_hash, salt FROM user_auth WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long uid = rs.getLong("uid");
                    String storedHash = rs.getString("password_hash");
                    String salt = rs.getString("salt");

                    if (storedHash == null) return null;

                    // 开发兜底：明文相等
                    if (storedHash.equals(password)) {
                        return uid;
                    }

                    // 常见两种拼接顺序
                    String h1 = sha256Hex((salt == null ? "" : salt) + password);
                    String h2 = sha256Hex(password + (salt == null ? "" : salt));

                    if (storedHash.equalsIgnoreCase(h1) || storedHash.equalsIgnoreCase(h2)) {
                        return uid;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[AUTH] SQL error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 用户注册
     */
    public boolean registerUser(String username, String password) {
        if (username == null || password == null || username.length() < 3 || password.length() < 6) {
            return false;
        }

        // 检查用户名是否已存在
        if (checkUserExists(username)) {
            return false;
        }

        String salt = generateSalt();
        String passwordHash = sha256Hex(password + salt); // 使用密码+盐的方式
        String recoveryCode = UUID.randomUUID().toString().substring(0, 8);

        String sql = "INSERT INTO user_auth (username, password_hash, salt, recovery_code) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            stmt.setString(3, salt);
            stmt.setString(4, recoveryCode);

            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            System.err.println("[REGISTER] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 重置密码
     */
    public boolean resetPassword(String recoveryCode, String newPassword) {
        if (recoveryCode == null || newPassword == null || newPassword.length() < 6) {
            return false;
        }

        String sql = "SELECT uid, salt FROM user_auth WHERE recovery_code = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, recoveryCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long uid = rs.getLong("uid");
                    String salt = rs.getString("salt");
                    String newPasswordHash = sha256Hex(newPassword + salt);

                    // 更新密码
                    String updateSql = "UPDATE user_auth SET password_hash = ? WHERE uid = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, newPasswordHash);
                        updateStmt.setLong(2, uid);
                        return updateStmt.executeUpdate() > 0;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[RESET_PASSWORD] SQL error: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 检查用户是否存在
     */
    private boolean checkUserExists(String username) {
        String sql = "SELECT uid FROM user_auth WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[CHECK_USER] SQL error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 生成随机盐值
     */
    private String generateSalt() {
        return UUID.randomUUID().toString().substring(0, 16);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
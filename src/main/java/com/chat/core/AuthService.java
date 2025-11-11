package com.chat.core;

import com.chat.utils.DatabaseManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthService {

    /**
     * 验证用户名和密码，成功则返回用户 uid，失败返回 null。
     * 支持以下校验策略：
     * 1) password_hash 与明文密码完全相等（开发环境兜底）
     * 2) password_hash == SHA-256(salt + password)
     * 3) password_hash == SHA-256(password + salt)
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
     * 兼容旧接口：仅返回是否认证通过。
     */
    public boolean authenticate(String username, String password) {
        return authenticateAndGetUid(username, password) != null;
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
            // 不太可能发生
            throw new RuntimeException(e);
        }
    }
}

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
 * 修改密码服务
 */
public class ChangePasswordService {

    /**
     * 修改用户密码（不修改recovery_code）
     */
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null || oldPassword == null || newPassword == null) {
            return false;
        }

        // 验证新密码长度
        if (newPassword.length() < 6) {
            return false;
        }

        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);

            // 1. 验证当前密码是否正确
            String checkSql = "SELECT password_hash, salt, recovery_code FROM user_auth WHERE uid = ?";
            String storedHash = null;
            String salt = null;
            String recoveryCode = null;

            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setLong(1, userId);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        storedHash = rs.getString("password_hash");
                        salt = rs.getString("salt");
                        recoveryCode = rs.getString("recovery_code");
                    } else {
                        return false; // 用户不存在
                    }
                }
            }

            // 验证当前密码
            if (!verifyPassword(oldPassword, storedHash, salt)) {
                return false; // 当前密码错误
            }

            // 2. 生成新的密码哈希和盐值
            String newSalt = generateSalt();
            String newPasswordHash = hashPassword(newPassword, newSalt);

            // 3. 更新数据库（只更新password_hash和salt，保留recovery_code）
            String updateSql = "UPDATE user_auth SET password_hash = ?, salt = ? WHERE uid = ?";

            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, newPasswordHash);
                updateStmt.setString(2, newSalt);
                updateStmt.setLong(3, userId);

                int affected = updateStmt.executeUpdate();
                if (affected > 0) {
                    conn.commit();

                    // 记录修改日志
                    System.out.println("[PASSWORD_CHANGE] 用户 " + userId + " 密码修改成功");
                    System.out.println("[PASSWORD_CHANGE] 旧盐值: " + (salt != null ? salt : "null"));
                    System.out.println("[PASSWORD_CHANGE] 新盐值: " + newSalt);
                    System.out.println("[PASSWORD_CHANGE] recovery_code 保持不变: " + recoveryCode);

                    return true;
                } else {
                    conn.rollback();
                    return false;
                }
            }

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("[ROLLBACK_ERROR] " + ex.getMessage());
                }
            }
            System.err.println("[CHANGE_PASSWORD] SQL error: " + e.getMessage());
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

    /**
     * 验证密码是否正确
     */
    private boolean verifyPassword(String password, String storedHash, String salt) {
        if (storedHash == null || password == null) {
            return false;
        }

        // 开发兜底：明文相等（测试环境下）
        if (storedHash.equals(password)) {
            System.out.println("[PASSWORD_VERIFY] 使用明文验证（开发环境）");
            return true;
        }

        // 常见两种拼接顺序
        String h1 = sha256Hex((salt == null ? "" : salt) + password);
        String h2 = sha256Hex(password + (salt == null ? "" : salt));

        boolean matchH1 = storedHash.equalsIgnoreCase(h1);
        boolean matchH2 = storedHash.equalsIgnoreCase(h2);

        if (matchH1 || matchH2) {
            System.out.println("[PASSWORD_VERIFY] 密码验证成功，使用方式: " + (matchH1 ? "salt+password" : "password+salt"));
            return true;
        }

        System.out.println("[PASSWORD_VERIFY] 密码验证失败");
        System.out.println("[PASSWORD_VERIFY] 存储哈希: " + storedHash);
        System.out.println("[PASSWORD_VERIFY] 计算哈希1: " + h1);
        System.out.println("[PASSWORD_VERIFY] 计算哈希2: " + h2);

        return false;
    }

    /**
     * 生成新密码的哈希值
     */
    private String hashPassword(String password, String salt) {
        // 使用 SHA-256(salt + password) 的方式（与AuthService保持一致）
        String hash = sha256Hex(salt + password);
        System.out.println("[PASSWORD_HASH] 生成新密码哈希: " + hash);
        return hash;
    }

    /**
     * 生成随机盐值
     */
    private String generateSalt() {
        String newSalt = UUID.randomUUID().toString().substring(0, 16);
        System.out.println("[PASSWORD_SALT] 生成新盐值: " + newSalt);
        return newSalt;
    }

    /**
     * SHA-256 哈希函数
     */
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

    /**
     * 验证用户是否存在
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
     * 获取用户的当前recovery_code（用于调试）
     */
    public String getRecoveryCode(Long userId) {
        String sql = "SELECT recovery_code FROM user_auth WHERE uid = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("recovery_code");
                }
            }
        } catch (SQLException e) {
            System.err.println("[GET_RECOVERY_CODE] SQL error: " + e.getMessage());
        }

        return null;
    }
}
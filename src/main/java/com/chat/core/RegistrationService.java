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
 * 用户注册服务
 */
public class RegistrationService {

    /**
     * 注册新用户
     * @param username 用户名
     * @param password 密码（明文）
     * @return RegistrationResult 包含注册结果信息
     */
    public RegistrationResult registerUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null) {
            return new RegistrationResult(false, null, null, "注册信息不完整");
        }

        // 密码基本验证
        if (password.trim().length() < 6) {
            return new RegistrationResult(false, null, null, "密码长度至少为6位");
        }

        // 检查用户名是否已存在
        if (isUsernameExists(username)) {
            return new RegistrationResult(false, null, null, "用户名已存在");
        }

        // 生成盐值和恢复代码
        String salt = generateSalt();
        String recoveryCode = generateRecoveryCode();

        // 对密码进行加盐哈希加密
        String encryptedPassword = hashPasswordWithSalt(password, salt);

        String sql = "INSERT INTO user_auth (username, password_hash, salt, recovery_code) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, username.trim());
            stmt.setString(2, encryptedPassword); // 使用加密后的密码
            stmt.setString(3, salt);
            stmt.setString(4, recoveryCode);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long uid = generatedKeys.getLong(1);
                        // 创建用户profile记录
                        createUserProfile(uid);
                        // 返回注册结果，使用recovery_code作为密钥
                        return new RegistrationResult(true, uid, recoveryCode, "注册成功");
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("[REGISTRATION] SQL error: " + e.getMessage());
            e.printStackTrace();
            return new RegistrationResult(false, null, null, "数据库错误: " + e.getMessage());
        }

        return new RegistrationResult(false, null, null, "注册失败");
    }

    /**
     * 注册结果封装类
     */
    public static class RegistrationResult {
        private final boolean success;
        private final Long uid;
        private final String recoveryCode; // 使用recovery_code作为密钥
        private final String message;

        public RegistrationResult(boolean success, Long uid, String recoveryCode, String message) {
            this.success = success;
            this.uid = uid;
            this.recoveryCode = recoveryCode;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public Long getUid() {
            return uid;
        }

        public String getRecoveryCode() {
            return recoveryCode;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 对密码进行加盐哈希加密
     * 使用格式：SHA-256(salt + password)
     * @param password 明文密码
     * @param salt 盐值
     * @return 加密后的密码哈希
     */
    private String hashPasswordWithSalt(String password, String salt) {
        String hash = sha256Hex(salt + password);
        System.out.println("[REGISTRATION_PASSWORD_HASH] 生成密码哈希");
        System.out.println("[REGISTRATION_PASSWORD_HASH] 盐值: " + salt);
        System.out.println("[REGISTRATION_PASSWORD_HASH] 明文密码: " + password);
        System.out.println("[REGISTRATION_PASSWORD_HASH] 加密结果: " + hash);
        return hash;
    }

    /**
     * 检查用户名是否已存在
     */
    private boolean isUsernameExists(String username) {
        String sql = "SELECT uid FROM user_auth WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // 如果找到记录，说明用户名已存在
            }

        } catch (SQLException e) {
            System.err.println("[USERNAME_CHECK] SQL error: " + e.getMessage());
            e.printStackTrace();
            return true; // 发生错误时认为用户名存在，避免重复注册
        }
    }

    /**
     * 创建用户profile记录
     */
    private void createUserProfile(long uid) {
        String sql = "INSERT INTO user_profile (user_id) VALUES (?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, uid);
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[PROFILE_CREATION] SQL error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 生成随机盐值
     */
    private String generateSalt() {
        String salt = UUID.randomUUID().toString().substring(0, 16);
        System.out.println("[REGISTRATION_SALT] 生成盐值: " + salt);
        return salt;
    }

    /**
     * 生成恢复代码（作为密钥返回给客户端）
     * 生成12位大写字母数字组合的代码
     */
    private String generateRecoveryCode() {
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        // 取前12位作为恢复代码
        String recoveryCode = uuid.substring(0, 12);
        System.out.println("[REGISTRATION_RECOVERY_CODE] 生成恢复代码: " + recoveryCode);
        return recoveryCode;
    }

    /**
     * SHA-256 加密
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
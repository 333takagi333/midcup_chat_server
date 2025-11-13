package com.chat.core;

import com.chat.utils.DatabaseManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 密码重置服务
 */
public class PasswordResetService {

    /**
     * 根据恢复代码重置密码
     * @param recoveryCode 恢复代码
     * @param newPassword 新密码（已加密）
     * @return 重置是否成功
     */
    public boolean resetPasswordByRecoveryCode(String recoveryCode, String newPassword) {
        if (recoveryCode == null || recoveryCode.trim().isEmpty() || newPassword == null) {
            return false;
        }

        String sql = "UPDATE user_auth SET password_hash = ? WHERE recovery_code = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newPassword);
            stmt.setString(2, recoveryCode.trim());

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("[PASSWORD_RESET] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 验证恢复代码是否存在
     * @param recoveryCode 恢复代码
     * @return 是否存在有效的恢复代码
     */
    public boolean validateRecoveryCode(String recoveryCode) {
        if (recoveryCode == null || recoveryCode.trim().isEmpty()) {
            return false;
        }

        String sql = "SELECT uid FROM user_auth WHERE recovery_code = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, recoveryCode.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // 如果找到记录，说明恢复代码有效
            }

        } catch (SQLException e) {
            System.err.println("[RECOVERY_CODE_VALIDATION] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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
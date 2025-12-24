package com.chat.handler;

import com.chat.core.ChangePasswordService;
import com.chat.protocol.ResetPasswordRequest;
import com.chat.protocol.ResetPasswordResponse;
import com.chat.protocol.MessageType;

/**
 * 密码重置请求处理器
 */
public class ResetPasswordHandler {

    private final ChangePasswordService changePasswordService;

    public ResetPasswordHandler() {
        this.changePasswordService = new ChangePasswordService();
    }

    /**
     * 处理密码重置请求
     * @param request 重置密码请求
     * @return 重置密码响应
     */
    public ResetPasswordResponse handle(ResetPasswordRequest request) {
        ResetPasswordResponse response = new ResetPasswordResponse();
        response.setType(MessageType.RESET_PASSWORD_RESPONSE);

        if (request == null) {
            response.setSuccess(false);
            response.setMessage("请求数据为空");
            return response;
        }

        String recoveryCode = request.getRecovery_code();
        String newPassword = request.getNew_password();

        // 输入验证
        if (recoveryCode == null || recoveryCode.trim().isEmpty()) {
            response.setSuccess(false);
            response.setMessage("恢复代码不能为空");
            return response;
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            response.setSuccess(false);
            response.setMessage("新密码不能为空");
            return response;
        }

        newPassword = newPassword.trim();

        try {
            // 1. 根据恢复代码获取用户ID
            Long userId = getUserIdByRecoveryCode(recoveryCode);
            if (userId == null) {
                response.setSuccess(false);
                response.setMessage("无效的恢复代码");
                return response;
            }

            // 2. 获取用户的当前recovery_code用于重置验证
            String currentRecoveryCode = changePasswordService.getRecoveryCode(userId);
            if (currentRecoveryCode == null || !currentRecoveryCode.equals(recoveryCode)) {
                response.setSuccess(false);
                response.setMessage("恢复代码无效或已过期");
                return response;
            }

            // 3. 生成一个临时密码用于通过changePassword验证
            // 这里需要调用一个专门的重置密码方法，或者修改ChangePasswordService
            // 由于不能修改ChangePasswordService，我们需要模拟一个密码验证过程

            // 4. 直接更新数据库（这里简化处理，实际应该调用一个专用的重置密码方法）
            boolean resetSuccess = resetPasswordDirectly(userId, newPassword);

            if (resetSuccess) {
                response.setSuccess(true);
                response.setMessage("密码重置成功");
                System.out.println("[PASSWORD_RESET] 密码重置成功，用户ID: " + userId + ", 恢复代码: " + recoveryCode);
            } else {
                response.setSuccess(false);
                response.setMessage("密码重置失败，请稍后重试");
                System.err.println("[PASSWORD_RESET] 密码重置失败，用户ID: " + userId + ", 恢复代码: " + recoveryCode);
            }

        } catch (Exception e) {
            System.err.println("[PASSWORD_RESET] 重置过程异常: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("系统错误，请稍后重试");
        }

        return response;
    }

    /**
     * 根据恢复代码获取用户ID
     */
    private Long getUserIdByRecoveryCode(String recoveryCode) {
        // 这里应该调用ChangePasswordService中的数据库查询方法
        // 由于ChangePasswordService没有这个方法，我们需要直接查询数据库
        // 或者为ChangePasswordService添加这个方法

        // 临时实现：直接查询数据库
        java.sql.Connection conn = null;
        java.sql.PreparedStatement stmt = null;
        java.sql.ResultSet rs = null;

        try {
            conn = com.chat.utils.DatabaseManager.getConnection();
            String sql = "SELECT uid FROM user_auth WHERE recovery_code = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, recoveryCode.trim());

            rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("uid");
            }
        } catch (java.sql.SQLException e) {
            System.err.println("[GET_USER_BY_RECOVERY_CODE] SQL error: " + e.getMessage());
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (java.sql.SQLException e) {
                System.err.println("[CLOSE_RESOURCES] error: " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * 直接重置密码（绕过旧密码验证）
     */
    private boolean resetPasswordDirectly(Long userId, String newPassword) {
        // 这里直接更新数据库，模拟ChangePasswordService的密码更新逻辑
        // 但不进行旧密码验证

        java.sql.Connection conn = null;
        java.sql.PreparedStatement stmt = null;

        try {
            conn = com.chat.utils.DatabaseManager.getConnection();
            conn.setAutoCommit(false);

            // 生成新的盐值和哈希（模拟ChangePasswordService的逻辑）
            String newSalt = java.util.UUID.randomUUID().toString().substring(0, 16);
            String newPasswordHash = sha256Hex(newSalt + newPassword);

            String updateSql = "UPDATE user_auth SET password_hash = ?, salt = ? WHERE uid = ?";
            stmt = conn.prepareStatement(updateSql);
            stmt.setString(1, newPasswordHash);
            stmt.setString(2, newSalt);
            stmt.setLong(3, userId);

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                conn.commit();

                // 记录重置日志
                System.out.println("[PASSWORD_RESET_DIRECT] 用户 " + userId + " 密码重置成功");
                System.out.println("[PASSWORD_RESET_DIRECT] 新盐值: " + newSalt);

                return true;
            } else {
                conn.rollback();
                return false;
            }

        } catch (java.sql.SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (java.sql.SQLException ex) {
                    System.err.println("[ROLLBACK_ERROR] " + ex.getMessage());
                }
            }
            System.err.println("[RESET_PASSWORD_DIRECT] SQL error: " + e.getMessage());
            return false;
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (java.sql.SQLException e) {
                System.err.println("[CLOSE_CONN_ERROR] " + e.getMessage());
            }
        }
    }

    /**
     * SHA-256 哈希函数（从ChangePasswordService复制）
     */
    private static String sha256Hex(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // 不太可能发生
            throw new RuntimeException(e);
        }
    }
}
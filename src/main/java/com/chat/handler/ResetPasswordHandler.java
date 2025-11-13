package com.chat.handler;

import com.chat.core.PasswordResetService;
import com.chat.protocol.ResetPasswordRequest;
import com.chat.protocol.ResetPasswordResponse;
import com.chat.protocol.MessageType;

/**
 * 密码重置请求处理器
 */
public class ResetPasswordHandler {

    private final PasswordResetService passwordResetService;

    public ResetPasswordHandler() {
        this.passwordResetService = new PasswordResetService();
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

        // 验证恢复代码是否存在
        if (!passwordResetService.validateRecoveryCode(recoveryCode)) {
            response.setSuccess(false);
            response.setMessage("无效的恢复代码");
            return response;
        }

        try {
            // 执行密码重置
            boolean resetSuccess = passwordResetService.resetPasswordByRecoveryCode(recoveryCode, newPassword);

            if (resetSuccess) {
                response.setSuccess(true);
                response.setMessage("密码重置成功");
                System.out.println("[PASSWORD_RESET] 密码重置成功，恢复代码: " + recoveryCode);
            } else {
                response.setSuccess(false);
                response.setMessage("密码重置失败，请稍后重试");
                System.err.println("[PASSWORD_RESET] 密码重置失败，恢复代码: " + recoveryCode);
            }

        } catch (Exception e) {
            System.err.println("[PASSWORD_RESET] 重置过程异常: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("系统错误，请稍后重试");
        }

        return response;
    }
}
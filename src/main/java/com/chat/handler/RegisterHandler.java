package com.chat.handler;

import com.chat.core.RegistrationService;
import com.chat.protocol.RegisterRequest;
import com.chat.protocol.RegisterResponse;
import com.chat.protocol.MessageType;

/**
 * 用户注册请求处理器
 */
public class RegisterHandler {

    private final RegistrationService registrationService;

    public RegisterHandler() {
        this.registrationService = new RegistrationService();
    }

    /**
     * 处理用户注册请求
     * @param request 注册请求
     * @return 注册响应
     */
    public RegisterResponse handle(RegisterRequest request) {
        RegisterResponse response = new RegisterResponse();
        response.setType(MessageType.REGISTER_RESPONSE);

        if (request == null) {
            response.setSuccess(false);
            response.setMessage("请求数据为空");
            return response;
        }

        String username = request.getUsername();
        String password = request.getPassword();

        // 输入验证
        if (username == null || username.trim().isEmpty()) {
            response.setSuccess(false);
            response.setMessage("用户名不能为空");
            return response;
        }

        if (password == null || password.trim().isEmpty()) {
            response.setSuccess(false);
            response.setMessage("密码不能为空");
            return response;
        }

        // 移除用户名格式验证，只保留非空检查
        if (username.trim().length() < 1) {
            response.setSuccess(false);
            response.setMessage("用户名不能为空");
            return response;
        }

        try {
            // 执行用户注册，返回RegistrationResult
            RegistrationService.RegistrationResult result =
                    registrationService.registerUser(username, password);

            if (result.isSuccess()) {
                response.setSuccess(true);
                response.setUid(result.getUid());
                response.setSecretKey(result.getRecoveryCode()); // 设置recovery_code作为密钥
                response.setMessage(result.getMessage());
                System.out.println("[REGISTRATION] 用户注册成功，用户名: " + username +
                        ", UID: " + result.getUid() +
                        ", RecoveryCode: " + result.getRecoveryCode());
            } else {
                response.setSuccess(false);
                response.setMessage(result.getMessage() != null ?
                        result.getMessage() : "注册失败");
                System.err.println("[REGISTRATION] 用户注册失败，用户名: " + username);
            }

        } catch (Exception e) {
            System.err.println("[REGISTRATION] 注册过程异常: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("系统错误，请稍后重试");
        }

        return response;
    }
}
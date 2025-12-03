package com.chat.handler;

import com.chat.core.ChangePasswordService;
import com.chat.protocol.ChangePasswordRequest;
import com.chat.protocol.ChangePasswordResponse;
import com.chat.protocol.MessageType;

/**
 * 设置处理器 - 处理修改密码等设置相关请求
 */
public class SettingHandler {

    private final ChangePasswordService changePasswordService;

    public SettingHandler() {
        this.changePasswordService = new ChangePasswordService();
    }

    /**
     * 处理修改密码请求
     */
    public ChangePasswordResponse handleChangePassword(ChangePasswordRequest request, Long currentUid) {
        ChangePasswordResponse response = new ChangePasswordResponse();

        if (request == null || currentUid == null) {
            response.setSuccess(false);
            response.setMessage("请求数据无效");
            return response;
        }

        // 验证请求中的用户ID是否与当前登录用户一致
        try {
            Long requestUserId = Long.parseLong(request.getUserId());
            if (!requestUserId.equals(currentUid)) {
                response.setSuccess(false);
                response.setMessage("用户ID不匹配");
                return response;
            }
        } catch (NumberFormatException e) {
            response.setSuccess(false);
            response.setMessage("用户ID格式错误");
            return response;
        }

        String oldPassword = request.getOldPassword();
        String newPassword = request.getNewPassword();

        // 基本验证
        if (oldPassword == null || oldPassword.trim().isEmpty()) {
            response.setSuccess(false);
            response.setMessage("当前密码不能为空");
            return response;
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            response.setSuccess(false);
            response.setMessage("新密码不能为空");
            return response;
        }

        newPassword = newPassword.trim();
        if (newPassword.length() < 6) {
            response.setSuccess(false);
            response.setMessage("新密码长度至少为6位");
            return response;
        }

        if (oldPassword.equals(newPassword)) {
            response.setSuccess(false);
            response.setMessage("新密码不能与当前密码相同");
            return response;
        }

        try {
            // 执行密码修改
            boolean success = changePasswordService.changePassword(currentUid, oldPassword, newPassword);

            if (success) {
                response.setSuccess(true);
                response.setMessage("密码修改成功");
            } else {
                response.setSuccess(false);
                response.setMessage("密码修改失败，请检查当前密码是否正确");
            }

        } catch (Exception e) {
            response.setSuccess(false);
            response.setMessage("修改密码时发生错误: " + e.getMessage());
            System.err.println("[CHANGE_PASSWORD_ERROR] " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }
}
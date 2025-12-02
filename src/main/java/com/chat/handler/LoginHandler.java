package com.chat.handler;

import com.chat.core.AuthService;
import com.chat.protocol.LoginRequest;
import com.chat.protocol.LoginResponse;
import com.chat.protocol.MessageType;
import java.util.Map;

public class LoginHandler {

    /**
     * 处理登录（协议：login_request）- 使用UID登录
     * 入参：LoginRequest（uid/password）
     * 返回：LoginResponse（type=login_response, uid/success/message/avatarUrl/username）
     */
    public LoginResponse handle(LoginRequest loginRequest) {
        LoginResponse resp = new LoginResponse();
        resp.setType(MessageType.LOGIN_RESPONSE);

        if (loginRequest == null) {
            resp.setUid(null);
            resp.setSuccess(false);
            resp.setMessage("Empty payload");
            return resp;
        }

        Long uid = loginRequest.getUid();  // 获取客户端传来的UID
        String password = loginRequest.getPassword();

        // 参数检查
        if (uid == null || password == null || password.isEmpty()) {
            resp.setUid(null);
            resp.setSuccess(false);
            resp.setMessage("用户ID或密码不能为空");
            return resp;
        }

        AuthService auth = new AuthService();
        boolean ok = false;
        String username = null;
        String avatarUrl = null;

        try {
            // 使用新的认证方法，验证UID和密码
            ok = auth.authenticateByUid(uid, password);
            if (ok) {
                // 获取用户信息（用户名和头像）
                Map<String, String> userInfo = auth.getUserInfoByUid(uid);
                if (userInfo != null) {
                    username = userInfo.get("username");
                    avatarUrl = userInfo.get("avatarUrl");
                }
            }
        } catch (Exception ex) {
            System.err.println("[AUTH] DB error: " + ex.getMessage());
            ex.printStackTrace();
        }

        System.out.println("用户UID " + uid + " 登录" + (ok ? "成功" : "失败"));

        if (ok) {
            resp.setUid(String.valueOf(uid));
            resp.setSuccess(true);
            resp.setMessage("登录成功");
            resp.setTimestamp(System.currentTimeMillis());
            resp.setUsername(username != null ? username : "用户" + uid);
            resp.setAvatarUrl(avatarUrl != null ? avatarUrl : ""); // 如果没有头像返回空字符串
        } else {
            resp.setUid(null);
            resp.setSuccess(false);
            resp.setMessage("用户ID或密码错误");
            resp.setTimestamp(System.currentTimeMillis());
        }
        return resp;
    }
}
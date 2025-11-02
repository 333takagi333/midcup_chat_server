package com.chat.handler;

import com.chat.core.AuthService;
import com.chat.protocol.LoginRequest;
import com.chat.protocol.LoginResponse;
import com.chat.protocol.MessageType;

public class LoginHandler {

    /**
     * 处理登录（协议：login_request）
     * 入参：LoginRequest（username/password）
     * 返回：LoginResponse（type=login_response, uid/success/message）
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

        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        AuthService auth = new AuthService();
        Long uid = null;
        try {
            uid = auth.authenticateAndGetUid(username, password);
        } catch (Exception ex) {
            System.err.println("[AUTH] DB error: " + ex.getMessage());
            ex.printStackTrace();
        }

        boolean ok = uid != null;
        System.out.println("用户 " + username + " 登录" + (ok ? "成功" : "失败"));

        if (ok) {
            resp.setUid(String.valueOf(uid));
            resp.setSuccess(true);
            resp.setMessage("Welcome, " + username);
            resp.setTimestamp(System.currentTimeMillis());
        } else {
            resp.setUid(null);
            resp.setSuccess(false);
            resp.setMessage("Invalid username or password");
            resp.setTimestamp(System.currentTimeMillis());
        }
        return resp;
    }
}
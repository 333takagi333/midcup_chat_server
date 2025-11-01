package com.chat.model;

/**
 * 登录请求载荷：包含用户名与密码。
 */
public class LoginPayload {
    private String username;
    private String password;

    // 获取用户名
    public String getUsername() {
        return username;
    }

    // 获取明文密码（演示用途）
    public String getPassword() {
        return password;
    }
}

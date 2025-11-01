package com.chat.model;

/**
 * 登录结果数据：告诉客户端用户名、是否通过认证以及可选的错误码。
 */
public class LoginResult {
    private String username;
    private boolean authenticated;
    private String error; // 失败时的错误码，可为空

    // 构造：成功或失败（无错误码）
    public LoginResult(String username, boolean authenticated) {
        this.username = username;
        this.authenticated = authenticated;
    }

    // 构造：失败且包含错误码
    public LoginResult(String username, boolean authenticated, String error) {
        this.username = username;
        this.authenticated = authenticated;
        this.error = error;
    }

    // 获取用户名
    public String getUsername() {
        return username;
    }

    // 是否认证通过
    public boolean isAuthenticated() {
        return authenticated;
    }

    // 获取错误码（可能为 null）
    public String getError() {
        return error;
    }
}

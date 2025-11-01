package com.chat.handler;

import com.chat.core.AuthService;
import com.chat.model.LoginPayload;
import com.chat.model.Request;
import com.chat.model.Response;

public class LoginHandler {

    public Response handle(Request<LoginPayload> request) {
        if (request.getPayload() == null) {
            return new Response("ERROR", "Empty payload");
        }

        LoginPayload p = request.getPayload();
        AuthService auth = new AuthService();
        boolean ok = false;
        try {
            ok = auth.authenticate(p);
        } catch (Exception ex) {
            System.err.println("[AUTH] DB error: " + ex.getMessage());
            ex.printStackTrace();
        }

        System.out.println("用户 " + p.getUsername() + "，密码 " + p.getPassword()
                + "，登录" + (ok ? "成功" : "失败"));

        return ok
                ? new Response("SUCCESS", "Login successful")
                : new Response("ERROR", "Invalid username or password");
    }
}
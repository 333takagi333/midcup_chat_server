package com.chat.handler;

import com.chat.core.AuthService;
import com.chat.utils.OnlineUserManager;
import com.chat.protocol.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final Gson gson = new Gson();
    private Long currentUid = null;
    private PrintWriter out;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            this.out = writer;
            System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[HANDLER] Received from client: " + line);

                try {
                    JsonObject root = gson.fromJson(line, JsonObject.class);
                    if (root == null || !root.has("type")) {
                        continue;
                    }
                    String type = root.get("type").getAsString();

                    switch (type) {
                        case MessageType.LOGIN_REQUEST -> {
                            LoginRequest loginReq = gson.fromJson(line, LoginRequest.class);
                            handleLogin(loginReq);
                        }
                        case MessageType.CHAT_PRIVATE_SEND -> {
                            if (currentUid == null) {
                                break;
                            }
                            ChatPrivateSend cps = gson.fromJson(line, ChatPrivateSend.class);
                            cps.setFromUserId(currentUid);
                            handlePrivateChat(cps);
                        }
                        case MessageType.RESET_PASSWORD_REQUEST -> {
                            ResetPasswordRequest resetReq = gson.fromJson(line, ResetPasswordRequest.class);
                            handleResetPassword(resetReq);
                        }
                        // 新增：处理用户注册请求
                        case MessageType.REGISTER_REQUEST -> {
                            RegisterRequest registerReq = gson.fromJson(line, RegisterRequest.class);
                            handleRegister(registerReq);
                        }
                        default -> System.out.println("[WARN] Unsupported type: " + type);
                    }
                } catch (JsonSyntaxException e) {
                    fallbackLog(line);
                } catch (Exception e) {
                    System.err.println("Handler error: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("[INFO] 客户端断开: " + clientSocket.getInetAddress() + " - " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // 新增：处理用户注册的方法
    private void handleRegister(RegisterRequest request) {
        if (request == null) {
            sendRegisterResponse(false, "请求数据为空", null);
            return;
        }

        RegisterHandler registerHandler = new RegisterHandler();
        RegisterResponse response = registerHandler.handle(request);

        sendJson(response);

        // 注册后关闭连接（安全考虑，避免连接保持）
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.out.println("[INFO] 注册后关闭连接: " + e.getMessage());
        }
    }

    // 原有的密码重置处理方法
    private void handleResetPassword(ResetPasswordRequest request) {
        if (request == null) {
            sendResetPasswordResponse(false, "请求数据为空");
            return;
        }

        ResetPasswordHandler resetHandler = new ResetPasswordHandler();
        ResetPasswordResponse response = resetHandler.handle(request);

        sendJson(response);

        // 密码重置后关闭连接（安全考虑）
        if (response.isSuccess()) {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("[INFO] 重置密码后关闭连接: " + e.getMessage());
            }
        }
    }

    // 原有的登录处理方法保持不变
    private void handleLogin(LoginRequest loginRequest) {
        if (loginRequest == null) {
            sendLoginResponse(null, false, "Empty payload");
            return;
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
            currentUid = uid;
            OnlineUserManager.addUser(currentUid, out);
            System.out.println("用户UID " + currentUid + " 登录成功，保持连接...");
            sendLoginResponse(String.valueOf(uid), true, "Welcome, " + username);
        } else {
            sendLoginResponse(null, false, "Invalid username or password");
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private void handlePrivateChat(ChatPrivateSend cps) {
        if (cps == null) return;
        new ChatHandler().handle(cps);
    }

    private void sendLoginResponse(String uid, boolean success, String message) {
        LoginResponse resp = new LoginResponse();
        resp.setType(MessageType.LOGIN_RESPONSE);
        resp.setUid(uid);
        resp.setSuccess(success);
        resp.setMessage(message);
        resp.setTimestamp(System.currentTimeMillis());
        sendJson(resp);
    }

    // 新增：发送注册响应
    private void sendRegisterResponse(boolean success, String message, Long uid) {
        RegisterResponse resp = new RegisterResponse(success, message, uid);
        sendJson(resp);
    }

    private void sendResetPasswordResponse(boolean success, String message) {
        ResetPasswordResponse resp = new ResetPasswordResponse(success, message);
        sendJson(resp);
    }

    private void sendJson(Object obj) {
        if (out != null && !out.checkError()) {
            out.println(gson.toJson(obj));
        }
    }

    private void fallbackLog(String line) {
        try {
            Pattern pu = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]*)\"");
            Matcher mu = pu.matcher(line);
            String u = mu.find() ? mu.group(1) : "?";
            System.out.println("[FALLBACK] 用户 " + u + " 登录失败(JSON无效)");
        } catch (Throwable ignored) {}
    }

    private void cleanup() {
        if (currentUid != null) {
            OnlineUserManager.removeUser(currentUid);
            System.out.println("[ONLINE] 用户UID " + currentUid + " 已下线");
        }
        try {
            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }
}
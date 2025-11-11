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
    private Long currentUid = null; // 以 UID 维护在线表，符合协议与 OnlineUserManager
    private PrintWriter out;  // 缓存，便于推送

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            this.out = writer;  // 缓存
            System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[HANDLER] Received from client: " + line);

                try {
                    JsonObject root = gson.fromJson(line, JsonObject.class);
                    if (root == null || !root.has("type")) {
                        // 协议必须包含 type
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
                                // 未登录，忽略
                                break;
                            }
                            ChatPrivateSend cps = gson.fromJson(line, ChatPrivateSend.class);
                            // 规范：以服务器记录的当前用户为准，防止伪造
                            cps.setFromUserId(currentUid);
                            handlePrivateChat(cps);
                        }
                        default -> System.out.println("[WARN] Unsupported type: " + type);
                    }
                } catch (JsonSyntaxException e) {
                    fallbackLog(line);
                    // 无效 JSON，忽略本条
                } catch (Exception e) {
                    System.err.println("Handler error: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }
        } catch (IOException e) {
            // 客户端断开：Connection reset / EOF → 正常
            System.out.println("[INFO] 客户端断开: " + clientSocket.getInetAddress() + " - " + e.getMessage());
        } finally {
            cleanup();
        }
    }

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
        // 避免记录明文密码
        System.out.println("用户 " + username + " 登录" + (ok ? "成功" : "失败"));

        if (ok) {
            currentUid = uid; // 以 uid 维持会话
            OnlineUserManager.addUser(currentUid, out);  // 注册在线
            System.out.println("用户UID " + currentUid + " 登录成功，保持连接...");
            // 返回数据库 uid
            sendLoginResponse(String.valueOf(uid), true, "Welcome, " + username);
        } else {
            sendLoginResponse(null, false, "Invalid username or password");
            // 登录失败后关闭连接
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private void handlePrivateChat(ChatPrivateSend cps) {
        if (cps == null) return;
        // 将具体处理委托给 ChatHandler，统一落库与推送逻辑
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

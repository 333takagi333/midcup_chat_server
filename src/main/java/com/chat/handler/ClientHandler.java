package com.chat.handler;

import com.chat.core.AuthService;
import com.chat.model.*;
import com.chat.utils.OnlineUserManager;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final Gson gson = new Gson();
    private String currentUser = null;
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
                    Request<?> raw = gson.fromJson(line, new TypeToken<Request<?>>() {}.getType());
                    if (raw == null || raw.getType() == null) {
                        sendError("Invalid request");
                        continue;
                    }

                    Response response;
                    switch (raw.getType()) {
                        case "LOGIN" -> {
                            Request<LoginPayload> loginReq = gson.fromJson(line,
                                    new TypeToken<Request<LoginPayload>>() {}.getType());
                            response = handleLogin(loginReq);
                            if ("SUCCESS".equals(response.getStatus())) {
                                currentUser = loginReq.getPayload().getUsername();
                                OnlineUserManager.addUser(currentUser, out);  // 注册在线
                                System.out.println("用户 " + currentUser + " 登录成功，保持连接...");
                            } else {
                                out.println(gson.toJson(response));
                                return; // 登录失败，关闭
                            }
                        }
                        case "CHAT" -> {
                            if (currentUser == null) {
                                response = new Response("ERROR", "Not logged in");
                            } else {
                                Request<ChatMessage> chatReq = gson.fromJson(line,
                                        new TypeToken<Request<ChatMessage>>() {}.getType());
                                if (chatReq.getPayload() != null) {
                                    chatReq.getPayload().setFrom(currentUser);  // 自动填充
                                }
                                response = new ChatHandler().handle(chatReq);
                            }
                        }
                        default -> response = new Response("ERROR", "Unsupported type: " + raw.getType());
                    }

                    out.println(gson.toJson(response));

                } catch (JsonSyntaxException e) {
                    fallbackLog(line);
                    sendError("Invalid JSON");
                } catch (Exception e) {
                    System.err.println("Handler error: " + e.getMessage());
                    e.printStackTrace();
                    sendError("Server error");
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

    private Response handleLogin(Request<LoginPayload> request) {
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

    private void sendError(String msg) {
        if (out != null && !out.checkError()) {
            out.println(gson.toJson(new Response("ERROR", msg)));
        }
    }

    private void fallbackLog(String line) {
        try {
            Pattern pu = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]*)\"");
            Pattern pp = Pattern.compile("\"password\"\\s*:\\s*\"([^\"]*)\"");
            Matcher mu = pu.matcher(line);
            Matcher mp = pp.matcher(line);
            String u = mu.find() ? mu.group(1) : "?";
            String p = mp.find() ? mp.group(1) : "?";
            System.out.println("[FALLBACK] 用户 " + u + "，密码 " + p + "，登录失败（JSON无效）");
        } catch (Throwable ignored) {}
    }

    private void cleanup() {
        if (currentUser != null) {
            OnlineUserManager.removeUser(currentUser);
            System.out.println("[ONLINE] 用户 " + currentUser + " 已下线");
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
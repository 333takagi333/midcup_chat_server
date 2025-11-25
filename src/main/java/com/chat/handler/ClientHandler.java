package com.chat.handler;

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

    // Handler实例
    private final LoginHandler loginHandler = new LoginHandler();
    private final RegisterHandler registerHandler = new RegisterHandler();
    private final UserInfoHandler userInfoHandler = new UserInfoHandler();
    private final FriendHandler friendHandler = new FriendHandler();
    private final ChatHandler chatHandler = new ChatHandler();
    private final ChatGroupHandler chatGroupHandler = new ChatGroupHandler();
    private final GroupHandler groupHandler = new GroupHandler();
    private final ChatHistoryHandler chatHistoryHandler = new ChatHistoryHandler();
    private final ResetPasswordHandler resetPasswordHandler = new ResetPasswordHandler();

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
                        case MessageType.REGISTER_REQUEST -> {
                            RegisterRequest registerReq = gson.fromJson(line, RegisterRequest.class);
                            handleRegister(registerReq);
                        }
                        case MessageType.USER_INFO_REQUEST -> {
                            if (currentUid == null) break;
                            UserInfoRequest userInfoReq = gson.fromJson(line, UserInfoRequest.class);
                            handleUserInfo(userInfoReq);
                        }
                        case MessageType.UPDATE_PROFILE_REQUEST -> {
                            if (currentUid == null) break;
                            UpdateProfileRequest updateProfileReq = gson.fromJson(line, UpdateProfileRequest.class);
                            handleUpdateProfile(updateProfileReq);
                        }
                        case MessageType.FRIEND_ADD_REQUEST -> {
                            if (currentUid == null) break;
                            FriendAddRequest friendReq = gson.fromJson(line, FriendAddRequest.class);
                            handleFriendAdd(friendReq);
                        }
                        case MessageType.FRIEND_LIST_REQUEST -> {
                            if (currentUid == null) break;
                            FriendListRequest friendListReq = gson.fromJson(line, FriendListRequest.class);
                            handleFriendList(friendListReq);
                        }
                        case MessageType.GROUP_LIST_REQUEST -> {
                            if (currentUid == null) break;
                            GroupListRequest groupListReq = gson.fromJson(line, GroupListRequest.class);
                            handleGroupList(groupListReq);
                        }
                        case MessageType.CHAT_PRIVATE_SEND -> {
                            if (currentUid == null) break;
                            ChatPrivateSend cps = gson.fromJson(line, ChatPrivateSend.class);
                            cps.setFromUserId(currentUid);
                            handlePrivateChat(cps);
                        }
                        case MessageType.CHAT_GROUP_SEND -> {
                            if (currentUid == null) break;
                            ChatGroupSend cgs = gson.fromJson(line, ChatGroupSend.class);
                            cgs.setFromUserId(currentUid);
                            handleGroupChat(cgs);
                        }
                        case MessageType.CHAT_HISTORY_REQUEST -> {
                            if (currentUid == null) break;
                            ChatHistoryRequest historyReq = gson.fromJson(line, ChatHistoryRequest.class);
                            handleChatHistory(historyReq);
                        }
                        case MessageType.RESET_PASSWORD_REQUEST -> {
                            ResetPasswordRequest resetReq = gson.fromJson(line, ResetPasswordRequest.class);
                            handleResetPassword(resetReq);
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

    private void handleLogin(LoginRequest loginRequest) {
        LoginResponse response = loginHandler.handle(loginRequest);
        if (response.isSuccess() && response.getUid() != null) {
            try {
                currentUid = Long.parseLong(response.getUid());
                OnlineUserManager.addUser(currentUid, out);
                System.out.println("用户UID " + currentUid + " 登录成功，保持连接...");
            } catch (NumberFormatException e) {
                System.err.println("[LOGIN] UID格式错误: " + response.getUid());
            }
        }
        sendJson(response);
    }

    private void handleRegister(RegisterRequest registerRequest) {
        RegisterResponse response = registerHandler.handle(registerRequest);
        sendJson(response);
    }

    private void handleUserInfo(UserInfoRequest userInfoRequest) {
        // 如果请求中没有指定userId，使用当前登录用户的UID
        if (userInfoRequest.getUserId() == null) {
            userInfoRequest.setUserId(currentUid);
        }
        UserInfoResponse response = userInfoHandler.handle(userInfoRequest);
        sendJson(response);
    }

    private void handleUpdateProfile(UpdateProfileRequest updateProfileRequest) {
        UpdateProfileResponse response = userInfoHandler.handleUpdateProfile(updateProfileRequest, currentUid);
        sendJson(response);
    }

    private void handleFriendAdd(FriendAddRequest friendRequest) {
        FriendAddResponse response = friendHandler.handleFriendAdd(friendRequest, currentUid);
        sendJson(response);
    }

    private void handleFriendList(FriendListRequest friendListRequest) {
        FriendListResponse response = friendHandler.handleFriendList(friendListRequest, currentUid);
        sendJson(response);
    }

    private void handleGroupList(GroupListRequest groupListRequest) {
        GroupListResponse response = groupHandler.handleGroupList(groupListRequest, currentUid);
        sendJson(response);
    }

    private void handlePrivateChat(ChatPrivateSend chatRequest) {
        boolean success = chatHandler.handle(chatRequest);
        if (!success) {
            System.out.println("[CHAT] 私聊消息处理失败");
        }
    }

    private void handleGroupChat(ChatGroupSend chatRequest) {
        boolean success = chatGroupHandler.handle(chatRequest);
        if (!success) {
            System.out.println("[GROUP_CHAT] 群聊消息处理失败");
        }
    }

    private void handleChatHistory(ChatHistoryRequest historyRequest) {
        ChatHistoryResponse response = chatHistoryHandler.handle(historyRequest, currentUid);
        sendJson(response);
    }

    private void handleResetPassword(ResetPasswordRequest resetRequest) {
        ResetPasswordResponse response = resetPasswordHandler.handle(resetRequest);
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
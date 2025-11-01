package com.chat.server;

import com.chat.core.AuthService;
import com.chat.model.LoginPayload;
import com.chat.model.LoginResult;
import com.chat.model.Request;
import com.chat.model.Response;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * 客户端连接处理器：读取请求、执行业务逻辑并返回响应（每个连接处理一次请求）。
 */
public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final Gson gson = new Gson();

    // 构造函数：保存客户端套接字
    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    // 线程入口：读取一行JSON请求，处理后返回JSON响应
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String line = in.readLine();
            if (line != null) {
                System.out.println("[HANDLER] 收到客户端请求");
                try {
                    Request<LoginPayload> request = gson.fromJson(line, new com.google.gson.reflect.TypeToken<Request<LoginPayload>>() {}.getType());

                    if (request != null && "LOGIN".equals(request.getType()) && request.getPayload() != null) {
                        LoginPayload payload = request.getPayload();

                        // 数据库校验
                        AuthService authService = new AuthService();
                        boolean authenticated = false;
                        try {
                            authenticated = authService.authenticate(payload);
                        } catch (Exception ex) {
                            System.err.println("[AUTH] 数据库校验出错: " + ex.getMessage());
                        }

                        // 控制台输出（不打印密码）
                        System.out.println("[AUTH] 用户 " + payload.getUsername() + " 登录" + (authenticated ? "成功" : "失败"));

                        // 回应客户端：包含可判断的 type 以及 data
                        if (authenticated) {
                            Response response = new Response(
                                    "LOGIN_RESULT",
                                    "SUCCESS",
                                    "Login successful",
                                    new LoginResult(payload.getUsername(), true)
                            );
                            out.println(gson.toJson(response));
                        } else {
                            Response response = new Response(
                                    "LOGIN_RESULT",
                                    "ERROR",
                                    "Invalid username or password",
                                    new LoginResult(payload.getUsername(), false, "INVALID_CREDENTIALS")
                            );
                            out.println(gson.toJson(response));
                        }
                    } else {
                        Response response = new Response(
                                "UNKNOWN",
                                "OK",
                                "Unsupported type or empty",
                                null
                        );
                        out.println(gson.toJson(response));
                    }
                } catch (JsonSyntaxException e) {
                    System.err.println("收到无效的JSON请求（内容已省略）");
                    Response response = new Response(
                            "ERROR",
                            "ERROR",
                            "Invalid request format",
                            null
                    );
                    out.println(gson.toJson(response));
                } catch (Throwable t) {
                    System.err.println("发生严重错误，连接处理终止：" + t.getMessage());
                    if (!clientSocket.isOutputShutdown()) {
                        Response response = new Response(
                                "FATAL",
                                "FATAL",
                                "Server encountered a critical error.",
                                null
                        );
                        out.println(gson.toJson(response));
                    }
                }
            }
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("软件中止了一个已建立的连接")
                    || msg.contains("forcibly closed")
                    || msg.contains("An established connection was aborted"))) {
                System.out.println("[INFO] 客户端异常断开: " + msg);
            } else {
                System.err.println("与客户端通信发生I/O错误: " + e.getMessage());
            }
        } finally {
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                    System.out.println("客户端连接已关闭: " + clientSocket.getInetAddress().getHostAddress());
                }
            } catch (IOException e) {
                System.err.println("关闭客户端套接字时出错: " + e.getMessage());
            }
        }
    }
}

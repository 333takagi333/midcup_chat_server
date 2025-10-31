package com.chat.server;

import com.chat.core.AuthService;
import com.chat.model.LoginPayload;
import com.chat.model.Request;
import com.chat.model.Response;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final Gson gson = new Gson();

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[HANDLER] Received from client: " + line);
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
                            System.err.println("[AUTH] DB error: " + ex.getMessage());
                            ex.printStackTrace();
                        }

                        // 控制台输出：用户..，密码..，登录成功/失败
                        System.out.println("用户 " + payload.getUsername() + "，密码 " + payload.getPassword() + "，登录" + (authenticated ? "成功" : "失败"));

                        // 回应客户端
                        Response response = authenticated
                                ? new Response("SUCCESS", "Login successful")
                                : new Response("ERROR", "Invalid username or password");
                        out.println(gson.toJson(response));
                        // 一次请求一次连接，避免对端先断开引发的异常
                        break;
                    } else {
                        out.println(gson.toJson(new Response("OK", "Unsupported type or empty")));
                        break;
                    }
                } catch (JsonSyntaxException e) {
                    System.err.println("Invalid JSON received from client: " + line);
                    e.printStackTrace();
                    // Fallback: try to extract username/password via regex for logging purpose
                    try {
                        Pattern pu = Pattern.compile("\\\"username\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
                        Pattern pp = Pattern.compile("\\\"password\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
                        Matcher mu = pu.matcher(line);
                        Matcher mp = pp.matcher(line);
                        String u = mu.find() ? mu.group(1) : "?";
                        String p = mp.find() ? mp.group(1) : "?";
                        System.out.println("[FALLBACK] 用户 " + u + "，密码 " + p + "，登录失败（JSON无效）");
                    } catch (Throwable ignore) {
                        // ignore
                    }
                    out.println(gson.toJson(new Response("ERROR", "Invalid request format")));
                    break;
                } catch (Throwable t) {
                    System.err.println("A critical error occurred, forcing thread to terminate.");
                    t.printStackTrace();
                    if (!clientSocket.isOutputShutdown()) {
                        out.println(gson.toJson(new Response("FATAL", "Server encountered a critical error.")));
                    }
                    break;
                }
            }
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("软件中止了一个已建立的连接")
                    || msg.contains("forcibly closed")
                    || msg.contains("An established connection was aborted"))) {
                System.out.println("[INFO] Client disconnected abruptly: " + msg);
            } else {
                System.err.println("I/O error with client " + clientSocket.getInetAddress().getHostAddress() + ": " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                    System.out.println("Client connection closed: " + clientSocket.getInetAddress().getHostAddress());
                }
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

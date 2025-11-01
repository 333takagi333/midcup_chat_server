package com.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TCP服务器：监听端口并为每个客户端连接创建一个处理线程。
 */
public class Server {
    private final int port;

    // 构造函数：保存监听端口
    public Server(int port) {
        this.port = port;
    }

    // 启动服务器：阻塞等待客户端连接并分发给处理线程
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port: " + port);
            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                // 为每个客户端创建独立线程处理
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port: " + port + ", error: " + e.getMessage());
        }
    }
}

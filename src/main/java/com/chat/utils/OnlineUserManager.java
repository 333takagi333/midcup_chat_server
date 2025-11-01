package com.chat.utils;

import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线用户管理器
 * 功能：保存当前登录用户的 PrintWriter，用于实时推送消息
 */
public class OnlineUserManager {

    // username -> PrintWriter (用于向客户端写数据)
    private static final Map<String, PrintWriter> onlineUsers = new ConcurrentHashMap<>();

    /**
     * 用户上线
     */
    public static void addUser(String username, PrintWriter out) {
        if (username != null && out != null) {
            onlineUsers.put(username, out);
            System.out.println("[ONLINE] 用户 " + username + " 上线，当前在线人数: " + onlineUsers.size());
        }
    }

    /**
     * 用户下线
     */
    public static void removeUser(String username) {
        if (username != null) {
            PrintWriter removed = onlineUsers.remove(username);
            if (removed != null) {
                System.out.println("[ONLINE] 用户 " + username + " 下线，当前在线人数: " + onlineUsers.size());
            }
        }
    }

    /**
     * 获取用户的输出流（用于推送）
     */
    public static PrintWriter getUserOutput(String username) {
        if (username == null) return null;
        PrintWriter out = onlineUsers.get(username);
        // 检查连接是否有效
        return (out != null && !out.checkError()) ? out : null;
    }

    /**
     * 判断用户是否在线
     */
    public static boolean isOnline(String username) {
        return username != null && onlineUsers.containsKey(username);
    }

    /**
     * 获取在线人数（可选）
     */
    public static int getOnlineCount() {
        return onlineUsers.size();
    }
}
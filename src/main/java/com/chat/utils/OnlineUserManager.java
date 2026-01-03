package com.chat.utils;

import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线用户管理器
 * 功能：保存当前登录用户的 PrintWriter，用于实时推送消息
 */
public class OnlineUserManager {

    // uid -> PrintWriter (用于向客户端写数据)
    private static final Map<Long, PrintWriter> onlineUsers = new ConcurrentHashMap<>();

    /**
     * 用户上线
     */
    public static void addUser(Long uid, PrintWriter out) {
        if (uid != null && out != null) {
            onlineUsers.put(uid, out);
            System.out.println("[ONLINE] 用户UID " + uid + " 上线，当前在线人数: " + onlineUsers.size());
        }
    }

    /**
     * 用户下线
     */
    public static void removeUser(Long uid) {
        if (uid != null) {
            PrintWriter removed = onlineUsers.remove(uid);
            if (removed != null) {
                System.out.println("[ONLINE] 用户UID " + uid + " 下线，当前在线人数: " + onlineUsers.size());
            }
        }
    }

    /**
     * 获取用户的输出流（用于推送）
     */
    public static PrintWriter getUserOutput(Long uid) {
        if (uid == null) return null;
        PrintWriter out = onlineUsers.get(uid);
        // 检查连接是否有效
        return (out != null && !out.checkError()) ? out : null;
    }

    /**
     * 判断用户是否在线
     */
    public static boolean isOnline(Long uid) {
        return uid != null && onlineUsers.containsKey(uid);
    }

    /**
     * 获取在线人数（可选）
     */
    public static int getOnlineCount() {
        return onlineUsers.size();
    }
}
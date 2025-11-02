package com.chat.handler;

import com.chat.core.ChatService;
import com.chat.model.ChatMessage;
import com.chat.model.Request;
import com.chat.model.Response;

public class ChatHandler {

    public Response handle(Request<ChatMessage> request) {
        if (request.getPayload() == null) {
            return new Response("ERROR", "Empty payload");
        }

        ChatMessage msg = request.getPayload();
        ChatService svc = new ChatService();
        boolean ok = false;
        try {
            ok = svc.processMessage(msg);
        } catch (Exception ex) {
            System.err.println("[CHAT] Process error: " + ex.getMessage());
            ex.printStackTrace();
        }

        System.out.println("发送者 " + msg.getFrom() + " 到 " + msg.getTo()
                + " : " + msg.getMessage() + "，处理" + (ok ? "成功" : "失败"));

        return ok
                ? new Response("SUCCESS", "Message sent successfully")
                : new Response("ERROR", "Failed to send message");
    }
}
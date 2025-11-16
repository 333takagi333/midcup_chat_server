package com.chat.handler;

import com.chat.core.ChatService;
import com.chat.core.GroupService;
import com.chat.protocol.ChatGroupReceive;
import com.chat.protocol.ChatGroupSend;
import com.chat.utils.OnlineUserManager;
import com.google.gson.Gson;

import java.io.PrintWriter;
import java.util.List;

/**
 * 群聊消息处理器
 */
public class ChatGroupHandler {

    private final Gson gson = new Gson();
    private final ChatService chatService;
    private final GroupService groupService;

    public ChatGroupHandler() {
        this.chatService = new ChatService();
        this.groupService = new GroupService();
    }

    /**
     * 处理群聊消息发送
     */
    public boolean handle(ChatGroupSend chatRequest) {
        if (chatRequest == null) return false;

        Long fromUserId = chatRequest.getFromUserId();
        Long groupId = chatRequest.getGroupId();
        String content = chatRequest.getContent();
        String contentType = chatRequest.getContentType();

        if (fromUserId == null || groupId == null || content == null || content.isEmpty()) {
            System.out.println("[GROUP_CHAT] 无效的消息字段：fromUserId/groupId/content 不能为空");
            return false;
        }

        // 检查用户是否在群组中
        if (!groupService.isUserInGroup(fromUserId, groupId)) {
            System.out.println("[GROUP_CHAT] 用户 " + fromUserId + " 不在群组 " + groupId + " 中");
            return false;
        }

        // 保存消息到数据库
        Long messageId = chatService.saveGroupMessage(chatRequest);
        if (messageId == null) {
            System.out.println("[GROUP_CHAT] 保存群聊消息失败");
            return false;
        }

        System.out.println("[GROUP_CHAT] 群组 " + groupId + " 收到用户 " + fromUserId + " 的消息: " + content);

        // 向群组所有在线成员广播消息
        broadcastToGroupMembers(groupId, fromUserId, chatRequest, messageId);

        return true;
    }

    /**
     * 向群组所有在线成员广播消息
     */
    private void broadcastToGroupMembers(Long groupId, Long fromUserId,
                                         ChatGroupSend originalMessage, Long messageId) {
        // 获取群组所有成员
        List<Long> memberIds = chatService.getGroupMembers(groupId);

        for (Long memberId : memberIds) {
            // 不向发送者自己发送（可选，根据需求调整）
            if (memberId.equals(fromUserId)) {
                continue;
            }

            // 向在线成员发送消息
            PrintWriter memberOut = OnlineUserManager.getUserOutput(memberId);
            if (memberOut != null) {
                ChatGroupReceive receiveMsg = createReceiveMessage(originalMessage, messageId);
                memberOut.println(gson.toJson(receiveMsg));
                System.out.println("[GROUP_CHAT] 向用户 " + memberId + " 发送群组消息");
            }
        }
    }

    /**
     * 创建接收消息对象
     */
    private ChatGroupReceive createReceiveMessage(ChatGroupSend sendMsg, Long messageId) {
        ChatGroupReceive receiveMsg = new ChatGroupReceive();
        receiveMsg.setType("chat_group_receive");
        receiveMsg.setGroupId(sendMsg.getGroupId());
        receiveMsg.setFromUserId(sendMsg.getFromUserId());
        receiveMsg.setContent(sendMsg.getContent());
        receiveMsg.setContentType(sendMsg.getContentType());
        receiveMsg.setFileUrl(sendMsg.getFileUrl());
        receiveMsg.setFileSize(sendMsg.getFileSize());
        receiveMsg.setFileName(sendMsg.getFileName());
        receiveMsg.setTimestamp(sendMsg.getTimestamp());
        receiveMsg.setId(messageId);
        receiveMsg.setIsRead(0); // 默认未读

        return receiveMsg;
    }
}
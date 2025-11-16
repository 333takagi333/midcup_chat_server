package com.chat.handler;

import com.chat.core.ChatService;
import com.chat.core.GroupService;
import com.chat.protocol.ChatHistoryRequest;
import com.chat.protocol.ChatHistoryResponse;
import com.chat.protocol.MessageType;

import java.util.List;

/**
 * 聊天历史记录处理器
 */
public class ChatHistoryHandler {

    private final ChatService chatService;
    private final GroupService groupService;

    public ChatHistoryHandler() {
        this.chatService = new ChatService();
        this.groupService = new GroupService();
    }

    /**
     * 处理聊天历史记录请求
     */
    public ChatHistoryResponse handle(ChatHistoryRequest request, Long currentUid) {
        ChatHistoryResponse response = new ChatHistoryResponse();
        response.setType(MessageType.CHAT_HISTORY_RESPONSE);

        if (request == null || currentUid == null) {
            response.setSuccess(false);
            response.setMessage("请求数据无效");
            return response;
        }

        String chatType = request.getChatType();
        if (chatType == null || (!chatType.equals("private") && !chatType.equals("group"))) {
            response.setSuccess(false);
            response.setMessage("聊天类型无效");
            return response;
        }

        try {
            List<ChatHistoryResponse.HistoryMessageItem> messages;

            if ("private".equals(chatType)) {
                messages = getPrivateChatHistory(request, currentUid);
            } else {
                messages = getGroupChatHistory(request, currentUid);
            }

            response.setMessages(messages);
            response.setChatType(chatType);
            response.setSuccess(true);
            response.setMessage("获取历史消息成功");

        } catch (Exception e) {
            System.err.println("[CHAT_HISTORY] Error: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("获取历史消息失败");
        }

        return response;
    }

    /**
     * 获取私聊历史记录
     */
    private List<ChatHistoryResponse.HistoryMessageItem> getPrivateChatHistory(
            ChatHistoryRequest request, Long currentUid) throws Exception {

        if (request.getTargetUserId() == null) {
            throw new IllegalArgumentException("私聊目标用户ID不能为空");
        }

        return chatService.getPrivateChatHistory(
                currentUid,
                request.getTargetUserId(),
                request.getBeforeTimestamp(),
                request.getLimit()
        );
    }

    /**
     * 获取群聊历史记录
     */
    private List<ChatHistoryResponse.HistoryMessageItem> getGroupChatHistory(
            ChatHistoryRequest request, Long currentUid) throws Exception {

        if (request.getGroupId() == null) {
            throw new IllegalArgumentException("群聊群组ID不能为空");
        }

        // 先检查用户是否在群组中
        if (!groupService.isUserInGroup(currentUid, request.getGroupId())) {
            throw new SecurityException("用户不在该群组中");
        }

        return chatService.getGroupChatHistory(
                request.getGroupId(),
                request.getBeforeTimestamp(),
                request.getLimit()
        );
    }
}
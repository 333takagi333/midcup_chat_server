package com.chat.handler;

import com.chat.core.ChatService;
import com.chat.protocol.ChatHistoryRequest;
import com.chat.protocol.ChatHistoryResponse;
import com.chat.protocol.MessageType;

/**
 * 聊天历史记录处理器 - 简化版
 */
public class ChatHistoryHandler {

    private final ChatService chatService = new ChatService();

    /**
     * 处理聊天历史记录请求
     */
    public ChatHistoryResponse handle(ChatHistoryRequest request, Long currentUid) {

        if (request == null || currentUid == null) {
            return createErrorResponse("请求数据无效");
        }

        String chatType = request.getChatType();
        if (chatType == null || (!chatType.equals("private") && !chatType.equals("group"))) {
            return createErrorResponse("聊天类型无效");
        }

        Long targetId = null;
        if ("private".equals(chatType)) {
            targetId = request.getTargetUserId();
            if (targetId == null) {
                return createErrorResponse("私聊目标用户ID不能为空");
            }
        } else {
            targetId = request.getGroupId();
            if (targetId == null) {
                return createErrorResponse("群聊群组ID不能为空");
            }
        }

        // 检查限制数量
        Integer limit = request.getLimit();
        if (limit == null || limit <= 0) {
            limit = 50;
        } else if (limit > 200) { // 限制最大查询条数
            limit = 200;
        }

        // 使用ChatService的统一方法处理
        ChatHistoryResponse response = chatService.processHistoryRequest(
                chatType,
                targetId,
                currentUid,
                request.getBeforeTimestamp(),
                limit
        );

        response.setType(MessageType.CHAT_HISTORY_RESPONSE);

        return response;
    }

    private ChatHistoryResponse createErrorResponse(String message) {
        ChatHistoryResponse response = new ChatHistoryResponse();
        response.setType(MessageType.CHAT_HISTORY_RESPONSE);
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}
package com.chat.handler;

import com.chat.core.FriendService;
import com.chat.protocol.FriendAddRequest;
import com.chat.protocol.FriendAddResponse;
import com.chat.protocol.FriendListRequest;
import com.chat.protocol.FriendListResponse;
import com.chat.protocol.MessageType;

import java.util.List;

/**
 * 好友关系处理器
 */
public class FriendHandler {

    private final FriendService friendService;

    public FriendHandler() {
        this.friendService = new FriendService();
    }

    /**
     * 处理添加好友请求
     */
    public FriendAddResponse handleFriendAdd(FriendAddRequest request, Long currentUid) {
        FriendAddResponse response = new FriendAddResponse();
        response.setType(MessageType.FRIEND_ADD_RESPONSE);

        if (request == null || currentUid == null) {
            response.setSuccess(false);
            response.setMessage("请求数据无效");
            return response;
        }

        Long toUserId = request.getToUserId();
        if (toUserId == null || toUserId.equals(currentUid)) {
            response.setSuccess(false);
            response.setMessage("目标用户ID无效");
            return response;
        }

        // 检查目标用户是否存在
        if (!friendService.userExists(toUserId)) {
            response.setSuccess(false);
            response.setMessage("目标用户不存在");
            return response;
        }

        // 检查是否已经是好友
        if (friendService.isFriend(currentUid, toUserId)) {
            response.setSuccess(false);
            response.setMessage("已经是好友关系");
            return response;
        }

        // 检查是否已有待处理的请求
        if (friendService.hasPendingRequest(currentUid, toUserId)) {
            response.setSuccess(false);
            response.setMessage("已发送过好友请求，请等待对方处理");
            return response;
        }

        // 创建好友请求
        Long requestId = friendService.createFriendRequest(currentUid, toUserId);
        if (requestId != null) {
            response.setSuccess(true);
            response.setRequestId(requestId);
            response.setStatus(0); // 待处理状态
            response.setMessage("好友请求发送成功");
        } else {
            response.setSuccess(false);
            response.setMessage("发送好友请求失败");
        }

        return response;
    }

    /**
     * 处理好友列表请求
     */
    public FriendListResponse handleFriendList(FriendListRequest request, Long currentUid) {
        FriendListResponse response = new FriendListResponse();
        response.setType(MessageType.FRIEND_LIST_RESPONSE);

        if (currentUid == null) {
            return response; // 返回空列表
        }

        List<FriendListResponse.FriendItem> friends = friendService.getFriendList(currentUid);
        response.setFriends(friends);
        return response;
    }
}
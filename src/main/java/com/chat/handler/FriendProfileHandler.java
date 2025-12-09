package com.chat.handler;

import com.chat.protocol.*;
import com.chat.core.FriendService;

/**
 * 好友资料处理器 - 处理好友详细信息和删除好友相关请求
 */
public class FriendProfileHandler {

    private final FriendService friendService;

    public FriendProfileHandler() {
        this.friendService = new FriendService();
    }

    /**
     * 处理好友详情请求
     */
    public FriendDetailResponse handleFriendDetail(FriendDetailRequest request) {
        FriendDetailResponse response = new FriendDetailResponse();

        // 参数验证
        if (request.getUserId() == null) {
            response.setSuccess(false);
            response.setMessage("用户ID不能为空");
            return response;
        }

        if (request.getFriendId() == null) {
            response.setSuccess(false);
            response.setMessage("好友ID不能为空");
            return response;
        }

        // 验证用户存在
        if (!friendService.userExists(request.getUserId())) {
            response.setSuccess(false);
            response.setMessage("当前用户不存在");
            return response;
        }

        if (!friendService.userExists(request.getFriendId())) {
            response.setSuccess(false);
            response.setMessage("好友不存在");
            return response;
        }

        // 获取好友详情
        return friendService.getFriendDetail(request.getUserId(), request.getFriendId());
    }

    /**
     * 处理删除好友请求
     */
    public DeleteFriendResponse handleDeleteFriend(DeleteFriendRequest request) {
        DeleteFriendResponse response = new DeleteFriendResponse();

        // 参数验证
        if (request.getUserId() == null) {
            response.setSuccess(false);
            response.setMessage("用户ID不能为空");
            return response;
        }

        if (request.getFriendId() == null) {
            response.setSuccess(false);
            response.setMessage("好友ID不能为空");
            return response;
        }

        // 验证用户存在
        if (!friendService.userExists(request.getUserId())) {
            response.setSuccess(false);
            response.setMessage("当前用户不存在");
            return response;
        }

        if (!friendService.userExists(request.getFriendId())) {
            response.setSuccess(false);
            response.setMessage("好友不存在");
            return response;
        }

        // 验证不能删除自己
        if (request.getUserId().equals(request.getFriendId())) {
            response.setSuccess(false);
            response.setMessage("不能删除自己");
            return response;
        }

        // 执行删除
        return friendService.deleteFriend(request.getUserId(), request.getFriendId());
    }
}
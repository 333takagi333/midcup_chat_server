package com.chat.handler;

import com.chat.core.UserService;
import com.chat.protocol.UserInfoRequest;
import com.chat.protocol.UserInfoResponse;
import com.chat.protocol.MessageType;

/**
 * 用户资料处理器
 */
public class UserInfoHandler {

    public UserInfoResponse handle(UserInfoRequest request) {
        UserInfoResponse response = new UserInfoResponse();
        response.setType(MessageType.USER_INFO_RESPONSE);

        if (request == null) {
            response.setSuccess(false);
            response.setMessage("请求数据为空");
            return response;
        }

        Long userId = request.getUserId();
        if (userId == null) {
            response.setSuccess(false);
            response.setMessage("用户ID不能为空");
            return response;
        }

        UserService userService = new UserService();
        UserService.UserProfile profile = userService.getUserProfile(userId);

        if (profile != null) {
            response.setSuccess(true);
            response.setUid(profile.getUid());
            response.setUsername(profile.getUsername());
            response.setAvatarUrl(profile.getAvatarUrl());
            response.setGender(profile.getGender());
            response.setBirthday(profile.getBirthday());
            response.setTele(profile.getTele());
            response.setMessage("获取用户资料成功");
        } else {
            response.setSuccess(false);
            response.setMessage("用户不存在或获取资料失败");
        }

        return response;
    }
}
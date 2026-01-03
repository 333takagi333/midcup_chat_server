package com.chat.handler;

import com.chat.protocol.*;
import com.chat.core.GroupService;

/**
 * 群聊详情处理器 - 处理群聊详细信息和退出群聊相关请求
 */
public class GroupDetailHandler {

    private final GroupService groupService;

    public GroupDetailHandler() {
        this.groupService = new GroupService();
    }

    /**
     * 处理群聊详情请求
     */
    public GroupDetailResponse handleGroupDetail(GroupDetailRequest request) {
        GroupDetailResponse response = new GroupDetailResponse();

        // 参数验证
        if (request.getGroupId() == null) {
            response.setSuccess(false);
            response.setMessage("群聊ID不能为空");
            return response;
        }

        if (request.getUserId() == null) {
            response.setSuccess(false);
            response.setMessage("用户ID不能为空");
            return response;
        }

        // 获取群聊详情
        return groupService.getGroupDetail(request.getGroupId(), request.getUserId());
    }

    /**
     * 处理退出群聊请求
     */
    public ExitGroupResponse handleExitGroup(ExitGroupRequest request) {
        ExitGroupResponse response = new ExitGroupResponse();

        // 参数验证
        if (request.getGroupId() == null) {
            response.setSuccess(false);
            response.setMessage("群聊ID不能为空");
            return response;
        }

        if (request.getUserId() == null) {
            response.setSuccess(false);
            response.setMessage("用户ID不能为空");
            return response;
        }

        // 执行退出操作
        return groupService.exitGroup(request.getGroupId(), request.getUserId());
    }
}
package com.chat.handler;

import com.chat.core.GroupService;
import com.chat.protocol.*;

/**
 * 群组处理器 - 处理群组相关请求
 */
public class GroupHandler {

    private final GroupService groupService;

    public GroupHandler() {
        this.groupService = new GroupService();
    }

    /**
     * 处理创建群聊请求
     */
    public GroupCreateResponse handleGroupCreate(GroupCreateRequest request, Long currentUid) {
        GroupCreateResponse response = new GroupCreateResponse();
        response.setType(MessageType.GROUP_CREATE_RESPONSE);

        if (request == null || currentUid == null) {
            response.setSuccess(false);
            response.setMessage("请求数据无效");
            return response;
        }

        String groupName = request.getGroupName();
        if (groupName == null || groupName.trim().isEmpty()) {
            response.setSuccess(false);
            response.setMessage("群聊名称不能为空");
            return response;
        }

        groupName = groupName.trim();
        if (groupName.length() > 20) {
            response.setSuccess(false);
            response.setMessage("群聊名称不能超过20个字符");
            return response;
        }

        try {
            // 创建群聊
            Long groupId = groupService.createGroup(groupName, currentUid);

            if (groupId != null) {
                response.setSuccess(true);
                response.setGroupId(groupId);
                response.setGroupName(groupName);
                response.setMessage("群聊创建成功");
            } else {
                response.setSuccess(false);
                response.setMessage("群聊创建失败，可能群聊名称已存在");
            }
        } catch (Exception e) {
            response.setSuccess(false);
            response.setMessage("创建群聊时发生错误: " + e.getMessage());
            System.err.println("[CREATE_GROUP_ERROR] " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    /**
     * 处理群组列表请求
     */
    public GroupListResponse handleGroupList(GroupListRequest request, Long currentUid) {
        GroupListResponse response = new GroupListResponse();
        response.setType(MessageType.GROUP_LIST_RESPONSE);

        if (currentUid == null) {
            return response; // 返回空列表
        }

        var groups = groupService.getGroupList(currentUid);
        response.setGroups(groups);
        return response;
    }

    /**
     * 检查用户是否在群组中
     */
    public boolean isUserInGroup(Long userId, Long groupId) {
        return groupService.isUserInGroup(userId, groupId);
    }
}
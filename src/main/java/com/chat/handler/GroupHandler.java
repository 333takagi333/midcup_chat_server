package com.chat.handler;

import com.chat.core.GroupService;
import com.chat.protocol.GroupListRequest;
import com.chat.protocol.GroupListResponse;
import com.chat.protocol.MessageType;

import java.util.List;

/**
 * 群组处理器 - 处理群组列表请求
 */
public class GroupHandler {

    private final GroupService groupService;

    public GroupHandler() {
        this.groupService = new GroupService();
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

        List<GroupListResponse.GroupItem> groups = groupService.getGroupList(currentUid);
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
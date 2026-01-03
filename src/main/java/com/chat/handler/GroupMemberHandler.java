package com.chat.handler;

import com.chat.core.GroupMemberService;
import com.chat.protocol.GroupAddMemberRequest;
import com.chat.protocol.GroupAddMemberResponse;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.List;

/**
 * 群成员管理Handler - 处理群成员添加等请求
 */
public class GroupMemberHandler {

    private final Gson gson = new Gson();
    private final GroupMemberService groupMemberService = new GroupMemberService();

    /**
     * 处理添加群成员请求
     */
    public GroupAddMemberResponse handleAddMember(GroupAddMemberRequest request, Long currentUid) {
        GroupAddMemberResponse response = new GroupAddMemberResponse();

        try {
            Long groupId = request.getGroupId();
            Long targetUserId = request.getTargetUserId();
            Long operatorId = currentUid;

            // 验证请求参数
            if (groupId == null) {
                response.setSuccess(false);
                response.setMessage("群组ID不能为空");
                return response;
            }

            if (targetUserId == null) {
                response.setSuccess(false);
                response.setMessage("目标用户ID不能为空");
                return response;
            }

            // 检查操作者是否在群中
            if (!groupMemberService.isUserInGroup(groupId, operatorId)) {
                response.setSuccess(false);
                response.setMessage("您不在该群聊中");
                return response;
            }

            // 检查操作者是否有添加成员的权限
            if (!groupMemberService.canAddMember(groupId, operatorId)) {
                response.setSuccess(false);
                response.setMessage("只有群主可以添加成员");
                return response;
            }

            // 添加群成员
            boolean success = groupMemberService.addGroupMember(groupId, targetUserId, operatorId);

            if (success) {
                response.setSuccess(true);
                response.setMessage("添加群成员成功");
                response.setGroupId(groupId);
                response.setTargetUserId(targetUserId);
                response.setOperatorId(operatorId);

                // 可选：发送系统消息通知群成员
                sendGroupMemberAddNotification(groupId, targetUserId, operatorId);

            } else {
                response.setSuccess(false);
                response.setMessage("添加群成员失败");
            }

        } catch (SecurityException e) {
            response.setSuccess(false);
            response.setMessage(e.getMessage());

        } catch (IllegalArgumentException e) {
            response.setSuccess(false);
            response.setMessage(e.getMessage());

        } catch (Exception e) {
            System.err.println("[GroupMemberHandler] 处理添加群成员请求失败: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("服务器内部错误: " + e.getMessage());
        }

        return response;
    }

    /**
     * 处理批量添加群成员请求
     */
    public GroupAddMemberResponse handleBatchAddMember(GroupAddMemberRequest request, Long currentUid) {
        GroupAddMemberResponse response = new GroupAddMemberResponse();

        try {
            Long groupId = request.getGroupId();
            String targetUserIdsStr = request.getTargetUserId().toString(); // 这里可能需要修改协议

            // 解析用户ID列表（假设以逗号分隔）
            List<Long> targetUserIds = Arrays.stream(targetUserIdsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .toList();

            if (targetUserIds.isEmpty()) {
                response.setSuccess(false);
                response.setMessage("没有有效的用户ID");
                return response;
            }

            // 批量添加成员
            int successCount = groupMemberService.addGroupMembers(groupId, targetUserIds, currentUid);

            if (successCount > 0) {
                response.setSuccess(true);
                response.setMessage(String.format("成功添加 %d 个成员到群聊", successCount));
                response.setGroupId(groupId);
                response.setOperatorId(currentUid);

                // 发送批量添加通知
                sendBatchGroupMemberAddNotification(groupId, targetUserIds, currentUid);

            } else {
                response.setSuccess(false);
                response.setMessage("添加群成员失败，可能用户已在群中或用户不存在");
            }

        } catch (Exception e) {
            System.err.println("[GroupMemberHandler] 批量添加群成员失败: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("服务器内部错误: " + e.getMessage());
        }

        return response;
    }

    /**
     * 发送群成员添加通知（可作为系统消息）
     */
    private void sendGroupMemberAddNotification(Long groupId, Long targetUserId, Long operatorId) {
        // 这里可以发送系统消息到群聊，通知成员变动
        System.out.println(String.format("[GroupMemberHandler] 群组 %d: 用户 %d 添加了用户 %d",
                groupId, operatorId, targetUserId));

        // 实际实现可能需要调用ChatHandler发送系统消息
        // 示例：发送到群聊的系统消息
        String systemMessage = String.format("用户 %d 邀请了用户 %d 加入群聊", operatorId, targetUserId);
        // chatHandler.sendGroupSystemMessage(groupId, systemMessage);
    }

    /**
     * 发送批量添加群成员通知
     */
    private void sendBatchGroupMemberAddNotification(Long groupId, List<Long> targetUserIds, Long operatorId) {
        System.out.println(String.format("[GroupMemberHandler] 群组 %d: 用户 %d 批量添加了 %d 个成员",
                groupId, operatorId, targetUserIds.size()));

        // 实际实现中发送系统消息
        // String systemMessage = String.format("用户 %d 邀请了 %d 位成员加入群聊", operatorId, targetUserIds.size());
        // chatHandler.sendGroupSystemMessage(groupId, systemMessage);
    }

    /**
     * 获取群成员列表
     */
    public String getGroupMembers(Long groupId, Long currentUid) {
        try {
            // 检查用户是否在群中
            if (!groupMemberService.isUserInGroup(groupId, currentUid)) {
                return "{\"success\":false,\"message\":\"您不在该群聊中\"}";
            }

            List<Object[]> members = groupMemberService.getGroupMemberDetails(groupId);

            // 构建JSON响应
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\"success\":true,\"members\":[");

            for (int i = 0; i < members.size(); i++) {
                Object[] member = members.get(i);
                jsonBuilder.append(String.format("{\"userId\":%d,\"username\":\"%s\",\"avatar\":\"%s\"}",
                        member[0], member[1], member[2]));

                if (i < members.size() - 1) {
                    jsonBuilder.append(",");
                }
            }

            jsonBuilder.append("]}");
            return jsonBuilder.toString();

        } catch (Exception e) {
            System.err.println("[GroupMemberHandler] 获取群成员列表失败: " + e.getMessage());
            return "{\"success\":false,\"message\":\"获取成员列表失败: " + e.getMessage() + "\"}";
        }
    }
}
package com.chat.handler;

import com.chat.core.UserService;
import com.chat.protocol.UpdateProfileRequest;
import com.chat.protocol.UpdateProfileResponse;
import com.chat.protocol.UserInfoRequest;
import com.chat.protocol.UserInfoResponse;
import com.chat.protocol.MessageType;
import com.chat.utils.FileStorageUtil;

import java.util.Base64;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 用户资料处理器
 */
public class UserInfoHandler {

    private final UserService userService = new UserService();
    private static final String AVATAR_UPLOAD_DIR = "uploads/avatars/";

    /**
     * 处理获取用户信息请求
     */
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

    /**
     * 处理更新用户资料请求
     */
    public UpdateProfileResponse handleUpdateProfile(UpdateProfileRequest request, Long currentUid) {
        UpdateProfileResponse response = new UpdateProfileResponse();
        response.setType(MessageType.UPDATE_PROFILE_RESPONSE);

        if (request == null || currentUid == null) {
            response.setSuccess(false);
            response.setMessage("请求数据无效");
            return response;
        }

        try {
            // 验证用户名
            if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
                String newUsername = request.getUsername().trim();

                // 检查用户名长度
                if (newUsername.length() < 2 || newUsername.length() > 20) {
                    response.setSuccess(false);
                    response.setMessage("用户名长度应在2-20个字符之间");
                    return response;
                }

                // 检查用户名是否已存在（排除当前用户）
                if (userService.isUsernameExists(newUsername, currentUid)) {
                    response.setSuccess(false);
                    response.setMessage("用户名已存在");
                    return response;
                }
            }

            // 处理头像上传
            String avatarUrl = null;
            if (request.getAvatarData() != null && !request.getAvatarData().isEmpty() &&
                    request.getAvatarFileName() != null && !request.getAvatarFileName().isEmpty()) {

                avatarUrl = saveAvatarImage(request.getAvatarData(), request.getAvatarFileName(), currentUid);
                if (avatarUrl == null) {
                    response.setSuccess(false);
                    response.setMessage("头像保存失败");
                    return response;
                }
            }

            // 创建用户资料对象
            UserService.UserProfile profile = new UserService.UserProfile();
            profile.setUid(currentUid);
            profile.setUsername(request.getUsername());
            profile.setGender(request.getGender());
            profile.setBirthday(request.getBirthday());
            profile.setTele(request.getTele());
            profile.setAvatarUrl(avatarUrl);

            // 更新用户资料
            boolean updateSuccess = userService.updateUserProfile(profile);
            if (updateSuccess) {
                response.setSuccess(true);
                response.setMessage("资料更新成功");
                response.setAvatarUrl(avatarUrl);
            } else {
                response.setSuccess(false);
                response.setMessage("资料更新失败");
            }

        } catch (Exception e) {
            System.err.println("[UPDATE_PROFILE] Error: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("服务器内部错误: " + e.getMessage());
        }

        return response;
    }

    /**
     * 保存头像图片到服务器
     */
    private String saveAvatarImage(String base64Data, String fileName, Long userId) {
        try {
            // 确保上传目录存在
            Path uploadDir = Paths.get(AVATAR_UPLOAD_DIR);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 生成唯一的文件名
            String fileExtension = getFileExtension(fileName);
            String uniqueFileName = "avatar_" + userId + "_" + System.currentTimeMillis() + fileExtension;
            Path filePath = uploadDir.resolve(uniqueFileName);

            // 解码Base64数据并保存文件
            byte[] imageData = Base64.getDecoder().decode(base64Data);
            Files.write(filePath, imageData);

            // 返回相对URL路径
            return AVATAR_UPLOAD_DIR + uniqueFileName;

        } catch (IOException e) {
            System.err.println("[AVATAR_SAVE] Error saving avatar: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex);
        }
        return ".png"; // 默认扩展名
    }
}
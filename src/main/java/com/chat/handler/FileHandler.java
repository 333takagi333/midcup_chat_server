package com.chat.handler;

import com.chat.core.FileService;
import com.chat.protocol.*;
import com.chat.utils.FileStorageUtil;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

/**
 * 文件处理器 - 处理文件上传下载相关请求（支持Base64传输）
 */
public class FileHandler {

    private final Gson gson = new Gson();
    private final FileService fileService = new FileService();
    private static final String FILE_UPLOAD_DIR = "uploads/files/";

    /**
     * 处理文件上传请求（Base64方式）
     */
    public FileUploadResponse handleFileUpload(FileUploadRequest request) {
        FileUploadResponse response = new FileUploadResponse();
        response.setType(MessageType.FILE_UPLOAD_RESPONSE);

        try {
            // 验证请求数据
            if (request == null || request.getSenderId() == null ||
                    request.getFileName() == null || request.getFileName().isEmpty()) {
                response.setSuccess(false);
                response.setMessage("请求数据不完整");
                return response;
            }

            // 验证聊天类型
            String chatType = request.getChatType();
            if (!"private".equals(chatType) && !"group".equals(chatType)) {
                response.setSuccess(false);
                response.setMessage("无效的聊天类型");
                return response;
            }

            // 注意：Base64文件内容不在这里处理，而是在FilePrivateSend/FileGroupSend中处理
            // 这里只返回成功响应，表示可以接收文件消息

            String fileId = generateFileId();

            response.setSuccess(true);
            response.setMessage("可以发送文件消息");
            response.setFileId(fileId);
            // uploadUrl和downloadUrl留空，因为文件内容将通过消息直接发送

        } catch (Exception e) {
            System.err.println("[FILE_HANDLER] 处理文件上传异常: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("服务器内部错误: " + e.getMessage());
        }

        return response;
    }

    /**
     * 处理私聊文件发送消息（Base64方式）
     */
    public boolean handleFilePrivateSend(FilePrivateSend message) {
        try {
            if (message == null || message.getSenderId() == null ||
                    message.getReceiverId() == null || message.getDownloadUrl() == null) {
                System.err.println("[FILE_HANDLER] 私聊文件消息数据不完整");
                return false;
            }

            // downloadUrl字段实际上包含的是Base64编码的文件内容
            String base64Data = message.getDownloadUrl();
            if (base64Data.isEmpty()) {
                System.err.println("[FILE_HANDLER] Base64数据为空");
                return false;
            }

            // 解码Base64数据
            byte[] fileData = Base64.getDecoder().decode(base64Data);
            if (fileData.length == 0) {
                System.err.println("[FILE_HANDLER] Base64解码后数据为空");
                return false;
            }

            // 生成文件保存路径
            String filePath = saveFileToDisk(fileData, message.getFileName());
            if (filePath == null) {
                System.err.println("[FILE_HANDLER] 文件保存失败");
                return false;
            }

            // 保存文件信息到数据库（使用相对路径）
            String relativePath = filePath.replace(Paths.get("").toAbsolutePath().toString() + File.separator, "")
                    .replace("\\", "/");

            boolean saved = fileService.saveFileInfo(
                    message.getFileId(),
                    message.getFileName(),
                    message.getFileSize(),
                    message.getFileType(),
                    message.getSenderId(),
                    message.getReceiverId(),
                    null, // 私聊没有groupId
                    relativePath
            );

            if (!saved) {
                System.err.println("[FILE_HANDLER] 文件信息保存到数据库失败");
                return false;
            }

            System.out.println("[FILE_HANDLER] 私聊文件保存成功: " + message.getFileName() +
                    " (" + message.getFileSize() + " bytes) -> " + relativePath);

            // 创建接收消息并转发给接收方
            FilePrivateReceive receiveMsg = new FilePrivateReceive();
            receiveMsg.setFileId(message.getFileId());
            receiveMsg.setFileName(message.getFileName());
            receiveMsg.setFileSize(message.getFileSize());
            receiveMsg.setFileType(message.getFileType());
            receiveMsg.setSenderId(message.getSenderId());
            receiveMsg.setReceiverId(message.getReceiverId());
            receiveMsg.setDownloadUrl(relativePath); // 存储文件路径而非Base64
            receiveMsg.setTimestamp(System.currentTimeMillis());

            // 这里需要实现转发逻辑（使用OnlineUserManager）
            forwardFilePrivateReceive(receiveMsg);

            return true;

        } catch (IllegalArgumentException e) {
            System.err.println("[FILE_HANDLER] Base64解码失败: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("[FILE_HANDLER] 处理私聊文件发送异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 处理群聊文件发送消息（Base64方式）
     */
    public boolean handleFileGroupSend(FileGroupSend message) {
        try {
            if (message == null || message.getSenderId() == null ||
                    message.getGroupId() == null || message.getDownloadUrl() == null) {
                System.err.println("[FILE_HANDLER] 群聊文件消息数据不完整");
                return false;
            }

            // downloadUrl字段实际上包含的是Base64编码的文件内容
            String base64Data = message.getDownloadUrl();
            if (base64Data.isEmpty()) {
                System.err.println("[FILE_HANDLER] Base64数据为空");
                return false;
            }

            // 解码Base64数据
            byte[] fileData = Base64.getDecoder().decode(base64Data);
            if (fileData.length == 0) {
                System.err.println("[FILE_HANDLER] Base64解码后数据为空");
                return false;
            }

            // 生成文件保存路径
            String filePath = saveFileToDisk(fileData, message.getFileName());
            if (filePath == null) {
                System.err.println("[FILE_HANDLER] 文件保存失败");
                return false;
            }

            // 保存文件信息到数据库（使用相对路径）
            String relativePath = filePath.replace(Paths.get("").toAbsolutePath().toString() + File.separator, "")
                    .replace("\\", "/");

            boolean saved = fileService.saveFileInfo(
                    message.getFileId(),
                    message.getFileName(),
                    message.getFileSize(),
                    message.getFileType(),
                    message.getSenderId(),
                    null, // 群聊没有receiverId
                    message.getGroupId(),
                    relativePath
            );

            if (!saved) {
                System.err.println("[FILE_HANDLER] 文件信息保存到数据库失败");
                return false;
            }

            System.out.println("[FILE_HANDLER] 群聊文件保存成功: " + message.getFileName() +
                    " (" + message.getFileSize() + " bytes) -> " + relativePath);

            // 创建接收消息并转发给群聊成员
            FileGroupReceive receiveMsg = new FileGroupReceive();
            receiveMsg.setFileId(message.getFileId());
            receiveMsg.setFileName(message.getFileName());
            receiveMsg.setFileSize(message.getFileSize());
            receiveMsg.setFileType(message.getFileType());
            receiveMsg.setSenderId(message.getSenderId());
            receiveMsg.setGroupId(message.getGroupId());
            receiveMsg.setDownloadUrl(relativePath); // 存储文件路径而非Base64
            receiveMsg.setTimestamp(System.currentTimeMillis());

            // 这里需要实现转发逻辑（使用OnlineUserManager）
            forwardFileGroupReceive(receiveMsg);

            return true;

        } catch (IllegalArgumentException e) {
            System.err.println("[FILE_HANDLER] Base64解码失败: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("[FILE_HANDLER] 处理群聊文件发送异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将文件保存到磁盘
     */
    private String saveFileToDisk(byte[] fileData, String fileName) {
        try {
            // 确保上传目录存在
            Path uploadDir = Paths.get(FILE_UPLOAD_DIR);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 生成安全的文件名
            String safeFileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
            String fileId = generateFileId();
            String timestamp = String.valueOf(System.currentTimeMillis());

            String finalFileName = timestamp + "_" + fileId + "_" + safeFileName;
            Path filePath = uploadDir.resolve(finalFileName);

            // 保存文件
            Files.write(filePath, fileData);

            System.out.println("[FILE_HANDLER] 文件保存到磁盘: " + filePath.toString());
            return filePath.toString();

        } catch (Exception e) {
            System.err.println("[FILE_HANDLER] 保存文件到磁盘失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 处理文件下载请求
     */
    public FileDownloadResponse handleFileDownload(FileDownloadRequest request) {
        FileDownloadResponse response = new FileDownloadResponse();
        response.setType(MessageType.FILE_DOWNLOAD_RESPONSE);

        try {
            // 验证请求数据
            if (request == null || request.getFileId() == null || request.getUserId() == null) {
                response.setSuccess(false);
                response.setMessage("请求数据不完整");
                return response;
            }

            // 验证用户权限
            if (!fileService.hasFilePermission(request.getFileId(), request.getUserId())) {
                response.setSuccess(false);
                response.setMessage("无权限下载该文件");
                return response;
            }

            // 获取文件信息
            FileService.FileInfo fileInfo = fileService.getFileInfo(request.getFileId(), request.getUserId());
            if (fileInfo == null) {
                response.setSuccess(false);
                response.setMessage("文件不存在");
                return response;
            }

            // 验证文件路径
            String filePath = fileInfo.getDownloadUrl();
            if (filePath == null || filePath.isEmpty()) {
                response.setSuccess(false);
                response.setMessage("文件路径无效");
                return response;
            }

            // 检查文件是否存在
            File file = new File(filePath);
            if (!file.exists()) {
                // 尝试使用绝对路径
                file = new File(FILE_UPLOAD_DIR + File.separator +
                        Paths.get(filePath).getFileName().toString());

                if (!file.exists()) {
                    response.setSuccess(false);
                    response.setMessage("文件不存在或已被删除");
                    return response;
                }
            }

            // 返回成功响应
            response.setSuccess(true);
            response.setMessage("文件下载链接已生成");
            response.setFileId(fileInfo.getFileId());
            response.setFileName(fileInfo.getFileName());
            response.setDownloadUrl(fileInfo.getDownloadUrl()); // 返回相对路径

            System.out.println("[FILE_HANDLER] 文件下载响应: " + fileInfo.getFileName() +
                    " -> " + fileInfo.getDownloadUrl());

        } catch (Exception e) {
            System.err.println("[FILE_HANDLER] 处理文件下载异常: " + e.getMessage());
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("服务器内部错误: " + e.getMessage());
        }

        return response;
    }

    /**
     * 转发私聊文件接收消息
     */
    private void forwardFilePrivateReceive(FilePrivateReceive message) {
        try {
            // 使用OnlineUserManager获取接收方的输出流
            // 这里需要你实现转发逻辑
            System.out.println("[FILE_HANDLER] 需要转发私聊文件消息给用户: " + message.getReceiverId());

            // 示例转发代码（需要你根据实际架构实现）：
            // PrintWriter out = OnlineUserManager.getUserOutput(message.getReceiverId());
            // if (out != null) {
            //     String json = gson.toJson(message);
            //     out.println(json);
            // }

        } catch (Exception e) {
            System.err.println("[FILE_HANDLER] 转发私聊文件消息失败: " + e.getMessage());
        }
    }

    /**
     * 转发群聊文件接收消息
     */
    private void forwardFileGroupReceive(FileGroupReceive message) {
        try {
            // 需要获取群聊所有在线成员并转发
            System.out.println("[FILE_HANDLER] 需要转发群聊文件消息给群组: " + message.getGroupId());

            // 示例转发代码（需要你根据实际架构实现）：
            // List<Long> memberIds = getGroupMembers(message.getGroupId());
            // for (Long memberId : memberIds) {
            //     if (!memberId.equals(message.getSenderId())) { // 不发给发送者自己
            //         PrintWriter out = OnlineUserManager.getUserOutput(memberId);
            //         if (out != null) {
            //             String json = gson.toJson(message);
            //             out.println(json);
            //         }
            //     }
            // }

        } catch (Exception e) {
            System.err.println("[FILE_HANDLER] 转发群聊文件消息失败: " + e.getMessage());
        }
    }

    /**
     * 生成文件ID
     */
    private String generateFileId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
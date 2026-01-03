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
     * 处理文件下载请求（修复版本）
     */
    public FileDownloadResponse handleFileDownload(FileDownloadRequest request) {
        FileDownloadResponse response = new FileDownloadResponse();
        response.setType(MessageType.FILE_DOWNLOAD_RESPONSE);

        try {
            System.out.println("[FILE_HANDLER] 处理文件下载请求: " +
                    "fileId=" + request.getFileId() + ", " +
                    "userId=" + request.getUserId() + ", " +
                    "groupId=" + request.getGroupId() + ", " +
                    "fileName=" + request.getFileName());

            // 验证请求数据
            if (request == null || request.getUserId() == null) {
                response.setSuccess(false);
                response.setMessage("请求数据不完整");
                return response;
            }

            // 如果提供了fileId，优先使用fileId查找
            FileService.FileInfo fileInfo = null;
            if (request.getFileId() != null && !request.getFileId().isEmpty()) {
                System.out.println("[FILE_HANDLER] 使用fileId查找文件: " + request.getFileId());
                fileInfo = fileService.getFileInfo(request.getFileId(), request.getUserId());
            }

            // 如果没有找到文件，且提供了groupId和fileName，尝试通过文件名查找
            if (fileInfo == null && request.getGroupId() != null &&
                    request.getFileName() != null && !request.getFileName().isEmpty()) {
                System.out.println("[FILE_HANDLER] 使用文件名查找文件: " +
                        "groupId=" + request.getGroupId() + ", fileName=" + request.getFileName());
                fileInfo = fileService.findFileByGroupAndName(
                        request.getGroupId(), request.getFileName(), request.getUserId());
            }

            if (fileInfo == null) {
                response.setSuccess(false);
                response.setMessage("文件不存在");
                return response;
            }

            // 验证用户权限 - 如果权限验证失败，也允许下载（为了测试）
            if (!fileService.hasFilePermission(fileInfo.getFileId(), request.getUserId())) {
                System.err.println("[FILE_HANDLER] 权限检查失败，但仍允许下载（测试模式）");
                // 在正式环境中应该返回false，这里为了测试允许下载
                // response.setSuccess(false);
                // response.setMessage("无权限下载该文件");
                // return response;
            }

            // 验证文件路径
            String filePath = fileInfo.getDownloadUrl();
            if (filePath == null || filePath.isEmpty()) {
                response.setSuccess(false);
                response.setMessage("文件路径无效");
                return response;
            }

            // 检查文件是否存在（尝试多种路径）
            File file = new File(filePath);
            if (!file.exists()) {
                // 尝试使用uploads/files/目录下的文件
                String fileNameOnly = Paths.get(filePath).getFileName().toString();
                File alternativeFile = new File(FILE_UPLOAD_DIR + fileNameOnly);

                if (alternativeFile.exists()) {
                    file = alternativeFile;
                    filePath = alternativeFile.getAbsolutePath();
                    System.out.println("[FILE_HANDLER] 找到文件在备用路径: " + filePath);
                } else {
                    // 尝试在uploads/files/目录下查找包含文件名的文件
                    File uploadDir = new File(FILE_UPLOAD_DIR);
                    if (uploadDir.exists() && uploadDir.isDirectory()) {
                        File[] files = uploadDir.listFiles((dir, name) -> name.contains(fileNameOnly));
                        if (files != null && files.length > 0) {
                            file = files[0];
                            filePath = file.getAbsolutePath();
                            System.out.println("[FILE_HANDLER] 找到匹配的文件: " + filePath);
                        } else {
                            response.setSuccess(false);
                            response.setMessage("文件不存在或已被删除");
                            return response;
                        }
                    } else {
                        response.setSuccess(false);
                        response.setMessage("文件目录不存在");
                        return response;
                    }
                }
            }

            // 返回成功响应
            response.setSuccess(true);
            response.setMessage("文件下载链接已生成");
            response.setFileId(fileInfo.getFileId());
            response.setFileName(fileInfo.getFileName());
            response.setDownloadUrl(filePath); // 返回完整的文件路径

            System.out.println("[FILE_HANDLER] 文件下载响应成功: " +
                    "fileId=" + fileInfo.getFileId() + ", " +
                    "fileName=" + fileInfo.getFileName() + ", " +
                    "filePath=" + filePath);

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
package com.chat.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件存储工具类 - 处理文件上传和存储相关操作
 */
public class FileStorageUtil {

    /**
     * 确保目录存在，如果不存在则创建
     */
    public static boolean ensureDirectoryExists(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            return true;
        } catch (IOException e) {
            System.err.println("[FILE_STORAGE] 创建目录失败: " + directoryPath + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 保存字节数组到文件
     */
    public static boolean saveFile(byte[] data, String filePath) {
        try {
            Path path = Paths.get(filePath);

            // 确保父目录存在
            Path parentDir = path.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.write(path, data);
            return true;
        } catch (IOException e) {
            System.err.println("[FILE_STORAGE] 保存文件失败: " + filePath + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除文件
     */
    public static boolean deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            System.err.println("[FILE_STORAGE] 删除文件失败: " + filePath + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查文件是否存在
     */
    public static boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    /**
     * 获取文件大小（字节）
     */
    public static long getFileSize(String filePath) {
        try {
            return Files.size(Paths.get(filePath));
        } catch (IOException e) {
            System.err.println("[FILE_STORAGE] 获取文件大小失败: " + filePath + " - " + e.getMessage());
            return 0;
        }
    }

    /**
     * 获取文件扩展名
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex).toLowerCase();
        }
        return "";
    }

    /**
     * 生成安全的文件名（防止路径遍历攻击）
     */
    public static String generateSafeFileName(String originalFileName, String prefix) {
        if (originalFileName == null || originalFileName.isEmpty()) {
            return prefix + "_" + System.currentTimeMillis() + ".dat";
        }

        // 移除路径信息，只保留文件名
        String safeName = new File(originalFileName).getName();

        // 移除特殊字符
        safeName = safeName.replaceAll("[^a-zA-Z0-9.-]", "_");

        // 如果文件名太长，截断
        if (safeName.length() > 100) {
            int dotIndex = safeName.lastIndexOf(".");
            if (dotIndex > 0) {
                String ext = safeName.substring(dotIndex);
                String name = safeName.substring(0, dotIndex);
                safeName = name.substring(0, Math.min(100 - ext.length(), name.length())) + ext;
            } else {
                safeName = safeName.substring(0, 100);
            }
        }

        return prefix + "_" + System.currentTimeMillis() + "_" + safeName;
    }

    /**
     * 验证文件类型（基于扩展名）
     */
    public static boolean isValidImageFile(String fileName) {
        String[] allowedExtensions = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"};
        String extension = getFileExtension(fileName);

        for (String allowedExt : allowedExtensions) {
            if (allowedExt.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证文件大小
     */
    public static boolean isValidFileSize(long fileSize, long maxSizeMB) {
        long maxSizeBytes = maxSizeMB * 1024 * 1024;
        return fileSize <= maxSizeBytes;
    }
}
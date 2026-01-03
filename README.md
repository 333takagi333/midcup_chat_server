# 中杯 服务器端

## 关于该项目

“中杯”聊天软件服务器端的配置与启动是基于云服务器实现的，因此以下所有操作都在云服务器上完成。

## 开始步骤

### 先决条件

云服务器需要安装配置的内容。

下载安装：

*   Java 建议在 java21 或以上版本运行
```sh
java -version
java 21.0.8 2025-07-15 LTS
``````
*  maven 建议在 maven3.9.11 以上运行
```sh
mvn -version
Apache Maven 3.9.11 
``````

*  mysql 建议在 mysql5.7 以上运行

```sh
mysql> SELECT VERSION();
+------------+
| VERSION()  |
+------------+
| 5.7.44-log |
+------------+
1 row in set (0.00 sec)
``````

*  nginx 建议在 nginx1.29.3 以上运行（nginx：静态资源服务器）

```sh
nginx-1.29.3
``````



环境配置：

*  mysql 所需结构  (以下为 Navicat 导出文件)

```sh
/*
 Navicat MySQL Dump SQL

 Source Server         : dxq
 Source Server Type    : MySQL
 Source Server Version : 50723 (5.7.23-log)
 Source Host           : localhost:3306
 Source Schema         : midcupchat

 Target Server Type    : MySQL
 Target Server Version : 50723 (5.7.23-log)
 File Encoding         : 65001

 Date: 03/01/2026 14:40:37
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for friend_request
-- ----------------------------
DROP TABLE IF EXISTS `friend_request`;
CREATE TABLE `friend_request`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `from_user` bigint(20) NOT NULL,
  `to_user` bigint(20) NOT NULL,
  `status` tinyint(4) NULL DEFAULT 0 COMMENT '0待处理 1同意 2拒绝',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_from`(`from_user`) USING BTREE,
  INDEX `idx_to`(`to_user`) USING BTREE,
  CONSTRAINT `fk_req_from` FOREIGN KEY (`from_user`) REFERENCES `user_auth` (`uid`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_req_to` FOREIGN KEY (`to_user`) REFERENCES `user_auth` (`uid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for friendship
-- ----------------------------
DROP TABLE IF EXISTS `friendship`;
CREATE TABLE `friendship`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `friend_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_friend`(`friend_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  CONSTRAINT `fk_friendship_friend` FOREIGN KEY (`friend_id`) REFERENCES `user_auth` (`uid`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_friendship_user` FOREIGN KEY (`user_id`) REFERENCES `user_auth` (`uid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 45 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for group_info
-- ----------------------------
DROP TABLE IF EXISTS `group_info`;
CREATE TABLE `group_info`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_group_owner`(`owner_id`) USING BTREE,
  CONSTRAINT `fk_group_owner` FOREIGN KEY (`owner_id`) REFERENCES `user_auth` (`uid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for group_member
-- ----------------------------
DROP TABLE IF EXISTS `group_member`;
CREATE TABLE `group_member`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_group`(`group_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  CONSTRAINT `fk_member_group` FOREIGN KEY (`group_id`) REFERENCES `group_info` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_member_user` FOREIGN KEY (`user_id`) REFERENCES `user_auth` (`uid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for message
-- ----------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `sender_id` bigint(20) NOT NULL,
  `receiver_id` bigint(20) NULL DEFAULT NULL,
  `group_id` bigint(20) NULL DEFAULT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `content_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `file_size` bigint(20) NULL DEFAULT NULL,
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `timestamp` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `is_read` tinyint(4) NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_group`(`group_id`) USING BTREE,
  INDEX `idx_receiver`(`receiver_id`) USING BTREE,
  INDEX `idx_sender`(`sender_id`) USING BTREE,
  CONSTRAINT `fk_msg_sender` FOREIGN KEY (`sender_id`) REFERENCES `user_auth` (`uid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 40 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_auth
-- ----------------------------
DROP TABLE IF EXISTS `user_auth`;
CREATE TABLE `user_auth`  (
  `uid` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `salt` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `recovery_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`uid`) USING BTREE,
  UNIQUE INDEX `username`(`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_profile
-- ----------------------------
DROP TABLE IF EXISTS `user_profile`;
CREATE TABLE `user_profile`  (
  `user_id` bigint(20) NOT NULL,
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `gender` tinyint(4) NULL DEFAULT 0,
  `birthday` date NULL DEFAULT NULL,
  `tele` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`user_id`) USING BTREE,
  CONSTRAINT `fk_user_profile` FOREIGN KEY (`user_id`) REFERENCES `user_auth` (`uid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;

``````

*   win+R 运行以下内容，配置本地端口“12345”和”12355”的入站规则

```sh
wf.msc
``````

*   在云服务器上的安全组里配置端口“12345”和”12355”的入方向访问规则

*  配置 nginx.conf 文件里的文件开放逻辑

### 启动步骤

在 3.1 环境配置完成后执行以下步骤

*   使用 maven 导出 jar 包

```sh
mvnd clean package -DskipTests
``````

*  双击启动 nginx.exe 文件

*   启动 jar 包

```sh
java -jar C:\Users\Administrator\Desktop\study\midcup_chat_server\target\midcup_chat_server-1.0-SNAPSHOT.jar
``````



*   当出现以下内容时，服务器便已经运行了。

```sh
Initializing database connection pool...
[main] INFO com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Starting...
[main] INFO com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@2abf4075
[main] INFO com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Start completed.
Database connection pool initialized successfully.
Server started on port: 12345
``````


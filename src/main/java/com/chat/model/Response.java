package com.chat.model;

/**
 * 标准响应封装：包含类型、状态、消息与可选数据。
 */
public class Response {
    // 响应类型标识，如 LOGIN_RESULT、ERROR、FATAL、UNKNOWN
    private String type;
    // 业务状态，如 SUCCESS、ERROR、FATAL、OK
    private String status;
    // 面向人类可读的说明信息
    private String message;
    // 可选数据载荷，不同类型返回不同结构
    private Object data;

    // 完整构造：指定类型、状态、消息与数据
    public Response(String type, String status, String message, Object data) {
        this.type = type;
        this.status = status;
        this.message = message;
        this.data = data;
    }

    // 构造函数：无数据载荷
    public Response(String type, String status, String message) {
        this(type, status, message, null);
    }

    // 兼容旧用法：默认类型为 GENERIC、无数据
    public Response(String status, String message) {
        this("GENERIC", status, message, null);
    }

    // 获取响应类型
    public String getType() {
        return type;
    }

    // 设置响应类型
    public void setType(String type) {
        this.type = type;
    }

    // 获取业务状态
    public String getStatus() {
        return status;
    }

    // 设定业务状态
    public void setStatus(String status) {
        this.status = status;
    }

    // 获取人类可读信息
    public String getMessage() {
        return message;
    }

    // 设定人类可读信息
    public void setMessage(String message) {
        this.message = message;
    }

    // 获取可选数据载荷
    public Object getData() {
        return data;
    }

    // 设定可选数据载荷
    public void setData(Object data) {
        this.data = data;
    }
}

package com.chat.model;

// 通用请求封装：包含类型与数据载荷
public class Request<T> {
    private String type;
    private T payload;

    // 获取请求类型
    public String getType() {
        return type;
    }

    // 获取请求载荷
    public T getPayload() {
        return payload;
    }

    // 设置请求类型
    public void setType(String type) {
        this.type = type;
    }

    // 设置请求载荷
    public void setPayload(T payload) {
        this.payload = payload;
    }
}

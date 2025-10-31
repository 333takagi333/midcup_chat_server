package com.chat.model;

public class Request<T> {
    private String type;
    private T payload;

    public String getType() {
        return type;
    }

    public T getPayload() {
        return payload;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }
}



package com.chat.model;

import com.google.gson.annotations.SerializedName;

public class Request<T> {
    private String type;

    @SerializedName("data")          // 注意这里是 data
    private T payload;

    private long timestamp;

    // ----- getters & setters -----
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
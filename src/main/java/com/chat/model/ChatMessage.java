package com.chat.model;

import com.google.gson.annotations.SerializedName;

public class ChatMessage {
    @SerializedName("fromUser")
    private String from;

    @SerializedName("toUser")
    private String to;

    @SerializedName("content")
    private String message;

    // 无参构造器（Gson 必须）
    public ChatMessage() {}

    public ChatMessage(String from, String to, String message) {
        this.from = from;
        this.to = to;
        this.message = message;
    }

    // getters and setters
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
package com.chat.core;

import com.chat.model.ChatMessage;
import com.chat.utils.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ChatService {
    public boolean processMessage(ChatMessage msg) {
        String sql = "INSERT INTO messages (sender, receiver, content) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, msg.getFrom());
            stmt.setString(2, msg.getTo());
            stmt.setString(3, msg.getMessage());

            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[CHAT] SQL error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
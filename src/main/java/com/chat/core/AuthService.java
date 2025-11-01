package com.chat.core;

import com.chat.model.LoginPayload;
import com.chat.utils.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthService {

    // 认证用户：根据用户名查询并校验密码（演示用，未做密码加密）
    public boolean authenticate(LoginPayload loginPayload) {
        String sql = "SELECT password_hash, salt FROM auth WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, loginPayload.getUsername());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    // salt 字段暂未使用，仅保留结构兼容
                    // String salt = rs.getString("salt");
                    return storedHash != null && storedHash.equals(loginPayload.getPassword());
                }
            }
        } catch (SQLException e) {
            System.err.println("[AUTH] SQL error: " + e.getMessage());
        }
        return false;
    }
}

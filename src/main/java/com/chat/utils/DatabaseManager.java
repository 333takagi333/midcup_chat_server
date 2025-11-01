package com.chat.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库管理工具：初始化连接池，提供获取连接与关闭方法。
 */
public class DatabaseManager {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/midcupchat";
    private static final String USER = "root";
    private static final String PASS = "123456";

    private static final HikariDataSource ds;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(USER);
        config.setPassword(PASS);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        ds = new HikariDataSource(config);
    }

    // 获取数据库连接（需调用方使用后关闭）
    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    // 关闭连接池（在JVM退出时调用）
    public static void shutdown() {
        if (ds != null) {
            ds.close();
        }
    }
}

package com.tankgame.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:tankgame.db";

    public DatabaseManager() {
        try {
            // Load the driver (optional in modern JDBC but good for SQLite)
            Class.forName("org.sqlite.JDBC");
            initDatabase();
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found.");
            e.printStackTrace();
        }
    }

    private void initDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS player_scores (" +
                     "name TEXT PRIMARY KEY, " +
                     "score INTEGER DEFAULT 0" +
                     ");";
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Database initialized.");
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    public int getScore(String name) {
        if (name == null || name.trim().isEmpty()) return 0;
        String sql = "SELECT score FROM player_scores WHERE name = ?";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("score");
            }
        } catch (SQLException e) {
            System.err.println("Error getting score: " + e.getMessage());
        }
        return 0;
    }

    public void incrementScore(String name) {
        if (name == null || name.trim().isEmpty()) return;
        // SQLite UPSERT syntax
        String sql = "INSERT INTO player_scores(name, score) VALUES(?, 1) " +
                     "ON CONFLICT(name) DO UPDATE SET score = score + 1";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error incrementing score: " + e.getMessage());
        }
    }
}

package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LegacyDatabase {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(DB_URL)) {
                connection.setAutoCommit(false);
                try {
                    createUsersTable(connection);
                    migrateLegacyPasswords(connection);
                    insertSeedUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
                    insertSeedUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
                    connection.commit();
                } catch (Exception error) {
                    connection.rollback();
                    throw error;
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    private void createUsersTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        }
    }

    private void migrateLegacyPasswords(Connection connection) throws SQLException {
        List<LegacyPassword> legacyPasswords = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, password FROM legacy_users")) {
            while (result.next()) {
                String encodedPassword = result.getString("password");
                if (!passwordHasher.isEncoded(encodedPassword)) {
                    legacyPasswords.add(new LegacyPassword(result.getLong("id"), encodedPassword));
                }
            }
        }

        String updateSql = "UPDATE legacy_users SET password = ? WHERE id = ?";
        try (PreparedStatement update = connection.prepareStatement(updateSql)) {
            for (LegacyPassword legacyPassword : legacyPasswords) {
                update.setString(1, passwordHasher.hash(legacyPassword.password()));
                update.setLong(2, legacyPassword.id());
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private void insertSeedUser(Connection connection, String username, String password, String displayName, String role) throws SQLException {
        if (userExists(connection, username)) {
            return;
        }

        String insertSql = "INSERT INTO legacy_users(username, password, display_name, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            insert.setString(1, username);
            insert.setString(2, passwordHasher.hash(password));
            insert.setString(3, displayName);
            insert.setString(4, role);
            insert.executeUpdate();
        }
    }

    private boolean userExists(Connection connection, String username) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM legacy_users WHERE username = ?")) {
            query.setString(1, username);
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    public UserRecord findByCredentials(String username, String password) {
        String sql = "SELECT id, username, display_name, role, password FROM legacy_users WHERE username = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next() && passwordHasher.matches(password, result.getString("password"))) {
                    return new UserRecord(result.getLong("id"), result.getString("username"), result.getString("display_name"), result.getString("role"));
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
        return null;
    }

    private record LegacyPassword(long id, String password) {}
}

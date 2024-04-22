package net.themegax.eventful.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    final String path = "jdbc:sqlite:plugins/Eventful/database.db";
    public void Initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            Files.createDirectories(Paths.get("plugins/Eventful"));
            Connection connection = DriverManager.getConnection(path);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(
                        "CREATE TABLE IF NOT EXISTS badge (" +
                                "def_id varchar(50) NOT NULL, " +
                                "owned_by varchar(16) NOT NULL, " +
                                "is_equipped int NOT NULL, " +
                                "uses_left int NOT NULL, " +
                                "use_count int NOT NULL, " +
                                "last_usage int" +
                                ");"
                );
                stmt.execute(
                        "CREATE TABLE IF NOT EXISTS item (" +
                                "def_id varchar(50) NOT NULL, " +
                                "owned_by varchar(16) NOT NULL, " +
                                "uses_left int NOT NULL, " +
                                "use_count int NOT NULL, " +
                                "last_usage int" +
                                ");"
                );
                stmt.execute(
                        "CREATE TABLE IF NOT EXISTS player (" +
                                "id varchar(16) PRIMARY KEY NOT NULL," +
                                "main_currency int NOT NULL, " +
                                "secondary_currency int NOT NULL, " +
                                "decor_currency int NOT NULL, " +
                                "last_swap int NOT NULL, " +
                                "is_swap_blocked int NOT NULL, " +
                                "is_cursed int NOT NULL" +
                                ");"
                );
            }
        } catch (ClassNotFoundException | SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void initializePlayer(String playerName) {
        // No need to register the player again
        if (hasPlayer(playerName)) return;

        try {
            Connection connection = DriverManager.getConnection(path);

            var statement = "INSERT INTO player (id, main_currency, secondary_currency, decor_currency, last_swap, is_swap_blocked, is_cursed)" +
                        "VALUES (?, 0, 0, 0, 0, 0, 0)";
            try (PreparedStatement stmt = connection.prepareStatement(statement)) {
                stmt.setString(1, playerName);
                stmt.execute();
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasPlayer(String playerName) {
        try {
            Connection connection = DriverManager.getConnection(path);

            var statement = "SELECT count(1) as has_player FROM player " +
                    "WHERE id=?;";
            try (PreparedStatement stmt = connection.prepareStatement(statement)) {
                stmt.setString(1, playerName);
                stmt.execute();

                var resultSet = stmt.getResultSet();
                resultSet.next();
                return resultSet.getBoolean("has_player");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<PlayerBadge> getPlayerBadges(String playerName) {
        try {
            List<PlayerBadge> playerBadges = new ArrayList<>();
            Connection connection = DriverManager.getConnection(path);

            var statement = "SELECT * FROM badge WHERE owned_by=?;";
            try (PreparedStatement stmt = connection.prepareStatement(statement)) {
                stmt.setString(1, playerName);
                stmt.execute();
                var resultSet = stmt.getResultSet();

                while (resultSet.next()) {
                    var ID = resultSet.getInt("ID");
                    var defID = resultSet.getString("def_id");


                    var item = resultSet.getString("item");
                    var nbt = resultSet.getString("ntb");
                    var emoji = resultSet.getString("emoji");
                    var totalUses = resultSet.getInt("total_uses");
                    var cooldownSeconds = resultSet.getInt("cooldown_seconds");
                    var onUse = resultSet.getString("on_use");

                    var ownedBy = resultSet.getString("owned_by");
                    var isEquipped = resultSet.getBoolean("is_equipped");
                    var usesLeft = resultSet.getInt("uses_left");
                    var useCount = resultSet.getInt("use_count");
                    var lastUsage = resultSet.getDate("last_usage");
                    playerBadges.add(new PlayerBadge(
                            ID, defID, item, nbt, emoji, totalUses, cooldownSeconds, onUse,
                            ownedBy, isEquipped, usesLeft, useCount, lastUsage)
                    );
                }
                return playerBadges;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
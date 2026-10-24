package kec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public final class DBConnection {
    private static final String URL = "jdbc:sqlite:kec_academics.db";

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public static void initialize() throws SQLException, IOException {
        String schema;
        try (InputStream resource = DBConnection.class.getClassLoader().getResourceAsStream("kec_schema.sql")) {
            if (resource != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource, StandardCharsets.UTF_8))) {
                    schema = reader.lines().collect(Collectors.joining(System.lineSeparator()));
                }
            } else {
                schema = Files.readString(Path.of("kec_schema.sql"), StandardCharsets.UTF_8);
            }
        }

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : schema.split(";")) {
                String command = sql.strip();
                if (!command.isEmpty()) {
                    statement.execute(command);
                }
            }
        }
    }
}

package h2ph.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final Path dataDirectory;
    private final Map<String, String> configValues = new HashMap<>();

    public ConfigManager(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public void loadConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            Path configFile = dataDirectory.resolve("config.yml");
            if (!Files.exists(configFile)) {
                createDefaultConfig(configFile);
            }

            readConfig(configFile);

            // Support an alternate backend database config which can override DB settings.
            Path backendConfig = dataDirectory.resolve("backend-database.yml");
            if (!Files.exists(backendConfig)) {
                createDefaultBackendConfig(backendConfig);
            }

            // Read backend config and overlay values (backend takes precedence)
            readConfig(backendConfig);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDefaultConfig(Path configFile) throws IOException {
        String defaultConfig = "# PrismChatVelocity Database Configuration\n" +
                "mysql:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  database: minecraft\n" +
                "  username: root\n" +
                "  password: password\n" +
                "\n" +
                "# Redis Configuration\n" +
                "redis:\n" +
                "  host: localhost\n" +
                "  port: 6379\n" +
                "  username: \"\"\n" +
                "  password: \"\"\n";

        // Add a default MOTD entry (uses legacy section sign codes and an escaped newline)
        defaultConfig += "\n# Server MOTD (use § color codes, use \n for newline)\n" +
            "motd: \"§5§lprismsmp.net§r\\n           §3§lɴᴏʀᴛʜ ᴀᴍᴇʀɪᴄᴀ ᴇᴀѕᴛ ʀᴇʟᴇᴀѕᴇᴅ\"\n";

        try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardOpenOption.CREATE)) {
            writer.write(defaultConfig);
        }
    }

    private void createDefaultBackendConfig(Path backendConfig) throws IOException {
        String backendDefault = "# Optional backend database config which overrides mysql values\n" +
                "# Use this file to point the proxy at a separate backend database if needed.\n" +
                "mysql:\n" +
                "  host: localhost\n" +
                "  port: 3306\n" +
                "  database: minecraft\n" +
                "  username: root\n" +
                "  password: password\n";

        try (BufferedWriter writer = Files.newBufferedWriter(backendConfig, StandardOpenOption.CREATE)) {
            writer.write(backendDefault);
        }
    }

    private void readConfig(Path configFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            String line;
            String currentSection = "";

            while ((line = reader.readLine()) != null) {
                String originalLine = line;
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#"))
                    continue;

                // Calculate indentation
                int indentation = 0;
                while (indentation < originalLine.length() && originalLine.charAt(indentation) == ' ') {
                    indentation++;
                }

                if (line.endsWith(":")) {
                    // It's a section
                    String sectionName = line.substring(0, line.length() - 1);
                    if (indentation == 0) {
                        currentSection = sectionName;
                    } else {
                        // Support nested sections if needed, but for now just 1 level deep is fine
                        // or we can append. logic for "redis:" inside something else is tricky without
                        // a stack.
                        // Assuming flat structure:
                        // mysql:
                        // host: ...
                        // redis:
                        // host: ...
                        // If we see indentation 0, we reset section.
                    }
                    continue;
                }

                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();

                    // Remove surrounding quotes if present
                    if (value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }

                    if (indentation > 0 && !currentSection.isEmpty()) {
                        configValues.put(currentSection + "." + key, value);
                    } else {
                        configValues.put(key, value);
                    }
                }
            }
        }
    }

    public String getString(String key, String defaultValue) {
        return configValues.getOrDefault(key, defaultValue);
    }

    public String getMotd(String defaultValue) {
        String raw = configValues.get("motd");
        if (raw == null) return defaultValue;
        return unescapeJavaString(raw);
    }

    // Very small unescape routine to handle common Java-style escapes (\n, \t, \\ and unicode escapes)
    private String unescapeJavaString(String input) {
        StringBuilder sb = new StringBuilder();
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char ch = input.charAt(i);
            if (ch == '\\' && i + 1 < len) {
                char next = input.charAt(++i);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    case '\'': sb.append('\''); break;
                    case 'u': {
                        // parse next 4 hex digits
                        if (i + 4 < len) {
                            String hex = input.substring(i + 1, i + 5);
                            try {
                                int code = Integer.parseInt(hex, 16);
                                sb.append((char) code);
                                i += 4; // consumed 4 hex digits
                            } catch (NumberFormatException e) {
                                // malformed, append literal
                                sb.append(next);
                            }
                        } else {
                            sb.append(next);
                        }
                        break;
                    }
                    default:
                        sb.append(next);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(configValues.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // Helper getters that prefer backend overrides when present.
    private String getWithBackendFallback(String key, String defaultValue) {
        String backendKey = "backend." + key;
        if (configValues.containsKey(backendKey)) {
            return configValues.get(backendKey);
        }
        return configValues.getOrDefault(key, defaultValue);
    }

    public String getDatabaseHost(String defaultValue) {
        return getWithBackendFallback("mysql.host", defaultValue);
    }

    public int getDatabasePort(int defaultValue) {
        String val = getWithBackendFallback("mysql.port", String.valueOf(defaultValue));
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String getDatabaseName(String defaultValue) {
        return getWithBackendFallback("mysql.database", defaultValue);
    }

    public String getDatabaseUsername(String defaultValue) {
        return getWithBackendFallback("mysql.username", defaultValue);
    }

    public String getDatabasePassword(String defaultValue) {
        return getWithBackendFallback("mysql.password", defaultValue);
    }
}

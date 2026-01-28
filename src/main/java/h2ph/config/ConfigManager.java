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
                "  password: password\n";

        try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardOpenOption.CREATE)) {
            writer.write(defaultConfig);
        }
    }

    private void readConfig(Path configFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;

                // Simple parser for "key: value"
                // This doesn't support nested structures well, but works for our simple use
                // case
                // if we expect flat keys or just parse basic lines.
                // Given the default config above, we can just look for specific keys.

                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    configValues.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
    }

    public String getString(String key, String defaultValue) {
        return configValues.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(configValues.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

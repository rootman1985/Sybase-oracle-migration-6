package fr.migration;

import java.io.InputStream;
import java.util.Properties;

public record Config(
        String sybaseUrl,
        String sybaseUser,
        String sybasePassword,
        String oracleUrl,
        String oracleUser,
        String oraclePassword,
        int batchSize,
        int oracleJdbcBatchSize
) {
    public static Config load() throws Exception {
        Properties p = new Properties();

        try (InputStream in = Config.class.getResourceAsStream("/application.properties")) {
            if (in == null) {
                throw new IllegalStateException("application.properties not found");
            }
            p.load(in);
        }

        return new Config(
                required(p, "sybase.url"),
                required(p, "sybase.user"),
                required(p, "sybase.password"),
                required(p, "oracle.url"),
                required(p, "oracle.user"),
                required(p, "oracle.password"),
                Integer.parseInt(p.getProperty("migration.batchSize", "10000")),
                Integer.parseInt(p.getProperty("migration.oracleJdbcBatchSize", "1000"))
        );
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        return value.trim();
    }
}

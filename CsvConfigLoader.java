package fr.migration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CsvConfigLoader {

    private static final int FIELD_COUNT = 9;

    private CsvConfigLoader() {
    }

    public static List<TableConfig> load() throws Exception {
        InputStream in = CsvConfigLoader.class.getResourceAsStream("/column-mappings.csv");

        if (in == null) {
            throw new IllegalStateException("column-mappings.csv not found");
        }

        Map<String, List<ColumnMapping>> groups = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line;
            int lineNumber = 0;

            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] f = line.split(",", -1);

                if (f.length != FIELD_COUNT) {
                    throw new IllegalArgumentException(
                            "Invalid CSV line " + lineNumber
                                    + ": expected " + FIELD_COUNT
                                    + " fields, got " + f.length
                    );
                }

                ColumnMapping m = new ColumnMapping(
                        clean(f[0]),
                        clean(f[1]),
                        clean(f[2]),
                        clean(f[3]),
                        clean(f[4]),
                        clean(f[5]),
                        clean(f[6]),
                        clean(f[7]),
                        clean(f[8])
                );

                validateMapping(m, lineNumber);

                groups.computeIfAbsent(m.sourceKey(), k -> new ArrayList<>())
                        .add(m);
            }
        }

        List<TableConfig> result = new ArrayList<>();

        for (List<ColumnMapping> mappings : groups.values()) {
            ColumnMapping first = mappings.get(0);

            for (ColumnMapping m : mappings) {
                if (!m.targetSchema().equalsIgnoreCase(first.targetSchema())
                        || !m.targetTable().equalsIgnoreCase(first.targetTable())) {
                    throw new IllegalArgumentException(
                            "A source table must map to one target table: "
                                    + first.sourceKey()
                    );
                }
            }

            result.add(new TableConfig(
                    first.sourceCatalog(),
                    first.sourceSchema(),
                    first.sourceTable(),
                    first.targetSchema(),
                    first.targetTable(),
                    List.copyOf(mappings)
            ));
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "column-mappings.csv contains no migration mappings"
            );
        }

        return result;
    }

    private static void validateMapping(ColumnMapping m, int line) {
        if (m.sourceCatalog().isBlank()
                || m.sourceSchema().isBlank()
                || m.sourceTable().isBlank()
                || m.sourceColumn().isBlank()
                || m.sourceType().isBlank()
                || m.targetSchema().isBlank()
                || m.targetTable().isBlank()
                || m.targetColumn().isBlank()
                || m.targetType().isBlank()) {

            throw new IllegalArgumentException(
                    "Empty field at CSV line " + line
            );
        }

        validateIdentifier(m.sourceSchema(), "sourceSchema", line);
        validateIdentifier(m.sourceTable(), "sourceTable", line);
        validateIdentifier(m.sourceColumn(), "sourceColumn", line);
        validateIdentifier(m.targetSchema(), "targetSchema", line);
        validateIdentifier(m.targetTable(), "targetTable", line);
        validateIdentifier(m.targetColumn(), "targetColumn", line);
    }

    private static void validateIdentifier(
            String value,
            String field,
            int line) {

        if (!value.matches("[A-Za-z_][A-Za-z0-9_$#]*")) {
            throw new IllegalArgumentException(
                    "Invalid SQL identifier in " + field
                            + " at CSV line " + line + ": " + value
            );
        }
    }

    private static String clean(String s) {
        String v = s.trim();

        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            v = v.substring(1, v.length() - 1);
        }

        return v;
    }
}

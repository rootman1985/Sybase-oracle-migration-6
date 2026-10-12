package fr.migration;

import java.util.List;

public final class MigrationApplication {

    private MigrationApplication() {
    }

    public static void main(String[] args) throws Exception {

        String mode = args.length > 0
                ? args[0].trim().toUpperCase()
                : "CHECK";

        Config config = Config.load();
        List<TableConfig> tables = CsvConfigLoader.load();

        MigrationService service = new MigrationService(config);

        if ("CHECK".equals(mode)) {
            service.check(tables);
            return;
        }

        if ("MIGRATE".equals(mode)) {
            service.check(tables);
            service.migrate(tables);
            return;
        }

        throw new IllegalArgumentException(
                "Unknown mode: " + mode
                        + ". Use CHECK or MIGRATE."
        );
    }
}

package fr.migration;

import java.util.List;

public record TableConfig(
        String sourceCatalog,
        String sourceSchema,
        String sourceTable,
        String targetSchema,
        String targetTable,
        List<ColumnMapping> columns
) {
    public ColumnMapping keyMapping() {
        return columns.get(0);
    }

    public String sourceQualifiedName() {
        return sourceCatalog + "." + sourceSchema + "." + sourceTable;
    }

    public String targetQualifiedName() {
        return targetSchema + "." + targetTable;
    }
}

package fr.migration;

public record ColumnMapping(
        String sourceCatalog,
        String sourceSchema,
        String sourceTable,
        String sourceColumn,
        String sourceType,
        String targetSchema,
        String targetTable,
        String targetColumn,
        String targetType
) {
    public String sourceKey() {
        return sourceCatalog + "." + sourceSchema + "." + sourceTable;
    }

    public String targetKey() {
        return targetSchema + "." + targetTable;
    }
}

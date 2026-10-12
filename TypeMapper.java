package fr.migration;

import java.util.Locale;

public final class TypeMapper {

    private TypeMapper() {
    }

    public static boolean compatible(ColumnMapping m) {
        String s = normalize(m.sourceType());
        String t = normalize(m.targetType());

        if (s.equals("timestamp")) {
            return t.equals("raw");
        }

        if (isInteger(s)) {
            return isNumericTarget(t);
        }

        if (s.equals("numeric") || s.equals("decimal")) {
            return isNumericTarget(t);
        }

        if (s.equals("varchar") || s.equals("varchar2")) {
            return t.equals("varchar2") || t.equals("char") || t.equals("clob");
        }

        if (s.equals("char")) {
            return t.equals("char") || t.equals("varchar2");
        }

        if (s.equals("text") || s.equals("unitext")) {
            return t.equals("clob") || t.equals("varchar2");
        }

        if (s.equals("image") || s.equals("binary") || s.equals("varbinary")) {
            return t.equals("blob") || t.equals("raw");
        }

        if (s.equals("datetime") || s.equals("smalldatetime")) {
            return t.equals("timestamp") || t.equals("date");
        }

        if (s.equals("date")) {
            return t.equals("date") || t.equals("timestamp");
        }

        if (s.equals("time")) {
            return t.equals("timestamp");
        }

        if (s.equals("float")) {
            return t.equals("binary_double")
                    || t.equals("binary_float")
                    || t.equals("number");
        }

        if (s.equals("bit")) {
            return t.equals("number")
                    || t.equals("char")
                    || t.equals("varchar2");
        }

        return false;
    }

    private static boolean isInteger(String s) {
        return s.equals("int")
                || s.equals("integer")
                || s.equals("smallint")
                || s.equals("tinyint")
                || s.equals("usmallint")
                || s.equals("bigint");
    }

    private static boolean isNumericTarget(String t) {
        return t.equals("number")
                || t.equals("numeric")
                || t.equals("decimal")
                || t.equals("binary_double")
                || t.equals("binary_float");
    }

    public static String normalize(String type) {
        return type.trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "");
    }
}

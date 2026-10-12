package fr.migration;

import java.io.Reader;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Locale;

public final class ValueBinder {

    private ValueBinder() {
    }

    public static void bind(
            ResultSet rs,
            PreparedStatement ps,
            int targetIndex,
            ColumnMapping m) throws SQLException {

        String source = TypeMapper.normalize(m.sourceType());
        String target = TypeMapper.normalize(m.targetType());

        // Sybase ASE timestamp is a binary rowversion, NOT a date/time value.
        if (source.equals("timestamp")) {
            byte[] bytes = rs.getBytes(m.sourceColumn());
            if (bytes == null) {
                ps.setNull(targetIndex, targetJdbcType(target));
                return;
            }
            if (bytes.length != 8) {
                throw new SQLException("Sybase ASE timestamp must contain exactly 8 bytes. Column="
                        + m.sourceColumn() + ", length=" + bytes.length);
            }
            if (!target.equals("raw")) {
                throw new SQLException("Sybase timestamp requires Oracle RAW target. "
                        + m.targetColumn() + " is " + m.targetType());
            }
            ps.setBytes(targetIndex, bytes);
            return;
        }

        // Handle TEXT/UNITEXT explicitly. getObject() can materialize driver-specific LOB
        // objects, and deferred streams are unsafe when executeBatch() happens later.
        // A StringReader remains valid until the batch is executed; this also preserves NULL.
        if (source.equals("text") || source.equals("unitext") || source.equals("ntext")) {
            String value = rs.getString(m.sourceColumn());
            if (value == null) {
                ps.setNull(targetIndex, targetJdbcType(target));
                return;
            }
            if (target.equals("clob") || target.equals("nclob")) {
                ps.setCharacterStream(targetIndex, new java.io.StringReader(value), value.length());
                return;
            }
            if (target.equals("varchar2") || target.equals("varchar") || target.equals("char")) {
                ps.setString(targetIndex, value);
                return;
            }
            throw new SQLException("Unsupported target type for Sybase " + source + ": " + m.targetType());
        }

        if (source.equals("image") || source.equals("binary") || source.equals("varbinary")) {
            byte[] value = rs.getBytes(m.sourceColumn());
            if (value == null) {
                ps.setNull(targetIndex, targetJdbcType(target));
            } else if (target.equals("blob")) {
                ps.setBinaryStream(targetIndex, new java.io.ByteArrayInputStream(value), value.length);
            } else if (target.equals("raw")) {
                ps.setBytes(targetIndex, value);
            } else {
                throw new SQLException("Unsupported target type for binary source: " + m.targetType());
            }
            return;
        }

        Object raw = rs.getObject(m.sourceColumn());
        if (raw == null) {
            ps.setNull(targetIndex, targetJdbcType(target));
            return;
        }

        if (isInteger(source) || source.equals("numeric") || source.equals("decimal")) {
            if (target.equals("number") || target.equals("numeric") || target.equals("decimal")) {
                ps.setBigDecimal(targetIndex, rs.getBigDecimal(m.sourceColumn()));
                return;
            }
        }

        if (source.equals("float") || source.equals("real")) {
            double value = rs.getDouble(m.sourceColumn());
            if (target.equals("binary_double") || target.equals("binary_float") || target.equals("number")) {
                ps.setDouble(targetIndex, value);
                return;
            }
        }

        if (source.equals("bit")) {
            boolean value = rs.getBoolean(m.sourceColumn());
            if (target.equals("number") || target.equals("numeric") || target.equals("decimal")) {
                ps.setInt(targetIndex, value ? 1 : 0);
                return;
            }
            if (target.equals("char") || target.equals("varchar2")) {
                ps.setString(targetIndex, value ? "1" : "0");
                return;
            }
        }

        if (source.equals("datetime") || source.equals("smalldatetime")) {
            Timestamp value = rs.getTimestamp(m.sourceColumn());
            if (target.equals("timestamp")) {
                ps.setTimestamp(targetIndex, value);
                return;
            }
            if (target.equals("date")) {
                ps.setDate(targetIndex, new java.sql.Date(value.getTime()));
                return;
            }
        }

        if (source.equals("date")) {
            java.sql.Date value = rs.getDate(m.sourceColumn());
            if (target.equals("date")) {
                ps.setDate(targetIndex, value);
                return;
            }
            if (target.equals("timestamp")) {
                ps.setTimestamp(targetIndex, new Timestamp(value.getTime()));
                return;
            }
        }

        if (source.equals("time")) {
            Time value = rs.getTime(m.sourceColumn());
            if (target.equals("timestamp")) {
                ps.setTimestamp(targetIndex, new Timestamp(value.getTime()));
                return;
            }
        }

        if (source.equals("varchar") || source.equals("char")) {
            String value = rs.getString(m.sourceColumn());
            if (target.equals("varchar2") || target.equals("char") || target.equals("clob")) {
                if (target.equals("clob")) {
                    ps.setCharacterStream(targetIndex, new java.io.StringReader(value), value.length());
                } else {
                    ps.setString(targetIndex, value);
                }
                return;
            }
        }

        ps.setObject(targetIndex, raw);
    }

    private static int targetJdbcType(String target) {
        return switch (target.toLowerCase(Locale.ROOT)) {
            case "number", "numeric", "decimal" -> Types.NUMERIC;
            case "varchar2", "varchar", "char" -> Types.VARCHAR;
            case "date" -> Types.DATE;
            case "timestamp" -> Types.TIMESTAMP;
            case "raw" -> Types.BINARY;
            case "blob" -> Types.BLOB;
            case "clob", "nclob" -> Types.CLOB;
            case "binary_double" -> Types.DOUBLE;
            case "binary_float" -> Types.FLOAT;
            default -> Types.OTHER;
        };
    }

    private static boolean isInteger(String s) {
        return s.equals("int")
                || s.equals("integer")
                || s.equals("smallint")
                || s.equals("tinyint")
                || s.equals("usmallint")
                || s.equals("bigint");
    }
}

package fr.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class Db {

    private Db() {
    }

    public static Connection openSybase(Config cfg) throws SQLException {
        try {
            Class.forName("com.sybase.jdbc4.jdbc.SybDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "Sybase jConnect driver not found. Check lib/jconn4.jar",
                    e
            );
        }

        Properties p = new Properties();
        p.setProperty("user", cfg.sybaseUser());
        p.setProperty("password", cfg.sybasePassword());

        // Keep SQLWarnings chained to SQLException for diagnostics.
        p.setProperty("DELETE_WARNINGS_FROM_EXCEPTION_CHAIN", "false");

        try {
            return DriverManager.getConnection(cfg.sybaseUrl(), p);
        } catch (SQLException e) {
            System.err.println("===== SYBASE CONNECTION ERROR =====");
            printSQLException(e);
            throw e;
        }
    }

    public static Connection openOracle(Config cfg) throws SQLException {
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "Oracle JDBC driver not found. Check the ojdbc11 Maven dependency.",
                    e
            );
        }

        try {
            return DriverManager.getConnection(
                    cfg.oracleUrl(),
                    cfg.oracleUser(),
                    cfg.oraclePassword()
            );
        } catch (SQLException e) {
            System.err.println("===== ORACLE CONNECTION ERROR =====");
            printSQLException(e);
            throw e;
        }
    }

    public static void printSQLException(SQLException e) {
        for (SQLException x = e; x != null; x = x.getNextException()) {
            System.err.println("SQLState : " + x.getSQLState());
            System.err.println("ErrorCode: " + x.getErrorCode());
            System.err.println("Message  : " + x.getMessage());
        }
    }
}

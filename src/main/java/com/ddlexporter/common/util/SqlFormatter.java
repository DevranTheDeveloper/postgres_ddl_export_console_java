package com.ddlexporter.common.util;

import java.util.regex.Pattern;

public class SqlFormatter {

    private static final String[] KEYWORDS = {
            "CREATE TABLE", "CREATE OR REPLACE VIEW", "CREATE OR REPLACE FUNCTION", "CREATE INDEX",
            "CREATE SEQUENCE", "CREATE TYPE", "CREATE SCHEMA", "ALTER TABLE", "DROP TABLE",
            "DROP VIEW", "DROP FUNCTION", "DROP INDEX", "DROP SEQUENCE", "PRIMARY KEY", "FOREIGN KEY",
            "REFERENCES", "CONSTRAINT", "NOT NULL", "NULL", "DEFAULT", "CHECK", "UNIQUE",
            "SELECT", "INSERT INTO", "UPDATE", "DELETE FROM", "FROM", "WHERE", "JOIN",
            "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "FULL JOIN", "ON", "GROUP BY", "ORDER BY",
            "HAVING", "LIMIT", "OFFSET", "UNION ALL", "UNION", "BEGIN", "COMMIT", "ROLLBACK",
            "INTEGER", "BIGINT", "SMALLINT", "VARCHAR", "CHARACTER VARYING", "TEXT", "BOOLEAN",
            "NUMERIC", "DECIMAL", "TIMESTAMP", "TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE", "DATE",
            "TIME", "JSONB", "JSON", "UUID", "BYTEA", "SERIAL", "BIGSERIAL", "RETURNS", "LANGUAGE",
            "AS", "BEGIN", "END", "ADD COLUMN", "DROP COLUMN", "ALTER COLUMN", "TYPE", "IF NOT EXISTS", "IF EXISTS"
    };

    public static String formatSql(String sql) {
        if (sql == null || sql.isBlank()) return sql;

        String result = sql;

        // 1. Capitalize Keywords safely
        for (String kw : KEYWORDS) {
            String regex = "(?i)\\b" + kw.replace(" ", "\\s+") + "\\b";
            result = Pattern.compile(regex).matcher(result).replaceAll(kw);
        }

        // 2. Format indentation line by line
        String[] lines = result.split("\n");
        StringBuilder sb = new StringBuilder();
        int indentLevel = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append("\n");
                continue;
            }

            if (trimmed.startsWith(");") || trimmed.startsWith(")")) {
                indentLevel = Math.max(0, indentLevel - 1);
            }

            for (int i = 0; i < indentLevel; i++) {
                sb.append("    ");
            }
            sb.append(trimmed).append("\n");

            if (trimmed.endsWith("(") || (trimmed.toUpperCase().startsWith("CREATE TABLE") && trimmed.contains("("))) {
                indentLevel++;
            }
        }

        return sb.toString().trim();
    }
}

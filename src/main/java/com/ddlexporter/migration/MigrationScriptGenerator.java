package com.ddlexporter.migration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MigrationScriptGenerator {

    public static class TableSchema {
        public String tableName = "";
        public final Map<String, String> columns = new LinkedHashMap<>(); // colName -> definition
        public final List<String> constraints = new ArrayList<>();
        public final List<String> indexes = new ArrayList<>();
    }

    public static String generateMigrationScript(String fileName, String oldSql, String newSql) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = LocalDateTime.now().format(dtf);

        TableSchema oldSchema = parseSchema(oldSql);
        TableSchema newSchema = parseSchema(newSql);

        StringBuilder sb = new StringBuilder();
        sb.append("-- ====================================================================\n");
        sb.append("-- PostgreSQL DDL Studio - Otomatik Üretilen Migration (ALTER) Scripti\n");
        sb.append("-- Oluşturulma Tarihi : ").append(timestamp).append("\n");
        sb.append("-- Hedef Dosya        : ").append(fileName != null ? fileName : "schema.sql").append("\n");
        sb.append("-- ====================================================================\n\n");
        sb.append("BEGIN;\n\n");

        List<String> alterStatements = new ArrayList<>();

        String targetTable = !newSchema.tableName.isEmpty() ? newSchema.tableName : oldSchema.tableName;
        if (targetTable.isEmpty()) {
            targetTable = fileName != null && fileName.endsWith(".sql")
                    ? fileName.substring(0, fileName.length() - 4)
                    : "hedef_tablo";
        }

        // 1. Check for Added Columns (present in newSchema, missing in oldSchema)
        for (Map.Entry<String, String> entry : newSchema.columns.entrySet()) {
            String col = entry.getKey();
            String def = entry.getValue();

            if (!oldSchema.columns.containsKey(col)) {
                alterStatements.add(String.format("ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s %s;", targetTable, col, def));
            } else {
                // Check if definition changed
                String oldDef = oldSchema.columns.get(col);
                if (!def.equalsIgnoreCase(oldDef)) {
                    alterStatements.add(String.format("-- Kolon tanımı güncellendi (%s -> %s)\nALTER TABLE %s ALTER COLUMN %s TYPE %s;",
                            oldDef, def, targetTable, col, extractTypeOnly(def)));
                }
            }
        }

        // 2. Check for Dropped Columns (present in oldSchema, missing in newSchema)
        for (String oldCol : oldSchema.columns.keySet()) {
            if (!newSchema.columns.containsKey(oldCol)) {
                alterStatements.add(String.format("-- UYARI: Silinen Kolon\nALTER TABLE %s DROP COLUMN IF EXISTS %s;", targetTable, oldCol));
            }
        }

        // 3. New Indexes / Constraints
        for (String idx : newSchema.indexes) {
            if (!oldSchema.indexes.contains(idx)) {
                alterStatements.add(idx + ";");
            }
        }

        for (String c : newSchema.constraints) {
            if (!oldSchema.constraints.contains(c)) {
                alterStatements.add(String.format("ALTER TABLE %s ADD %s;", targetTable, c));
            }
        }

        if (alterStatements.isEmpty()) {
            sb.append("-- İki şema arasında herhangi bir yapısal fark (ALTER gereksinimi) bulunamadı.\n");
            sb.append("-- Şemalar birebir eşleşmektedir.\n\n");
        } else {
            sb.append("-- 1. Yapısal Şema Değişiklikleri (ALTER Operations)\n");
            for (String stmt : alterStatements) {
                sb.append(stmt).append("\n");
            }
            sb.append("\n");
        }

        sb.append("COMMIT;\n");
        sb.append("-- Migration işlemi başarıyla tamamlandı.\n");

        return sb.toString();
    }

    private static TableSchema parseSchema(String sql) {
        TableSchema schema = new TableSchema();
        if (sql == null || sql.isBlank()) return schema;

        String[] lines = sql.split("\n");
        boolean inCreateTable = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;

            if (trimmed.toUpperCase().startsWith("CREATE TABLE")) {
                inCreateTable = true;
                int start = trimmed.toUpperCase().indexOf("CREATE TABLE") + 12;
                int end = trimmed.indexOf("(");
                if (end > start) {
                    schema.tableName = trimmed.substring(start, end).replace("IF NOT EXISTS", "").trim();
                }
                continue;
            }

            if (inCreateTable) {
                if (trimmed.startsWith(");") || trimmed.equals(")")) {
                    inCreateTable = false;
                    continue;
                }

                String cleanLine = trimmed;
                if (cleanLine.endsWith(",")) {
                    cleanLine = cleanLine.substring(0, cleanLine.length() - 1).trim();
                }

                if (cleanLine.toUpperCase().startsWith("CONSTRAINT") || cleanLine.toUpperCase().startsWith("PRIMARY KEY") || cleanLine.toUpperCase().startsWith("FOREIGN KEY")) {
                    schema.constraints.add(cleanLine);
                } else {
                    int firstSpace = cleanLine.indexOf(" ");
                    if (firstSpace > 0) {
                        String colName = cleanLine.substring(0, firstSpace).trim().replace("\"", "");
                        String colDef = cleanLine.substring(firstSpace + 1).trim();
                        schema.columns.put(colName.toLowerCase(), colDef);
                    }
                }
            } else if (trimmed.toUpperCase().startsWith("CREATE INDEX")) {
                String cleanIdx = trimmed;
                if (cleanIdx.endsWith(";")) cleanIdx = cleanIdx.substring(0, cleanIdx.length() - 1);
                schema.indexes.add(cleanIdx);
            }
        }

        return schema;
    }

    private static String extractTypeOnly(String def) {
        if (def == null) return "text";
        String[] parts = def.split("\\s+");
        return parts.length > 0 ? parts[0] : def;
    }
}

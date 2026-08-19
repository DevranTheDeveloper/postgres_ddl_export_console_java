package com.ddlexporter.er;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ErDiagramEngine {

    public static class ErColumn {
        public String name;
        public String type;
        public boolean isPk;
        public boolean isFk;
        public String fkTargetTable;
        public String fkTargetColumn;

        public ErColumn(String name, String type, boolean isPk, boolean isFk) {
            this.name = name;
            this.type = type;
            this.isPk = isPk;
            this.isFk = isFk;
        }
    }

    public static class ErTable {
        public String name;
        public String schema;
        public List<ErColumn> columns = new ArrayList<>();
        public int x = 0;
        public int y = 0;
        public int width = 210;
        public int height = 150;

        public ErTable(String name, String schema) {
            this.name = name;
            this.schema = schema;
        }
    }

    public static class ErRelation {
        public String sourceTable;
        public String sourceColumn;
        public String targetTable;
        public String targetColumn;

        public ErRelation(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
            this.sourceTable = sourceTable;
            this.sourceColumn = sourceColumn;
            this.targetTable = targetTable;
            this.targetColumn = targetColumn;
        }
    }

    public static class ErModel {
        public final Map<String, ErTable> tables = new LinkedHashMap<>();
        public final List<ErRelation> relations = new ArrayList<>();
    }

    public static ErModel buildModelFromDirectory(File exportDir) {
        return buildModelFromDirectory(exportDir, null);
    }

    public static ErModel buildModelFromDirectory(File exportDir, String targetDbName) {
        ErModel model = new ErModel();
        if (exportDir == null || !exportDir.exists()) {
            return model;
        }

        try {
            List<File> sqlFiles = Files.walk(exportDir.toPath())
                    .filter(p -> {
                        String s = p.toString().toLowerCase();
                        boolean isSql = s.endsWith(".sql");
                        boolean isTable = s.contains("/table/") || s.contains("\\table\\") || s.contains("table");
                        if (targetDbName != null && !targetDbName.isBlank()) {
                            return isSql && isTable && s.contains(targetDbName.toLowerCase());
                        }
                        return isSql && isTable;
                    })
                    .map(java.nio.file.Path::toFile)
                    .toList();

            for (File file : sqlFiles) {
                String content = Files.readString(file.toPath());
                parseSqlToModel(content, model);
            }
        } catch (Exception ignored) {}

        if (!model.tables.isEmpty()) {
            arrangeLayout(model);
        }

        return model;
    }

    /**
     * Builds ER Model directly by querying live PostgreSQL schema metadata.
     */
    public static ErModel buildModelFromDatabase(Connection conn, String schemaName) {
        ErModel model = new ErModel();
        if (conn == null) return model;
        if (schemaName == null || schemaName.isBlank()) schemaName = "public";

        try {
            // 1. Fetch tables
            String tableSql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name";
            try (PreparedStatement ps = conn.prepareStatement(tableSql)) {
                ps.setString(1, schemaName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tName = rs.getString("table_name");
                        model.tables.put(tName, new ErTable(tName, schemaName));
                    }
                }
            }

            if (model.tables.isEmpty()) return model;

            // 2. Fetch columns
            String colSql = "SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_schema = ? ORDER BY table_name, ordinal_position";
            try (PreparedStatement ps = conn.prepareStatement(colSql)) {
                ps.setString(1, schemaName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tName = rs.getString("table_name");
                        String colName = rs.getString("column_name");
                        String dataType = rs.getString("data_type");
                        ErTable tbl = model.tables.get(tName);
                        if (tbl != null) {
                            tbl.columns.add(new ErColumn(colName, dataType, false, false));
                        }
                    }
                }
            }

            // 3. Fetch Primary Keys
            String pkSql = "SELECT kcu.table_name, kcu.column_name " +
                    "FROM information_schema.table_constraints tc " +
                    "JOIN information_schema.key_column_usage kcu " +
                    "  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema " +
                    "WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_schema = ?";
            try (PreparedStatement ps = conn.prepareStatement(pkSql)) {
                ps.setString(1, schemaName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tName = rs.getString("table_name");
                        String colName = rs.getString("column_name");
                        ErTable tbl = model.tables.get(tName);
                        if (tbl != null) {
                            for (ErColumn col : tbl.columns) {
                                if (col.name.equalsIgnoreCase(colName)) {
                                    col.isPk = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            // 4. Fetch Foreign Keys
            String fkSql = "SELECT " +
                    "    kcu.table_name AS src_table, " +
                    "    kcu.column_name AS src_col, " +
                    "    ccu.table_name AS tgt_table, " +
                    "    ccu.column_name AS tgt_col " +
                    "FROM information_schema.table_constraints AS tc " +
                    "JOIN information_schema.key_column_usage AS kcu " +
                    "  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema " +
                    "JOIN information_schema.constraint_column_usage AS ccu " +
                    "  ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema " +
                    "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = ?";
            try (PreparedStatement ps = conn.prepareStatement(fkSql)) {
                ps.setString(1, schemaName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String srcTable = rs.getString("src_table");
                        String srcCol = rs.getString("src_col");
                        String tgtTable = rs.getString("tgt_table");
                        String tgtCol = rs.getString("tgt_col");

                        model.relations.add(new ErRelation(srcTable, srcCol, tgtTable, tgtCol));

                        ErTable tbl = model.tables.get(srcTable);
                        if (tbl != null) {
                            for (ErColumn col : tbl.columns) {
                                if (col.name.equalsIgnoreCase(srcCol)) {
                                    col.isFk = true;
                                    col.fkTargetTable = tgtTable;
                                    col.fkTargetColumn = tgtCol;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            arrangeLayout(model);
        } catch (Exception ignored) {}

        return model;
    }

    public static void parseSqlToModel(String sql, ErModel model) {
        if (sql == null || sql.isBlank()) return;

        Pattern createTablePattern = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:([a-zA-Z0-9_]+)\\.)?([a-zA-Z0-9_]+)\\s*\\((.+?)\\);", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = createTablePattern.matcher(sql);

        while (matcher.find()) {
            String schema = matcher.group(1) != null ? matcher.group(1) : "public";
            String tableName = matcher.group(2);
            String body = matcher.group(3);

            ErTable table = new ErTable(tableName, schema);
            String[] lines = body.split("\n");

            Set<String> pkCols = new HashSet<>();
            List<ErRelation> foundRelations = new ArrayList<>();

            // 1. First pass for table-level constraints
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.toUpperCase().contains("PRIMARY KEY")) {
                    Pattern pkPattern = Pattern.compile("PRIMARY\\s+KEY\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
                    Matcher pkMatcher = pkPattern.matcher(trimmed);
                    if (pkMatcher.find()) {
                        String[] cols = pkMatcher.group(1).split(",");
                        for (String c : cols) pkCols.add(c.trim().replace("\"", "").toLowerCase());
                    }
                }
                if (trimmed.toUpperCase().contains("FOREIGN KEY") && trimmed.toUpperCase().contains("REFERENCES")) {
                    Pattern fkPattern = Pattern.compile("FOREIGN\\s+KEY\\s*\\(([^)]+)\\)\\s*REFERENCES\\s*(?:[a-zA-Z0-9_]+\\.)?([a-zA-Z0-9_]+)\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
                    Matcher fkMatcher = fkPattern.matcher(trimmed);
                    if (fkMatcher.find()) {
                        String srcCol = fkMatcher.group(1).trim().replace("\"", "");
                        String tgtTable = fkMatcher.group(2).trim().replace("\"", "");
                        String tgtCol = fkMatcher.group(3).trim().replace("\"", "");
                        foundRelations.add(new ErRelation(tableName, srcCol, tgtTable, tgtCol));
                    }
                }
            }

            // 2. Second pass for column definitions
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("CONSTRAINT") || trimmed.startsWith("PRIMARY KEY") || trimmed.startsWith("FOREIGN KEY") || trimmed.startsWith("CHECK") || trimmed.startsWith("UNIQUE")) {
                    continue;
                }
                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith(")")) continue;

                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 2) {
                    String colName = parts[0].replace("\"", "").replace(",", "");
                    String colType = parts[1].replace(",", "").toUpperCase();

                    boolean isPk = pkCols.contains(colName.toLowerCase()) || trimmed.toUpperCase().contains("PRIMARY KEY");
                    boolean isFk = false;
                    String fkTarget = null;
                    String fkCol = null;

                    if (trimmed.toUpperCase().contains("REFERENCES")) {
                        Pattern inlineFk = Pattern.compile("REFERENCES\\s+(?:[a-zA-Z0-9_]+\\.)?([a-zA-Z0-9_]+)\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
                        Matcher fkM = inlineFk.matcher(trimmed);
                        if (fkM.find()) {
                            isFk = true;
                            fkTarget = fkM.group(1);
                            fkCol = fkM.group(2);
                            foundRelations.add(new ErRelation(tableName, colName, fkTarget, fkCol));
                        }
                    }

                    ErColumn column = new ErColumn(colName, colType, isPk, isFk);
                    column.fkTargetTable = fkTarget;
                    column.fkTargetColumn = fkCol;
                    table.columns.add(column);
                }
            }

            for (ErRelation rel : foundRelations) {
                model.relations.add(rel);
                for (ErColumn col : table.columns) {
                    if (col.name.equalsIgnoreCase(rel.sourceColumn)) {
                        col.isFk = true;
                        col.fkTargetTable = rel.targetTable;
                        col.fkTargetColumn = rel.targetColumn;
                    }
                }
            }

            model.tables.put(tableName, table);
        }
    }

    public static void arrangeLayout(ErModel model) {
        int x = 40;
        int y = 40;
        int maxRowHeight = 0;
        int colCount = 0;
        int maxCols = 3;

        for (ErTable table : model.tables.values()) {
            int calculatedHeight = 44 + (table.columns.size() * 22) + 12;
            table.height = Math.max(120, calculatedHeight);
            table.width = 230;

            table.x = x;
            table.y = y;

            maxRowHeight = Math.max(maxRowHeight, table.height);
            colCount++;

            if (colCount >= maxCols) {
                colCount = 0;
                x = 40;
                y += maxRowHeight + 50;
                maxRowHeight = 0;
            } else {
                x += table.width + 70;
            }
        }
    }

    public static String exportToMermaid(ErModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("erDiagram\n");

        for (ErTable table : model.tables.values()) {
            sb.append("    ").append(table.name).append(" {\n");
            for (ErColumn col : table.columns) {
                String type = col.type.replaceAll("[^a-zA-Z0-9_]", "_");
                sb.append("        ").append(type).append(" ").append(col.name);
                if (col.isPk) sb.append(" PK");
                if (col.isFk) sb.append(" FK");
                sb.append("\n");
            }
            sb.append("    }\n");
        }

        for (ErRelation rel : model.relations) {
            sb.append("    ").append(rel.sourceTable).append(" }|--|| ").append(rel.targetTable).append(" : \"references\"\n");
        }

        return sb.toString();
    }

    public static String generateTableDdl(ErTable table) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(table.schema).append(".").append(table.name).append(" (\n");
        for (int i = 0; i < table.columns.size(); i++) {
            ErColumn col = table.columns.get(i);
            sb.append("    ").append(col.name).append(" ").append(col.type);
            if (col.isPk) sb.append(" PRIMARY KEY");
            if (col.isFk && col.fkTargetTable != null) {
                sb.append(" REFERENCES ").append(col.fkTargetTable).append("(").append(col.fkTargetColumn).append(")");
            }
            if (i < table.columns.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(");");
        return sb.toString();
    }
}

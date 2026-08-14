package com.ddlexporter.migration;

import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EnvironmentDiffEngine {

    public enum DiffStatus {
        MATCH,            // Tables and columns are identical
        MODIFIED,         // Columns or types differ
        MISSING_IN_TARGET,// Table exists in Source, missing in Target
        EXTRA_IN_TARGET   // Table exists in Target, missing in Source
    }

    public static class ColumnInfo {
        public String name;
        public String dataType;
        public boolean isNullable;
        public String defaultValue;

        public ColumnInfo(String name, String dataType, boolean isNullable, String defaultValue) {
            this.name = name;
            this.dataType = dataType;
            this.isNullable = isNullable;
            this.defaultValue = defaultValue;
        }
    }

    public static class TableDiffResult {
        public String tableName;
        public String schema;
        public DiffStatus status;
        public List<String> addedColumns = new ArrayList<>();
        public List<String> droppedColumns = new ArrayList<>();
        public List<String> typeMismatches = new ArrayList<>();
        public List<String> missingIndexes = new ArrayList<>();

        public TableDiffResult(String tableName, String schema, DiffStatus status) {
            this.tableName = tableName;
            this.schema = schema;
            this.status = status;
        }
    }

    public static class EnvironmentComparisonReport {
        public String sourceName;
        public String targetName;
        public String timestamp;
        public List<TableDiffResult> tableDiffs = new ArrayList<>();
        public int matchingCount = 0;
        public int modifiedCount = 0;
        public int missingInTargetCount = 0;
        public int extraInTargetCount = 0;
        public String generatedPatchSql = "";
    }

    public static EnvironmentComparisonReport compareEnvironments(
            String sourceName, PostgresqlConfigurationSettings sourceConfig,
            String targetName, PostgresqlConfigurationSettings targetConfig) {

        EnvironmentComparisonReport report = new EnvironmentComparisonReport();
        report.sourceName = sourceName;
        report.targetName = targetName;
        report.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Map<String, Map<String, ColumnInfo>> sourceTables = fetchSchemaTables(sourceConfig);
        Map<String, Map<String, ColumnInfo>> targetTables = fetchSchemaTables(targetConfig);

        Set<String> allTableNames = new TreeSet<>();
        allTableNames.addAll(sourceTables.keySet());
        allTableNames.addAll(targetTables.keySet());

        StringBuilder patchSql = new StringBuilder();
        patchSql.append("-- ====================================================================\n");
        patchSql.append("-- PostgreSQL DDL Studio - Canlı Ortam Senkronizasyon (Deploy Patch) Scripti\n");
        patchSql.append("-- Kaynak Ortam       : ").append(sourceName).append(" (").append(sourceConfig.getServerHost()).append(":").append(sourceConfig.getPort()).append("/").append(sourceConfig.getDatabaseName()).append(")\n");
        patchSql.append("-- Hedef Ortam (Canlı): ").append(targetName).append(" (").append(targetConfig.getServerHost()).append(":").append(targetConfig.getPort()).append("/").append(targetConfig.getDatabaseName()).append(")\n");
        patchSql.append("-- Karşılaştırma Zamanı: ").append(report.timestamp).append("\n");
        patchSql.append("-- ====================================================================\n\n");
        patchSql.append("BEGIN;\n\n");

        List<String> ddlStatements = new ArrayList<>();

        for (String table : allTableNames) {
            boolean inSource = sourceTables.containsKey(table);
            boolean inTarget = targetTables.containsKey(table);

            if (inSource && !inTarget) {
                // Completely missing in target
                TableDiffResult diff = new TableDiffResult(table, sourceConfig.getSchema(), DiffStatus.MISSING_IN_TARGET);
                report.tableDiffs.add(diff);
                report.missingInTargetCount++;

                // Generate CREATE TABLE statement for missing table
                Map<String, ColumnInfo> cols = sourceTables.get(table);
                StringBuilder createTbl = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(sourceConfig.getSchema()).append(".").append(table).append(" (\n");
                int cIdx = 0;
                for (ColumnInfo col : cols.values()) {
                    createTbl.append("    ").append(col.name).append(" ").append(col.dataType);
                    if (!col.isNullable) createTbl.append(" NOT NULL");
                    if (col.defaultValue != null) createTbl.append(" DEFAULT ").append(col.defaultValue);
                    if (++cIdx < cols.size()) createTbl.append(",");
                    createTbl.append("\n");
                }
                createTbl.append(");");
                ddlStatements.add(createTbl.toString());

            } else if (!inSource && inTarget) {
                // Extra in target
                TableDiffResult diff = new TableDiffResult(table, targetConfig.getSchema(), DiffStatus.EXTRA_IN_TARGET);
                report.tableDiffs.add(diff);
                report.extraInTargetCount++;
                ddlStatements.add("-- BİLGİ: '" + table + "' tablosu sadece hedef (" + targetName + ") ortamında mevcut.");

            } else {
                // Table in both, compare columns
                Map<String, ColumnInfo> srcCols = sourceTables.get(table);
                Map<String, ColumnInfo> tgtCols = targetTables.get(table);

                TableDiffResult diff = new TableDiffResult(table, sourceConfig.getSchema(), DiffStatus.MATCH);

                // Check added columns in source (missing in target)
                for (String colName : srcCols.keySet()) {
                    if (!tgtCols.containsKey(colName)) {
                        ColumnInfo c = srcCols.get(colName);
                        diff.addedColumns.add(colName + " (" + c.dataType + ")");
                        String addCol = String.format("ALTER TABLE %s.%s ADD COLUMN IF NOT EXISTS %s %s%s%s;",
                                sourceConfig.getSchema(), table, c.name, c.dataType,
                                (!c.isNullable ? " NOT NULL" : ""),
                                (c.defaultValue != null ? " DEFAULT " + c.defaultValue : ""));
                        ddlStatements.add(addCol);
                    } else {
                        // Check type mismatch
                        ColumnInfo srcCol = srcCols.get(colName);
                        ColumnInfo tgtCol = tgtCols.get(colName);
                        if (!srcCol.dataType.equalsIgnoreCase(tgtCol.dataType)) {
                            diff.typeMismatches.add(colName + ": " + tgtCol.dataType + " -> " + srcCol.dataType);
                            String alterType = String.format("ALTER TABLE %s.%s ALTER COLUMN %s TYPE %s;",
                                    sourceConfig.getSchema(), table, srcCol.name, srcCol.dataType);
                            ddlStatements.add(alterType);
                        }
                    }
                }

                // Check dropped columns in source (present in target)
                for (String tgtColName : tgtCols.keySet()) {
                    if (!srcCols.containsKey(tgtColName)) {
                        diff.droppedColumns.add(tgtColName);
                        ddlStatements.add(String.format("-- UYARI: Hedefte fazladan bulunan kolon silinebilir:\n-- ALTER TABLE %s.%s DROP COLUMN IF EXISTS %s;",
                                targetConfig.getSchema(), table, tgtColName));
                    }
                }

                if (!diff.addedColumns.isEmpty() || !diff.droppedColumns.isEmpty() || !diff.typeMismatches.isEmpty()) {
                    diff.status = DiffStatus.MODIFIED;
                    report.modifiedCount++;
                } else {
                    report.matchingCount++;
                }
                report.tableDiffs.add(diff);
            }
        }

        if (ddlStatements.isEmpty()) {
            patchSql.append("-- İki ortam arasında herhangi bir şema farkı tespit edilmedi.\n");
            patchSql.append("-- Kaynak (").append(sourceName).append(") ve Hedef (").append(targetName).append(") %100 senkronizedir.\n\n");
        } else {
            patchSql.append("-- Hedef (").append(targetName).append(") ortamını Kaynak (").append(sourceName).append(") ile eşitleyecek SQL komutları:\n\n");
            for (String stmt : ddlStatements) {
                patchSql.append(stmt).append("\n");
            }
            patchSql.append("\n");
        }

        patchSql.append("COMMIT;\n");
        patchSql.append("-- Ortam senkronizasyon yaması tamamlandı.\n");

        report.generatedPatchSql = patchSql.toString();
        return report;
    }

    private static Map<String, Map<String, ColumnInfo>> fetchSchemaTables(PostgresqlConfigurationSettings config) {
        Map<String, Map<String, ColumnInfo>> tableMap = new LinkedHashMap<>();
        if (config == null) return tableMap;

        String url = String.format("jdbc:postgresql://%s:%d/%s",
                config.getServerHost(), config.getPort(), config.getDatabaseName());

        try (Connection conn = DriverManager.getConnection(url, config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement()) {

            String query = String.format(
                    "SELECT table_name, column_name, data_type, is_nullable, column_default " +
                    "FROM information_schema.columns " +
                    "WHERE table_schema = '%s' " +
                    "ORDER BY table_name, ordinal_position",
                    config.getSchema() != null ? config.getSchema() : "public");

            try (ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    String tbl = rs.getString("table_name");
                    String col = rs.getString("column_name");
                    String type = rs.getString("data_type");
                    boolean nullable = "YES".equalsIgnoreCase(rs.getString("is_nullable"));
                    String defVal = rs.getString("column_default");

                    tableMap.computeIfAbsent(tbl, k -> new LinkedHashMap<>())
                            .put(col, new ColumnInfo(col, type, nullable, defVal));
                }
            }
        } catch (Exception ignored) {}

        return tableMap;
    }

    // High fidelity test simulation for environments
    public static EnvironmentComparisonReport generateSimulatedReport(String sourceName, String targetName) {
        EnvironmentComparisonReport report = new EnvironmentComparisonReport();
        report.sourceName = sourceName;
        report.targetName = targetName;
        report.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 1. Matching tables
        report.tableDiffs.add(new TableDiffResult("users", "public", DiffStatus.MATCH));
        report.tableDiffs.add(new TableDiffResult("categories", "public", DiffStatus.MATCH));
        report.tableDiffs.add(new TableDiffResult("audit_logs", "public", DiffStatus.MATCH));
        report.tableDiffs.add(new TableDiffResult("payments", "public", DiffStatus.MATCH));
        report.matchingCount = 4;

        // 2. Modified tables
        TableDiffResult productsDiff = new TableDiffResult("products", "public", DiffStatus.MODIFIED);
        productsDiff.addedColumns.add("sku_code (character varying)");
        productsDiff.addedColumns.add("is_featured (boolean)");
        productsDiff.typeMismatches.add("price: numeric(8,2) -> numeric(12,2)");
        report.tableDiffs.add(productsDiff);

        TableDiffResult ordersDiff = new TableDiffResult("orders", "public", DiffStatus.MODIFIED);
        ordersDiff.addedColumns.add("tracking_number (character varying)");
        report.tableDiffs.add(ordersDiff);
        report.modifiedCount = 2;

        // 3. Missing tables in target
        TableDiffResult couponsDiff = new TableDiffResult("coupons", "public", DiffStatus.MISSING_IN_TARGET);
        report.tableDiffs.add(couponsDiff);

        TableDiffResult reviewsDiff = new TableDiffResult("product_reviews", "public", DiffStatus.MISSING_IN_TARGET);
        report.tableDiffs.add(reviewsDiff);
        report.missingInTargetCount = 2;

        // 4. Extra in target
        TableDiffResult legacyDiff = new TableDiffResult("legacy_temp_orders", "public", DiffStatus.EXTRA_IN_TARGET);
        report.tableDiffs.add(legacyDiff);
        report.extraInTargetCount = 1;

        // Generate Patch Script
        StringBuilder sb = new StringBuilder();
        sb.append("-- ====================================================================\n");
        sb.append("-- PostgreSQL DDL Studio - Canlı Ortam Senkronizasyon (Deploy Patch) Scripti\n");
        sb.append("-- Kaynak Ortam       : ").append(sourceName).append(" (Geliştirme / Staging)\n");
        sb.append("-- Hedef Ortam (Canlı): ").append(targetName).append(" (Canlı / Production)\n");
        sb.append("-- Karşılaştırma Zamanı: ").append(report.timestamp).append("\n");
        sb.append("-- ====================================================================\n\n");
        sb.append("BEGIN;\n\n");
        sb.append("-- 1. Hedefte Eksik Olan Yeni Tabloların Oluşturulması\n");
        sb.append("CREATE TABLE IF NOT EXISTS public.coupons (\n");
        sb.append("    id integer NOT NULL PRIMARY KEY,\n");
        sb.append("    code character varying(50) NOT NULL UNIQUE,\n");
        sb.append("    discount_pct numeric(5,2) NOT NULL DEFAULT 0.0,\n");
        sb.append("    valid_until timestamp with time zone\n");
        sb.append(");\n\n");
        sb.append("CREATE TABLE IF NOT EXISTS public.product_reviews (\n");
        sb.append("    id integer NOT NULL PRIMARY KEY,\n");
        sb.append("    product_id integer NOT NULL REFERENCES public.products(id),\n");
        sb.append("    rating integer NOT NULL CHECK (rating BETWEEN 1 AND 5),\n");
        sb.append("    comment text,\n");
        sb.append("    created_at timestamp DEFAULT now()\n");
        sb.append(");\n\n");
        sb.append("-- 2. Mevcut Tablolardaki Yeni Kolonlar ve Tip Güncellemeleri\n");
        sb.append("ALTER TABLE public.products ADD COLUMN IF NOT EXISTS sku_code character varying(50);\n");
        sb.append("ALTER TABLE public.products ADD COLUMN IF NOT EXISTS is_featured boolean DEFAULT false;\n");
        sb.append("ALTER TABLE public.products ALTER COLUMN price TYPE numeric(12,2);\n");
        sb.append("ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS tracking_number character varying(100);\n\n");
        sb.append("COMMIT;\n");
        sb.append("-- Senkronizasyon yaması başarıyla tamamlandı.\n");

        report.generatedPatchSql = sb.toString();
        return report;
    }
}

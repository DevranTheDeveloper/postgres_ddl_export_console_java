package com.ddlexporter.postgresql.extractor;

import com.ddlexporter.common.logger.ILogger;
import com.ddlexporter.common.writer.IWriter;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcDdlExtractor implements IDdlExtractor {
    private final PostgresqlConfigurationSettings settings;
    private final IWriter writer;
    private final ILogger logger;

    public JdbcDdlExtractor(PostgresqlConfigurationSettings settings, IWriter writer, ILogger logger) {
        this.settings = settings;
        this.writer = writer;
        this.logger = logger;
    }

    @Override
    public void extract() {
        String url = String.format("jdbc:postgresql://%s:%d/%s",
                settings.getServerHost(), settings.getPort(), settings.getDatabaseName());

        logger.log("JDBC ile PostgreSQL veritabanına bağlanılıyor: " + url);

        try (Connection conn = DriverManager.getConnection(url, settings.getUsername(), settings.getPassword())) {
            logger.log("JDBC bağlantısı başarılı. Nesneler sorgulanıyor...");

            extractSchemas(conn);
            extractTypes(conn);
            extractSequences(conn);
            extractTables(conn);
            extractViews(conn);
            extractFunctionsAndProcedures(conn);
            extractIndexes(conn);
            extractTriggers(conn);

            logger.log("JDBC DDL Aktarım İşlemi Tamamlandı.");

        } catch (SQLException e) {
            throw new RuntimeException("JDBC ile DDL çıkarma hatası: " + e.getMessage(), e);
        }
    }

    private void extractSchemas(Connection conn) throws SQLException {
        String sql = "SELECT nspname FROM pg_catalog.pg_namespace " +
                "WHERE nspname NOT LIKE 'pg_%' AND nspname != 'information_schema' ORDER BY nspname";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String schema = rs.getString("nspname");
                String ddl = "CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(schema) + ";";
                exportObject("SCHEMA", schema, ddl);
            }
        }
    }

    private void extractTypes(Connection conn) throws SQLException {
        String sql = "SELECT n.nspname, t.typname, e.enumlabel " +
                "FROM pg_catalog.pg_type t " +
                "JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace " +
                "JOIN pg_catalog.pg_enum e ON e.enumtypid = t.oid " +
                "WHERE n.nspname NOT LIKE 'pg_%' AND n.nspname != 'information_schema' " +
                "ORDER BY n.nspname, t.typname, e.enumsortorder";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            String currentType = null;
            String currentSchema = null;
            List<String> enumLabels = new ArrayList<>();

            while (rs.next()) {
                String schema = rs.getString("nspname");
                String typeName = rs.getString("typname");
                String label = rs.getString("enumlabel");

                if (currentType != null && (!currentType.equals(typeName) || !currentSchema.equals(schema))) {
                    writeEnumType(currentSchema, currentType, enumLabels);
                    enumLabels.clear();
                }

                currentSchema = schema;
                currentType = typeName;
                enumLabels.add(label);
            }

            if (currentType != null) {
                writeEnumType(currentSchema, currentType, enumLabels);
            }
        }
    }

    private void writeEnumType(String schema, String typeName, List<String> labels) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TYPE ").append(quoteIdentifier(schema)).append(".").append(quoteIdentifier(typeName))
                .append(" AS ENUM (\n");
        for (int i = 0; i < labels.size(); i++) {
            sb.append("  '").append(labels.get(i).replace("'", "''")).append("'");
            if (i < labels.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(");");

        exportObject("TYPE", schema + "." + typeName, sb.toString());
    }

    private void extractSequences(Connection conn) throws SQLException {
        String sql = "SELECT sequence_schema, sequence_name FROM information_schema.sequences " +
                "WHERE sequence_schema NOT LIKE 'pg_%' AND sequence_schema != 'information_schema' " +
                "ORDER BY sequence_schema, sequence_name";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String schema = rs.getString("sequence_schema");
                String seqName = rs.getString("sequence_name");

                String ddl = "CREATE SEQUENCE " + quoteIdentifier(schema) + "." + quoteIdentifier(seqName) + ";";
                exportObject("SEQUENCE", schema + "." + seqName, ddl);
            }
        }
    }

    private void extractTables(Connection conn) throws SQLException {
        String sql = "SELECT table_schema, table_name FROM information_schema.tables " +
                "WHERE table_type = 'BASE TABLE' AND table_schema NOT LIKE 'pg_%' AND table_schema != 'information_schema' " +
                "ORDER BY table_schema, table_name";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String schema = rs.getString("table_schema");
                String tableName = rs.getString("table_name");

                String tableDdl = generateTableDdl(conn, schema, tableName);
                if (tableDdl != null) {
                    exportObject("TABLE", schema + "." + tableName, tableDdl);
                }
            }
        }
    }

    private String generateTableDdl(Connection conn, String schema, String tableName) throws SQLException {
        String colSql = "SELECT column_name, data_type, character_maximum_length, is_nullable, column_default " +
                "FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = ? " +
                "ORDER BY ordinal_position";

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(quoteIdentifier(schema)).append(".").append(quoteIdentifier(tableName)).append(" (\n");

        try (PreparedStatement stmt = conn.prepareStatement(colSql)) {
            stmt.setString(1, schema);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",\n");
                    first = false;

                    String colName = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    int maxLen = rs.getInt("character_maximum_length");
                    String isNullable = rs.getString("is_nullable");
                    String colDefault = rs.getString("column_default");

                    sb.append("  ").append(quoteIdentifier(colName)).append(" ").append(dataType.toUpperCase());
                    if (maxLen > 0) {
                        sb.append("(").append(maxLen).append(")");
                    }
                    if (colDefault != null && !colDefault.isEmpty()) {
                        sb.append(" DEFAULT ").append(colDefault);
                    }
                    if ("NO".equalsIgnoreCase(isNullable)) {
                        sb.append(" NOT NULL");
                    }
                }
            }
        }
        sb.append("\n);");
        return sb.toString();
    }

    private void extractViews(Connection conn) throws SQLException {
        String sql = "SELECT c.relname AS view_name, n.nspname AS view_schema, " +
                "pg_catalog.pg_get_viewdef(c.oid, true) AS view_def " +
                "FROM pg_catalog.pg_class c " +
                "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE c.relkind = 'v' AND n.nspname NOT LIKE 'pg_%' AND n.nspname != 'information_schema' " +
                "ORDER BY n.nspname, c.relname";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String schema = rs.getString("view_schema");
                String viewName = rs.getString("view_name");
                String viewDef = rs.getString("view_def");

                String ddl = "CREATE OR REPLACE VIEW " + quoteIdentifier(schema) + "." + quoteIdentifier(viewName) +
                        " AS\n" + viewDef;
                exportObject("VIEW", schema + "." + viewName, ddl);
            }
        }
    }

    private void extractFunctionsAndProcedures(Connection conn) throws SQLException {
        String sql = "SELECT p.proname, n.nspname, p.prokind, " +
                "pg_catalog.pg_get_functiondef(p.oid) AS func_def " +
                "FROM pg_catalog.pg_proc p " +
                "JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace " +
                "WHERE n.nspname NOT LIKE 'pg_%' AND n.nspname != 'information_schema' " +
                "ORDER BY n.nspname, p.proname";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String schema = rs.getString("nspname");
                String name = rs.getString("proname");
                String kind = rs.getString("prokind"); // 'f' = function, 'p' = procedure
                String def = rs.getString("func_def");

                String objectType = "p".equalsIgnoreCase(kind) ? "STORED_PROCEDURE" : "FUNCTION";
                exportObject(objectType, schema + "." + name, def);
            }
        }
    }

    private void extractIndexes(Connection conn) throws SQLException {
        String sql = "SELECT schemaname, indexname, indexdef FROM pg_catalog.pg_indexes " +
                "WHERE schemaname NOT LIKE 'pg_%' AND schemaname != 'information_schema' " +
                "ORDER BY schemaname, indexname";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String schema = rs.getString("schemaname");
                String indexName = rs.getString("indexname");
                String indexDef = rs.getString("indexdef");

                exportObject("INDEX", schema + "." + indexName, indexDef + ";");
            }
        }
    }

    private void extractTriggers(Connection conn) throws SQLException {
        String sql = "SELECT t.tgname, n.nspname, pg_catalog.pg_get_triggerdef(t.oid, true) AS trig_def " +
                "FROM pg_catalog.pg_trigger t " +
                "JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid " +
                "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE NOT t.tgisinternal AND n.nspname NOT LIKE 'pg_%' AND n.nspname != 'information_schema' " +
                "ORDER BY n.nspname, t.tgname";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String schema = rs.getString("nspname");
                String triggerName = rs.getString("tgname");
                String triggerDef = rs.getString("trig_def");

                exportObject("TRIGGER", schema + "." + triggerName, triggerDef + ";");
            }
        }
    }

    private void exportObject(String type, String nameWithSchema, String ddl) {
        writer.start(settings.getDatabaseName(), type, nameWithSchema);
        writer.writeLine(ddl);
        writer.finish();
        logger.log("Dışa aktarıldı (JDBC): [" + type + "] " + nameWithSchema);
    }

    private String quoteIdentifier(String id) {
        if (id == null) return "\"\"";
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }
}

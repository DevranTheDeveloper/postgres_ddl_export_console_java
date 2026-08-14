package com.ddlexporter.er;

import java.io.File;
import java.nio.file.Files;
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
            return generateSampleModel();
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

        if (model.tables.isEmpty()) {
            return generateSampleModel();
        }

        arrangeLayout(model);
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
                        String tgtTbl = fkMatcher.group(2).trim();
                        String tgtCol = fkMatcher.group(3).trim().replace("\"", "");
                        foundRelations.add(new ErRelation(tableName, srcCol, tgtTbl, tgtCol));
                    }
                }
            }

            // 2. Second pass for columns
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.endsWith(",")) trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.toUpperCase().startsWith("CONSTRAINT")
                        || trimmed.toUpperCase().startsWith("PRIMARY KEY") || trimmed.toUpperCase().startsWith("FOREIGN KEY")) {
                    continue;
                }

                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 2) {
                    String colName = parts[0].replace("\"", "");
                    String colType = parts[1];
                    boolean isPk = pkCols.contains(colName.toLowerCase()) || trimmed.toUpperCase().contains("PRIMARY KEY");
                    boolean isFk = false;

                    for (ErRelation rel : foundRelations) {
                        if (rel.sourceColumn.equalsIgnoreCase(colName)) {
                            isFk = true;
                            break;
                        }
                    }

                    if (trimmed.toUpperCase().contains("REFERENCES")) {
                        Pattern inlineFk = Pattern.compile("REFERENCES\\s*(?:[a-zA-Z0-9_]+\\.)?([a-zA-Z0-9_]+)\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
                        Matcher ifk = inlineFk.matcher(trimmed);
                        if (ifk.find()) {
                            isFk = true;
                            foundRelations.add(new ErRelation(tableName, colName, ifk.group(1), ifk.group(2)));
                        }
                    }

                    table.columns.add(new ErColumn(colName, colType, isPk, isFk));
                }
            }

            table.height = 36 + Math.max(1, table.columns.size()) * 20 + 8;
            model.tables.put(tableName, table);
            model.relations.addAll(foundRelations);
        }
    }

    public static void arrangeLayout(ErModel model) {
        int col = 0;
        int row = 0;
        int spacingX = 270;
        int spacingY = 220;
        int startX = 40;
        int startY = 40;
        int maxCols = 3;

        for (ErTable table : model.tables.values()) {
            table.x = startX + col * spacingX;
            table.y = startY + row * spacingY;
            col++;
            if (col >= maxCols) {
                col = 0;
                row++;
            }
        }
    }

    public static ErModel generateSampleModel() {
        ErModel model = new ErModel();

        // 1. Users
        ErTable users = new ErTable("users", "public");
        users.columns.add(new ErColumn("id", "integer", true, false));
        users.columns.add(new ErColumn("email", "varchar(255)", false, false));
        users.columns.add(new ErColumn("full_name", "varchar(100)", false, false));
        users.columns.add(new ErColumn("created_at", "timestamp", false, false));
        users.height = 36 + users.columns.size() * 20 + 8;
        users.x = 40; users.y = 50;
        model.tables.put("users", users);

        // 2. Categories
        ErTable categories = new ErTable("categories", "public");
        categories.columns.add(new ErColumn("id", "integer", true, false));
        categories.columns.add(new ErColumn("name", "varchar(100)", false, false));
        categories.columns.add(new ErColumn("slug", "varchar(100)", false, false));
        categories.height = 36 + categories.columns.size() * 20 + 8;
        categories.x = 330; categories.y = 50;
        model.tables.put("categories", categories);

        // 3. Products
        ErTable products = new ErTable("products", "public");
        products.columns.add(new ErColumn("id", "integer", true, false));
        products.columns.add(new ErColumn("category_id", "integer", false, true));
        products.columns.add(new ErColumn("name", "varchar(255)", false, false));
        products.columns.add(new ErColumn("price", "numeric(10,2)", false, false));
        products.columns.add(new ErColumn("is_active", "boolean", false, false));
        products.height = 36 + products.columns.size() * 20 + 8;
        products.x = 330; products.y = 260;
        model.tables.put("products", products);

        // 4. Orders
        ErTable orders = new ErTable("orders", "public");
        orders.columns.add(new ErColumn("id", "integer", true, false));
        orders.columns.add(new ErColumn("user_id", "integer", false, true));
        orders.columns.add(new ErColumn("order_date", "timestamp", false, false));
        orders.columns.add(new ErColumn("total_amount", "numeric(12,2)", false, false));
        orders.columns.add(new ErColumn("status", "varchar(50)", false, false));
        orders.height = 36 + orders.columns.size() * 20 + 8;
        orders.x = 40; orders.y = 260;
        model.tables.put("orders", orders);

        // 5. Order Items
        ErTable orderItems = new ErTable("order_items", "public");
        orderItems.columns.add(new ErColumn("id", "integer", true, false));
        orderItems.columns.add(new ErColumn("order_id", "integer", false, true));
        orderItems.columns.add(new ErColumn("product_id", "integer", false, true));
        orderItems.columns.add(new ErColumn("quantity", "integer", false, false));
        orderItems.columns.add(new ErColumn("unit_price", "numeric(10,2)", false, false));
        orderItems.height = 36 + orderItems.columns.size() * 20 + 8;
        orderItems.x = 620; orderItems.y = 260;
        model.tables.put("order_items", orderItems);

        // 6. Product Reviews
        ErTable reviews = new ErTable("product_reviews", "public");
        reviews.columns.add(new ErColumn("id", "integer", true, false));
        reviews.columns.add(new ErColumn("product_id", "integer", false, true));
        reviews.columns.add(new ErColumn("user_id", "integer", false, true));
        reviews.columns.add(new ErColumn("rating", "integer", false, false));
        reviews.columns.add(new ErColumn("comment", "text", false, false));
        reviews.height = 36 + reviews.columns.size() * 20 + 8;
        reviews.x = 620; reviews.y = 50;
        model.tables.put("product_reviews", reviews);

        // Relations
        model.relations.add(new ErRelation("products", "category_id", "categories", "id"));
        model.relations.add(new ErRelation("orders", "user_id", "users", "id"));
        model.relations.add(new ErRelation("order_items", "order_id", "orders", "id"));
        model.relations.add(new ErRelation("order_items", "product_id", "products", "id"));
        model.relations.add(new ErRelation("product_reviews", "product_id", "products", "id"));
        model.relations.add(new ErRelation("product_reviews", "user_id", "users", "id"));

        return model;
    }

    public static String exportToMermaid(ErModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("erDiagram\n");
        for (ErRelation rel : model.relations) {
            sb.append("    ").append(rel.targetTable).append(" ||--o{ ").append(rel.sourceTable)
                    .append(" : \"references (").append(rel.sourceColumn).append(")\"\n");
        }
        for (ErTable tbl : model.tables.values()) {
            sb.append("    ").append(tbl.name).append(" {\n");
            for (ErColumn col : tbl.columns) {
                sb.append("        ").append(col.type.replace(" ", "_")).append(" ").append(col.name);
                if (col.isPk) sb.append(" PK");
                if (col.isFk) sb.append(" FK");
                sb.append("\n");
            }
            sb.append("    }\n");
        }
        return sb.toString();
    }

    public static String generateTableDdl(ErTable tbl) {
        if (tbl == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(tbl.schema != null ? tbl.schema + "." : "public.").append(tbl.name).append(" (\n");
        int idx = 0;
        for (ErColumn col : tbl.columns) {
            sb.append("    ").append(col.name).append(" ").append(col.type);
            if (col.isPk) sb.append(" PRIMARY KEY");
            if (++idx < tbl.columns.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append(");");
        return sb.toString();
    }
}

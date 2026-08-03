package com.ddlexporter.postgresql.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PostgresqlConfigurationSettings {
    @JsonProperty("serverHost")
    private String serverHost = "localhost";

    @JsonProperty("port")
    private int port = 5432;

    @JsonProperty("databaseName")
    private String databaseName;

    @JsonProperty("username")
    private String username = "postgres";

    @JsonProperty("password")
    private String password;

    @JsonProperty("schema")
    private String schema = "public";

    @JsonProperty("pgDumpPath")
    private String pgDumpPath = "pg_dump";

    @JsonProperty("pgRestorePath")
    private String pgRestorePath = "pg_restore";

    public PostgresqlConfigurationSettings() {}

    public String getServerHost() { return serverHost; }
    public void setServerHost(String serverHost) { this.serverHost = serverHost; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getPgDumpPath() { return pgDumpPath; }
    public void setPgDumpPath(String pgDumpPath) { this.pgDumpPath = pgDumpPath; }

    public String getPgRestorePath() { return pgRestorePath; }
    public void setPgRestorePath(String pgRestorePath) { this.pgRestorePath = pgRestorePath; }
}

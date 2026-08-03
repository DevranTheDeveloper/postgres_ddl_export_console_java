package com.ddlexporter.common.scripter;

import com.ddlexporter.common.config.IConfigurationReader;
import com.ddlexporter.common.logger.ILogger;
import com.ddlexporter.common.writer.IWriter;
import com.ddlexporter.postgresql.PostgresqlScripter;

public class ScripterBuilder {
    private final String databaseType;
    private IConfigurationReader configurationReader;
    private IWriter writer;
    private ILogger logger;

    private ScripterBuilder(String databaseType) {
        this.databaseType = databaseType != null ? databaseType.trim().toUpperCase() : "";
    }

    public static ScripterBuilder get(String databaseType) {
        return new ScripterBuilder(databaseType);
    }

    public ScripterBuilder addConfigurationReader(IConfigurationReader configurationReader) {
        this.configurationReader = configurationReader;
        return this;
    }

    public ScripterBuilder addWriter(IWriter writer) {
        this.writer = writer;
        return this;
    }

    public ScripterBuilder addLogger(ILogger logger) {
        this.logger = logger;
        return this;
    }

    public IScripter build() {
        switch (databaseType) {
            case "POSTGRESQL":
            case "POSTGRES":
                return new PostgresqlScripter(configurationReader, writer, logger);
            default:
                throw new IllegalArgumentException("Desteklenmeyen veritabanı tipi: '" + databaseType + "'. Desteklenen tipler: POSTGRESQL");
        }
    }
}

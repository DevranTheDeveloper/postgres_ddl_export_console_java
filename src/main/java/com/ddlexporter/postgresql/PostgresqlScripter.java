package com.ddlexporter.postgresql;

import com.ddlexporter.common.config.IConfigurationReader;
import com.ddlexporter.common.logger.ILogger;
import com.ddlexporter.common.scripter.IScripter;
import com.ddlexporter.common.writer.IWriter;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;
import com.ddlexporter.postgresql.extractor.IDdlExtractor;
import com.ddlexporter.postgresql.extractor.JdbcDdlExtractor;
import com.ddlexporter.postgresql.extractor.PgDumpDdlExtractor;

import java.io.File;

public class PostgresqlScripter implements IScripter {
    public static final String DATABASE_TYPE = "POSTGRESQL";

    private final PostgresqlConfigurationSettings settings;
    private final IWriter writer;
    private final ILogger logger;

    public PostgresqlScripter(IConfigurationReader configurationReader, IWriter writer, ILogger logger) {
        if (configurationReader == null) {
            throw new IllegalArgumentException("ConfigurationReader null olamaz.");
        }
        this.settings = configurationReader.read(PostgresqlConfigurationSettings.class);
        this.writer = writer;
        this.logger = logger;
    }

    @Override
    public void execute() {
        logger.log("PostgreSQL DDL Dışa Aktarma İşlemi Başladı...");
        if (settings == null) {
            throw freshException("PostgreSQL ayarları okunamadı.");
        }

        IDdlExtractor extractor = selectExtractor();
        try {
            extractor.extract();
            logger.log("PostgreSQL DDL Dışa Aktarma İşlemi Tamamlandı.");
        } catch (Exception e) {
            logger.log("pg_dump ile aktarım başarısız oldu. JDBC yedek (fallback) motoru deneniyor...");
            logger.logError("pg_dump hatası: " + e.getMessage(), e);

            IDdlExtractor jdbcFallback = new JdbcDdlExtractor(settings, writer, logger);
            jdbcFallback.extract();
            logger.log("PostgreSQL DDL Dışa Aktarma İşlemi (JDBC Fallback) Tamamlandı.");
        }
    }

    private IDdlExtractor selectExtractor() {
        String pgDumpExe = settings.getPgDumpPath();
        if (isExecutableAvailable(pgDumpExe)) {
            logger.log("pg_dump motoru seçildi.");
            return new PgDumpDdlExtractor(settings, writer, logger);
        } else {
            logger.log("pg_dump çalıştırılabilir dosyası bulunamadı, JDBC motoru seçildi.");
            return new JdbcDdlExtractor(settings, writer, logger);
        }
    }

    private boolean isExecutableAvailable(String exePath) {
        if (exePath == null || exePath.isBlank()) {
            exePath = "pg_dump";
        }
        File file = new File(exePath);
        if (file.exists() && file.isFile()) {
            return true;
        }
        try {
            Process p = new ProcessBuilder(exePath, "--version").start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private RuntimeException freshException(String msg) {
        return new RuntimeException(msg);
    }
}

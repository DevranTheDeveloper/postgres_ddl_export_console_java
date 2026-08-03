package com.ddlexporter.postgresql.extractor;

import com.ddlexporter.common.logger.ILogger;
import com.ddlexporter.common.model.TocEntry;
import com.ddlexporter.common.writer.IWriter;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PgDumpDdlExtractor implements IDdlExtractor {
    private final PostgresqlConfigurationSettings settings;
    private final IWriter writer;
    private final ILogger logger;

    private static final Pattern TOC_PATTERN = Pattern.compile(
            "^(?<archiveId>\\d+);\\s+(?<objectId>[\\d\\s]+)\\s+(?<type>[A-Z_ ]+)\\s+(?<schema>\\S+)\\s+(?<name>.+?)\\s+(?<owner>\\S+)$"
    );

    public PgDumpDdlExtractor(PostgresqlConfigurationSettings settings, IWriter writer, ILogger logger) {
        this.settings = settings;
        this.writer = writer;
        this.logger = logger;
    }

    @Override
    public void extract() {
        String pgDumpExe = (settings.getPgDumpPath() == null || settings.getPgDumpPath().isBlank())
                ? "pg_dump" : settings.getPgDumpPath();

        String pgRestoreExe = resolvePgRestorePath(pgDumpExe);

        File tempArchiveFile = null;
        try {
            tempArchiveFile = File.createTempFile("pg_dump_archive_", ".dump");
            tempArchiveFile.deleteOnExit();

            List<String> dumpArgs = new ArrayList<>();
            dumpArgs.add(pgDumpExe);
            dumpArgs.add("--host=" + settings.getServerHost());
            dumpArgs.add("--port=" + settings.getPort());
            dumpArgs.add("--username=" + settings.getUsername());
            dumpArgs.add("--dbname=" + settings.getDatabaseName());
            dumpArgs.add("-Fc");
            dumpArgs.add("-s");
            dumpArgs.add("-f");
            dumpArgs.add(tempArchiveFile.getAbsolutePath());

            logger.log("pg_dump çalıştırılıyor: " + String.join(" ", dumpArgs));
            runProcess(dumpArgs, settings.getPassword());

            logger.log("TOC (Table of Contents) alınıyor...");
            List<String> restoreListArgs = List.of(pgRestoreExe, "--list", tempArchiveFile.getAbsolutePath());
            String tocOutput = runProcessAndReadOutput(restoreListArgs, settings.getPassword());

            String[] tocLines = tocOutput.split("\\r?\\n");
            List<TocEntry> tocEntries = parseToc(tocLines);

            logger.log("TOC içinden " + tocEntries.size() + " adet nesne çözümlendi. DDL script'leri dışa aktarılıyor...");

            for (TocEntry entry : tocEntries) {
                File listFile = null;
                try {
                    listFile = File.createTempFile("pg_restore_list_", ".txt");
                    try (FileWriter fw = new FileWriter(listFile, StandardCharsets.UTF_8)) {
                        fw.write(entry.getOriginalLine());
                    }

                    List<String> restoreDdlArgs = List.of(
                            pgRestoreExe,
                            "-f", "-",
                            "--use-list=" + listFile.getAbsolutePath(),
                            tempArchiveFile.getAbsolutePath()
                    );

                    String ddl = runProcessAndReadOutput(restoreDdlArgs, settings.getPassword());

                    if (ddl != null && !ddl.isBlank() && !isOnlyComments(ddl)) {
                        String safeSchema = sanitizeName(entry.getSchema());
                        String safeName = sanitizeName(entry.getName());

                        String objectNameWithSchema = (safeSchema.isEmpty() || safeSchema.equals("-"))
                                ? safeName : safeSchema + "." + safeName;
                        String safeType = sanitizeName(entry.getType());

                        writer.start(settings.getDatabaseName(), safeType, objectNameWithSchema);
                        writer.writeLine(ddl.trim());
                        writer.finish();

                        logger.log("Dışa aktarıldı: [" + safeType + "] " + objectNameWithSchema);
                    }
                } catch (Exception ex) {
                    logger.logError("Nesne dışa aktarılırken hata oluştu: " + entry.getName(), ex);
                } finally {
                    if (listFile != null && listFile.exists()) {
                        listFile.delete();
                    }
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Geçici dosya oluşturulamadı: " + e.getMessage(), e);
        } finally {
            if (tempArchiveFile != null && tempArchiveFile.exists()) {
                tempArchiveFile.delete();
            }
        }
    }

    private String resolvePgRestorePath(String pgDumpExe) {
        if (settings.getPgRestorePath() != null && !settings.getPgRestorePath().isBlank()) {
            return settings.getPgRestorePath();
        }
        File pgDumpFile = new File(pgDumpExe);
        if (pgDumpFile.getParent() != null) {
            String restoreName = pgDumpExe.toLowerCase().endsWith(".exe") ? "pg_restore.exe" : "pg_restore";
            return new File(pgDumpFile.getParent(), restoreName).getAbsolutePath();
        }
        return "pg_restore";
    }

    private boolean isOnlyComments(String ddl) {
        String[] lines = ddl.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                return false;
            }
        }
        return true;
    }

    private void runProcess(List<String> command, String password) {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (password != null && !password.isEmpty()) {
            pb.environment().put("PGPASSWORD", password);
        }
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();
            StringBuilder errorBuilder = new StringBuilder();

            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorBuilder.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            });
            errorThread.start();

            // Consume stdout to prevent hang
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {}
            }

            int exitCode = process.waitFor();
            errorThread.join(5000);

            if (exitCode != 0) {
                throw new RuntimeException("Komut başarısız oldu (Exit Code " + exitCode + "): " + errorBuilder);
            }
        } catch (Exception e) {
            throw new RuntimeException("Süreç çalıştırılırken hata: " + e.getMessage(), e);
        }
    }

    private String runProcessAndReadOutput(List<String> command, String password) {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (password != null && !password.isEmpty()) {
            pb.environment().put("PGPASSWORD", password);
        }

        try {
            Process process = pb.start();
            StringBuilder outputBuilder = new StringBuilder();
            StringBuilder errorBuilder = new StringBuilder();

            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorBuilder.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            });
            errorThread.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            errorThread.join(5000);

            if (exitCode != 0) {
                throw new RuntimeException("Komut başarısız oldu (Exit Code " + exitCode + "): " + errorBuilder);
            }

            return outputBuilder.toString();
        } catch (Exception e) {
            throw new RuntimeException("Süreç çalıştırılırken hata: " + e.getMessage(), e);
        }
    }

    public List<TocEntry> parseToc(String[] lines) {
        List<TocEntry> entries = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank() || line.trim().startsWith(";")) {
                continue;
            }
            String trimmed = line.trim();
            Matcher matcher = TOC_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                String type = matcher.group("type").trim();
                if (!type.equals("ENCODING") && !type.equals("STDSTRINGS") && !type.equals("SEARCHPATH")) {
                    String schema = matcher.group("schema");
                    entries.add(new TocEntry(
                            matcher.group("archiveId"),
                            type,
                            "-".equals(schema) ? "" : schema,
                            matcher.group("name").trim(),
                            line
                    ));
                }
            } else {
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 6 && parts[0].endsWith(";")) {
                    String archiveId = parts[0].substring(0, parts[0].length() - 1);
                    String type = parts[3];
                    if (!type.equals("ENCODING") && !type.equals("STDSTRINGS") && !type.equals("SEARCHPATH")) {
                        String schema = "-".equals(parts[4]) ? "" : parts[4];
                        StringBuilder nameBuilder = new StringBuilder();
                        for (int i = 5; i < parts.length - 1; i++) {
                            if (i > 5) nameBuilder.append(" ");
                            nameBuilder.append(parts[i]);
                        }
                        entries.add(new TocEntry(archiveId, type, schema, nameBuilder.toString(), line));
                    }
                }
            }
        }
        return entries;
    }

    private String sanitizeName(String str) {
        if (str == null) return "";
        return str.trim();
    }
}

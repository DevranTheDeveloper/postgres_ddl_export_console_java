package com.ddlexporter.common.writer;

import com.ddlexporter.common.logger.ILogger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FileWriter implements IWriter {
    private final String outputBaseDir;
    private final ILogger logger;

    private BufferedWriter currentWriter;
    private File currentFile;

    public FileWriter(String outputBaseDir, ILogger logger) {
        this.outputBaseDir = outputBaseDir;
        this.logger = logger;
    }

    @Override
    public void start(String databaseName, String objectType, String objectNameWithSchema) {
        finish(); // Close any previous writer if open

        String folderName = mapToFolderType(objectType);
        String safeDatabase = sanitizeFileName(databaseName);
        String safeObjectName = sanitizeObjectName(objectNameWithSchema);

        File dbDir = new File(outputBaseDir, safeDatabase);
        File typeDir = new File(dbDir, folderName);

        if (!typeDir.exists()) {
            boolean created = typeDir.mkdirs();
            if (!created && !typeDir.exists()) {
                logger.log("Warning: Could not create directory: " + typeDir.getAbsolutePath());
            }
        }

        currentFile = new File(typeDir, safeObjectName + ".sql");
        try {
            currentWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(currentFile, false), StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.logError("Failed to create file writer for " + currentFile.getAbsolutePath(), e);
        }
    }

    @Override
    public void writeLine(String text) {
        if (currentWriter != null) {
            try {
                currentWriter.write(text);
                currentWriter.newLine();
            } catch (IOException e) {
                logger.logError("Failed to write line to file: " + (currentFile != null ? currentFile.getAbsolutePath() : "unknown"), e);
            }
        }
    }

    @Override
    public void finish() {
        if (currentWriter != null) {
            try {
                currentWriter.flush();
                currentWriter.close();
            } catch (IOException e) {
                logger.logError("Failed to close file writer", e);
            } finally {
                currentWriter = null;
                currentFile = null;
            }
        }
    }

    private String mapToFolderType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "OTHER";
        }
        String upper = rawType.trim().toUpperCase();

        if (upper.equals("SCHEMA")) return "SCHEMA";
        if (upper.contains("TABLE") && !upper.contains("ATTACH")) return "TABLE";
        if (upper.contains("VIEW")) return "VIEW";
        if (upper.contains("PROCEDURE")) return "STORED_PROCEDURE";
        if (upper.contains("FUNCTION")) return "FUNCTION";
        if (upper.contains("INDEX")) return "INDEX";
        if (upper.contains("SEQUENCE")) return "SEQUENCE";
        if (upper.contains("TYPE") || upper.contains("DOMAIN")) return "TYPE";
        if (upper.contains("CONSTRAINT") || upper.contains("FK") || upper.contains("PK")) return "CONSTRAINT";
        if (upper.contains("TRIGGER")) return "TRIGGER";

        return upper.replaceAll("[^A-Z0-9_]", "_");
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) return "Unknown";
        return name.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }

    private String sanitizeObjectName(String nameWithSchema) {
        if (nameWithSchema == null || nameWithSchema.isBlank()) return "unnamed";
        // Convert schema.name -> schema_name
        return nameWithSchema.replaceAll("[\\\\/:*?\"<>|\\s\\.]", "_");
    }
}

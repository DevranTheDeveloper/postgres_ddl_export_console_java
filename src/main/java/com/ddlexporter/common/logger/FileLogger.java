package com.ddlexporter.common.logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileLogger implements ILogger {
    private final String logFilePath;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public FileLogger(String logFilePath) {
        this.logFilePath = logFilePath != null && !logFilePath.isBlank() ? logFilePath : "app.log";
    }

    @Override
    public void log(String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String formattedMessage = "[" + timestamp + "] [INFO] " + message;
        writeToFile(formattedMessage);
    }

    @Override
    public void logError(String message, Throwable throwable) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String formattedMessage = "[" + timestamp + "] [ERROR] " + message
                + (throwable != null ? " -> " + throwable.getMessage() : "");
        writeToFile(formattedMessage);
    }

    private synchronized void writeToFile(String text) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, StandardCharsets.UTF_8, true))) {
            writer.write(text);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Log dosyasına yazılamadı: " + e.getMessage());
        }
    }
}

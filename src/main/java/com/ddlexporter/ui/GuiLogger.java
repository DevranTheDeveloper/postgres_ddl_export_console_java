package com.ddlexporter.ui;

import com.ddlexporter.common.logger.ILogger;

import javax.swing.SwingUtilities;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class GuiLogger implements ILogger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Consumer<String> logConsumer;

    public GuiLogger(Consumer<String> logConsumer) {
        this.logConsumer = logConsumer;
    }

    @Override
    public void log(String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String formatted = "[" + timestamp + "] [INFO] " + message;
        System.out.println(formatted);
        if (logConsumer != null) {
            SwingUtilities.invokeLater(() -> logConsumer.accept(formatted));
        }
    }

    @Override
    public void logError(String message, Throwable throwable) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String formatted = "[" + timestamp + "] [ERROR] " + message + (throwable != null ? " -> " + throwable.getMessage() : "");
        System.err.println(formatted);
        if (logConsumer != null) {
            SwingUtilities.invokeLater(() -> logConsumer.accept(formatted));
        }
    }
}

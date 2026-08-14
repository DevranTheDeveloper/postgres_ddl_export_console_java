package com.ddlexporter.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SystemDiagnosticsManager {
    public enum Level {
        WARN,
        ERROR
    }

    public static class DiagnosticIssue {
        public final String id;
        public final String timestamp;
        public final Level level;
        public final String title;
        public final String source;
        public final String details;
        public final String suggestion;

        public DiagnosticIssue(Level level, String title, String source, String details, String suggestion) {
            this.id = java.util.UUID.randomUUID().toString().substring(0, 8);
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            this.level = level;
            this.title = title;
            this.source = source;
            this.details = details;
            this.suggestion = suggestion;
        }
    }

    private final List<DiagnosticIssue> issues = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public synchronized void addIssue(Level level, String title, String source, String details, String suggestion) {
        issues.add(0, new DiagnosticIssue(level, title, source, details, suggestion));
        if (issues.size() > 50) {
            issues.remove(issues.size() - 1);
        }
        notifyListeners();
    }

    public synchronized List<DiagnosticIssue> getIssues() {
        return Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public synchronized int getErrorCount() {
        return (int) issues.stream().filter(i -> i.level == Level.ERROR).count();
    }

    public synchronized int getWarningCount() {
        return (int) issues.stream().filter(i -> i.level == Level.WARN).count();
    }

    public synchronized void clear() {
        issues.clear();
        notifyListeners();
    }

    public synchronized void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception ignored) {}
        }
    }
}

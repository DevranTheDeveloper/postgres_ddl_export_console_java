package com.ddlexporter.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SystemDiagnosticsManager {
    private static final String STORAGE_FILE = "diagnostics_issues.json";
    private static final int MAX_ISSUES = 100;
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum Level {
        WARN,
        ERROR
    }

    public static class DiagnosticIssue {
        public String id;
        public String timestamp;
        public Level level;
        public String title;
        public String source;
        public String details;
        public String suggestion;
        public boolean resolved = false;
        public String resolvedAt = null;

        public DiagnosticIssue() {}

        public DiagnosticIssue(Level level, String title, String source, String details, String suggestion) {
            this.id = java.util.UUID.randomUUID().toString().substring(0, 8);
            this.timestamp = LocalDateTime.now().format(DTF);
            this.level = level;
            this.title = title;
            this.source = source;
            this.details = details;
            this.suggestion = suggestion;
            this.resolved = false;
            this.resolvedAt = null;
        }
    }

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final List<DiagnosticIssue> issues = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public SystemDiagnosticsManager() {
        loadIssues();
        cleanExpiredResolved(7); // Clean resolved issues older than 7 days automatically
    }

    public synchronized void addIssue(Level level, String title, String source, String details, String suggestion) {
        // Prevent immediate duplicate spam
        for (DiagnosticIssue existing : issues) {
            if (!existing.resolved && existing.title.equalsIgnoreCase(title) && existing.source.equalsIgnoreCase(source)) {
                return;
            }
        }

        issues.add(0, new DiagnosticIssue(level, title, source, details, suggestion));
        if (issues.size() > MAX_ISSUES) {
            issues.remove(issues.size() - 1);
        }
        saveIssues();
        notifyListeners();
    }

    public synchronized void setResolved(String issueId, boolean resolved) {
        for (DiagnosticIssue issue : issues) {
            if (issue.id != null && issue.id.equals(issueId)) {
                issue.resolved = resolved;
                issue.resolvedAt = resolved ? LocalDateTime.now().format(DTF) : null;
                saveIssues();
                notifyListeners();
                break;
            }
        }
    }

    public synchronized List<DiagnosticIssue> getAllIssues() {
        return Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public synchronized List<DiagnosticIssue> getActiveIssues() {
        return issues.stream().filter(i -> !i.resolved).collect(Collectors.toList());
    }

    public synchronized List<DiagnosticIssue> getResolvedIssues() {
        return issues.stream().filter(i -> i.resolved).collect(Collectors.toList());
    }

    public synchronized int getActiveErrorCount() {
        return (int) issues.stream().filter(i -> !i.resolved && i.level == Level.ERROR).count();
    }

    public synchronized int getActiveWarningCount() {
        return (int) issues.stream().filter(i -> !i.resolved && i.level == Level.WARN).count();
    }

    public synchronized void cleanExpiredResolved(int daysRetention) {
        LocalDateTime cutoff = LocalDateTime.now().minus(daysRetention, ChronoUnit.DAYS);
        boolean changed = false;

        List<DiagnosticIssue> toRemove = new ArrayList<>();
        for (DiagnosticIssue issue : issues) {
            if (issue.resolved && issue.resolvedAt != null) {
                try {
                    LocalDateTime resTime = LocalDateTime.parse(issue.resolvedAt, DTF);
                    if (resTime.isBefore(cutoff)) {
                        toRemove.add(issue);
                        changed = true;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (changed) {
            issues.removeAll(toRemove);
            saveIssues();
            notifyListeners();
        }
    }

    public synchronized void clearResolved() {
        issues.removeIf(i -> i.resolved);
        saveIssues();
        notifyListeners();
    }

    public synchronized void clearAll() {
        issues.clear();
        saveIssues();
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

    private void loadIssues() {
        File file = com.ddlexporter.common.util.AppPathHelper.getConfigFile(STORAGE_FILE);
        if (!file.exists()) return;
        try {
            List<DiagnosticIssue> loaded = mapper.readValue(file, new TypeReference<List<DiagnosticIssue>>() {});
            if (loaded != null) {
                issues.clear();
                issues.addAll(loaded);
            }
        } catch (Exception ignored) {}
    }

    private void saveIssues() {
        try {
            File file = com.ddlexporter.common.util.AppPathHelper.getConfigFile(STORAGE_FILE);
            mapper.writeValue(file, issues);
        } catch (Exception ignored) {}
    }
}

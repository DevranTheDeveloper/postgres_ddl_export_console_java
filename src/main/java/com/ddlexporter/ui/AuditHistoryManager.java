package com.ddlexporter.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuditHistoryManager {
    private static final String HISTORY_FILE = "audit_history.json";
    private static final int MAX_ENTRIES = 100;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final List<AuditEntry> entries = new ArrayList<>();

    public AuditHistoryManager() {
        loadHistory();
    }

    public synchronized void logAction(String action, String dbUser, String database, String details, long durationMs, boolean success) {
        String osUser = System.getProperty("user.name", "unknown");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String time = LocalDateTime.now().format(dtf);

        String fullUser = dbUser + " (" + osUser + ")";
        AuditEntry entry = new AuditEntry(time, fullUser, database, action, details, durationMs + " ms", success ? "BAŞARILI" : "HATA");

        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        saveHistory();
    }

    public synchronized List<AuditEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized void clearHistory() {
        entries.clear();
        saveHistory();
    }

    private void loadHistory() {
        File file = new File(HISTORY_FILE);
        if (!file.exists()) return;
        try {
            List<AuditEntry> loaded = mapper.readValue(file, new TypeReference<List<AuditEntry>>() {});
            if (loaded != null) {
                entries.clear();
                entries.addAll(loaded);
            }
        } catch (Exception ignored) {}
    }

    private void saveHistory() {
        try {
            mapper.writeValue(new File(HISTORY_FILE), entries);
        } catch (Exception ignored) {}
    }

    public static class AuditEntry {
        public String timestamp;
        public String user;
        public String database;
        public String action;
        public String details;
        public String duration;
        public String status;

        public AuditEntry() {}

        public AuditEntry(String timestamp, String user, String database, String action, String details, String duration, String status) {
            this.timestamp = timestamp;
            this.user = user;
            this.database = database;
            this.action = action;
            this.details = details;
            this.duration = duration;
            this.status = status;
        }
    }
}

package com.ddlexporter.schedule;

import com.ddlexporter.common.config.IConfigurationReader;
import com.ddlexporter.common.logger.ILogger;
import com.ddlexporter.common.scripter.IScripter;
import com.ddlexporter.common.scripter.ScripterBuilder;
import com.ddlexporter.common.writer.FileWriter;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;
import com.ddlexporter.ui.AuditHistoryManager;
import com.ddlexporter.ui.ProfileManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ScheduledBackupManager {
    private static final String CONFIG_FILE = "scheduled_backup_config.json";
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static class ScheduleConfig {
        public boolean enabled = false;
        public int intervalMinutes = 60; // Default 1 hour
        public String targetProfile = "";
        public boolean autoGitCommit = true;
        public String lastRunTime = "Henüz çalıştırılmadı";
        public String lastStatus = "BEKLEMEDE";
        public int totalRuns = 0;
    }

    private ScheduleConfig config = new ScheduleConfig();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> currentTask = null;

    private final ProfileManager profileManager;
    private final AuditHistoryManager auditManager;
    private Consumer<String> loggerCallback = null;
    private Runnable onBackupCompletedCallback = null;

    public ScheduledBackupManager(ProfileManager profileManager, AuditHistoryManager auditManager) {
        this.profileManager = profileManager;
        this.auditManager = auditManager;
        loadConfig();
    }

    public synchronized void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try {
                ScheduleConfig loaded = mapper.readValue(file, ScheduleConfig.class);
                if (loaded != null) {
                    this.config = loaded;
                }
            } catch (Exception ignored) {}
        }
    }

    public synchronized void saveConfig() {
        try {
            mapper.writeValue(new File(CONFIG_FILE), config);
        } catch (Exception ignored) {}
    }

    public ScheduleConfig getConfig() {
        return config;
    }

    public void setLoggerCallback(Consumer<String> callback) {
        this.loggerCallback = callback;
    }

    public void setOnBackupCompletedCallback(Runnable callback) {
        this.onBackupCompletedCallback = callback;
    }

    public synchronized void updateSchedule(boolean enabled, int intervalMinutes, String targetProfile, boolean autoGitCommit) {
        config.enabled = enabled;
        config.intervalMinutes = Math.max(1, intervalMinutes);
        config.targetProfile = targetProfile;
        config.autoGitCommit = autoGitCommit;
        saveConfig();

        restartScheduler();
    }

    public synchronized void restartScheduler() {
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
        }

        if (config.enabled && config.intervalMinutes > 0) {
            currentTask = scheduler.scheduleAtFixedRate(
                    this::runScheduledBackup,
                    config.intervalMinutes,
                    config.intervalMinutes,
                    TimeUnit.MINUTES
            );
            log("[ZAMANLAYICI] Otomatik DDL yedekleme başlatıldı (Her " + config.intervalMinutes + " dakikada bir).");
        } else {
            log("[ZAMANLAYICI] Otomatik DDL yedekleme devre dışı bırakıldı.");
        }
    }

    public void runNow() {
        scheduler.execute(this::runScheduledBackup);
    }

    private void runScheduledBackup() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String nowStr = LocalDateTime.now().format(dtf);

        String profileName = config.targetProfile;
        if (profileName == null || profileName.isBlank()) {
            var profiles = profileManager.getProfiles();
            if (!profiles.isEmpty()) {
                profileName = profiles.keySet().iterator().next();
            }
        }

        if (profileName == null || profileName.isBlank()) {
            log("[ZAMANLAYICI HATA] Yedekleme için geçerli bir profil bulunamadı.");
            return;
        }

        PostgresqlConfigurationSettings settings = profileManager.getProfile(profileName);
        if (settings == null) {
            log("[ZAMANLAYICI HATA] '" + profileName + "' profil ayarları okunamadı.");
            return;
        }

        String outputDir = "database_schemas";
        log("[ZAMANLAYICI] Otomatik DDL dışa aktarımı başladı (Profil: " + profileName + ")...");

        long startTime = System.currentTimeMillis();
        try {
            ILogger taskLogger = new ILogger() {
                @Override public void log(String message) { ScheduledBackupManager.this.log(message); }
                @Override public void logError(String message, Throwable throwable) {
                    ScheduledBackupManager.this.log("[HATA] " + message + (throwable != null ? ": " + throwable.getMessage() : ""));
                }
            };

            IConfigurationReader memoryReader = new IConfigurationReader() {
                @Override
                public <T> T read(Class<T> clazz) {
                    return clazz.cast(settings);
                }
            };

            IScripter scripter = ScripterBuilder.get("POSTGRESQL")
                    .addConfigurationReader(memoryReader)
                    .addWriter(new FileWriter(outputDir, taskLogger))
                    .addLogger(taskLogger)
                    .build();

            scripter.execute();

            long duration = System.currentTimeMillis() - startTime;
            config.lastRunTime = nowStr;
            config.lastStatus = "BAŞARILI (" + duration + " ms)";
            config.totalRuns++;
            saveConfig();

            auditManager.logAction("Otomatik DDL Yedekleme (Cron)", settings.getUsername(),
                    settings.getDatabaseName() + "/" + settings.getSchema(),
                    "Zamanlanmış otomatik DDL yedeği alındı", duration, true);

            log("[ZAMANLAYICI] Otomatik DDL yedekleme başarıyla tamamlandı (" + duration + " ms).");

            if (onBackupCompletedCallback != null) {
                onBackupCompletedCallback.run();
            }

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            config.lastRunTime = nowStr;
            config.lastStatus = "HATA: " + ex.getMessage();
            saveConfig();

            auditManager.logAction("Otomatik DDL Yedekleme (Cron)", settings.getUsername(),
                    settings.getDatabaseName() + "/" + settings.getSchema(),
                    "Hata: " + ex.getMessage(), duration, false);

            log("[ZAMANLAYICI HATA] Otomatik yedekleme başarısız: " + ex.getMessage());
        }
    }

    private void log(String msg) {
        if (loggerCallback != null) {
            loggerCallback.accept(msg);
        }
    }
}

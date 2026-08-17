package com.ddlexporter.ui;

import com.ddlexporter.common.util.CryptoUtils;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileManager {
    private static final String PROFILES_FILE = "profiles.json";
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Map<String, PostgresqlConfigurationSettings> profiles = new LinkedHashMap<>();

    public ProfileManager() {
        loadProfiles();
    }

    public synchronized void loadProfiles() {
        profiles.clear();
        File file = new File(PROFILES_FILE);
        if (file.exists()) {
            try {
                Map<String, PostgresqlConfigurationSettings> loaded = mapper.readValue(file,
                        new TypeReference<LinkedHashMap<String, PostgresqlConfigurationSettings>>() {});
                if (loaded != null) {
                    for (Map.Entry<String, PostgresqlConfigurationSettings> entry : loaded.entrySet()) {
                        PostgresqlConfigurationSettings s = entry.getValue();
                        if (s != null && s.getPassword() != null) {
                            // Decrypt password if it was encrypted with ENC(...)
                            s.setPassword(CryptoUtils.decrypt(s.getPassword()));
                        }
                        profiles.put(entry.getKey(), s);
                    }
                }
            } catch (Exception e) {
                System.err.println("Profiller okunamadı: " + e.getMessage());
            }
        }

        // Add default profile if empty
        if (profiles.isEmpty()) {
            PostgresqlConfigurationSettings defaultSetting = new PostgresqlConfigurationSettings();
            defaultSetting.setServerHost("localhost");
            defaultSetting.setPort(5432);
            defaultSetting.setDatabaseName("denemeDatabase");
            defaultSetting.setUsername("postgres");
            defaultSetting.setPassword("12345");
            defaultSetting.setSchema("public");
            profiles.put("Local Docker (5432)", defaultSetting);
            saveProfiles();
        }
    }

    public synchronized void saveProfiles() {
        try {
            // Prepare a secure copy with encrypted passwords for disk storage
            Map<String, PostgresqlConfigurationSettings> secureCopy = new LinkedHashMap<>();
            for (Map.Entry<String, PostgresqlConfigurationSettings> entry : profiles.entrySet()) {
                PostgresqlConfigurationSettings original = entry.getValue();
                if (original != null) {
                    PostgresqlConfigurationSettings copy = copySettings(original);
                    if (copy.getPassword() != null && !copy.getPassword().isBlank()) {
                        copy.setPassword(CryptoUtils.encrypt(copy.getPassword()));
                    }
                    secureCopy.put(entry.getKey(), copy);
                }
            }
            mapper.writeValue(new File(PROFILES_FILE), secureCopy);
        } catch (Exception e) {
            System.err.println("Profiller kaydedilemedi: " + e.getMessage());
        }
    }

    private PostgresqlConfigurationSettings copySettings(PostgresqlConfigurationSettings src) {
        PostgresqlConfigurationSettings dest = new PostgresqlConfigurationSettings();
        dest.setServerHost(src.getServerHost());
        dest.setPort(src.getPort());
        dest.setDatabaseName(src.getDatabaseName());
        dest.setUsername(src.getUsername());
        dest.setPassword(src.getPassword());
        dest.setSchema(src.getSchema());
        dest.setPgDumpPath(src.getPgDumpPath());
        dest.setPgRestorePath(src.getPgRestorePath());
        return dest;
    }

    public synchronized Map<String, PostgresqlConfigurationSettings> getProfiles() {
        return new LinkedHashMap<>(profiles);
    }

    public synchronized void addOrUpdateProfile(String name, PostgresqlConfigurationSettings settings) {
        if (name != null && !name.isBlank() && settings != null) {
            profiles.put(name.trim(), settings);
            saveProfiles();
        }
    }

    public synchronized void deleteProfile(String name) {
        if (name != null && profiles.containsKey(name)) {
            profiles.remove(name);
            saveProfiles();
        }
    }

    public synchronized PostgresqlConfigurationSettings getProfile(String name) {
        return profiles.get(name);
    }
}

package com.ddlexporter.common.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Manages configuration and data file paths across operating systems (macOS, Windows, Linux).
 * Supports both Portable mode and Installed mode (e.g. C:\Program Files\PostgreSQL DDL Studio).
 */
public class AppPathHelper {
    private static final String APP_DIR_NAME = ".postgres-ddl-studio";

    /**
     * Resolves a configuration or data file.
     * Ensures the returned file path is always writable by the current user.
     */
    public static File getConfigFile(String fileName) {
        // 1. Check if current directory has a writable version (Portable mode)
        File localFile = new File(fileName);
        if (localFile.exists() && localFile.canWrite()) {
            return localFile;
        }

        // Test if current working directory allows creating new files
        if (!localFile.exists()) {
            try {
                File testFile = new File(".test_write_" + System.currentTimeMillis());
                if (testFile.createNewFile()) {
                    testFile.delete();
                    return localFile;
                }
            } catch (Throwable ignored) {
                // Directory is write-protected (e.g. C:\Program Files\...)
            }
        }

        // 2. Resolve safe User Directory (%APPDATA% on Windows, ~/.postgres-ddl-studio on macOS/Linux)
        File userDir = getUserDataDirectory();
        File userFile = new File(userDir, fileName);

        // If a read-only template exists in installation dir but not in userDir, copy it over
        if (!userFile.exists() && localFile.exists()) {
            try {
                Files.copy(localFile.toPath(), userFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable ignored) {}
        }

        return userFile;
    }

    /**
     * Returns the dedicated, user-writable application data directory.
     */
    public static File getUserDataDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase();
        File baseDir;

        String appData = System.getenv("APPDATA");
        if (os.contains("win") && appData != null && !appData.isBlank()) {
            baseDir = new File(appData, "PostgreSQL-DDL-Studio");
        } else {
            String userHome = System.getProperty("user.home", ".");
            baseDir = new File(userHome, APP_DIR_NAME);
        }

        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return baseDir;
    }
}

package com.ddlexporter.common.util;

import java.io.File;

/**
 * Manages configuration and data file paths across operating systems (macOS, Windows, Linux).
 * Supports both Portable mode and Installed mode (e.g. C:\Program Files\PostgreSQL DDL Studio).
 */
public class AppPathHelper {
    private static final String APP_DIR_NAME = ".postgres-ddl-studio";

    public static File getConfigFile(String fileName) {
        // 1. If local file exists in current directory, prioritize it
        File localFile = new File(fileName);
        if (localFile.exists()) {
            return localFile;
        }

        // 2. Test if current directory is writable
        try {
            File testFile = new File(".test_write_" + System.currentTimeMillis());
            if (testFile.createNewFile()) {
                testFile.delete();
                return localFile;
            }
        } catch (Exception ignored) {
            // Current directory is read-only (e.g. C:\Program Files\...)
        }

        // 3. Fallback to User Home application directory (~/.postgres-ddl-studio/ or %APPDATA%/...)
        String userHome = System.getProperty("user.home", ".");
        File appDir = new File(userHome, APP_DIR_NAME);
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        return new File(appDir, fileName);
    }
}

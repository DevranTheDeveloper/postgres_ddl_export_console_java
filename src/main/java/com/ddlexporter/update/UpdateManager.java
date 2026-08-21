package com.ddlexporter.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BiConsumer;

public class UpdateManager {
    public static final String CURRENT_VERSION = "5.5.2";
    private static final String GITHUB_REPO = "DevranTheDeveloper/postgres_ddl_export_console_java";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public static class ReleaseInfo {
        public String tagName;
        public String name;
        public String body;
        public String publishedAt;
        public String jarDownloadUrl;
        public String exeDownloadUrl;
        public String dmgDownloadUrl;
        public String htmlUrl;
        public boolean updateAvailable;
    }

    public UpdateManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * Checks GitHub Releases API for the latest version with resilient fallbacks.
     */
    public ReleaseInfo checkLatestRelease() throws Exception {
        String jsonBody = null;

        // 1. Primary Check: Java 11+ HttpClient
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Mozilla/5.0 (compatible; PostgreSQL-DDL-Studio-Updater/" + CURRENT_VERSION + ")")
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                jsonBody = response.body();
            }
        } catch (Exception ignored) {}

        // 2. Secondary Fallback Check: Direct HttpURLConnection with standard TLS
        if (jsonBody == null || jsonBody.isBlank()) {
            URL url = new URI(GITHUB_API_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; PostgreSQL-DDL-Studio-Updater/" + CURRENT_VERSION + ")");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);

            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream in = conn.getInputStream()) {
                    jsonBody = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            } else {
                throw new RuntimeException("GitHub API Baglanti Hatasi (HTTP " + code + ")");
            }
        }

        JsonNode root = mapper.readTree(jsonBody);
        ReleaseInfo info = new ReleaseInfo();
        info.tagName = root.hasNonNull("tag_name") ? root.get("tag_name").asText().trim() : "";
        info.name = root.hasNonNull("name") ? root.get("name").asText() : info.tagName;
        info.body = root.hasNonNull("body") ? root.get("body").asText() : "Surum notlari mevcut degil.";
        info.publishedAt = root.hasNonNull("published_at") ? root.get("published_at").asText() : "";
        info.htmlUrl = root.hasNonNull("html_url") ? root.get("html_url").asText() : "https://github.com/" + GITHUB_REPO + "/releases/latest";

        String remoteVer = info.tagName.replaceAll("[^0-9.]", "");
        info.updateAvailable = isNewerVersion(CURRENT_VERSION, remoteVer);

        JsonNode assets = root.path("assets");
        if (assets.isArray()) {
            for (JsonNode asset : assets) {
                String name = asset.hasNonNull("name") ? asset.get("name").asText().toLowerCase() : "";
                String downloadUrl = asset.hasNonNull("browser_download_url") ? asset.get("browser_download_url").asText() : "";

                if (name.endsWith(".jar")) {
                    info.jarDownloadUrl = downloadUrl;
                } else if (name.endsWith(".exe")) {
                    info.exeDownloadUrl = downloadUrl;
                } else if (name.endsWith(".dmg")) {
                    info.dmgDownloadUrl = downloadUrl;
                }
            }
        }

        // Guaranteed fallback URL for JAR download
        if (info.jarDownloadUrl == null || info.jarDownloadUrl.isBlank()) {
            info.jarDownloadUrl = "https://github.com/" + GITHUB_REPO + "/releases/download/" + info.tagName + "/postgres_ddl_export_console_java-1.0.0.jar";
        }

        return info;
    }

    /**
     * Compares semantic versions (e.g. 5.5.0 vs 5.5.1).
     */
    public static boolean isNewerVersion(String currentVer, String remoteVer) {
        if (remoteVer == null || remoteVer.isBlank()) return false;
        try {
            String[] currentParts = currentVer.replaceAll("[^0-9.]", "").split("\\.");
            String[] remoteParts = remoteVer.replaceAll("[^0-9.]", "").split("\\.");

            int length = Math.max(currentParts.length, remoteParts.length);
            for (int i = 0; i < length; i++) {
                int c = (i < currentParts.length && !currentParts[i].isBlank()) ? Integer.parseInt(currentParts[i]) : 0;
                int r = (i < remoteParts.length && !remoteParts[i].isBlank()) ? Integer.parseInt(remoteParts[i]) : 0;
                if (r > c) return true;
                if (r < c) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Downloads the updated JAR file with robust multi-redirect following and progress reporting.
     */
    public void downloadUpdate(String downloadUrl, File targetFile, BiConsumer<Long, Long> progressCallback) throws Exception {
        String targetUrl = downloadUrl;
        HttpURLConnection conn = null;
        int redirectCount = 0;

        while (redirectCount < 8) {
            URL url = new URI(targetUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; PostgreSQL-DDL-Studio-Updater/" + CURRENT_VERSION + ")");
            conn.setRequestProperty("Accept", "*/*");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(45000);

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 307 || responseCode == 308 || responseCode == 303) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null && !newUrl.isBlank()) {
                    if (!newUrl.startsWith("http")) {
                        newUrl = new URI(targetUrl).resolve(newUrl).toString();
                    }
                    targetUrl = newUrl;
                    redirectCount++;
                    conn.disconnect();
                    continue;
                }
            }

            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != 206) {
                throw new RuntimeException("Sunucu Yanit Vermedi (HTTP " + responseCode + ")");
            }
            break;
        }

        if (conn == null) {
            throw new RuntimeException("Baglanti kurulamadi.");
        }

        long contentLength = conn.getContentLengthLong();
        if (contentLength <= 0) {
            contentLength = 9 * 1024 * 1024; // Default estimate ~9MB
        }

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(targetFile)) {

            byte[] buffer = new byte[16384];
            long totalRead = 0;
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (progressCallback != null) {
                    progressCallback.accept(totalRead, contentLength);
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Performs atomic in-place restart across Windows, macOS, and Linux.
     */
    public static void applyUpdateAndRestart(File downloadedFile) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        File runningJar = getRunningJarFile();

        if (runningJar == null || !runningJar.getName().endsWith(".jar")) {
            runningJar = new File("target/postgres_ddl_export_console_java-1.0.0.jar");
        }

        File appDir = runningJar.getParentFile() != null ? runningJar.getParentFile() : new File(".");

        if (os.contains("mac")) {
            // Find macOS .app bundle directory if running inside an app bundle
            File appBundle = null;
            File current = runningJar;
            while (current != null) {
                if (current.getName().endsWith(".app")) {
                    appBundle = current;
                    break;
                }
                current = current.getParentFile();
            }

            File updaterSh = File.createTempFile("pg_ddl_mac_updater", ".sh");
            long currentPid = ProcessHandle.current().pid();

            StringBuilder sh = new StringBuilder();
            sh.append("#!/bin/bash\n");
            sh.append("while kill -0 ").append(currentPid).append(" 2>/dev/null; do sleep 0.15; done\n");
            sh.append("cp -f \"").append(downloadedFile.getAbsolutePath()).append("\" \"").append(runningJar.getAbsolutePath()).append("\"\n");
            sh.append("chmod 755 \"").append(runningJar.getAbsolutePath()).append("\"\n");
            if (appBundle != null) {
                sh.append("open -n \"").append(appBundle.getAbsolutePath()).append("\"\n");
            } else {
                sh.append("java -jar \"").append(runningJar.getAbsolutePath()).append("\" &\n");
            }
            sh.append("rm -f \"$0\"\n");
            sh.append("exit 0\n");

            java.nio.file.Files.writeString(updaterSh.toPath(), sh.toString());
            updaterSh.setExecutable(true);

            new ProcessBuilder("/bin/bash", updaterSh.getAbsolutePath()).start();
            System.exit(0);

        } else if (os.contains("win")) {
            // Windows detached updater batch script with retry loop and ping delay
            File updaterBat = File.createTempFile("pg_ddl_win_updater", ".bat");
            File stdJar = new File(appDir, "PostgreSQL-DDL-Studio.jar");
            File winBatScript = new File(appDir, "PostgreSQL-DDL-Studio.bat");

            StringBuilder bat = new StringBuilder();
            bat.append("@echo off\r\n");
            bat.append("setlocal\r\n");
            bat.append("set \"SRC=").append(downloadedFile.getAbsolutePath()).append("\"\r\n");
            bat.append("set \"DST=").append(runningJar.getAbsolutePath()).append("\"\r\n");
            bat.append("set \"STD=").append(stdJar.getAbsolutePath()).append("\"\r\n");
            bat.append("set \"BAT=").append(winBatScript.getAbsolutePath()).append("\"\r\n");
            bat.append("\r\n");
            bat.append("REM Wait for Java process to exit and release file handle\r\n");
            bat.append("for /L %%i in (1,1,20) do (\r\n");
            bat.append("    ping 127.0.0.1 -n 2 >nul\r\n");
            bat.append("    copy /y \"%SRC%\" \"%DST%\" >nul 2>nul\r\n");
            bat.append("    if not errorlevel 1 (\r\n");
            bat.append("        copy /y \"%SRC%\" \"%STD%\" >nul 2>nul\r\n");
            bat.append("        goto :LAUNCH\r\n");
            bat.append("    )\r\n");
            bat.append(")\r\n");
            bat.append("\r\n");
            bat.append(":LAUNCH\r\n");
            bat.append("del /f /q \"%SRC%\" 2>nul\r\n");
            bat.append("if exist \"%BAT%\" (\r\n");
            bat.append("    start \"\" \"%BAT%\"\r\n");
            bat.append(") else (\r\n");
            bat.append("    start \"\" javaw -jar \"%DST%\"\r\n");
            bat.append(")\r\n");
            bat.append("del /f /q \"%~f0\" 2>nul\r\n");
            bat.append("exit\r\n");

            java.nio.file.Files.writeString(updaterBat.toPath(), bat.toString());

            new ProcessBuilder("cmd.exe", "/c", updaterBat.getAbsolutePath()).start();
            System.exit(0);

        } else {
            // Linux detached POSIX updater
            File updaterSh = File.createTempFile("pg_ddl_linux_updater", ".sh");
            long currentPid = ProcessHandle.current().pid();

            StringBuilder sh = new StringBuilder();
            sh.append("#!/bin/bash\n");
            sh.append("while kill -0 ").append(currentPid).append(" 2>/dev/null; do sleep 0.15; done\n");
            sh.append("cp -f \"").append(downloadedFile.getAbsolutePath()).append("\" \"").append(runningJar.getAbsolutePath()).append("\"\n");
            sh.append("chmod 755 \"").append(runningJar.getAbsolutePath()).append("\"\n");
            File linuxRunScript = new File(appDir, "run.sh");
            if (linuxRunScript.exists()) {
                sh.append("chmod +x \"").append(linuxRunScript.getAbsolutePath()).append("\"\n");
                sh.append("\"").append(linuxRunScript.getAbsolutePath()).append("\" &\n");
            } else {
                sh.append("java -jar \"").append(runningJar.getAbsolutePath()).append("\" &\n");
            }
            sh.append("rm -f \"$0\"\n");
            sh.append("exit 0\n");

            java.nio.file.Files.writeString(updaterSh.toPath(), sh.toString());
            updaterSh.setExecutable(true);

            new ProcessBuilder("/bin/bash", updaterSh.getAbsolutePath()).start();
            System.exit(0);
        }
    }

    private static File getRunningJarFile() {
        try {
            return new File(UpdateManager.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (Exception ignored) {
            return null;
        }
    }
}

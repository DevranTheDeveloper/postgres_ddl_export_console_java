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
    public static final String CURRENT_VERSION = "5.4.0";
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
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * Checks GitHub Releases API for the latest version.
     */
    public ReleaseInfo checkLatestRelease() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API_URL))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "PostgreSQL-DDL-Studio/" + CURRENT_VERSION)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("GitHub API Yanıt Vermedi (HTTP " + response.statusCode() + ")");
        }

        JsonNode root = mapper.readTree(response.body());
        ReleaseInfo info = new ReleaseInfo();
        info.tagName = root.hasNonNull("tag_name") ? root.get("tag_name").asText().trim() : "";
        info.name = root.hasNonNull("name") ? root.get("name").asText() : info.tagName;
        info.body = root.hasNonNull("body") ? root.get("body").asText() : "Sürüm notları mevcut değil.";
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

        return info;
    }

    /**
     * Compares semantic versions (e.g. 5.4.0 vs 5.5.0).
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
     * Downloads the updated JAR file with live progress reporting.
     * @param downloadUrl The direct asset URL
     * @param targetFile Destination file
     * @param progressCallback Callback with (downloadedBytes, totalBytes)
     */
    public void downloadUpdate(String downloadUrl, File targetFile, BiConsumer<Long, Long> progressCallback) throws Exception {
        URL url = new URI(downloadUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "PostgreSQL-DDL-Studio/" + CURRENT_VERSION);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        int responseCode = conn.getResponseCode();
        // Follow redirects
        if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 307 || responseCode == 308) {
            String newUrl = conn.getHeaderField("Location");
            conn = (HttpURLConnection) new URI(newUrl).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "PostgreSQL-DDL-Studio/" + CURRENT_VERSION);
        }

        long contentLength = conn.getContentLengthLong();
        if (contentLength <= 0) {
            contentLength = 10 * 1024 * 1024; // Default estimate 10MB
        }

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(targetFile)) {

            byte[] buffer = new byte[8192];
            long totalRead = 0;
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (progressCallback != null) {
                    progressCallback.accept(totalRead, contentLength);
                }
            }
        }
    }

    /**
     * Performs atomic in-place restart across Windows, macOS, and Linux.
     */
    public static void applyUpdateAndRestart(File downloadedFile) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        File runningJar = getRunningJarFile();

        if (runningJar == null || !runningJar.getName().endsWith(".jar")) {
            // If running in development / IDE, replace target jar
            runningJar = new File("target/postgres_ddl_export_console_java-1.0.0.jar");
        }

        File appDir = runningJar.getParentFile() != null ? runningJar.getParentFile() : new File(".");

        if (os.contains("win")) {
            // Windows atomic updater script with file unlock delay and auto-launcher
            File updaterBat = new File(appDir, "update_runner.bat");
            String batContent = "@echo off\r\n" +
                    "ping 127.0.0.1 -n 2 >nul\r\n" +
                    "move /y \"" + downloadedFile.getAbsolutePath() + "\" \"" + runningJar.getAbsolutePath() + "\"\r\n" +
                    "if exist \"" + new File(appDir, "PostgreSQL-DDL-Studio.bat").getAbsolutePath() + "\" (\r\n" +
                    "    start \"\" \"" + new File(appDir, "PostgreSQL-DDL-Studio.bat").getAbsolutePath() + "\"\r\n" +
                    ") else (\r\n" +
                    "    start \"\" javaw -jar \"" + runningJar.getAbsolutePath() + "\"\r\n" +
                    ")\r\n" +
                    "del \"%~f0\"\r\n" +
                    "exit\r\n";
            java.nio.file.Files.writeString(updaterBat.toPath(), batContent);

            new ProcessBuilder("cmd.exe", "/c", updaterBat.getAbsolutePath())
                    .directory(appDir)
                    .start();
        } else {
            // Linux & macOS atomic inode replacement and relaunch
            java.nio.file.Files.move(downloadedFile.toPath(), runningJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            File linuxRunScript = new File(appDir, "run.sh");
            if (linuxRunScript.exists() && linuxRunScript.canExecute()) {
                new ProcessBuilder(linuxRunScript.getAbsolutePath())
                        .directory(appDir)
                        .start();
            } else {
                new ProcessBuilder("java", "-jar", runningJar.getAbsolutePath())
                        .directory(appDir)
                        .start();
            }
        }

        System.exit(0);
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

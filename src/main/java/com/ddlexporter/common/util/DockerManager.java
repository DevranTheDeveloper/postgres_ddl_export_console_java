package com.ddlexporter.common.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DockerManager {

    private static String cachedDockerPath = null;

    public static class DockerContainerInfo {
        public final String id;
        public final String name;
        public final String status;
        public final String ports;
        public final String image;
        public final boolean isRunning;

        public DockerContainerInfo(String id, String name, String status, String ports, String image, boolean isRunning) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.ports = ports;
            this.image = image;
            this.isRunning = isRunning;
        }

        @Override
        public String toString() {
            String state = isRunning ? "Aktif" : "Durduruldu";
            String portInfo = "5432";
            if (ports != null && ports.contains("->")) {
                try {
                    String pStr = ports.split("->")[0];
                    if (pStr.contains(":")) {
                        portInfo = pStr.substring(pStr.lastIndexOf(":") + 1).trim();
                    }
                } catch (Exception ignored) {}
            }
            return name + " (" + state + " • Port " + portInfo + ")";
        }
    }

    /**
     * Resolves the full path to the docker CLI across macOS, Linux, and Windows.
     */
    public static synchronized String getDockerExecutable() {
        if (cachedDockerPath != null) {
            return cachedDockerPath;
        }

        String userHome = System.getProperty("user.home", "");
        String os = System.getProperty("os.name", "").toLowerCase();

        List<String> candidates = new ArrayList<>();

        if (os.contains("mac")) {
            candidates.add("/usr/local/bin/docker");
            candidates.add("/opt/homebrew/bin/docker");
            candidates.add("/Applications/Docker.app/Contents/Resources/bin/docker");
            candidates.add(userHome + "/.docker/bin/docker");
            candidates.add(userHome + "/.orbstack/bin/docker");
            candidates.add("/usr/bin/docker");
        } else if (os.contains("win")) {
            candidates.add("C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe");
            candidates.add("C:\\Program Files\\Docker\\Docker\\resources\\docker.exe");
            candidates.add(userHome + "\\.docker\\bin\\docker.exe");
            candidates.add("docker.exe");
        } else {
            candidates.add("/usr/bin/docker");
            candidates.add("/usr/local/bin/docker");
            candidates.add("/snap/bin/docker");
            candidates.add(userHome + "/.docker/bin/docker");
        }

        candidates.add("docker");

        for (String candidate : candidates) {
            try {
                File f = new File(candidate);
                if (f.exists() && f.canExecute()) {
                    cachedDockerPath = candidate;
                    return candidate;
                }
            } catch (Exception ignored) {}
        }

        cachedDockerPath = "docker";
        return cachedDockerPath;
    }

    /**
     * Creates a ProcessBuilder configured with system paths and Docker socket discovery.
     */
    public static ProcessBuilder createProcessBuilder(String... args) {
        String dockerBin = getDockerExecutable();
        List<String> command = new ArrayList<>();
        command.add(dockerBin);
        for (String arg : args) {
            command.add(arg);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        Map<String, String> env = pb.environment();

        // Ensure rich PATH environment for GUI processes
        String existingPath = env.getOrDefault("PATH", "");
        String userHome = System.getProperty("user.home", "");
        String pathExtensions = "/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:" +
                userHome + "/.docker/bin:" +
                userHome + "/.orbstack/bin:" +
                "/Applications/Docker.app/Contents/Resources/bin";

        env.put("PATH", pathExtensions + (existingPath.isEmpty() ? "" : ":" + existingPath));

        // Check common Unix socket locations if DOCKER_HOST is not set
        if (!env.containsKey("DOCKER_HOST")) {
            String[] commonSockets = {
                    userHome + "/.docker/run/docker.sock",
                    "/var/run/docker.sock",
                    userHome + "/.orbstack/run/docker.sock"
            };
            for (String sock : commonSockets) {
                if (new File(sock).exists()) {
                    env.put("DOCKER_HOST", "unix://" + sock);
                    break;
                }
            }
        }

        return pb;
    }

    public static boolean isDockerAvailable() {
        try {
            Process p = createProcessBuilder("info").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<DockerContainerInfo> listPostgresContainers() {
        List<DockerContainerInfo> list = new ArrayList<>();
        try {
            Process p = createProcessBuilder("ps", "-a", "--format", "{{.ID}}||{{.Names}}||{{.Status}}||{{.Ports}}||{{.Image}}").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|\\|");
                    if (parts.length >= 5) {
                        String id = parts[0].trim();
                        String name = parts[1].trim();
                        String status = parts[2].trim();
                        String ports = parts[3].trim();
                        String image = parts[4].trim();
                        boolean isRunning = status.toLowerCase().startsWith("up");
                        if (image.toLowerCase().contains("postgres") || name.toLowerCase().contains("postgres") || ports.contains("5432")) {
                            list.add(new DockerContainerInfo(id, name, status, ports, image, isRunning));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static boolean startContainer(String containerNameOrId) {
        try {
            Process p = createProcessBuilder("start", containerNameOrId).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean createAndRunDemoContainer(String containerName, int port, String password) {
        try {
            Process p = createProcessBuilder("run", "-d",
                    "--name", containerName,
                    "-e", "POSTGRES_PASSWORD=" + password,
                    "-p", port + ":5432",
                    "postgres:16").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean seedDemoEcommerceSchema(String host, int port, String databaseName, String username, String password) {
        String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName != null ? databaseName : "postgres");
        String sql = """
            CREATE TABLE IF NOT EXISTS public.customers (
                customer_id SERIAL PRIMARY KEY,
                full_name VARCHAR(150) NOT NULL,
                email VARCHAR(200) UNIQUE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS public.products (
                product_id SERIAL PRIMARY KEY,
                product_name VARCHAR(150) NOT NULL,
                sku VARCHAR(50) UNIQUE NOT NULL,
                unit_price NUMERIC(10, 2) NOT NULL,
                stock_quantity INT DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS public.orders (
                order_id SERIAL PRIMARY KEY,
                customer_id INT REFERENCES public.customers(customer_id) ON DELETE CASCADE,
                order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                total_amount NUMERIC(10, 2) NOT NULL,
                status VARCHAR(50) DEFAULT 'PENDING'
            );

            CREATE TABLE IF NOT EXISTS public.order_items (
                item_id SERIAL PRIMARY KEY,
                order_id INT REFERENCES public.orders(order_id) ON DELETE CASCADE,
                product_id INT REFERENCES public.products(product_id) ON DELETE RESTRICT,
                quantity INT DEFAULT 1,
                price NUMERIC(10, 2) NOT NULL
            );

            CREATE OR REPLACE VIEW public.v_customer_order_summary AS
            SELECT c.customer_id, c.full_name, c.email, COUNT(o.order_id) AS total_orders, COALESCE(SUM(o.total_amount), 0) AS total_spent
            FROM public.customers c
            LEFT JOIN public.orders o ON c.customer_id = o.customer_id
            GROUP BY c.customer_id, c.full_name, c.email;
        """;

        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (Exception e) {
            System.err.println("Seed verisi eklenirken hata: " + e.getMessage());
            return false;
        }
    }
}

package com.ddlexporter.common.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DockerManager {

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

    public static boolean isDockerAvailable() {
        try {
            Process p = new ProcessBuilder("docker", "info").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<DockerContainerInfo> listPostgresContainers() {
        List<DockerContainerInfo> list = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("docker", "ps", "-a", "--format", "{{.ID}}||{{.Names}}||{{.Status}}||{{.Ports}}||{{.Image}}").start();
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
            Process p = new ProcessBuilder("docker", "start", containerNameOrId).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean createAndRunDemoContainer(String containerName, int port, String password) {
        try {
            Process p = new ProcessBuilder("docker", "run", "-d",
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
                total_amount NUMERIC(12, 2) NOT NULL,
                status VARCHAR(50) DEFAULT 'PENDING',
                ordered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS public.order_items (
                item_id SERIAL PRIMARY KEY,
                order_id INT REFERENCES public.orders(order_id) ON DELETE CASCADE,
                product_id INT REFERENCES public.products(product_id),
                quantity INT NOT NULL,
                unit_price NUMERIC(10, 2) NOT NULL
            );

            CREATE TABLE IF NOT EXISTS public.payments (
                payment_id SERIAL PRIMARY KEY,
                order_id INT REFERENCES public.orders(order_id) ON DELETE CASCADE,
                payment_method VARCHAR(50) NOT NULL,
                amount NUMERIC(12, 2) NOT NULL,
                paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE OR REPLACE VIEW public.active_customer_orders AS
            SELECT o.order_id, c.full_name, c.email, o.total_amount, o.status, o.ordered_at
            FROM public.orders o
            JOIN public.customers c ON o.customer_id = c.customer_id;
        """;

        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

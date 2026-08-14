package com.ddlexporter.ui;

import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.function.Supplier;

public class ServerStatusPanel extends JPanel {
    private final Supplier<PostgresqlConfigurationSettings> settingsSupplier;

    // Top KPI Labels
    private final JLabel statusValueLabel = new JLabel("Bilinmiyor");
    private final JLabel versionLabel = new JLabel("PostgreSQL --");
    private final JLabel connectionsValueLabel = new JLabel("0 / 0");
    private final JLabel dbSizeValueLabel = new JLabel("0 MB");
    private final JLabel cacheHitValueLabel = new JLabel("%0.0");
    private final JLabel txStatsLabel = new JLabel("Commit: 0 | Rollback: 0");

    // Charts
    private final MetricsChartPanel chartPanel = new MetricsChartPanel();

    // Active Queries Table
    private final DefaultTableModel tableModel;
    private final JTable activityTable;
    private final JCheckBox autoRefreshBox = new JCheckBox("5 saniyede bir otomatik yenile", false);
    private final JButton refreshBtn = new JButton("Yenile");
    private final JButton testModeBtn = new JButton("Canlı Testi Başlat");
    private final JLabel lastUpdateLabel = new JLabel("Son Güncelleme: Henüz yapılmadı");

    private Timer autoRefreshTimer;
    private Timer simulationTimer;
    private boolean isSimulationRunning = false;
    private int simulationStep = 0;
    private boolean isDark = false;

    public ServerStatusPanel(Supplier<PostgresqlConfigurationSettings> settingsSupplier) {
        this.settingsSupplier = settingsSupplier;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        setOpaque(false);

        // 1. Top Section: Header Toolbar + 4 KPI Cards
        JPanel topContainer = new JPanel(new BorderLayout(0, 8));
        topContainer.setOpaque(false);

        // Header Toolbar
        JPanel headerToolbar = new JPanel(new BorderLayout());
        headerToolbar.setOpaque(false);
        JLabel title = new JLabel("PostgreSQL Canlı Sunucu Durumu & Metrikler");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        headerToolbar.add(title, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        controls.add(lastUpdateLabel);
        controls.add(autoRefreshBox);

        testModeBtn.setFont(testModeBtn.getFont().deriveFont(Font.BOLD, 12f));
        testModeBtn.addActionListener(e -> toggleSimulationTest());
        controls.add(testModeBtn);

        refreshBtn.setFont(refreshBtn.getFont().deriveFont(Font.BOLD, 12f));
        refreshBtn.addActionListener(e -> {
            if (isSimulationRunning) stopSimulationTest();
            refreshMetrics();
        });
        controls.add(refreshBtn);
        headerToolbar.add(controls, BorderLayout.EAST);
        topContainer.add(headerToolbar, BorderLayout.NORTH);

        // 4 KPI Cards Grid
        JPanel cardsGrid = new JPanel(new GridLayout(1, 4, 10, 0));
        cardsGrid.setOpaque(false);
        cardsGrid.setPreferredSize(new Dimension(0, 85));

        cardsGrid.add(createKpiCard("Sunucu Durumu", statusValueLabel, versionLabel));
        cardsGrid.add(createKpiCard("Aktif Bağlantılar", connectionsValueLabel, new JLabel("Maksimum Havuz Kapasitesi")));
        cardsGrid.add(createKpiCard("Veritabanı Boyutu", dbSizeValueLabel, new JLabel("Toplam Disk Alanı")));
        cardsGrid.add(createKpiCard("Cache Hit (Önbellek)", cacheHitValueLabel, txStatsLabel));

        topContainer.add(cardsGrid, BorderLayout.CENTER);
        add(topContainer, BorderLayout.NORTH);

        // 2. Center Split: Left Chart (Transactions & Cache) | Right Activity Table (pg_stat_activity)
        JPanel centerContainer = new JPanel(new GridLayout(1, 2, 12, 0));
        centerContainer.setOpaque(false);

        // Left: Visual Metrics Chart
        JPanel chartWrapper = new JPanel(new BorderLayout());
        chartWrapper.setBorder(BorderFactory.createTitledBorder("İşlem & Önbellek Performans Grafiği"));
        chartWrapper.add(chartPanel, BorderLayout.CENTER);
        centerContainer.add(chartWrapper);

        // Right: Active Queries Table
        JPanel tableWrapper = new JPanel(new BorderLayout(0, 4));
        tableWrapper.setBorder(BorderFactory.createTitledBorder("Canlı Oturumlar & Sorgular (pg_stat_activity)"));

        String[] columns = {"PID", "Kullanıcı", "İstemci", "Durum", "Sorgu"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        activityTable = new JTable(tableModel);
        activityTable.setRowHeight(22);
        activityTable.getTableHeader().setFont(activityTable.getTableHeader().getFont().deriveFont(Font.BOLD, 11f));
        activityTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        activityTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        activityTable.getColumnModel().getColumn(2).setPreferredWidth(85);
        activityTable.getColumnModel().getColumn(3).setPreferredWidth(65);
        activityTable.getColumnModel().getColumn(4).setPreferredWidth(220);

        tableWrapper.add(new JScrollPane(activityTable), BorderLayout.CENTER);
        centerContainer.add(tableWrapper);

        add(centerContainer, BorderLayout.CENTER);

        // Timer for auto-refresh
        autoRefreshTimer = new Timer(5000, e -> {
            if (autoRefreshBox.isSelected() && isShowing() && !isSimulationRunning) {
                refreshMetrics();
            }
        });
        autoRefreshTimer.start();
    }

    private JPanel createKpiCard(String title, JLabel mainLabel, JLabel subLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 215, 225), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        card.setBackground(Color.WHITE);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 11f));
        titleLbl.setForeground(new Color(100, 105, 115));
        card.add(titleLbl, BorderLayout.NORTH);

        mainLabel.setFont(mainLabel.getFont().deriveFont(Font.BOLD, 18f));
        mainLabel.setForeground(new Color(25, 30, 40));
        card.add(mainLabel, BorderLayout.CENTER);

        subLabel.setFont(subLabel.getFont().deriveFont(Font.PLAIN, 10f));
        subLabel.setForeground(new Color(120, 125, 135));
        card.add(subLabel, BorderLayout.SOUTH);

        return card;
    }

    private void toggleSimulationTest() {
        if (isSimulationRunning) {
            stopSimulationTest();
            refreshMetrics();
        } else {
            startSimulationTest();
        }
    }

    private void startSimulationTest() {
        isSimulationRunning = true;
        simulationStep = 0;
        testModeBtn.setText("Testi Durdur");

        // Simulation Test Scenario: 10 dynamic live load states
        simulationTimer = new Timer(900, e -> {
            simulationStep++;
            runSimulationStep(simulationStep);
            if (simulationStep >= 12) {
                simulationStep = 0; // loop seamlessly
            }
        });
        simulationTimer.start();
        runSimulationStep(0);
    }

    private void stopSimulationTest() {
        isSimulationRunning = false;
        if (simulationTimer != null) {
            simulationTimer.stop();
            simulationTimer = null;
        }
        testModeBtn.setText("Canlı Testi Başlat");
    }

    private void runSimulationStep(int step) {
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        lastUpdateLabel.setText("Test Çalışıyor (Adım " + (step + 1) + "/12) - " + java.time.LocalTime.now().format(dtf));

        statusValueLabel.setText("Çevrimiçi (Simülasyon)");
        statusValueLabel.setForeground(new Color(22, 163, 74));
        versionLabel.setText("PostgreSQL 16.2 Test Modu");

        int maxConns = 100;
        int activeConns;
        long commits;
        long rollbacks;
        long blksHit;
        long blksRead;
        String dbSize;

        tableModel.setRowCount(0);

        switch (step % 4) {
            case 0: // Normal Baseline Load
                activeConns = 8;
                commits = 1250;
                rollbacks = 4;
                blksHit = 84500;
                blksRead = 950;
                dbSize = "42.5 MB";
                tableModel.addRow(new Object[]{1012, "postgres", "127.0.0.1", "active", "SELECT * FROM public.products ORDER BY id LIMIT 50"});
                tableModel.addRow(new Object[]{1015, "app_user", "192.168.1.20", "idle", "COMMIT"});
                tableModel.addRow(new Object[]{1018, "report_svc", "192.168.1.35", "active", "SELECT count(*), avg(price) FROM products"});
                break;

            case 1: // Moderate Traffic Surge
                activeConns = 38;
                commits = 4820;
                rollbacks = 32;
                blksHit = 142000;
                blksRead = 3400;
                dbSize = "45.1 MB";
                tableModel.addRow(new Object[]{1024, "api_gateway", "10.0.0.5", "active", "INSERT INTO orders (product_id, amount) VALUES (42, 3)"});
                tableModel.addRow(new Object[]{1028, "api_gateway", "10.0.0.6", "active", "UPDATE products SET stock = stock - 1 WHERE id = 42"});
                tableModel.addRow(new Object[]{1031, "postgres", "127.0.0.1", "active", "SELECT pg_stat_activity.pid, query FROM pg_stat_activity"});
                tableModel.addRow(new Object[]{1035, "analytics", "10.0.0.99", "active", "SELECT category, sum(price) FROM products GROUP BY category"});
                tableModel.addRow(new Object[]{1040, "app_user", "192.168.1.20", "idle", "RELEASE SAVEPOINT sp1"});
                break;

            case 2: // High Load / Peak Activity (Amber Warning Level)
                activeConns = 76;
                commits = 12450;
                rollbacks = 240;
                blksHit = 280000;
                blksRead = 14500;
                dbSize = "52.8 MB";
                tableModel.addRow(new Object[]{1050, "batch_job", "10.0.1.10", "active", "VACUUM ANALYZE public.products"});
                tableModel.addRow(new Object[]{1055, "api_gateway", "10.0.0.5", "active", "SELECT * FROM orders FOR UPDATE"});
                tableModel.addRow(new Object[]{1060, "api_gateway", "10.0.0.7", "active", "INSERT INTO audit_logs (action, time) VALUES ('EXPORT', now())"});
                tableModel.addRow(new Object[]{1065, "etl_worker", "10.0.2.14", "active", "COPY products_archive TO STDOUT WITH CSV"});
                tableModel.addRow(new Object[]{1070, "web_client", "192.168.1.102", "active", "SELECT json_agg(p) FROM products p"});
                tableModel.addRow(new Object[]{1075, "admin", "127.0.0.1", "idle in tx", "UPDATE settings SET maintenance = true"});
                break;

            default: // Stress Load (Red Level Gauge Demo)
                activeConns = 94;
                commits = 28400;
                rollbacks = 1250;
                blksHit = 510000;
                blksRead = 48000;
                dbSize = "68.4 MB";
                tableModel.addRow(new Object[]{1080, "stress_test", "127.0.0.1", "active", "SELECT generate_series(1, 100000), random()"});
                tableModel.addRow(new Object[]{1085, "api_cluster", "10.0.0.12", "active", "INSERT INTO orders SELECT * FROM staging_orders"});
                tableModel.addRow(new Object[]{1090, "api_cluster", "10.0.0.14", "waiting", "LOCK TABLE products IN EXCLUSIVE MODE"});
                tableModel.addRow(new Object[]{1095, "dba_user", "127.0.0.1", "active", "SELECT pg_cancel_backend(1090)"});
                tableModel.addRow(new Object[]{1100, "indexer", "10.0.3.5", "active", "REINDEX TABLE public.products"});
                break;
        }

        int pct = (int) Math.round(((double) activeConns / maxConns) * 100.0);
        connectionsValueLabel.setText(activeConns + " / " + maxConns + " (%" + pct + ")");
        dbSizeValueLabel.setText(dbSize);

        long totalBlks = blksHit + blksRead;
        double cacheHitRatio = totalBlks > 0 ? ((double) blksHit / totalBlks) * 100.0 : 100.0;
        cacheHitValueLabel.setText(String.format("%%%.1f", cacheHitRatio));
        cacheHitValueLabel.setForeground(cacheHitRatio > 90 ? new Color(22, 163, 74) : new Color(217, 119, 6));

        txStatsLabel.setText("Commit: " + commits + " | Rollback: " + rollbacks);

        // Animate Charts
        chartPanel.updateData(commits, rollbacks, blksHit, blksRead, activeConns, maxConns, isDark);
    }

    public void refreshMetrics() {
        PostgresqlConfigurationSettings settings = settingsSupplier.get();
        if (settings == null) return;

        refreshBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            private String serverVersion = "--";
            private int activeConns = 0;
            private int maxConns = 100;
            private String dbSize = "0 MB";
            private double cacheHitRatio = 0.0;
            private long commits = 0;
            private long rollbacks = 0;
            private long blksHit = 0;
            private long blksRead = 0;
            private boolean isOnline = false;
            private final java.util.List<Object[]> sessionRows = new java.util.ArrayList<>();

            @Override
            protected Void doInBackground() {
                String url = String.format("jdbc:postgresql://%s:%d/%s",
                        settings.getServerHost(), settings.getPort(), settings.getDatabaseName());

                try (Connection conn = DriverManager.getConnection(url, settings.getUsername(), settings.getPassword());
                     Statement stmt = conn.createStatement()) {

                    isOnline = true;

                    // 1. Version
                    try (ResultSet rs = stmt.executeQuery("SELECT version()")) {
                        if (rs.next()) {
                            String fullVer = rs.getString(1);
                            if (fullVer.contains("PostgreSQL")) {
                                int idx = fullVer.indexOf("PostgreSQL");
                                int end = fullVer.indexOf(" ", idx + 11);
                                serverVersion = (end > 0) ? fullVer.substring(idx, end) : "PostgreSQL";
                            }
                        }
                    }

                    // 2. Max Connections
                    try (ResultSet rs = stmt.executeQuery("SELECT current_setting('max_connections')")) {
                        if (rs.next()) maxConns = rs.getInt(1);
                    }

                    // 3. Active Connections
                    try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()")) {
                        if (rs.next()) activeConns = rs.getInt(1);
                    }

                    // 4. DB Size
                    try (ResultSet rs = stmt.executeQuery("SELECT pg_size_pretty(pg_database_size(current_database()))")) {
                        if (rs.next()) dbSize = rs.getString(1);
                    }

                    // 5. Cache Hit & Transactions
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT xact_commit, xact_rollback, blks_read, blks_hit FROM pg_stat_database WHERE datname = current_database()")) {
                        if (rs.next()) {
                            commits = rs.getLong("xact_commit");
                            rollbacks = rs.getLong("xact_rollback");
                            blksRead = rs.getLong("blks_read");
                            blksHit = rs.getLong("blks_hit");

                            long totalBlks = blksHit + blksRead;
                            if (totalBlks > 0) {
                                cacheHitRatio = ((double) blksHit / totalBlks) * 100.0;
                            } else {
                                cacheHitRatio = 100.0;
                            }
                        }
                    }

                    // 6. Active Sessions (pg_stat_activity)
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT pid, usename, client_addr, state, query FROM pg_stat_activity " +
                            "WHERE datname = current_database() ORDER BY pid ASC LIMIT 25")) {
                        while (rs.next()) {
                            int pid = rs.getInt("pid");
                            String user = rs.getString("usename");
                            String addr = rs.getString("client_addr");
                            String state = rs.getString("state");
                            String query = rs.getString("query");
                            if (addr == null) addr = "127.0.0.1";
                            if (query == null) query = "-";
                            query = query.replaceAll("\\s+", " ").trim();

                            sessionRows.add(new Object[]{pid, user != null ? user : "postgres", addr, state != null ? state : "idle", query});
                        }
                    }

                } catch (Exception ex) {
                    isOnline = false;
                }
                return null;
            }

            @Override
            protected void done() {
                refreshBtn.setEnabled(true);
                java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
                lastUpdateLabel.setText("Son Güncelleme: " + java.time.LocalTime.now().format(dtf));

                if (isOnline) {
                    statusValueLabel.setText("Çevrimiçi");
                    statusValueLabel.setForeground(new Color(22, 163, 74)); // Green
                    versionLabel.setText(serverVersion);

                    int pct = (int) Math.round(((double) activeConns / maxConns) * 100.0);
                    connectionsValueLabel.setText(activeConns + " / " + maxConns + " (%" + pct + ")");
                    dbSizeValueLabel.setText(dbSize);
                    cacheHitValueLabel.setText(String.format("%%%.1f", cacheHitRatio));
                    cacheHitValueLabel.setForeground(cacheHitRatio > 90 ? new Color(22, 163, 74) : new Color(217, 119, 6));

                    txStatsLabel.setText("Commit: " + commits + " | Rollback: " + rollbacks);

                    chartPanel.updateData(commits, rollbacks, blksHit, blksRead, activeConns, maxConns, isDark);

                    tableModel.setRowCount(0);
                    for (Object[] row : sessionRows) {
                        tableModel.addRow(row);
                    }
                } else {
                    statusValueLabel.setText("Bağlantı Yok");
                    statusValueLabel.setForeground(new Color(220, 38, 38)); // Red
                    versionLabel.setText("Erişilemedi");
                    connectionsValueLabel.setText("-- / --");
                    dbSizeValueLabel.setText("--");
                    cacheHitValueLabel.setText("--");
                    tableModel.setRowCount(0);
                }
            }
        }.execute();
    }

    public void applyTheme(boolean isDark) {
        this.isDark = isDark;
        chartPanel.setDark(isDark);
        if (isDark) {
            activityTable.setBackground(new Color(24, 26, 32));
            activityTable.setForeground(new Color(230, 235, 245));
            lastUpdateLabel.setForeground(new Color(160, 165, 175));
        } else {
            activityTable.setBackground(Color.WHITE);
            activityTable.setForeground(new Color(30, 35, 45));
            lastUpdateLabel.setForeground(new Color(100, 105, 115));
        }
    }

    // Custom Java2D Metrics Chart Component
    public static class MetricsChartPanel extends JPanel {
        private long commits = 100;
        private long rollbacks = 2;
        private long blksHit = 950;
        private long blksRead = 50;
        private int activeConns = 5;
        private int maxConns = 100;
        private boolean isDark = false;

        public MetricsChartPanel() {
            setPreferredSize(new Dimension(0, 220));
            setOpaque(false);
        }

        public void updateData(long commits, long rollbacks, long blksHit, long blksRead, int activeConns, int maxConns, boolean isDark) {
            this.commits = commits;
            this.rollbacks = rollbacks;
            this.blksHit = blksHit;
            this.blksRead = blksRead;
            this.activeConns = activeConns;
            this.maxConns = maxConns;
            this.isDark = isDark;
            repaint();
        }

        public void setDark(boolean isDark) {
            this.isDark = isDark;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();

            Color textColor = isDark ? new Color(220, 225, 235) : new Color(40, 45, 55);
            Color subColor = isDark ? new Color(150, 155, 165) : new Color(110, 115, 125);
            Color trackBg = isDark ? new Color(35, 38, 48) : new Color(230, 234, 240);

            // Left Metric: Transactions (Commit vs Rollback Bar)
            int halfW = (width - 40) / 2;
            int startY = 24;

            // 1. Transaction Bar Chart
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g2.setColor(textColor);
            g2.drawString("İşlem Başarısı (Transactions)", 16, startY);

            long totalTx = commits + rollbacks;
            double commitPct = totalTx > 0 ? ((double) commits / totalTx) * 100.0 : 100.0;
            double rollbackPct = 100.0 - commitPct;

            g2.setColor(trackBg);
            g2.fillRoundRect(16, startY + 8, halfW, 20, 8, 8);

            int commitBarW = (int) Math.round((commitPct / 100.0) * halfW);
            g2.setColor(new Color(34, 197, 94)); // Emerald Green
            g2.fillRoundRect(16, startY + 8, commitBarW, 20, 8, 8);

            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g2.setColor(subColor);
            g2.drawString(String.format("Commit: %d (%%%.1f)  |  Rollback: %d (%%%.1f)", commits, commitPct, rollbacks, rollbackPct), 16, startY + 44);

            // 2. Cache Hit Efficiency Bar
            int secondY = startY + 65;
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g2.setColor(textColor);
            g2.drawString("Önbellek Verimliliği (Cache vs Disk I/O)", 16, secondY);

            long totalBlks = blksHit + blksRead;
            double hitPct = totalBlks > 0 ? ((double) blksHit / totalBlks) * 100.0 : 100.0;

            g2.setColor(trackBg);
            g2.fillRoundRect(16, secondY + 8, halfW, 20, 8, 8);

            int hitBarW = (int) Math.round((hitPct / 100.0) * halfW);
            g2.setColor(new Color(59, 130, 246)); // Crisp Blue
            g2.fillRoundRect(16, secondY + 8, hitBarW, 20, 8, 8);

            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g2.setColor(subColor);
            g2.drawString(String.format("Bellekten Okuma: %%%.1f  |  Diskten Okuma: %%%.1f", hitPct, (100.0 - hitPct)), 16, secondY + 44);

            // 3. Right Side: Connection Pool Usage Gauge Ring
            int rightX = halfW + 40;
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g2.setColor(textColor);
            g2.drawString("Bağlantı Havuz Doluluğu", rightX, startY);

            int gaugeSize = 100;
            int gaugeX = rightX + (halfW - gaugeSize) / 2;
            int gaugeY = startY + 16;

            // Background Ring
            g2.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(trackBg);
            g2.drawArc(gaugeX, gaugeY, gaugeSize, gaugeSize, 0, 360);

            // Filled Arc
            double connPct = maxConns > 0 ? ((double) activeConns / maxConns) : 0.05;
            int arcAngle = (int) Math.round(connPct * 360.0);
            g2.setColor(connPct < 0.7 ? new Color(34, 197, 94) : (connPct < 0.9 ? new Color(245, 158, 11) : new Color(239, 68, 68)));
            g2.drawArc(gaugeX, gaugeY, gaugeSize, gaugeSize, 90, -arcAngle);

            // Centered Percentage
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g2.setColor(textColor);
            String centerTxt = "%" + (int) Math.round(connPct * 100);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(centerTxt, gaugeX + (gaugeSize - fm.stringWidth(centerTxt)) / 2, gaugeY + (gaugeSize / 2) + 6);

            g2.dispose();
        }
    }
}

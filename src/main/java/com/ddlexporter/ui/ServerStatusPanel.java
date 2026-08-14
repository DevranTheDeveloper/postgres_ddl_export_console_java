package com.ddlexporter.ui;

import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.function.Supplier;

public class ServerStatusPanel extends JPanel {
    private final Supplier<PostgresqlConfigurationSettings> settingsSupplier;
    private final AuditHistoryManager auditManager;
    private final SystemDiagnosticsManager diagnosticsManager;

    // Top KPI Labels
    private final JLabel statusValueLabel = new JLabel("Bilinmiyor");
    private final JLabel versionLabel = new JLabel("PostgreSQL --");
    private final JLabel connectionsValueLabel = new JLabel("0 / 0");
    private final JLabel dbSizeValueLabel = new JLabel("0 MB");
    private final JLabel cacheHitValueLabel = new JLabel("%0.0");
    private final JLabel txStatsLabel = new JLabel("Commit: 0 | Rollback: 0");
    private final JLabel diagnosticsValueLabel = new JLabel("0 Hata | 0 Uyarı");
    private final JLabel diagnosticsSubLabel = new JLabel("Detaylar için tıklayın ↗");

    // Charts
    private final MetricsChartPanel chartPanel = new MetricsChartPanel();

    // Tables
    private final DefaultTableModel activityTableModel;
    private final JTable activityTable;

    private final DefaultTableModel topTablesModel;
    private final JTable topTablesTable;

    private final DefaultTableModel historyTableModel;
    private final JTable historyTable;

    private final DefaultTableModel diagnosticsTableModel;
    private final JTable diagnosticsTable;

    private final JTabbedPane detailsTabbedPane;

    private final JCheckBox autoRefreshBox = new JCheckBox("5 sn otomatik yenile", false);
    private final JButton refreshBtn = new JButton("Yenile");
    private final JButton testModeBtn = new JButton("Canlı Testi Başlat");
    private final JLabel lastUpdateLabel = new JLabel("Son Güncelleme: Henüz yapılmadı");

    private Timer autoRefreshTimer;
    private Timer simulationTimer;
    private boolean isSimulationRunning = false;
    private int simulationStep = 0;
    private boolean isDark = false;

    public ServerStatusPanel(Supplier<PostgresqlConfigurationSettings> settingsSupplier,
                             AuditHistoryManager auditManager,
                             SystemDiagnosticsManager diagnosticsManager) {
        this.settingsSupplier = settingsSupplier;
        this.auditManager = auditManager;
        this.diagnosticsManager = diagnosticsManager;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        setOpaque(false);

        // 1. Top Section: Header Toolbar + 5 KPI Cards
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

        // 5 KPI Cards Grid (Equal Width)
        JPanel cardsGrid = new JPanel(new GridLayout(1, 5, 8, 0));
        cardsGrid.setOpaque(false);
        cardsGrid.setPreferredSize(new Dimension(0, 85));

        cardsGrid.add(createKpiCard("Sunucu Durumu", statusValueLabel, versionLabel, null));
        cardsGrid.add(createKpiCard("Aktif Bağlantılar", connectionsValueLabel, new JLabel("Havuz Kapasitesi"), null));
        cardsGrid.add(createKpiCard("Veritabanı Boyutu", dbSizeValueLabel, new JLabel("Toplam Disk Alanı"), null));
        cardsGrid.add(createKpiCard("Cache Hit (Önbellek)", cacheHitValueLabel, txStatsLabel, null));

        // Clickable 5th Card: System Diagnostics & Health Center
        JPanel diagnosticsCard = createKpiCard("Sistem Sağlığı & Uyarılar", diagnosticsValueLabel, diagnosticsSubLabel, this::openDiagnosticsDialog);
        cardsGrid.add(diagnosticsCard);

        topContainer.add(cardsGrid, BorderLayout.CENTER);
        add(topContainer, BorderLayout.NORTH);

        // 2. Center Split: Left Chart | Right 4-Tab Detailed Inspector
        JPanel centerContainer = new JPanel(new GridLayout(1, 2, 12, 0));
        centerContainer.setOpaque(false);

        // Left: Visual Metrics Chart
        JPanel chartWrapper = new JPanel(new BorderLayout());
        chartWrapper.setBorder(BorderFactory.createTitledBorder("İşlem & Önbellek Performans Grafiği"));
        chartWrapper.add(chartPanel, BorderLayout.CENTER);
        centerContainer.add(chartWrapper);

        // Right: 4-Tab Detailed Inspector
        detailsTabbedPane = new JTabbedPane();

        // --- Sub-Tab 1: Active Queries (pg_stat_activity) ---
        String[] activityCols = {"PID", "Kullanıcı", "İstemci", "Durum", "Sorgu"};
        activityTableModel = new DefaultTableModel(activityCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        activityTable = new JTable(activityTableModel);
        setupTableStyle(activityTable);
        detailsTabbedPane.addTab("Canlı Oturumlar (" + activityTableModel.getRowCount() + ")", new JScrollPane(activityTable));

        // --- Sub-Tab 2: Top Tables by Size ---
        String[] tableCols = {"Tablo Adı", "Toplam Boyut", "Satır Sayısı (Yaklaşık)", "Şema"};
        topTablesModel = new DefaultTableModel(tableCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        topTablesTable = new JTable(topTablesModel);
        setupTableStyle(topTablesTable);
        detailsTabbedPane.addTab("Tablo Boyutları", new JScrollPane(topTablesTable));

        // --- Sub-Tab 3: Audit / Export History ---
        String[] historyCols = {"Zaman", "Kullanıcı", "İşlem", "Detay", "Süre", "Durum"};
        historyTableModel = new DefaultTableModel(historyCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        historyTable = new JTable(historyTableModel);
        setupTableStyle(historyTable);

        historyTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String val = String.valueOf(value);
                if ("BAŞARILI".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(22, 163, 74));
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                } else if ("HATA".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(220, 38, 38));
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                }
                return c;
            }
        });

        JPanel historyTabPanel = new JPanel(new BorderLayout(0, 4));
        historyTabPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        JButton clearHistoryBtn = new JButton("Geçmişi Temizle");
        clearHistoryBtn.setFont(clearHistoryBtn.getFont().deriveFont(Font.PLAIN, 11f));
        clearHistoryBtn.addActionListener(e -> {
            auditManager.clearHistory();
            loadAuditHistory();
        });
        JPanel historyFooter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        historyFooter.add(clearHistoryBtn);
        historyTabPanel.add(historyFooter, BorderLayout.SOUTH);
        detailsTabbedPane.addTab("İşlem Geçmişi", historyTabPanel);

        // --- Sub-Tab 4: Direct System Diagnostics ---
        String[] diagCols = {"Seviye", "Zaman", "Başlık", "Kaynak"};
        diagnosticsTableModel = new DefaultTableModel(diagCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        diagnosticsTable = new JTable(diagnosticsTableModel);
        setupTableStyle(diagnosticsTable);

        diagnosticsTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String val = String.valueOf(value);
                if ("KRİTİK HATA".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(220, 38, 38));
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                } else if ("UYARI".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(217, 119, 6));
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                }
                return c;
            }
        });

        JPanel diagTabPanel = new JPanel(new BorderLayout(0, 4));
        diagTabPanel.add(new JScrollPane(diagnosticsTable), BorderLayout.CENTER);

        JPanel diagFooter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        JButton openModalBtn = new JButton("Detaylı Teşhis Raporunu Aç");
        openModalBtn.setFont(openModalBtn.getFont().deriveFont(Font.BOLD, 11f));
        openModalBtn.addActionListener(e -> openDiagnosticsDialog());
        diagFooter.add(openModalBtn);
        diagTabPanel.add(diagFooter, BorderLayout.SOUTH);

        detailsTabbedPane.addTab("Uyarı & Hatalar (0)", diagTabPanel);

        centerContainer.add(detailsTabbedPane);
        add(centerContainer, BorderLayout.CENTER);

        // Listen for diagnostics updates
        diagnosticsManager.addListener(this::updateDiagnosticsUi);

        // Timer for auto-refresh
        autoRefreshTimer = new Timer(5000, e -> {
            if (autoRefreshBox.isSelected() && isShowing() && !isSimulationRunning) {
                refreshMetrics();
            }
        });
        autoRefreshTimer.start();
        loadAuditHistory();
        updateDiagnosticsUi();
    }

    private void setupTableStyle(JTable table) {
        table.setRowHeight(24);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 11f));
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    }

    private JPanel createKpiCard(String title, JLabel mainLabel, JLabel subLabel, Runnable onClick) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 215, 225), 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        card.setBackground(Color.WHITE);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 11f));
        titleLbl.setForeground(new Color(100, 105, 115));
        card.add(titleLbl, BorderLayout.NORTH);

        mainLabel.setFont(mainLabel.getFont().deriveFont(Font.BOLD, 16f));
        mainLabel.setForeground(new Color(25, 30, 40));
        card.add(mainLabel, BorderLayout.CENTER);

        subLabel.setFont(subLabel.getFont().deriveFont(Font.PLAIN, 10f));
        subLabel.setForeground(new Color(120, 125, 135));
        card.add(subLabel, BorderLayout.SOUTH);

        if (onClick != null) {
            card.setCursor(new Cursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(59, 130, 246), 1, true),
                            BorderFactory.createEmptyBorder(8, 10, 8, 10)
                    ));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(210, 215, 225), 1, true),
                            BorderFactory.createEmptyBorder(8, 10, 8, 10)
                    ));
                }
            });
        }

        return card;
    }

    private void openDiagnosticsDialog() {
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        Frame ownerFrame = (ancestor instanceof Frame) ? (Frame) ancestor : null;
        new DiagnosticsDialog(ownerFrame, diagnosticsManager, isDark).setVisible(true);
        updateDiagnosticsUi();
    }

    public void updateDiagnosticsUi() {
        int activeErrs = diagnosticsManager.getActiveErrorCount();
        int activeWarns = diagnosticsManager.getActiveWarningCount();
        int resolvedCount = diagnosticsManager.getResolvedIssues().size();

        diagnosticsValueLabel.setText(activeErrs + " Hata | " + activeWarns + " Uyarı");
        if (activeErrs > 0) {
            diagnosticsValueLabel.setForeground(new Color(220, 38, 38));
        } else if (activeWarns > 0) {
            diagnosticsValueLabel.setForeground(new Color(217, 119, 6));
        } else {
            diagnosticsValueLabel.setForeground(new Color(22, 163, 74));
        }

        if (resolvedCount > 0) {
            diagnosticsSubLabel.setText("Detaylar ↗ (Çözülen: " + resolvedCount + ")");
        } else {
            diagnosticsSubLabel.setText("Detaylar için tıklayın ↗");
        }

        detailsTabbedPane.setTitleAt(3, "Uyarı & Hatalar (" + (activeErrs + activeWarns) + ")");

        diagnosticsTableModel.setRowCount(0);
        for (SystemDiagnosticsManager.DiagnosticIssue issue : diagnosticsManager.getAllIssues()) {
            diagnosticsTableModel.addRow(new Object[]{
                    issue.resolved ? "Çözüldü" : (issue.level == SystemDiagnosticsManager.Level.ERROR ? "KRİTİK HATA" : "UYARI"),
                    issue.timestamp,
                    issue.title,
                    issue.source
            });
        }
    }

    public void loadAuditHistory() {
        historyTableModel.setRowCount(0);
        for (AuditHistoryManager.AuditEntry entry : auditManager.getEntries()) {
            historyTableModel.addRow(new Object[]{
                    entry.timestamp, entry.user, entry.action, entry.details, entry.duration, entry.status
            });
        }
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

        simulationTimer = new Timer(900, e -> {
            simulationStep++;
            runSimulationStep(simulationStep);
            if (simulationStep >= 12) {
                simulationStep = 0;
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

        activityTableModel.setRowCount(0);

        switch (step % 4) {
            case 0:
                activeConns = 8; commits = 1250; rollbacks = 4; blksHit = 84500; blksRead = 950; dbSize = "42.5 MB";
                activityTableModel.addRow(new Object[]{1012, "postgres", "127.0.0.1", "active", "SELECT * FROM public.products ORDER BY id LIMIT 50"});
                activityTableModel.addRow(new Object[]{1015, "app_user", "192.168.1.20", "idle", "COMMIT"});
                activityTableModel.addRow(new Object[]{1018, "report_svc", "192.168.1.35", "active", "SELECT count(*), avg(price) FROM products"});
                break;
            case 1:
                activeConns = 38; commits = 4820; rollbacks = 32; blksHit = 142000; blksRead = 3400; dbSize = "45.1 MB";
                activityTableModel.addRow(new Object[]{1024, "api_gateway", "10.0.0.5", "active", "INSERT INTO orders (product_id, amount) VALUES (42, 3)"});
                activityTableModel.addRow(new Object[]{1028, "api_gateway", "10.0.0.6", "active", "UPDATE products SET stock = stock - 1 WHERE id = 42"});
                activityTableModel.addRow(new Object[]{1031, "postgres", "127.0.0.1", "active", "SELECT pg_stat_activity.pid, query FROM pg_stat_activity"});
                break;
            case 2:
                activeConns = 76; commits = 12450; rollbacks = 240; blksHit = 280000; blksRead = 14500; dbSize = "52.8 MB";
                activityTableModel.addRow(new Object[]{1050, "batch_job", "10.0.1.10", "active", "VACUUM ANALYZE public.products"});
                activityTableModel.addRow(new Object[]{1055, "api_gateway", "10.0.0.5", "active", "SELECT * FROM orders FOR UPDATE"});
                activityTableModel.addRow(new Object[]{1060, "api_gateway", "10.0.0.7", "active", "INSERT INTO audit_logs (action, time) VALUES ('EXPORT', now())"});
                if (diagnosticsManager.getActiveWarningCount() == 0) {
                    diagnosticsManager.addIssue(SystemDiagnosticsManager.Level.WARN,
                            "'customers_archive' tablosunda Primary Key eksik",
                            "DDL Scripter Motoru",
                            "Tablo taranırken birincil anahtar kısıtlaması bulunamadı. Replikasyon ve performans sorunlarına yol açabilir.",
                            "ALTER TABLE public.customers_archive ADD PRIMARY KEY (id);");
                }
                break;
            default:
                activeConns = 94; commits = 28400; rollbacks = 1250; blksHit = 510000; blksRead = 48000; dbSize = "68.4 MB";
                activityTableModel.addRow(new Object[]{1080, "stress_test", "127.0.0.1", "active", "SELECT generate_series(1, 100000), random()"});
                activityTableModel.addRow(new Object[]{1085, "api_cluster", "10.0.0.12", "active", "INSERT INTO orders SELECT * FROM staging_orders"});
                activityTableModel.addRow(new Object[]{1090, "api_cluster", "10.0.0.14", "waiting", "LOCK TABLE products IN EXCLUSIVE MODE"});
                if (diagnosticsManager.getActiveErrorCount() == 0) {
                    diagnosticsManager.addIssue(SystemDiagnosticsManager.Level.ERROR,
                            "Bağlantı Havuzu Kritik Doluluk Seviyesinde (%94)",
                            "PostgreSQL Bağlantı Havuzu",
                            "Aktif bağlantı sayısı 94/100 limitine yaklaştı. Yeni gelen istemci bağlantıları reddedilebilir veya kilitlenebilir.",
                            "max_connections parametresini yükseltin veya PgBouncer benzeri bir connection pooler devreye alın.");
                }
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
        detailsTabbedPane.setTitleAt(0, "Canlı Oturumlar (" + activityTableModel.getRowCount() + ")");

        chartPanel.updateData(commits, rollbacks, blksHit, blksRead, activeConns, maxConns, isDark);
    }

    public void refreshMetrics() {
        PostgresqlConfigurationSettings settings = settingsSupplier.get();
        if (settings == null) return;

        loadAuditHistory();
        updateDiagnosticsUi();
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
            private String connError = null;
            private final java.util.List<Object[]> sessionRows = new java.util.ArrayList<>();
            private final java.util.List<Object[]> tableRows = new java.util.ArrayList<>();

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
                            cacheHitRatio = (totalBlks > 0) ? (((double) blksHit / totalBlks) * 100.0) : 100.0;
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

                    // 7. Top Tables by Size
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT c.relname, pg_size_pretty(pg_total_relation_size(c.oid)) AS total_size, " +
                            "c.reltuples::bigint AS row_estimate, n.nspname " +
                            "FROM pg_class c " +
                            "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                            "WHERE relkind = 'r' AND n.nspname NOT IN ('pg_catalog', 'information_schema') " +
                            "ORDER BY pg_total_relation_size(c.oid) DESC LIMIT 15")) {
                        while (rs.next()) {
                            String tblName = rs.getString("relname");
                            String sizeStr = rs.getString("total_size");
                            long rowCount = rs.getLong("row_estimate");
                            String schema = rs.getString("nspname");
                            tableRows.add(new Object[]{tblName, sizeStr, rowCount >= 0 ? rowCount : 0, schema});
                        }
                    }

                } catch (Exception ex) {
                    isOnline = false;
                    connError = ex.getMessage();
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
                    statusValueLabel.setForeground(new Color(22, 163, 74));
                    versionLabel.setText(serverVersion);

                    int pct = (int) Math.round(((double) activeConns / maxConns) * 100.0);
                    connectionsValueLabel.setText(activeConns + " / " + maxConns + " (%" + pct + ")");
                    dbSizeValueLabel.setText(dbSize);
                    cacheHitValueLabel.setText(String.format("%%%.1f", cacheHitRatio));
                    cacheHitValueLabel.setForeground(cacheHitRatio > 90 ? new Color(22, 163, 74) : new Color(217, 119, 6));

                    txStatsLabel.setText("Commit: " + commits + " | Rollback: " + rollbacks);

                    chartPanel.updateData(commits, rollbacks, blksHit, blksRead, activeConns, maxConns, isDark);

                    activityTableModel.setRowCount(0);
                    for (Object[] row : sessionRows) {
                        activityTableModel.addRow(row);
                    }
                    detailsTabbedPane.setTitleAt(0, "Canlı Oturumlar (" + activityTableModel.getRowCount() + ")");

                    topTablesModel.setRowCount(0);
                    for (Object[] row : tableRows) {
                        topTablesModel.addRow(row);
                    }

                    // Check for potential warnings
                    if (pct >= 85) {
                        diagnosticsManager.addIssue(SystemDiagnosticsManager.Level.WARN,
                                "Yüksek Bağlantı Havuzu Kullanımı (%" + pct + ")",
                                "PostgreSQL Havuzu",
                                "Bağlantı sayısı " + activeConns + "/" + maxConns + " seviyesine ulaştı.",
                                "Bağlantı limitini kontrol edin.");
                    }
                } else {
                    statusValueLabel.setText("Bağlantı Yok");
                    statusValueLabel.setForeground(new Color(220, 38, 38));
                    versionLabel.setText("Erişilemedi");
                    connectionsValueLabel.setText("-- / --");
                    dbSizeValueLabel.setText("--");
                    cacheHitValueLabel.setText("--");
                    activityTableModel.setRowCount(0);
                    topTablesModel.setRowCount(0);

                    if (connError != null) {
                        diagnosticsManager.addIssue(SystemDiagnosticsManager.Level.ERROR,
                                "Veritabanı Sunucusuna Bağlanılamadı",
                                "JDBC Sürücüsü",
                                "Sunucu adresi veya kimlik bilgileri doğrulanamadı:\n" + connError,
                                "PostgreSQL servisinin çalıştığından ve şifrenin doğruluğundan emin olun.");
                    }
                }
                updateDiagnosticsUi();
            }
        }.execute();
    }

    public void applyTheme(boolean isDark) {
        this.isDark = isDark;
        chartPanel.setDark(isDark);
        Color tableBg = isDark ? new Color(24, 26, 32) : Color.WHITE;
        Color tableFg = isDark ? new Color(230, 235, 245) : new Color(30, 35, 45);

        activityTable.setBackground(tableBg);
        activityTable.setForeground(tableFg);

        topTablesTable.setBackground(tableBg);
        topTablesTable.setForeground(tableFg);

        historyTable.setBackground(tableBg);
        historyTable.setForeground(tableFg);

        diagnosticsTable.setBackground(tableBg);
        diagnosticsTable.setForeground(tableFg);

        lastUpdateLabel.setForeground(isDark ? new Color(160, 165, 175) : new Color(100, 105, 115));
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
            g2.setColor(new Color(34, 197, 94));
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
            g2.setColor(new Color(59, 130, 246));
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

            g2.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(trackBg);
            g2.drawArc(gaugeX, gaugeY, gaugeSize, gaugeSize, 0, 360);

            double connPct = maxConns > 0 ? ((double) activeConns / maxConns) : 0.05;
            int arcAngle = (int) Math.round(connPct * 360.0);
            g2.setColor(connPct < 0.7 ? new Color(34, 197, 94) : (connPct < 0.9 ? new Color(245, 158, 11) : new Color(239, 68, 68)));
            g2.drawArc(gaugeX, gaugeY, gaugeSize, gaugeSize, 90, -arcAngle);

            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g2.setColor(textColor);
            String centerTxt = "%" + (int) Math.round(connPct * 100);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(centerTxt, gaugeX + (gaugeSize - fm.stringWidth(centerTxt)) / 2, gaugeY + (gaugeSize / 2) + 6);

            g2.dispose();
        }
    }
}

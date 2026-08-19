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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ServerStatusPanel extends JPanel {
    private static final String CARD_DISCONNECTED = "DISCONNECTED";
    private static final String CARD_CONNECTED = "CONNECTED";

    private final CardLayout rootCardLayout = new CardLayout();
    private final JPanel rootCards = new JPanel(rootCardLayout);

    private final Supplier<PostgresqlConfigurationSettings> settingsSupplier;
    private final AuditHistoryManager auditManager;
    private final SystemDiagnosticsManager diagnosticsManager;
    private Consumer<PostgresqlConfigurationSettings> onSettingsUpdate;

    // --- Disconnected / Zero-State UI Components ---
    private final JPanel disconnectedCard = new JPanel(new GridBagLayout());
    private final JLabel disconnectedIcon = new JLabel("🔌");
    private final JLabel disconnectedTitle = new JLabel("PostgreSQL Canlı Sunucu Durumu");
    private final JLabel disconnectedSubtitle = new JLabel("Canlı metrikleri, bağlantı havuzunu ve aktif sorguları izlemek için sunucuya bağlanın.");
    private final JLabel disconnectedTargetLabel = new JLabel("Hedef: localhost:5432 / postgres");
    private final JLabel disconnectedErrorLabel = new JLabel("");
    private final JButton connectNowBtn = new JButton("⚡ Sunucuya Bağlan & Metrikleri Göster");
    private final JButton quickStartBtn = new JButton("🚀 Hızlı Başlangıç & Kurulum Asistanı");
    private final JButton demoModeBtn = new JButton("🧪 Demo / Simülasyon Modu");
    private JPanel disconnectedBox;

    // --- Connected Live Dashboard Components ---
    private final JPanel connectedPanel = new JPanel(new BorderLayout(10, 10));

    // Top KPI Labels
    private final JLabel statusValueLabel = new JLabel("Çevrimiçi");
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
    private final JButton disconnectBtn = new JButton("🔌 Bağlantıyı Kes");
    private final JLabel lastUpdateLabel = new JLabel("Son Güncelleme: --:--:--");

    private Timer autoRefreshTimer;
    private Timer simulationTimer;
    private boolean isSimulationRunning = false;
    private int simulationStep = 0;
    private boolean isDark = false;
    private boolean isConnected = false;

    private static class KpiCardView {
        final JPanel panel;
        final JLabel titleLabel;
        final JLabel mainLabel;
        final JLabel subLabel;
        final Runnable onClick;
        final boolean customMainColor;

        KpiCardView(JPanel panel, JLabel titleLabel, JLabel mainLabel, JLabel subLabel, Runnable onClick, boolean customMainColor) {
            this.panel = panel;
            this.titleLabel = titleLabel;
            this.mainLabel = mainLabel;
            this.subLabel = subLabel;
            this.onClick = onClick;
            this.customMainColor = customMainColor;
        }
    }
    private final List<KpiCardView> kpiCards = new ArrayList<>();

    public ServerStatusPanel(Supplier<PostgresqlConfigurationSettings> settingsSupplier,
                             AuditHistoryManager auditManager,
                             SystemDiagnosticsManager diagnosticsManager) {
        this.settingsSupplier = settingsSupplier;
        this.auditManager = auditManager;
        this.diagnosticsManager = diagnosticsManager;

        setLayout(new BorderLayout());
        setOpaque(false);

        // 1. Build Disconnected Zero-State Screen
        buildDisconnectedUi();

        // 2. Build Connected Live Dashboard
        // Table Models
        String[] activityCols = {"PID", "Kullanıcı", "İstemci", "Durum", "Sorgu"};
        activityTableModel = new DefaultTableModel(activityCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        activityTable = new JTable(activityTableModel);
        setupTableStyle(activityTable);

        String[] tableCols = {"Tablo Adı", "Toplam Boyut", "Satır Sayısı (Yaklaşık)", "Şema"};
        topTablesModel = new DefaultTableModel(tableCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        topTablesTable = new JTable(topTablesModel);
        setupTableStyle(topTablesTable);

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

        detailsTabbedPane = new JTabbedPane();
        buildConnectedUi();

        // 3. Assemble Root Card Layout
        rootCards.setOpaque(false);
        rootCards.add(disconnectedCard, CARD_DISCONNECTED);
        rootCards.add(connectedPanel, CARD_CONNECTED);
        add(rootCards, BorderLayout.CENTER);

        // Listen for diagnostics updates
        diagnosticsManager.addListener(this::updateDiagnosticsUi);

        // Auto-Refresh Timer (Only runs when connected)
        autoRefreshTimer = new Timer(5000, e -> {
            if (isConnected && autoRefreshBox.isSelected() && isShowing() && !isSimulationRunning) {
                refreshMetrics();
            }
        });
        autoRefreshTimer.start();

        // Start in disconnected zero-state by default
        showDisconnectedState(null);
    }

    private void buildDisconnectedUi() {
        disconnectedCard.setOpaque(false);

        disconnectedBox = new JPanel();
        disconnectedBox.setLayout(new BoxLayout(disconnectedBox, BoxLayout.Y_AXIS));
        disconnectedBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 65, 78), 1, true),
                BorderFactory.createEmptyBorder(32, 48, 32, 48)
        ));

        // Icon
        disconnectedIcon.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 48));
        disconnectedIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Title
        disconnectedTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        disconnectedTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Subtitle
        disconnectedSubtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        disconnectedSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Target Info Pill
        disconnectedTargetLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        disconnectedTargetLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        disconnectedTargetLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(75, 82, 95), 1, true),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));

        // Error Message Label
        disconnectedErrorLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        disconnectedErrorLabel.setForeground(new Color(239, 68, 68));
        disconnectedErrorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        disconnectedErrorLabel.setVisible(false);

        // Connect Now Button (Primary)
        connectNowBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        connectNowBtn.setPreferredSize(new Dimension(320, 38));
        connectNowBtn.setMaximumSize(new Dimension(340, 38));
        connectNowBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        connectNowBtn.setFocusable(false);
        connectNowBtn.addActionListener(e -> {
            updateTargetLabel();
            refreshMetrics();
        });

        // Action Buttons Row
        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        actionsRow.setOpaque(false);
        actionsRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        quickStartBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        quickStartBtn.setFocusable(false);
        quickStartBtn.addActionListener(e -> openDockerWizard());

        demoModeBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        demoModeBtn.setFocusable(false);
        demoModeBtn.addActionListener(e -> startSimulationTest());

        actionsRow.add(quickStartBtn);
        actionsRow.add(demoModeBtn);

        // Add to box with spacing
        disconnectedBox.add(disconnectedIcon);
        disconnectedBox.add(Box.createRigidArea(new Dimension(0, 12)));
        disconnectedBox.add(disconnectedTitle);
        disconnectedBox.add(Box.createRigidArea(new Dimension(0, 8)));
        disconnectedBox.add(disconnectedSubtitle);
        disconnectedBox.add(Box.createRigidArea(new Dimension(0, 16)));
        disconnectedBox.add(disconnectedTargetLabel);
        disconnectedBox.add(Box.createRigidArea(new Dimension(0, 12)));
        disconnectedBox.add(disconnectedErrorLabel);
        disconnectedBox.add(Box.createRigidArea(new Dimension(0, 16)));
        disconnectedBox.add(connectNowBtn);
        disconnectedBox.add(Box.createRigidArea(new Dimension(0, 14)));
        disconnectedBox.add(actionsRow);

        disconnectedCard.add(disconnectedBox);
    }

    private void buildConnectedUi() {
        connectedPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        connectedPanel.setOpaque(false);

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

        JButton dockerWizardBtn = new JButton("Hızlı Kurulum");
        dockerWizardBtn.setFont(dockerWizardBtn.getFont().deriveFont(Font.BOLD, 12f));
        dockerWizardBtn.addActionListener(e -> openDockerWizard());
        controls.add(dockerWizardBtn);

        refreshBtn.setFont(refreshBtn.getFont().deriveFont(Font.BOLD, 12f));
        refreshBtn.addActionListener(e -> {
            if (isSimulationRunning) stopSimulationTest();
            refreshMetrics();
        });
        controls.add(refreshBtn);

        disconnectBtn.setFont(disconnectBtn.getFont().deriveFont(Font.BOLD, 12f));
        disconnectBtn.addActionListener(e -> {
            if (isSimulationRunning) stopSimulationTest();
            showDisconnectedState(null);
        });
        controls.add(disconnectBtn);

        headerToolbar.add(controls, BorderLayout.EAST);
        topContainer.add(headerToolbar, BorderLayout.NORTH);

        // 5 KPI Cards Grid (Equal Width)
        JPanel cardsGrid = new JPanel(new GridLayout(1, 5, 8, 0));
        cardsGrid.setOpaque(false);
        cardsGrid.setPreferredSize(new Dimension(0, 85));

        cardsGrid.add(createKpiCard("Sunucu Durumu", statusValueLabel, versionLabel, this::openDockerWizard));
        cardsGrid.add(createKpiCard("Aktif Bağlantılar", connectionsValueLabel, new JLabel("Havuz Kapasitesi"), null));
        cardsGrid.add(createKpiCard("Veritabanı Boyutu", dbSizeValueLabel, new JLabel("Toplam Disk Alanı"), null));
        cardsGrid.add(createKpiCard("Cache Hit (Önbellek)", cacheHitValueLabel, txStatsLabel, null));

        JPanel diagnosticsCard = createKpiCard("Sistem Sağlığı & Uyarılar", diagnosticsValueLabel, diagnosticsSubLabel, this::openDiagnosticsDialog);
        cardsGrid.add(diagnosticsCard);

        topContainer.add(cardsGrid, BorderLayout.CENTER);
        connectedPanel.add(topContainer, BorderLayout.NORTH);

        // 2. Center Split: Left Chart | Right 4-Tab Detailed Inspector
        JPanel centerContainer = new JPanel(new GridLayout(1, 2, 12, 0));
        centerContainer.setOpaque(false);

        // Left: Visual Metrics Chart
        JPanel chartWrapper = new JPanel(new BorderLayout());
        chartWrapper.setBorder(BorderFactory.createTitledBorder("İşlem & Önbellek Performans Grafiği"));
        chartWrapper.add(chartPanel, BorderLayout.CENTER);
        centerContainer.add(chartWrapper);

        // Right: 4-Tab Detailed Inspector
        detailsTabbedPane.addTab("Canlı Oturumlar (" + activityTableModel.getRowCount() + ")", new JScrollPane(activityTable));
        detailsTabbedPane.addTab("Tablo Boyutları", new JScrollPane(topTablesTable));

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
        connectedPanel.add(centerContainer, BorderLayout.CENTER);
    }

    private void updateTargetLabel() {
        PostgresqlConfigurationSettings settings = settingsSupplier.get();
        if (settings != null) {
            disconnectedTargetLabel.setText(String.format("Hedef: %s:%d  |  Veritabanı: %s  |  Kullanıcı: %s",
                    settings.getServerHost(), settings.getPort(), settings.getDatabaseName(), settings.getUsername()));
        }
    }

    public void showDisconnectedState(String errorMessage) {
        isConnected = false;
        updateTargetLabel();

        if (errorMessage != null && !errorMessage.isBlank()) {
            disconnectedErrorLabel.setText("⚠️ Bağlantı Başarısız: " + errorMessage);
            disconnectedErrorLabel.setVisible(true);
        } else {
            disconnectedErrorLabel.setVisible(false);
        }

        connectNowBtn.setEnabled(true);
        connectNowBtn.setText("⚡ Sunucuya Bağlan & Metrikleri Göster");
        rootCardLayout.show(rootCards, CARD_DISCONNECTED);
    }

    public void showConnectedState() {
        isConnected = true;
        rootCardLayout.show(rootCards, CARD_CONNECTED);
    }

    private void setupTableStyle(JTable table) {
        table.setRowHeight(24);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 11f));
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    }

    private JPanel createKpiCard(String title, JLabel mainLabel, JLabel subLabel, Runnable onClick) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isDark ? new Color(55, 60, 72) : new Color(210, 215, 225), 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        card.setBackground(isDark ? new Color(34, 37, 46) : Color.WHITE);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        titleLabel.setForeground(isDark ? new Color(160, 170, 185) : new Color(100, 105, 115));

        mainLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        subLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        subLabel.setForeground(isDark ? new Color(130, 135, 150) : new Color(120, 125, 135));

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        textPanel.setOpaque(false);
        textPanel.add(mainLabel);
        textPanel.add(subLabel);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(textPanel, BorderLayout.CENTER);

        boolean customColor = (mainLabel == statusValueLabel || mainLabel == diagnosticsValueLabel);

        if (onClick != null) {
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }
                @Override
                public void mouseEntered(MouseEvent e) {
                    card.setBackground(isDark ? new Color(42, 46, 58) : new Color(240, 243, 250));
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    card.setBackground(isDark ? new Color(34, 37, 46) : Color.WHITE);
                }
            });
        }

        kpiCards.add(new KpiCardView(card, titleLabel, mainLabel, subLabel, onClick, customColor));
        return card;
    }

    public void setOnSettingsUpdate(Consumer<PostgresqlConfigurationSettings> onSettingsUpdate) {
        this.onSettingsUpdate = onSettingsUpdate;
    }

    private void openDockerWizard() {
        Container ancestor = SwingUtilities.getWindowAncestor(this);
        Frame ownerFrame = (ancestor instanceof Frame) ? (Frame) ancestor : null;
        new UniversalDatabaseHubDialog(ownerFrame, s -> {
            if (onSettingsUpdate != null) {
                onSettingsUpdate.accept(s);
            }
        }, this::refreshMetrics).setVisible(true);
    }

    private void openDiagnosticsDialog() {
        Container ancestor = SwingUtilities.getWindowAncestor(this);
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

        if (detailsTabbedPane.getTabCount() >= 4) {
            detailsTabbedPane.setTitleAt(3, "Uyarı & Hatalar (" + (activeErrs + activeWarns) + ")");
        }

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

    private void startSimulationTest() {
        isSimulationRunning = true;
        simulationStep = 0;
        showConnectedState();

        if (simulationTimer != null) simulationTimer.stop();
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
    }

    private void runSimulationStep(int step) {
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        lastUpdateLabel.setText("Demo Modu (Adım " + (step + 1) + "/12) - " + java.time.LocalTime.now().format(dtf));

        statusValueLabel.setText("Çevrimiçi (Simülasyon)");
        statusValueLabel.setForeground(new Color(22, 163, 74));
        versionLabel.setText("PostgreSQL 16.2 Demo");

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
                break;
            default:
                activeConns = 94; commits = 28400; rollbacks = 1250; blksHit = 510000; blksRead = 48000; dbSize = "68.4 MB";
                activityTableModel.addRow(new Object[]{1080, "stress_test", "127.0.0.1", "active", "SELECT generate_series(1, 100000), random()"});
                activityTableModel.addRow(new Object[]{1085, "api_cluster", "10.0.0.12", "active", "INSERT INTO orders SELECT * FROM staging_orders"});
                activityTableModel.addRow(new Object[]{1090, "api_cluster", "10.0.0.14", "waiting", "LOCK TABLE products IN EXCLUSIVE MODE"});
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
        if (settings == null) {
            showDisconnectedState("Bağlantı ayarları bulunamadı.");
            return;
        }

        connectNowBtn.setEnabled(false);
        connectNowBtn.setText("Bağlanıyor...");
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
                connectNowBtn.setEnabled(true);
                connectNowBtn.setText("⚡ Sunucuya Bağlan & Metrikleri Göster");

                java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
                lastUpdateLabel.setText("Son Güncelleme: " + java.time.LocalTime.now().format(dtf));

                if (isOnline) {
                    showConnectedState();

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

                    loadAuditHistory();
                    updateDiagnosticsUi();
                } else {
                    showDisconnectedState(connError);
                }
            }
        }.execute();
    }

    public void applyTheme(boolean isDark) {
        this.isDark = isDark;
        chartPanel.setDark(isDark);

        Color cardBg = isDark ? new Color(34, 37, 46) : Color.WHITE;
        Color cardBorder = isDark ? new Color(55, 60, 72) : new Color(210, 215, 225);
        Color titleFg = isDark ? new Color(160, 170, 185) : new Color(100, 105, 115);
        Color subFg = isDark ? new Color(130, 135, 150) : new Color(120, 125, 135);
        Color defaultMainFg = isDark ? new Color(240, 245, 255) : new Color(25, 30, 40);

        if (disconnectedBox != null) {
            disconnectedBox.setBackground(cardBg);
            disconnectedBox.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(cardBorder, 1, true),
                    BorderFactory.createEmptyBorder(32, 48, 32, 48)
            ));
            disconnectedTitle.setForeground(defaultMainFg);
            disconnectedSubtitle.setForeground(subFg);
            disconnectedTargetLabel.setForeground(titleFg);
        }

        for (KpiCardView kpi : kpiCards) {
            kpi.panel.setBackground(cardBg);
            kpi.panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(cardBorder, 1, true),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));
            kpi.titleLabel.setForeground(titleFg);
            kpi.subLabel.setForeground(subFg);
            if (!kpi.customMainColor) {
                kpi.mainLabel.setForeground(defaultMainFg);
            }
        }

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
        private long commits = 0;
        private long rollbacks = 0;
        private long blksHit = 0;
        private long blksRead = 0;
        private int activeConns = 0;
        private int maxConns = 0;
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
            double commitPct = totalTx > 0 ? ((double) commits / totalTx) * 100.0 : 0.0;
            double rollbackPct = totalTx > 0 ? 100.0 - commitPct : 0.0;

            g2.setColor(trackBg);
            g2.fillRoundRect(16, startY + 8, halfW, 20, 8, 8);

            if (commitPct > 0) {
                int commitBarW = (int) Math.round((commitPct / 100.0) * halfW);
                g2.setColor(new Color(34, 197, 94));
                g2.fillRoundRect(16, startY + 8, commitBarW, 20, 8, 8);
            }

            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g2.setColor(subColor);
            g2.drawString(String.format("Commit: %d (%%%.1f)  |  Rollback: %d (%%%.1f)", commits, commitPct, rollbacks, rollbackPct), 16, startY + 44);

            // 2. Cache Hit Efficiency Bar
            int secondY = startY + 65;
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g2.setColor(textColor);
            g2.drawString("Önbellek Verimliliği (Cache vs Disk I/O)", 16, secondY);

            long totalBlks = blksHit + blksRead;
            double hitPct = totalBlks > 0 ? ((double) blksHit / totalBlks) * 100.0 : 0.0;

            g2.setColor(trackBg);
            g2.fillRoundRect(16, secondY + 8, halfW, 20, 8, 8);

            if (hitPct > 0) {
                int hitBarW = (int) Math.round((hitPct / 100.0) * halfW);
                g2.setColor(new Color(59, 130, 246));
                g2.fillRoundRect(16, secondY + 8, hitBarW, 20, 8, 8);
            }

            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g2.setColor(subColor);
            g2.drawString(String.format("Bellekten Okuma: %%%.1f  |  Diskten Okuma: %%%.1f", hitPct, (totalBlks > 0 ? (100.0 - hitPct) : 0.0)), 16, secondY + 44);

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

            double connPct = maxConns > 0 ? ((double) activeConns / maxConns) : 0.0;
            int arcAngle = (int) Math.round(connPct * 360.0);
            g2.setColor(connPct < 0.7 ? new Color(34, 197, 94) : (connPct < 0.9 ? new Color(245, 158, 11) : new Color(239, 68, 68)));
            g2.drawArc(gaugeX, gaugeY, gaugeSize, gaugeSize, 90, -arcAngle);

            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g2.setColor(textColor);
            String pctText = String.format("%%%d", (int) Math.round(connPct * 100.0));
            FontMetrics fm = g2.getFontMetrics();
            int textX = gaugeX + (gaugeSize - fm.stringWidth(pctText)) / 2;
            int textY = gaugeY + (gaugeSize + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(pctText, textX, textY);

            g2.dispose();
        }
    }
}

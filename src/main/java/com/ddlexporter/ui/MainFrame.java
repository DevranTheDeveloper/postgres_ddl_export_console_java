package com.ddlexporter.ui;

import com.ddlexporter.common.config.IConfigurationReader;
import com.ddlexporter.common.logger.ILogger;
import com.ddlexporter.common.scripter.IScripter;
import com.ddlexporter.common.scripter.ScripterBuilder;
import com.ddlexporter.common.writer.FileWriter;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;

import com.ddlexporter.schedule.ScheduledBackupManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {
    private static final String CARD_SCHEMA = "SCHEMA";
    private static final String CARD_ERD = "ERD";
    private static final String CARD_SETTINGS = "SETTINGS";
    private static final String CARD_DIFF = "DIFF";
    private static final String CARD_GIT = "GIT";
    private static final String CARD_METRICS = "METRICS";

    private final ProfileManager profileManager = new ProfileManager();
    private final AuditHistoryManager auditManager = new AuditHistoryManager();
    private final SystemDiagnosticsManager diagnosticsManager = new SystemDiagnosticsManager();
    private final ScheduledBackupManager scheduleManager;
    private final JComboBox<String> profileComboBox = new JComboBox<>();

    private final ConnectionPanel connectionPanel;
    private final SchemaExplorerPanel schemaExplorerPanel;
    private final ErDiagramPanel erDiagramPanel;
    private final DiffViewerPanel diffViewerPanel;
    private final GitSyncPanel gitSyncPanel;
    private final ServerStatusPanel serverStatusPanel;
    private final LogPanel logPanel;

    private final JButton scheduleStatusBtn = new JButton();
    private final JButton updateBtn = new JButton("Güncellemeleri Denetle");
    private final com.ddlexporter.update.UpdateManager updateManager = new com.ddlexporter.update.UpdateManager();
    private com.ddlexporter.update.UpdateManager.ReleaseInfo latestReleaseInfo = null;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentCards = new JPanel(cardLayout);
    private final JPanel navTabBar = new JPanel(new GridLayout(1, 6, 0, 0));
    private final List<JButton> tabButtons = new ArrayList<>();
    private String currentCard = CARD_SCHEMA;

    private final JLabel statusLabel;
    private final JButton toggleLogBtn;
    private final JButton themeToggleBtn;
    private final JPanel topHeader;
    private final JLabel profileLabel;
    private final JLabel titleLabel;
    private final JPanel statusBar;

    private static final String PREF_FILE = "user_preferences.json";
    private boolean isDarkMode;
    private boolean isLogVisible = true;

    public MainFrame() {
        super("PostgreSQL DDL Export Studio - v" + com.ddlexporter.update.UpdateManager.CURRENT_VERSION);

        // macOS Native Properties
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "PostgreSQL DDL Studio");

        this.isDarkMode = loadSavedThemePreference();
        initLookAndFeel(isDarkMode);
        initAppIcon();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1220, 780);
        setMinimumSize(new Dimension(950, 620));
        setLocationRelativeTo(null);

        // Sub-Panels
        connectionPanel = new ConnectionPanel(this::startExportProcess);
        schemaExplorerPanel = new SchemaExplorerPanel();
        erDiagramPanel = new ErDiagramPanel();
        serverStatusPanel = new ServerStatusPanel(connectionPanel::getSettingsFromUi, auditManager, diagnosticsManager);
        serverStatusPanel.setOnSettingsUpdate(s -> {
            connectionPanel.applySettings(s);
            connectionPanel.testConnection();
            erDiagramPanel.loadFromDatabase(s);
            startExportProcess();
        });
        diffViewerPanel = new DiffViewerPanel(profileManager);
        gitSyncPanel = new GitSyncPanel();
        logPanel = new LogPanel();

        // Background Scheduled Backup Engine
        scheduleManager = new ScheduledBackupManager(profileManager, auditManager);
        scheduleManager.setLoggerCallback(logPanel::appendLog);
        scheduleManager.setOnBackupCompletedCallback(() -> SwingUtilities.invokeLater(() -> {
            String outDir = connectionPanel.getOutputDir();
            schemaExplorerPanel.loadExportDirectory(outDir);
            erDiagramPanel.setExportDir(outDir);
            diffViewerPanel.setExportDir(outDir);
            gitSyncPanel.refreshGitStatus();
            serverStatusPanel.loadAuditHistory();
            updateScheduleButtonUi();
        }));
        scheduleManager.restartScheduler();

        // Connect Visual Table Designer to Schema Explorer & ERD panels
        schemaExplorerPanel.setOnNewTableRequested(this::openVisualTableDesigner);
        erDiagramPanel.setOnNewTableRequested(this::openVisualTableDesigner);

        // Connect ERD visual navigation to SQL editor and Diff viewer
        erDiagramPanel.setTableNavigateListener(tableName -> {
            selectTab(CARD_SCHEMA);
            schemaExplorerPanel.openTableFile(tableName);
        });
        erDiagramPanel.setDiffNavigateListener(tableName -> {
            selectTab(CARD_DIFF);
        });

        // Connect SQL save events to sync Diff, ERD, Git, and Audit Log
        schemaExplorerPanel.setOnFileSavedListener(() -> {
            String outDir = connectionPanel.getOutputDir();
            diffViewerPanel.setExportDir(outDir);
            erDiagramPanel.setExportDir(outDir);
            gitSyncPanel.refreshGitStatus();
            PostgresqlConfigurationSettings s = connectionPanel.getSettingsFromUi();
            auditManager.logAction("SQL Dosyası Düzenlendi", s.getUsername(), s.getDatabaseName(), "Şema Gezgininden dosya kaydedildi", 0, true);
            serverStatusPanel.loadAuditHistory();
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            logPanel.appendLog("[" + java.time.LocalDateTime.now().format(dtf) + "] [INFO] SQL dosyası güncellendi ve diske kaydedildi.");
        });

        // Main Container
        JPanel mainContainer = new JPanel(new BorderLayout(0, 0));

        // 1. Top Navbar Header
        topHeader = new JPanel(new BorderLayout(16, 0));
        profileLabel = new JLabel("Profil:");
        titleLabel = new JLabel("PostgreSQL DDL Export Studio");
        themeToggleBtn = new JButton(isDarkMode ? "Açık Tema" : "Koyu Tema");

        setupTopHeader();

        // Top Section combining Header + Full-Width Segmented Tab Navigation Bar
        JPanel topCombinedPanel = new JPanel(new BorderLayout(0, 0));
        topCombinedPanel.add(topHeader, BorderLayout.NORTH);

        setupFullWidthTabBar();
        topCombinedPanel.add(navTabBar, BorderLayout.SOUTH);
        mainContainer.add(topCombinedPanel, BorderLayout.NORTH);

        // 2. Center Content Cards (CardLayout seamlessly attached to tab bar)
        contentCards.add(schemaExplorerPanel, CARD_SCHEMA);
        contentCards.add(erDiagramPanel, CARD_ERD);
        contentCards.add(connectionPanel, CARD_SETTINGS);
        contentCards.add(serverStatusPanel, CARD_METRICS);
        contentCards.add(diffViewerPanel, CARD_DIFF);
        contentCards.add(gitSyncPanel, CARD_GIT);

        // Vertical Split between Content Cards and Log Panel
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, contentCards, logPanel);
        mainSplit.setDividerLocation(480);
        mainSplit.setResizeWeight(0.7);
        mainSplit.setBorder(null);
        mainContainer.add(mainSplit, BorderLayout.CENTER);

        // 3. Bottom Status Bar (Centered and Balanced)
        statusBar = new JPanel(new BorderLayout(12, 0));
        statusBar.setPreferredSize(new Dimension(0, 36));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 215, 225)),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)));

        statusLabel = new JLabel("Hazır | PostgreSQL DDL Studio aktif.");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        statusBar.add(statusLabel, BorderLayout.WEST);

        toggleLogBtn = new JButton("Logları Gizle");
        toggleLogBtn.setFont(toggleLogBtn.getFont().deriveFont(Font.PLAIN, 11f));
        toggleLogBtn.setFocusable(false);
        toggleLogBtn.addActionListener(e -> {
            isLogVisible = !isLogVisible;
            logPanel.setVisible(isLogVisible);
            toggleLogBtn.setText(isLogVisible ? "Logları Gizle" : "Logları Göster");
            mainSplit.setDividerLocation(isLogVisible ? 480 : 720);
        });
        statusBar.add(toggleLogBtn, BorderLayout.EAST);

        mainContainer.add(statusBar, BorderLayout.SOUTH);
        setContentPane(mainContainer);

        // Initial Data & Theme Application
        applyCustomTheme(isDarkMode);
        selectTab(CARD_SCHEMA);
        loadInitialData();
    }

    private void setupTopHeader() {
        topHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(215, 220, 230)),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));

        // Left Branding & Profile Selector Area
        JPanel leftBrandProfilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftBrandProfilePanel.setOpaque(false);

        titleLabel.setText("PostgreSQL DDL Studio");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        leftBrandProfilePanel.add(titleLabel);

        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 20));
        leftBrandProfilePanel.add(sep);

        profileLabel.setFont(profileLabel.getFont().deriveFont(Font.BOLD, 12f));
        leftBrandProfilePanel.add(profileLabel);

        profileComboBox.setPreferredSize(new Dimension(175, 28));
        profileComboBox.setFont(profileComboBox.getFont().deriveFont(Font.PLAIN, 12f));
        profileComboBox.addActionListener(e -> onProfileSelected());
        leftBrandProfilePanel.add(profileComboBox);

        JButton profileMenuBtn = new JButton("Yönet ▾");
        profileMenuBtn.setFont(profileMenuBtn.getFont().deriveFont(Font.BOLD, 11f));
        profileMenuBtn.setToolTipText("Profil yönetim seçenekleri (Yeni Ekle, Çoğalt, Sil)");
        profileMenuBtn.addActionListener(e -> showProfileMenu(profileMenuBtn));
        leftBrandProfilePanel.add(profileMenuBtn);

        topHeader.add(leftBrandProfilePanel, BorderLayout.WEST);

        // Right Utility Actions (Clean, concise & icon-enhanced)
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControls.setOpaque(false);

        // Scheduled Backup Status & Dialog Button
        scheduleStatusBtn.setFont(scheduleStatusBtn.getFont().deriveFont(Font.BOLD, 11f));
        scheduleStatusBtn.setToolTipText("Otomatik Zamanlanmış DDL Yedekleme Yapılandırması");
        scheduleStatusBtn.setFocusable(false);
        scheduleStatusBtn.addActionListener(e -> {
            new ScheduledBackupDialog(this, scheduleManager, profileManager, this::updateScheduleButtonUi).setVisible(true);
        });
        rightControls.add(scheduleStatusBtn);

        // Update Button (Live Hot-Updater)
        updateBtn.setFont(updateBtn.getFont().deriveFont(Font.BOLD, 11f));
        updateBtn.setToolTipText("Canlı Güncellemeleri Denetle & Yükle");
        updateBtn.setFocusable(false);
        updateBtn.addActionListener(e -> checkForUpdates(true));
        rightControls.add(updateBtn);

        // Shortcuts & Help Guide Button
        JButton shortcutsBtn = new JButton("Kısayollar");
        shortcutsBtn.setFont(shortcutsBtn.getFont().deriveFont(Font.PLAIN, 11f));
        shortcutsBtn.setToolTipText("Klavye kısayolları ve kullanım ipuçları");
        shortcutsBtn.setFocusable(false);
        shortcutsBtn.addActionListener(e -> new HelpShortcutsDialog(this).setVisible(true));
        rightControls.add(shortcutsBtn);

        // Theme Toggle Button
        themeToggleBtn.setFont(themeToggleBtn.getFont().deriveFont(Font.BOLD, 11f));
        themeToggleBtn.setFocusable(false);
        themeToggleBtn.addActionListener(e -> toggleTheme());
        rightControls.add(themeToggleBtn);

        topHeader.add(rightControls, BorderLayout.EAST);
    }

    private void setupFullWidthTabBar() {
        navTabBar.setPreferredSize(new Dimension(0, 38));
        navTabBar.removeAll();
        tabButtons.clear();

        tabButtons.add(createTabButton("Şema ve SQL Gezgini", CARD_SCHEMA));
        tabButtons.add(createTabButton("İlişki Haritası (ERD)", CARD_ERD));
        tabButtons.add(createTabButton("Bağlantı Ayarları", CARD_SETTINGS));
        tabButtons.add(createTabButton("Sunucu Durumu", CARD_METRICS));
        tabButtons.add(createTabButton("Şema Farkı (Diff)", CARD_DIFF));
        tabButtons.add(createTabButton("Git ve GitHub", CARD_GIT));

        for (JButton btn : tabButtons) {
            navTabBar.add(btn);
        }
    }

    private JButton createTabButton(String title, String cardKey) {
        JButton btn = new JButton(title);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(true);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> selectTab(cardKey));
        btn.putClientProperty("cardKey", cardKey);
        return btn;
    }

    private void selectTab(String cardKey) {
        this.currentCard = cardKey;
        cardLayout.show(contentCards, cardKey);
        updateTabBarStyles();

        if (CARD_METRICS.equals(cardKey)) {
            serverStatusPanel.refreshMetrics();
        }
    }

    private void updateTabBarStyles() {
        Color activeBg = isDarkMode ? new Color(36, 40, 50) : Color.WHITE;
        Color activeFg = isDarkMode ? new Color(245, 247, 250) : new Color(25, 30, 40);
        Color inactiveBg = isDarkMode ? new Color(22, 24, 30) : new Color(240, 243, 246);
        Color inactiveFg = isDarkMode ? new Color(145, 152, 165) : new Color(90, 100, 115);
        Color borderColor = isDarkMode ? new Color(45, 50, 60) : new Color(220, 224, 230);
        Color topPressedColor = isDarkMode ? new Color(100, 110, 125) : new Color(175, 185, 195);

        navTabBar.setBackground(inactiveBg);
        navTabBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor));

        for (int i = 0; i < tabButtons.size(); i++) {
            JButton btn = tabButtons.get(i);
            String cardKey = (String) btn.getClientProperty("cardKey");
            boolean isActive = cardKey.equals(currentCard);

            btn.setBackground(isActive ? activeBg : inactiveBg);
            btn.setForeground(isActive ? activeFg : inactiveFg);

            int rightBorder = (i == tabButtons.size() - 1) ? 0 : 1;
            if (isActive) {
                // Subtle gray top pressed highlight & seamless bottom
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(2, 0, 0, rightBorder, topPressedColor),
                        BorderFactory.createEmptyBorder(6, 16, 8, 16)));
            } else {
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, rightBorder, borderColor),
                        BorderFactory.createEmptyBorder(8, 16, 8, 16)));
            }
        }
    }

    public static boolean isSystemDarkMode() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) {
                Process p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start();
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                    String line = reader.readLine();
                    if ("Dark".equalsIgnoreCase(line)) {
                        return true;
                    }
                }
            } else if (os.contains("linux")) {
                Process p = new ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").start();
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && line.contains("dark")) {
                        return true;
                    }
                }
            } else if (os.contains("win")) {
                Process p = new ProcessBuilder("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme").start();
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("AppsUseLightTheme") && line.contains("0x0")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    private static boolean loadSavedThemePreference() {
        try {
            File f = com.ddlexporter.common.util.AppPathHelper.getConfigFile(PREF_FILE);
            if (f.exists()) {
                String content = java.nio.file.Files.readString(f.toPath());
                if (content.contains("\"darkMode\": true") || content.contains("\"darkMode\":true")) {
                    return true;
                } else if (content.contains("\"darkMode\": false") || content.contains("\"darkMode\":false")) {
                    return false;
                }
            }
        } catch (Exception ignored) {}
        return isSystemDarkMode();
    }

    private static void saveThemePreference(boolean dark) {
        try {
            String json = "{\n  \"darkMode\": " + dark + "\n}\n";
            File f = com.ddlexporter.common.util.AppPathHelper.getConfigFile(PREF_FILE);
            java.nio.file.Files.writeString(f.toPath(), json);
        } catch (Exception ignored) {}
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        saveThemePreference(isDarkMode);
        themeToggleBtn.setText(isDarkMode ? "Açık Tema" : "Koyu Tema");
        initLookAndFeel(isDarkMode);
        SwingUtilities.updateComponentTreeUI(this);
        applyCustomTheme(isDarkMode);
    }

    private void applyCustomTheme(boolean dark) {
        if (dark) {
            topHeader.setBackground(new Color(28, 30, 36));
            topHeader.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(48, 52, 62)),
                    BorderFactory.createEmptyBorder(10, 18, 10, 18)));
            titleLabel.setForeground(new Color(240, 245, 255));
            profileLabel.setForeground(new Color(210, 215, 225));
            statusBar.setBackground(new Color(28, 30, 36));
            statusBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(48, 52, 62)),
                    BorderFactory.createEmptyBorder(6, 16, 6, 16)));
            contentCards.setBackground(new Color(28, 30, 36));
        } else {
            topHeader.setBackground(new Color(245, 247, 250));
            topHeader.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(215, 220, 230)),
                    BorderFactory.createEmptyBorder(10, 18, 10, 18)));
            titleLabel.setForeground(new Color(25, 30, 40));
            profileLabel.setForeground(new Color(30, 35, 45));
            statusBar.setBackground(new Color(245, 247, 250));
            statusBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 215, 225)),
                    BorderFactory.createEmptyBorder(6, 16, 6, 16)));
            contentCards.setBackground(new Color(245, 247, 250));
        }

        updateTabBarStyles();
        schemaExplorerPanel.applyTheme(dark);
        erDiagramPanel.applyTheme(dark);
        serverStatusPanel.applyTheme(dark);
        logPanel.applyTheme(dark);
        diffViewerPanel.applyTheme(dark);
    }

    private void initLookAndFeel(boolean dark) {
        try {
            boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
            if (isMac) {
                if (dark) {
                    com.formdev.flatlaf.themes.FlatMacDarkLaf.setup();
                } else {
                    com.formdev.flatlaf.themes.FlatMacLightLaf.setup();
                }
            } else {
                if (dark) {
                    com.formdev.flatlaf.FlatDarkLaf.setup();
                } else {
                    com.formdev.flatlaf.FlatLightLaf.setup();
                }
            }
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }
    }

    private void updateScheduleButtonUi() {
        ScheduledBackupManager.ScheduleConfig cfg = scheduleManager.getConfig();
        if (cfg.enabled) {
            scheduleStatusBtn.setText("Oto-Yedek: " + cfg.intervalMinutes + " dk (Aktif)");
            scheduleStatusBtn.setForeground(new Color(22, 163, 74));
        } else {
            scheduleStatusBtn.setText("Oto-Yedek: Kapalı");
            scheduleStatusBtn.setForeground(null);
        }
    }

    private void loadInitialData() {
        refreshProfileDropdown();
        updateScheduleButtonUi();

        File exportDir = new File(connectionPanel.getOutputDir());
        int count = 0;
        if (exportDir.exists()) {
            schemaExplorerPanel.loadExportDirectory(exportDir.getAbsolutePath());
            erDiagramPanel.setExportDir(exportDir.getAbsolutePath());
            diffViewerPanel.setExportDir(exportDir.getAbsolutePath());
            File[] files = exportDir.listFiles((dir, name) -> name.endsWith(".sql"));
            if (files != null) count = files.length;
        }

        // Standard System Startup Log
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String now = java.time.LocalDateTime.now().format(dtf);
        logPanel.appendLog("[" + now + "] [INFO] PostgreSQL DDL Export Studio hazır.");
        if (count > 0) {
            logPanel.appendLog("[" + now + "] [INFO] Mevcut çıktı dizini yüklendi (" + count + " adet DDL dosyası aktif).");
        }

        // Check for updates asynchronously in the background on startup
        checkForUpdates(false);
    }

    private void checkForUpdates(boolean userInitiated) {
        if (userInitiated) {
            updateBtn.setEnabled(false);
            updateBtn.setText("Denetleniyor...");
        }

        new SwingWorker<com.ddlexporter.update.UpdateManager.ReleaseInfo, Void>() {
            private Exception error = null;

            @Override
            protected com.ddlexporter.update.UpdateManager.ReleaseInfo doInBackground() {
                try {
                    return updateManager.checkLatestRelease();
                } catch (Exception ex) {
                    error = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                updateBtn.setEnabled(true);
                com.ddlexporter.update.UpdateManager.ReleaseInfo info = null;
                try {
                    info = get();
                } catch (Exception ignored) {}

                if (info != null && info.updateAvailable) {
                    latestReleaseInfo = info;
                    updateBtn.setText(info.tagName + " Güncelle");
                    updateBtn.setForeground(new Color(34, 197, 94));
                    statusLabel.setText("Yeni Sürüm (" + info.tagName + ") hazır. Güncellemek için tıklayın.");
                    logPanel.appendLog("[GÜNCELLEME] Yeni sürüm (" + info.tagName + ") bulundu.");
                    if (userInitiated) {
                        new UpdateDialog(MainFrame.this, info, updateManager).setVisible(true);
                    }
                } else if (info != null) {
                    updateBtn.setText("Güncellemeleri Denetle");
                    updateBtn.setForeground(null);
                    if (userInitiated) {
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Tebrikler! PostgreSQL DDL Studio'nun en güncel sürümünü (v" + com.ddlexporter.update.UpdateManager.CURRENT_VERSION + ") kullanıyorsunuz.",
                                "Uygulama Güncel", JOptionPane.INFORMATION_MESSAGE);
                    }
                } else {
                    updateBtn.setText("Güncellemeleri Denetle");
                    updateBtn.setForeground(null);
                    if (userInitiated) {
                        String msg = error != null ? error.getMessage() : "Bilinmeyen ağ hatası.";
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Güncellemeler kontrol edilemedi:\n" + msg,
                                "Ağ Hatası", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    private void refreshProfileDropdown() {
        profileComboBox.removeAllItems();
        var profiles = profileManager.getProfiles();
        for (String name : profiles.keySet()) {
            profileComboBox.addItem(name);
        }
        if (diffViewerPanel != null) {
            diffViewerPanel.refreshProfiles();
        }
    }

    private void onProfileSelected() {
        String selected = (String) profileComboBox.getSelectedItem();
        if (selected != null) {
            PostgresqlConfigurationSettings settings = profileManager.getProfile(selected);
            if (settings != null) {
                connectionPanel.loadSettingsToUi(settings);
                statusLabel.setText("Profil: " + selected + " (" + settings.getServerHost() + ":"
                        + settings.getPort() + "/" + settings.getDatabaseName() + ")");
                schemaExplorerPanel.focusDatabase(settings.getDatabaseName());
                erDiagramPanel.setDatabase(connectionPanel.getOutputDir(), settings.getDatabaseName());
                erDiagramPanel.loadFromDatabase(settings);
                serverStatusPanel.showDisconnectedState(null);
            }
        }
    }

    private void createNewProfile() {
        String name = JOptionPane.showInputDialog(this, "Yeni profil adını girin (Örn: Production, Staging):",
                "Yeni Profil", JOptionPane.QUESTION_MESSAGE);
        if (name != null && !name.isBlank()) {
            PostgresqlConfigurationSettings settings = connectionPanel.getSettingsFromUi();
            profileManager.addOrUpdateProfile(name.trim(), settings);
            refreshProfileDropdown();
            profileComboBox.setSelectedItem(name.trim());
            JOptionPane.showMessageDialog(this, "'" + name + "' profili başarıyla kaydedildi!", "Başarılı",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showProfileMenu(Component invoker) {
        String selected = (String) profileComboBox.getSelectedItem();
        JPopupMenu menu = new JPopupMenu();

        // Rounded border and internal padding
        Color popupBorderColor = isDarkMode ? new Color(60, 65, 75) : new Color(210, 215, 225);
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(popupBorderColor, 1, true),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        JMenuItem itemSave = new JMenuItem("Seçili Profili Kaydet / Güncelle ('" + selected + "')");
        itemSave.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        itemSave.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        itemSave.setForeground(new Color(22, 163, 74));
        itemSave.addActionListener(e -> {
            if (selected != null) {
                PostgresqlConfigurationSettings s = connectionPanel.getSettingsFromUi();
                profileManager.addOrUpdateProfile(selected, s);
                statusLabel.setText("Profil: " + selected + " güncellendi (" + s.getServerHost() + ":" + s.getPort() + "/" + s.getDatabaseName() + ")");
                JOptionPane.showMessageDialog(this, "'" + selected + "' profil ayarları başarıyla kaydedildi!", "Kaydedildi", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        menu.add(itemSave);

        menu.addSeparator();

        JMenuItem itemNew = new JMenuItem("Yeni Profil Oluştur...");
        itemNew.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        itemNew.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        itemNew.addActionListener(e -> createNewProfile());
        menu.add(itemNew);

        JMenuItem itemDuplicate = new JMenuItem("Mevcut Profili Çoğalt...");
        itemDuplicate.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        itemDuplicate.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        itemDuplicate.addActionListener(e -> {
            if (selected != null) {
                String copyName = JOptionPane.showInputDialog(this, "'" + selected + "' profilinin kopyası için yeni ad girin:", selected + "_Kopya");
                if (copyName != null && !copyName.isBlank()) {
                    PostgresqlConfigurationSettings settings = profileManager.getProfile(selected);
                    if (settings != null) {
                        profileManager.addOrUpdateProfile(copyName.trim(), settings);
                        refreshProfileDropdown();
                        profileComboBox.setSelectedItem(copyName.trim());
                    }
                }
            }
        });
        menu.add(itemDuplicate);

        menu.addSeparator();

        JMenuItem itemDelete = new JMenuItem("Seçili Profili Sil ('" + selected + "')...");
        itemDelete.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        itemDelete.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        itemDelete.setForeground(new Color(220, 53, 69)); // Red safety accent
        itemDelete.addActionListener(e -> deleteSelectedProfile());
        menu.add(itemDelete);

        // Show with a comfortable 4px gap below the button
        menu.show(invoker, 0, invoker.getHeight() + 4);
    }

    private void deleteSelectedProfile() {
        String selected = (String) profileComboBox.getSelectedItem();
        if (selected == null) return;

        if ("Default".equalsIgnoreCase(selected)) {
            JOptionPane.showMessageDialog(this,
                    "Varsayılan 'Default' profili sistem gereksinimidir ve silinemez.",
                    "İşlem Engellendi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "'" + selected + "' profilini kalıcı olarak silmek istediğinize emin misiniz?\nBu işlem geri alınamaz.",
                "Profili Silme Onayı",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            profileManager.deleteProfile(selected);
            refreshProfileDropdown();
            JOptionPane.showMessageDialog(this,
                    "'" + selected + "' profili başarıyla silindi.",
                    "Silindi", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void startExportProcess() {
        PostgresqlConfigurationSettings settings = connectionPanel.getSettingsFromUi();
        String outputDir = connectionPanel.getOutputDir();

        String currentProfile = (String) profileComboBox.getSelectedItem();
        if (currentProfile != null) {
            profileManager.addOrUpdateProfile(currentProfile, settings);
        }

        connectionPanel.setExporting(true);
        logPanel.setProgressIndeterminate(true, "DDL Dışa Aktarma İşlemi Başlatıldı...");
        statusLabel.setText("DDL çıkarma işlemi devam ediyor...");

        long startTime = System.currentTimeMillis();
        ILogger guiLogger = new GuiLogger(logPanel::appendLog);

        new SwingWorker<Void, Void>() {
            private Exception exportError = null;

            @Override
            protected Void doInBackground() {
                try {
                    IConfigurationReader memoryReader = new IConfigurationReader() {
                        @Override
                        public <T> T read(Class<T> clazz) {
                            return clazz.cast(settings);
                        }
                    };

                    IScripter scripter = ScripterBuilder.get("POSTGRESQL")
                            .addConfigurationReader(memoryReader)
                            .addWriter(new FileWriter(outputDir, guiLogger))
                            .addLogger(guiLogger)
                            .build();

                    scripter.execute();
                } catch (Exception ex) {
                    exportError = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                connectionPanel.setExporting(false);
                long duration = System.currentTimeMillis() - startTime;
                if (exportError == null) {
                    auditManager.logAction("DDL Dışa Aktarma", settings.getUsername(), settings.getDatabaseName() + "/" + settings.getSchema(), "Tüm şema başarıyla çıkarıldı (" + outputDir + ")", duration, true);
                    serverStatusPanel.loadAuditHistory();
                    logPanel.setProgress(100, "DDL Aktarımı Başarıyla Tamamlandı!");
                    statusLabel.setText("DDL Başarıyla Aktarıldı! (" + outputDir + ")");
                    schemaExplorerPanel.loadExportDirectory(outputDir);
                    erDiagramPanel.setExportDir(outputDir);
                    diffViewerPanel.setExportDir(outputDir);
                    gitSyncPanel.refreshGitStatus();
                    selectTab(CARD_SCHEMA);

                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Tüm PostgreSQL veritabanı DDL script'leri başarıyla dışa aktarıldı!",
                            "İşlem Başarılı", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    auditManager.logAction("DDL Dışa Aktarma", settings.getUsername(), settings.getDatabaseName() + "/" + settings.getSchema(), "Hata: " + exportError.getMessage(), duration, false);
                    diagnosticsManager.addIssue(SystemDiagnosticsManager.Level.ERROR,
                            "DDL Dışa Aktarma Başarısız Oldu",
                            "DDL Scripter Motoru",
                            "Dışa aktarma işlemi sırasında hata fırlatıldı:\n" + exportError.getMessage(),
                            "Veritabanı izinlerini ve tablo erişim haklarını doğrulayın.");
                    serverStatusPanel.loadAuditHistory();
                    serverStatusPanel.updateDiagnosticsUi();
                    logPanel.setProgress(0, "Hata Oluştu!");
                    statusLabel.setText("Hata: " + exportError.getMessage());
                    logPanel.appendLog("[HATA] " + exportError.getMessage());
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Dışa aktarma sırasında hata oluştu:\n" + exportError.getMessage(),
                            "Hata", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void openVisualTableDesigner() {
        String outDir = connectionPanel.getOutputDir();
        File exportDirFile = (outDir != null && !outDir.isBlank()) ? new File(outDir) : new File("export_output");

        VisualTableDesignerDialog dialog = new VisualTableDesignerDialog(
                this,
                connectionPanel::getSettingsFromUi,
                exportDirFile,
                () -> SwingUtilities.invokeLater(() -> {
                    // On table created / saved:
                    schemaExplorerPanel.loadExportDirectory(exportDirFile.getAbsolutePath());
                    erDiagramPanel.setExportDir(exportDirFile.getAbsolutePath());
                    PostgresqlConfigurationSettings settings = connectionPanel.getSettingsFromUi();
                    erDiagramPanel.loadFromDatabase(settings);
                    diffViewerPanel.setExportDir(exportDirFile.getAbsolutePath());
                    gitSyncPanel.refreshGitStatus();
                    serverStatusPanel.loadAuditHistory();
                    java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    logPanel.appendLog("[" + java.time.LocalDateTime.now().format(dtf) + "] [INFO] Yeni tablo başarıyla oluşturuldu ve şemaya senkronize edildi.");
                })
        );
        dialog.setVisible(true);
    }

    private void initAppIcon() {
        try {
            java.awt.Image icon = null;
            var res = getClass().getResourceAsStream("/app_icon.png");
            if (res != null) {
                icon = javax.imageio.ImageIO.read(res);
            } else {
                File localImg = new File("src/main/resources/app_icon.png");
                if (localImg.exists()) {
                    icon = javax.imageio.ImageIO.read(localImg);
                }
            }

            if (icon != null) {
                setIconImage(icon);

                if (Taskbar.isTaskbarSupported()) {
                    Taskbar taskbar = Taskbar.getTaskbar();
                    if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                        taskbar.setIconImage(icon);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}

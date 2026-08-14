package com.ddlexporter.ui;

import com.ddlexporter.common.config.IConfigurationReader;
import com.ddlexporter.common.logger.ILogger;
import com.ddlexporter.common.scripter.IScripter;
import com.ddlexporter.common.scripter.ScripterBuilder;
import com.ddlexporter.common.writer.FileWriter;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class MainFrame extends JFrame {
    private final ProfileManager profileManager = new ProfileManager();
    private final JComboBox<String> profileComboBox = new JComboBox<>();

    private final ConnectionPanel connectionPanel;
    private final SchemaExplorerPanel schemaExplorerPanel;
    private final DiffViewerPanel diffViewerPanel;
    private final GitSyncPanel gitSyncPanel;
    private final LogPanel logPanel;

    private final JTabbedPane tabbedPane;
    private final JLabel statusLabel;
    private final JButton toggleLogBtn;
    private final JButton themeToggleBtn;
    private final JPanel topHeader;
    private final JLabel profileLabel;
    private final JLabel titleLabel;

    private boolean isDarkMode = false; // Default: Clean Light Theme as requested
    private boolean isLogVisible = true;

    public MainFrame() {
        super("🐘 PostgreSQL DDL Export Studio - v2.0.0");

        // macOS Native Properties
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "PostgreSQL DDL Studio");

        initLookAndFeel(isDarkMode);
        initAppIcon();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1220, 780);
        setMinimumSize(new Dimension(950, 620));
        setLocationRelativeTo(null);

        // Sub-Panels
        connectionPanel = new ConnectionPanel(this::startExportProcess);
        schemaExplorerPanel = new SchemaExplorerPanel();
        diffViewerPanel = new DiffViewerPanel();
        gitSyncPanel = new GitSyncPanel();
        logPanel = new LogPanel();

        // Main Container
        JPanel mainContainer = new JPanel(new BorderLayout(0, 0));

        // 1. Top Navbar Header
        topHeader = new JPanel(new BorderLayout(16, 0));
        profileLabel = new JLabel("🗄️ Profil:");
        titleLabel = new JLabel("PostgreSQL DDL Export Studio");
        themeToggleBtn = new JButton("🌙 Koyu Mod");

        setupTopHeader();
        mainContainer.add(topHeader, BorderLayout.NORTH);

        // 2. Center Tabs (Spacious Modern Design)
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(Font.BOLD, 13f));
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        tabbedPane.addTab("🗂️ Şema & SQL Gezgini", schemaExplorerPanel);
        tabbedPane.addTab("🔌 Bağlantı Ayarları", connectionPanel);
        tabbedPane.addTab("🔄 Şema Farkı (Diff)", diffViewerPanel);
        tabbedPane.addTab("🐙 Git & GitHub", gitSyncPanel);

        // Vertical Split
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedPane, logPanel);
        mainSplit.setDividerLocation(480);
        mainSplit.setResizeWeight(0.7);
        mainSplit.setBorder(null);
        mainContainer.add(mainSplit, BorderLayout.CENTER);

        // 3. Bottom Status Bar (Perfectly Centered and Aligned)
        JPanel statusBar = new JPanel(new BorderLayout(12, 0));
        statusBar.setPreferredSize(new Dimension(0, 36));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 215, 225)),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));

        statusLabel = new JLabel("🟢 Hazır | PostgreSQL DDL Studio aktif.");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        statusBar.add(statusLabel, BorderLayout.WEST);

        toggleLogBtn = new JButton("📊 Logları Gizle");
        toggleLogBtn.setFont(toggleLogBtn.getFont().deriveFont(Font.PLAIN, 11f));
        toggleLogBtn.setFocusable(false);
        toggleLogBtn.addActionListener(e -> {
            isLogVisible = !isLogVisible;
            logPanel.setVisible(isLogVisible);
            toggleLogBtn.setText(isLogVisible ? "📊 Logları Gizle" : "📊 Logları Göster");
            mainSplit.setDividerLocation(isLogVisible ? 480 : 720);
        });
        statusBar.add(toggleLogBtn, BorderLayout.EAST);

        mainContainer.add(statusBar, BorderLayout.SOUTH);
        setContentPane(mainContainer);

        // Initial Data & Theme Application
        applyCustomTheme(isDarkMode);
        loadInitialData();
    }

    private void setupTopHeader() {
        topHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(215, 220, 230)),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)
        ));

        // Left Branding
        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brandPanel.setOpaque(false);
        JLabel logo = new JLabel("🐘");
        logo.setFont(logo.getFont().deriveFont(22f));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        brandPanel.add(logo);
        brandPanel.add(titleLabel);
        topHeader.add(brandPanel, BorderLayout.WEST);

        // Right Controls: Profile & Theme Switcher
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightControls.setOpaque(false);

        profileLabel.setFont(profileLabel.getFont().deriveFont(Font.BOLD, 12f));
        rightControls.add(profileLabel);

        profileComboBox.setPreferredSize(new Dimension(220, 30));
        profileComboBox.setFont(profileComboBox.getFont().deriveFont(Font.PLAIN, 12f));
        profileComboBox.addActionListener(e -> onProfileSelected());
        rightControls.add(profileComboBox);

        JButton newProfileBtn = new JButton("➕ Yeni Profil");
        newProfileBtn.setFont(newProfileBtn.getFont().deriveFont(Font.BOLD, 12f));
        newProfileBtn.addActionListener(e -> createNewProfile());
        rightControls.add(newProfileBtn);

        JButton delProfileBtn = new JButton("🗑️");
        delProfileBtn.setToolTipText("Seçili profili sil");
        delProfileBtn.addActionListener(e -> deleteSelectedProfile());
        rightControls.add(delProfileBtn);

        // Theme Toggle Button
        themeToggleBtn.setFont(themeToggleBtn.getFont().deriveFont(Font.BOLD, 12f));
        themeToggleBtn.setFocusable(false);
        themeToggleBtn.addActionListener(e -> toggleTheme());
        rightControls.add(themeToggleBtn);

        topHeader.add(rightControls, BorderLayout.EAST);
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        themeToggleBtn.setText(isDarkMode ? "☀️ Açık Mod" : "🌙 Koyu Mod");
        initLookAndFeel(isDarkMode);
        applyCustomTheme(isDarkMode);
        SwingUtilities.updateComponentTreeUI(this);
    }

    private void applyCustomTheme(boolean dark) {
        if (dark) {
            topHeader.setBackground(new Color(24, 26, 32));
            topHeader.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 55, 65)),
                    BorderFactory.createEmptyBorder(10, 18, 10, 18)
            ));
            titleLabel.setForeground(new Color(235, 240, 250));
            profileLabel.setForeground(new Color(200, 205, 215));
        } else {
            topHeader.setBackground(new Color(245, 247, 250));
            topHeader.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(215, 220, 230)),
                    BorderFactory.createEmptyBorder(10, 18, 10, 18)
            ));
            titleLabel.setForeground(new Color(25, 30, 40));
            profileLabel.setForeground(new Color(30, 35, 45)); // High contrast, clearly visible!
        }

        schemaExplorerPanel.applyTheme(dark);
        logPanel.applyTheme(dark);
        diffViewerPanel.applyTheme(dark);
    }

    private void initLookAndFeel(boolean dark) {
        try {
            String lafClassName = dark ? "com.formdev.flatlaf.FlatDarkLaf" : "com.formdev.flatlaf.FlatLightLaf";
            Class<?> flatLafClass = Class.forName(lafClassName);
            LookAndFeel laf = (LookAndFeel) flatLafClass.getDeclaredConstructor().newInstance();
            UIManager.setLookAndFeel(laf);
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }
    }

    private void loadInitialData() {
        refreshProfileDropdown();

        File exportDir = new File(connectionPanel.getOutputDir());
        if (exportDir.exists()) {
            schemaExplorerPanel.loadExportDirectory(exportDir.getAbsolutePath());
            diffViewerPanel.setExportDir(exportDir.getAbsolutePath());
        }
    }

    private void refreshProfileDropdown() {
        profileComboBox.removeAllItems();
        var profiles = profileManager.getProfiles();
        for (String name : profiles.keySet()) {
            profileComboBox.addItem(name);
        }
    }

    private void onProfileSelected() {
        String selected = (String) profileComboBox.getSelectedItem();
        if (selected != null) {
            PostgresqlConfigurationSettings settings = profileManager.getProfile(selected);
            if (settings != null) {
                connectionPanel.loadSettingsToUi(settings);
                statusLabel.setText("🟢 Profil: " + selected + " (" + settings.getServerHost() + ":" + settings.getPort() + "/" + settings.getDatabaseName() + ")");
            }
        }
    }

    private void createNewProfile() {
        String name = JOptionPane.showInputDialog(this, "Yeni profil adını girin (Örn: Production, Staging):", "Yeni Profil", JOptionPane.QUESTION_MESSAGE);
        if (name != null && !name.isBlank()) {
            PostgresqlConfigurationSettings settings = connectionPanel.getSettingsFromUi();
            profileManager.addOrUpdateProfile(name.trim(), settings);
            refreshProfileDropdown();
            profileComboBox.setSelectedItem(name.trim());
            JOptionPane.showMessageDialog(this, "'" + name + "' profili başarıyla kaydedildi!", "Başarılı", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void deleteSelectedProfile() {
        String selected = (String) profileComboBox.getSelectedItem();
        if (selected != null) {
            int confirm = JOptionPane.showConfirmDialog(this, "'" + selected + "' profilini silmek istediğinize emin misiniz?", "Profili Sil", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                profileManager.deleteProfile(selected);
                refreshProfileDropdown();
            }
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
        statusLabel.setText("⏳ DDL çıkarma işlemi devam ediyor...");

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
                if (exportError == null) {
                    logPanel.setProgress(100, "✅ DDL Aktarımı Başarıyla Tamamlandı!");
                    statusLabel.setText("✅ DDL Başarıyla Aktarıldı! (" + outputDir + ")");
                    schemaExplorerPanel.loadExportDirectory(outputDir);
                    diffViewerPanel.setExportDir(outputDir);
                    gitSyncPanel.refreshGitStatus();
                    tabbedPane.setSelectedIndex(0);

                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Tüm PostgreSQL veritabanı DDL script'leri başarıyla dışa aktarıldı!",
                            "İşlem Başarılı", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    logPanel.setProgress(0, "❌ Hata Oluştu!");
                    statusLabel.setText("❌ Hata: " + exportError.getMessage());
                    logPanel.appendLog("[HATA] " + exportError.getMessage());
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Dışa aktarma sırasında hata oluştu:\n" + exportError.getMessage(),
                            "Hata", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void initAppIcon() {
        try {
            int size = 128;
            java.awt.image.BufferedImage icon = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = icon.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            GradientPaint gp = new GradientPaint(0, 0, new Color(41, 128, 185), size, size, new Color(44, 62, 80));
            g2.setPaint(gp);
            g2.fillRoundRect(8, 8, size - 16, size - 16, 28, 28);

            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 64));
            FontMetrics fm = g2.getFontMetrics();
            String emoji = "🐘";
            int x = (size - fm.stringWidth(emoji)) / 2;
            int y = (size - fm.getHeight()) / 2 + fm.getAscent() - 2;
            g2.drawString(emoji, x, y);
            g2.dispose();

            setIconImage(icon);

            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(icon);
                }
            }
        } catch (Throwable ignored) {}
    }
}

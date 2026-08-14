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
    private boolean isLogVisible = true;

    public MainFrame() {
        super("🐘 PostgreSQL DDL Export Studio - v2.0.0");

        // macOS Native Properties
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "PostgreSQL DDL Studio");

        initLookAndFeel();
        initAppIcon();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        // Sub-Panels
        connectionPanel = new ConnectionPanel(this::startExportProcess);
        schemaExplorerPanel = new SchemaExplorerPanel();
        diffViewerPanel = new DiffViewerPanel();
        gitSyncPanel = new GitSyncPanel();
        logPanel = new LogPanel();

        // Main Container
        JPanel mainContainer = new JPanel(new BorderLayout(0, 0));

        // 1. Top Header with Profile Management
        JPanel topHeader = createTopHeader();
        mainContainer.add(topHeader, BorderLayout.NORTH);

        // 2. Center Tabs
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(Font.BOLD, 13f));
        tabbedPane.addTab("🗂️ Şema & SQL Gezgini", schemaExplorerPanel);
        tabbedPane.addTab("🔌 Bağlantı Ayarları", connectionPanel);
        tabbedPane.addTab("🔄 Şema Farkı (Diff)", diffViewerPanel);
        tabbedPane.addTab("🐙 Git & GitHub", gitSyncPanel);

        // Vertical Split: Tabs on Top, Live Log Console on Bottom
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedPane, logPanel);
        mainSplit.setDividerLocation(480);
        mainSplit.setResizeWeight(0.7);
        mainContainer.add(mainSplit, BorderLayout.CENTER);

        // 3. Bottom Status Bar
        JPanel statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(55, 55, 55)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));

        statusLabel = new JLabel("🟢 Hazır | PostgreSQL DDL Studio aktif.");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusBar.add(statusLabel, BorderLayout.WEST);

        toggleLogBtn = new JButton("📊 Logları Gizle");
        toggleLogBtn.setFont(toggleLogBtn.getFont().deriveFont(Font.PLAIN, 11f));
        toggleLogBtn.addActionListener(e -> {
            isLogVisible = !isLogVisible;
            logPanel.setVisible(isLogVisible);
            toggleLogBtn.setText(isLogVisible ? "📊 Logları Gizle" : "📊 Logları Göster");
            mainSplit.setDividerLocation(isLogVisible ? 480 : 700);
        });
        statusBar.add(toggleLogBtn, BorderLayout.EAST);

        mainContainer.add(statusBar, BorderLayout.SOUTH);
        setContentPane(mainContainer);

        // Initial Load
        loadInitialData();
    }

    private JPanel createTopHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(55, 55, 55)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        header.setBackground(new Color(28, 30, 36));

        // Branding
        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brandPanel.setOpaque(false);
        JLabel logo = new JLabel("🐘");
        logo.setFont(logo.getFont().deriveFont(22f));
        JLabel title = new JLabel("PostgreSQL DDL Export Studio");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setForeground(new Color(230, 230, 230));
        brandPanel.add(logo);
        brandPanel.add(title);
        header.add(brandPanel, BorderLayout.WEST);

        // Profile Selection Area
        JPanel profilePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        profilePanel.setOpaque(false);
        profilePanel.add(new JLabel("🗄️ Profil:"));

        profileComboBox.setPreferredSize(new Dimension(200, 28));
        profileComboBox.addActionListener(e -> onProfileSelected());
        profilePanel.add(profileComboBox);

        JButton newProfileBtn = new JButton("➕ Yeni Profil");
        newProfileBtn.addActionListener(e -> createNewProfile());
        profilePanel.add(newProfileBtn);

        JButton delProfileBtn = new JButton("🗑️");
        delProfileBtn.setToolTipText("Seçili profili sil");
        delProfileBtn.addActionListener(e -> deleteSelectedProfile());
        profilePanel.add(delProfileBtn);

        header.add(profilePanel, BorderLayout.EAST);
        return header;
    }

    private void loadInitialData() {
        refreshProfileDropdown();

        // Load export directory if available
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
                statusLabel.setText("🟢 Profil seçildi: " + selected + " (" + settings.getServerHost() + ":" + settings.getPort() + "/" + settings.getDatabaseName() + ")");
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

        // Also save current settings to selected profile
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
                    tabbedPane.setSelectedIndex(0); // Switch to Schema Explorer tab

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

    private void initLookAndFeel() {
        try {
            Class<?> flatLafClass = Class.forName("com.formdev.flatlaf.FlatDarkLaf");
            LookAndFeel laf = (LookAndFeel) flatLafClass.getDeclaredConstructor().newInstance();
            UIManager.setLookAndFeel(laf);
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }
    }
}

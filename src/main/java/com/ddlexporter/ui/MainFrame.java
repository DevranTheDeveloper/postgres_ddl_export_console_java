package com.ddlexporter.ui;

import com.ddlexporter.common.config.IConfigurationReader;
import com.ddlexporter.common.config.JsonConfigurationReader;
import com.ddlexporter.common.logger.ILogger;
import com.ddlexporter.common.scripter.IScripter;
import com.ddlexporter.common.scripter.ScripterBuilder;
import com.ddlexporter.common.writer.FileWriter;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class MainFrame extends JFrame {
    private final ConnectionPanel connectionPanel;
    private final SchemaExplorerPanel schemaExplorerPanel;
    private final LogPanel logPanel;

    public MainFrame() {
        super("🐘 PostgreSQL DDL Export Studio - v1.0.0");
        initLookAndFeel();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null); // Center on screen

        connectionPanel = new ConnectionPanel(this::startExportProcess);
        schemaExplorerPanel = new SchemaExplorerPanel();
        logPanel = new LogPanel();

        // Main Layout
        JPanel mainContainer = new JPanel(new BorderLayout(8, 8));
        mainContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Center Split: Left Connection, Right Schema Explorer
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, connectionPanel, schemaExplorerPanel);
        centerSplit.setDividerLocation(340);
        centerSplit.setResizeWeight(0.3);

        // Vertical Split: Center Content on Top, Log Panel on Bottom
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerSplit, logPanel);
        mainSplit.setDividerLocation(460);
        mainSplit.setResizeWeight(0.65);

        mainContainer.add(mainSplit, BorderLayout.CENTER);
        setContentPane(mainContainer);

        // Load existing settings if available
        loadInitialSettings();
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

    private void loadInitialSettings() {
        File settingsFile = new File("postgresql_settings.json");
        if (settingsFile.exists()) {
            try {
                JsonConfigurationReader reader = new JsonConfigurationReader(settingsFile.getAbsolutePath());
                PostgresqlConfigurationSettings settings = reader.read(PostgresqlConfigurationSettings.class);
                connectionPanel.loadSettingsToUi(settings);
                logPanel.appendLog("[SİSTEM] postgresql_settings.json ayarları yüklendi.");
            } catch (Exception e) {
                logPanel.appendLog("[UYARI] postgresql_settings.json okunamadı: " + e.getMessage());
            }
        }

        // Also check if export_output folder exists and load it
        File exportDir = new File(connectionPanel.getOutputDir());
        if (exportDir.exists()) {
            schemaExplorerPanel.loadExportDirectory(exportDir.getAbsolutePath());
        }
    }

    private void startExportProcess() {
        PostgresqlConfigurationSettings settings = connectionPanel.getSettingsFromUi();
        String outputDir = connectionPanel.getOutputDir();

        connectionPanel.setExporting(true);
        logPanel.setProgressIndeterminate(true, "DDL Dışa Aktarma İşlemi Başlatıldı...");

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
                    schemaExplorerPanel.loadExportDirectory(outputDir);
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Tüm PostgreSQL veritabanı DDL script'leri başarıyla dışa aktarıldı!",
                            "İşlem Başarılı", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    logPanel.setProgress(0, "❌ Hata Oluştu!");
                    logPanel.appendLog("[HATA] " + exportError.getMessage());
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Dışa aktarma sırasında hata oluştu:\n" + exportError.getMessage(),
                            "Hata", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}

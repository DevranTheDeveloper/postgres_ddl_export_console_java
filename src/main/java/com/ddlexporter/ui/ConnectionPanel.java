package com.ddlexporter.ui;

import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ConnectionPanel extends JPanel {
    private final JTextField hostField = new JTextField("localhost");
    private final JTextField portField = new JTextField("5432");
    private final JTextField dbNameField = new JTextField("denemeDatabase");
    private final JTextField userField = new JTextField("postgres");
    private final JPasswordField passField = new JPasswordField("12345");
    private final JTextField schemaField = new JTextField("public");
    private final JTextField outputDirField = new JTextField("./export_output");

    private final JButton testBtn = new JButton("⚡ Bağlantıyı Test Et");
    private final JButton exportBtn = new JButton("🚀 DDL Dışa Aktar (Export)");
    private final JButton saveSettingsBtn = new JButton("💾 Ayarları Kaydet");
    private final JButton openFolderBtn = new JButton("📂 Klasörü Aç");
    private final JButton zipBtn = new JButton("📦 ZIP İndir");

    public ConnectionPanel(Runnable onStartExport) {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createTitledBorder("⚙️ PostgreSQL Bağlantı & Dışa Aktarma Ayarları"));

        // Form Grid
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;
        addFormField(formPanel, gbc, row++, "Sunucu (Host):", hostField);
        addFormField(formPanel, gbc, row++, "Port:", portField);
        addFormField(formPanel, gbc, row++, "Veritabanı Adı:", dbNameField);
        addFormField(formPanel, gbc, row++, "Kullanıcı Adı:", userField);
        addFormField(formPanel, gbc, row++, "Şifre:", passField);
        addFormField(formPanel, gbc, row++, "Şema:", schemaField);

        // Output Directory with Browse Button
        JPanel dirPanel = new JPanel(new BorderLayout(4, 0));
        dirPanel.add(outputDirField, BorderLayout.CENTER);
        JButton browseBtn = new JButton("...");
        browseBtn.setPreferredSize(new Dimension(30, 24));
        browseBtn.addActionListener(e -> chooseOutputDirectory());
        dirPanel.add(browseBtn, BorderLayout.EAST);
        addFormField(formPanel, gbc, row++, "Çıktı Dizini:", dirPanel);

        add(formPanel, BorderLayout.NORTH);

        // Action Buttons
        JPanel actionPanel = new JPanel(new GridLayout(5, 1, 6, 6));
        actionPanel.setBorder(BorderFactory.createEmptyBorder(10, 6, 10, 6));

        testBtn.setBackground(new Color(40, 100, 180));
        testBtn.setForeground(Color.WHITE);
        testBtn.setFont(testBtn.getFont().deriveFont(Font.BOLD));
        testBtn.addActionListener(e -> testConnection());
        actionPanel.add(testBtn);

        exportBtn.setBackground(new Color(34, 139, 34));
        exportBtn.setForeground(Color.WHITE);
        exportBtn.setFont(exportBtn.getFont().deriveFont(Font.BOLD, 13f));
        exportBtn.addActionListener(e -> {
            if (onStartExport != null) onStartExport.run();
        });
        actionPanel.add(exportBtn);

        saveSettingsBtn.addActionListener(e -> saveSettingsToJson());
        actionPanel.add(saveSettingsBtn);

        openFolderBtn.addActionListener(e -> openOutputFolder());
        actionPanel.add(openFolderBtn);

        zipBtn.addActionListener(e -> exportAsZip());
        actionPanel.add(zipBtn);

        add(actionPanel, BorderLayout.CENTER);
    }

    private void addFormField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.3;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 0.7;
        panel.add(field, gbc);
    }

    public PostgresqlConfigurationSettings getSettingsFromUi() {
        PostgresqlConfigurationSettings settings = new PostgresqlConfigurationSettings();
        settings.setServerHost(hostField.getText().trim());
        try {
            settings.setPort(Integer.parseInt(portField.getText().trim()));
        } catch (NumberFormatException ignored) {
            settings.setPort(5432);
        }
        settings.setDatabaseName(dbNameField.getText().trim());
        settings.setUsername(userField.getText().trim());
        settings.setPassword(new String(passField.getPassword()));
        settings.setSchema(schemaField.getText().trim());
        return settings;
    }

    public void loadSettingsToUi(PostgresqlConfigurationSettings settings) {
        if (settings == null) return;
        if (settings.getServerHost() != null) hostField.setText(settings.getServerHost());
        portField.setText(String.valueOf(settings.getPort() > 0 ? settings.getPort() : 5432));
        if (settings.getDatabaseName() != null) dbNameField.setText(settings.getDatabaseName());
        if (settings.getUsername() != null) userField.setText(settings.getUsername());
        if (settings.getPassword() != null) passField.setText(settings.getPassword());
        if (settings.getSchema() != null) schemaField.setText(settings.getSchema());
    }

    public String getOutputDir() {
        return outputDirField.getText().trim();
    }

    private void chooseOutputDirectory() {
        JFileChooser chooser = new JFileChooser(outputDirField.getText().trim());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void testConnection() {
        PostgresqlConfigurationSettings settings = getSettingsFromUi();
        String url = String.format("jdbc:postgresql://%s:%d/%s",
                settings.getServerHost(), settings.getPort(), settings.getDatabaseName());

        testBtn.setEnabled(false);
        testBtn.setText("Bağlanıyor...");

        new SwingWorker<Boolean, Void>() {
            private String errorMessage = null;

            @Override
            protected Boolean doInBackground() {
                try (Connection conn = DriverManager.getConnection(url, settings.getUsername(), settings.getPassword())) {
                    return conn.isValid(3);
                } catch (Exception ex) {
                    errorMessage = ex.getMessage();
                    return false;
                }
            }

            @Override
            protected void done() {
                testBtn.setEnabled(true);
                testBtn.setText("⚡ Bağlantıyı Test Et");
                try {
                    if (get()) {
                        JOptionPane.showMessageDialog(ConnectionPanel.this,
                                "✅ PostgreSQL Veritabanı Bağlantısı Başarılı!",
                                "Bağlantı Başarılı", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(ConnectionPanel.this,
                                "❌ Bağlantı Başarısız:\n" + errorMessage,
                                "Bağlantı Hatası", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ConnectionPanel.this,
                            "Hata: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void saveSettingsToJson() {
        try {
            PostgresqlConfigurationSettings settings = getSettingsFromUi();
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new File("postgresql_settings.json"), settings);
            JOptionPane.showMessageDialog(this, "Ayar dosyası (postgresql_settings.json) kaydedildi!", "Başarılı", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ayar kaydedilemedi: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openOutputFolder() {
        try {
            File dir = new File(getOutputDir());
            if (!dir.exists()) dir.mkdirs();
            Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Klasör açılamadı: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportAsZip() {
        File dir = new File(getOutputDir());
        if (!dir.exists() || !dir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Dışa aktarılan klasör bulunamadı!", "Uyarı", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("ddl_export_" + System.currentTimeMillis() + ".zip"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File zipFile = chooser.getSelectedFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                Path sourcePath = dir.toPath();
                Files.walk(sourcePath)
                        .filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(path).toString());
                            try {
                                zos.putNextEntry(zipEntry);
                                Files.copy(path, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                System.err.println("Zip entry error: " + e.getMessage());
                            }
                        });
                JOptionPane.showMessageDialog(this, "Tüm DDL dosyaları ZIP olarak kaydedildi:\n" + zipFile.getAbsolutePath(), "Başarılı", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "ZIP oluşturma hatası: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void setExporting(boolean exporting) {
        exportBtn.setEnabled(!exporting);
        testBtn.setEnabled(!exporting);
    }
}

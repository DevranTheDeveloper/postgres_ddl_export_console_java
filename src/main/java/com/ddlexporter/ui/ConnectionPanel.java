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
    private final JTextField schemaField = new JTextField("public");
    private final JTextField userField = new JTextField("postgres");
    private final JPasswordField passField = new JPasswordField("12345");
    private final JTextField outputDirField = new JTextField("./export_output");

    private final JButton testBtn = new JButton("Bağlantıyı Test Et");
    private final JButton exportBtn = new JButton("DDL Dışa Aktar (Export)");
    private final JButton saveSettingsBtn = new JButton("Ayarları Kaydet");
    private final JButton openFolderBtn = new JButton("Klasörü Aç");
    private final JButton zipBtn = new JButton("ZIP İndir");

    public ConnectionPanel(Runnable onStartExport) {
        setLayout(new BorderLayout());
        setOpaque(false);

        // Center Container: Maximum width card layout (Clean, compact, no endless stacked bars)
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);

        JPanel cardPanel = new JPanel(new BorderLayout(0, 16));
        cardPanel.setPreferredSize(new Dimension(680, 480));
        cardPanel.setMaximumSize(new Dimension(720, 520));
        cardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("PostgreSQL Bağlantı & Dışa Aktarma Ayarları"),
                BorderFactory.createEmptyBorder(12, 16, 16, 16)
        ));

        // Form Fields (2 Columns Grid)
        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        fieldsPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 1: Host (75%) & Port (25%)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.75;
        fieldsPanel.add(createFieldGroup("Sunucu (Host):", hostField), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.25;
        fieldsPanel.add(createFieldGroup("Port:", portField), gbc);

        // Row 2: Database Name (50%) & Schema (50%)
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.5;
        fieldsPanel.add(createFieldGroup("Veritabanı Adı:", dbNameField), gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.5;
        fieldsPanel.add(createFieldGroup("Şema (Schema):", schemaField), gbc);

        // Row 3: Username (50%) & Password (50%)
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.5;
        fieldsPanel.add(createFieldGroup("Kullanıcı Adı:", userField), gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.5;
        fieldsPanel.add(createFieldGroup("Şifre:", passField), gbc);

        // Row 4: Output Directory (Full Width with Browse button)
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 1.0;
        JPanel dirGroup = new JPanel(new BorderLayout(6, 0));
        dirGroup.setOpaque(false);
        dirGroup.add(outputDirField, BorderLayout.CENTER);
        JButton browseBtn = new JButton("Gözat...");
        browseBtn.addActionListener(e -> chooseOutputDirectory());
        dirGroup.add(browseBtn, BorderLayout.EAST);
        fieldsPanel.add(createFieldGroup("Çıktı Dizini (Export Directory):", dirGroup), gbc);

        cardPanel.add(fieldsPanel, BorderLayout.CENTER);

        // Action Buttons: Modern Horizontal Layout
        JPanel actionsContainer = new JPanel(new GridLayout(2, 1, 8, 8));
        actionsContainer.setOpaque(false);

        // Primary Row
        JPanel primaryRow = new JPanel(new GridLayout(1, 3, 10, 0));
        primaryRow.setOpaque(false);

        testBtn.setPreferredSize(new Dimension(0, 36));
        testBtn.setFont(testBtn.getFont().deriveFont(Font.BOLD, 12f));
        testBtn.addActionListener(e -> testConnection());
        primaryRow.add(testBtn);

        exportBtn.setPreferredSize(new Dimension(0, 38));
        exportBtn.setBackground(new Color(34, 139, 34));
        exportBtn.setForeground(Color.WHITE);
        exportBtn.setFont(exportBtn.getFont().deriveFont(Font.BOLD, 13f));
        exportBtn.addActionListener(e -> {
            if (onStartExport != null) onStartExport.run();
        });
        primaryRow.add(exportBtn);

        saveSettingsBtn.setPreferredSize(new Dimension(0, 36));
        saveSettingsBtn.setFont(saveSettingsBtn.getFont().deriveFont(Font.BOLD, 12f));
        saveSettingsBtn.addActionListener(e -> saveSettingsToJson());
        primaryRow.add(saveSettingsBtn);

        actionsContainer.add(primaryRow);

        // Secondary Row (Quick Tools)
        JPanel secondaryRow = new JPanel(new GridLayout(1, 2, 10, 0));
        secondaryRow.setOpaque(false);

        openFolderBtn.setPreferredSize(new Dimension(0, 32));
        openFolderBtn.addActionListener(e -> openOutputFolder());
        secondaryRow.add(openFolderBtn);

        zipBtn.setPreferredSize(new Dimension(0, 32));
        zipBtn.addActionListener(e -> exportAsZip());
        secondaryRow.add(zipBtn);

        actionsContainer.add(secondaryRow);
        cardPanel.add(actionsContainer, BorderLayout.SOUTH);

        centerWrapper.add(cardPanel);
        add(centerWrapper, BorderLayout.CENTER);
    }

    private JPanel createFieldGroup(String labelText, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(new Color(80, 85, 95));
        panel.add(label, BorderLayout.NORTH);

        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 30));
        panel.add(field, BorderLayout.CENTER);
        return panel;
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
                testBtn.setText("Bağlantıyı Test Et");
                try {
                    if (get()) {
                        JOptionPane.showMessageDialog(ConnectionPanel.this,
                                "PostgreSQL Veritabanı Bağlantısı Başarılı!",
                                "Bağlantı Başarılı (Onay)", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(ConnectionPanel.this,
                                "Bağlantı Başarısız (Ret):\n" + errorMessage,
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

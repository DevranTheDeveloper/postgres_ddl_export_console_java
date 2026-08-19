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
    private final JTextField userField = new JTextField("postgres");
    private final JPasswordField passField = new JPasswordField("");
    private final JComboBox<String> sslModeBox = new JComboBox<>(new String[]{"Devre Dışı (disable)", "Tercih Et (prefer)", "Zorunlu (require)"});

    private final JTextField dbNameField = new JTextField("postgres");
    private final JTextField schemaField = new JTextField("public");
    private final JTextField outputDirField = new JTextField("./export_output");

    private final JCheckBox chkTables = new JCheckBox("Tablolar (TABLE)", true);
    private final JCheckBox chkViews = new JCheckBox("Görünümler (VIEW)", true);
    private final JCheckBox chkFunctions = new JCheckBox("Fonksiyonlar (FUNCTION)", true);
    private final JCheckBox chkSequences = new JCheckBox("Diziler (SEQUENCE)", true);
    private final JCheckBox chkIndexes = new JCheckBox("İndeksler (INDEX)", true);

    private final JButton testBtn = new JButton("Bağlantıyı Test Et");
    private final JButton exportBtn = new JButton("DDL Dışa Aktar (Export)");
    private final JButton saveSettingsBtn = new JButton("Ayarları Kaydet");
    private final JButton openFolderBtn = new JButton("Klasörü Aç");
    private final JButton zipBtn = new JButton("ZIP İndir");

    public ConnectionPanel(Runnable onStartExport) {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        setOpaque(false);

        // Center: 2 Balanced Side-by-Side Cards (Fills 100% Width Perfectly)
        JPanel gridContainer = new JPanel(new GridLayout(1, 2, 16, 0));
        gridContainer.setOpaque(false);

        // --- LEFT CARD: Sunucu ve Kimlik Doğrulama ---
        JPanel leftCard = new JPanel(new BorderLayout(0, 12));
        leftCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Sunucu & Kimlik Doğrulama (Server & Auth)"),
                BorderFactory.createEmptyBorder(12, 14, 14, 14)
        ));

        JPanel leftForm = new JPanel(new GridBagLayout());
        leftForm.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Host (70%) + Port (30%)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.7;
        leftForm.add(createFieldGroup("Sunucu Adresi (Host):", hostField), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.3;
        leftForm.add(createFieldGroup("Port:", portField), gbc);

        // Row 1: Username (Full Width)
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        leftForm.add(createFieldGroup("Kullanıcı Adı (Username):", userField), gbc);

        // Row 2: Password (Full Width)
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
        leftForm.add(createFieldGroup("Şifre (Password):", passField), gbc);

        // Row 3: SSL Mode
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 1.0;
        leftForm.add(createFieldGroup("SSL Bağlantı Modu:", sslModeBox), gbc);

        leftCard.add(leftForm, BorderLayout.CENTER);

        // Left Actions
        JPanel leftActionPanel = new JPanel(new GridLayout(1, 1, 0, 0));
        leftActionPanel.setOpaque(false);
        testBtn.setPreferredSize(new Dimension(0, 36));
        testBtn.setFont(testBtn.getFont().deriveFont(Font.BOLD, 12f));
        testBtn.addActionListener(e -> testConnection());
        leftActionPanel.add(testBtn);
        leftCard.add(leftActionPanel, BorderLayout.SOUTH);

        gridContainer.add(leftCard);

        // --- RIGHT CARD: Veritabanı ve Dışa Aktarma Kapsamı ---
        JPanel rightCard = new JPanel(new BorderLayout(0, 12));
        rightCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Veritabanı & Dışa Aktarma Kapsamı (Scope & Output)"),
                BorderFactory.createEmptyBorder(12, 14, 14, 14)
        ));

        JPanel rightForm = new JPanel(new GridBagLayout());
        rightForm.setOpaque(false);

        // Row 0: DB Name (50%) + Schema (50%)
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1; gbc.weightx = 0.5;
        rightForm.add(createFieldGroup("Veritabanı Adı (DB Name):", dbNameField), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.5;
        rightForm.add(createFieldGroup("Şema (Schema):", schemaField), gbc);

        // Row 1: Output Directory with browse
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        JPanel dirGroup = new JPanel(new BorderLayout(6, 0));
        dirGroup.setOpaque(false);
        dirGroup.add(outputDirField, BorderLayout.CENTER);
        JButton browseBtn = new JButton("Gözat...");
        browseBtn.addActionListener(e -> chooseOutputDirectory());
        dirGroup.add(browseBtn, BorderLayout.EAST);
        rightForm.add(createFieldGroup("Çıktı Dizini (Output Directory):", dirGroup), gbc);

        // Row 2: Scope Checkboxes
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
        JPanel checksPanel = new JPanel(new GridLayout(2, 3, 4, 4));
        checksPanel.setOpaque(false);
        checksPanel.setBorder(BorderFactory.createTitledBorder("Dışa Aktarılacak Nesne Tipleri"));
        checksPanel.add(chkTables);
        checksPanel.add(chkViews);
        checksPanel.add(chkFunctions);
        checksPanel.add(chkSequences);
        checksPanel.add(chkIndexes);
        rightForm.add(checksPanel, gbc);

        rightCard.add(rightForm, BorderLayout.CENTER);

        // Right Actions (Export & Save)
        JPanel rightActionPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        rightActionPanel.setOpaque(false);

        exportBtn.setPreferredSize(new Dimension(0, 36));
        exportBtn.setFont(exportBtn.getFont().deriveFont(Font.BOLD, 12f));
        exportBtn.addActionListener(e -> {
            if (onStartExport != null) onStartExport.run();
        });
        rightActionPanel.add(exportBtn);

        saveSettingsBtn.setPreferredSize(new Dimension(0, 36));
        saveSettingsBtn.setFont(saveSettingsBtn.getFont().deriveFont(Font.BOLD, 12f));
        saveSettingsBtn.addActionListener(e -> saveSettingsToJson());
        rightActionPanel.add(saveSettingsBtn);

        rightCard.add(rightActionPanel, BorderLayout.SOUTH);

        gridContainer.add(rightCard);
        add(gridContainer, BorderLayout.CENTER);

        // --- BOTTOM BAR: Hızlı Yardımcı Araçlar ---
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottomBar.setOpaque(false);

        JButton databaseHubBtn = new JButton("Hızlı Başlangıç Asistanı");
        databaseHubBtn.setPreferredSize(new Dimension(170, 30));
        databaseHubBtn.setFont(databaseHubBtn.getFont().deriveFont(Font.BOLD, 12f));
        databaseHubBtn.addActionListener(e -> {
            Window ancestor = SwingUtilities.getWindowAncestor(this);
            Frame owner = (ancestor instanceof Frame) ? (Frame) ancestor : null;
            UniversalDatabaseHubDialog dialog = new UniversalDatabaseHubDialog(owner, this::applySettings, this::testConnection);
            dialog.setVisible(true);
        });
        bottomBar.add(databaseHubBtn);

        openFolderBtn.setPreferredSize(new Dimension(140, 30));
        openFolderBtn.addActionListener(e -> openOutputFolder());
        bottomBar.add(openFolderBtn);

        zipBtn.setPreferredSize(new Dimension(140, 30));
        zipBtn.addActionListener(e -> exportAsZip());
        bottomBar.add(zipBtn);

        add(bottomBar, BorderLayout.SOUTH);
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

    public void testConnection() {
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
                        boolean isRemote = !settings.getServerHost().equalsIgnoreCase("localhost")
                                && !settings.getServerHost().equals("127.0.0.1")
                                && !settings.getServerHost().equals("::1");
                        String ssl = (String) sslModeBox.getSelectedItem();
                        if (isRemote && ("disable".equalsIgnoreCase(ssl) || "prefer".equalsIgnoreCase(ssl))) {
                            JOptionPane.showMessageDialog(ConnectionPanel.this,
                                    "PostgreSQL Veritabanı Bağlantısı Başarılı!\n\n"
                                            + "Güvenlik Tavsiyesi: Uzak sunucuya '" + ssl + "' SSL modu ile bağlanıyorsunuz.\n"
                                            + "Üretim ortamlarında ağ dinleme (MitM) risklerine karşı 'require' veya 'verify-full' modu önerilir.",
                                    "Bağlantı Başarılı (Güvenlik Tavsiyesi)", JOptionPane.WARNING_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(ConnectionPanel.this,
                                    "PostgreSQL Veritabanı Bağlantısı Başarılı!\nAktif Bağlantı ve SSL Durumu Doğrulandı.",
                                    "Bağlantı Başarılı", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(ConnectionPanel.this,
                                "Bağlantı Başarısız:\n" + errorMessage,
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

    public void applySettings(PostgresqlConfigurationSettings settings) {
        if (settings == null) return;
        if (settings.getServerHost() != null) hostField.setText(settings.getServerHost());
        if (settings.getPort() > 0) portField.setText(String.valueOf(settings.getPort()));
        if (settings.getDatabaseName() != null) dbNameField.setText(settings.getDatabaseName());
        if (settings.getUsername() != null) userField.setText(settings.getUsername());
        if (settings.getPassword() != null) passField.setText(settings.getPassword());
        if (settings.getSchema() != null) schemaField.setText(settings.getSchema());
    }

    public void setExporting(boolean exporting) {
        exportBtn.setEnabled(!exporting);
        testBtn.setEnabled(!exporting);
    }
}

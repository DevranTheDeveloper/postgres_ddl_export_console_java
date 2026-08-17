package com.ddlexporter.ui;

import com.ddlexporter.common.util.DockerManager;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class DockerWizardDialog extends JDialog {
    private final Consumer<PostgresqlConfigurationSettings> onConnectCallback;
    private final Runnable onRefreshCallback;
    private final JComboBox<DockerManager.DockerContainerInfo> containerComboBox = new JComboBox<>();
    private final JLabel dockerStatusLabel = new JLabel();
    private final JButton connectExistingBtn = new JButton("Seçili Konteynere Bağlan");
    private final JButton startStoppedBtn = new JButton("Konteyneri Başlat");
    private final JButton createNewBtn = new JButton("Yeni Demo PostgreSQL Başlat (5432)");
    private final JButton seedDataBtn = new JButton("Örnek E-Ticaret Şeması Yükle (Seed)");
    private final JTextField portField = new JTextField("5432", 6);
    private final JTextField passwordField = new JTextField("postgres", 10);
    private final JTextField containerNameField = new JTextField("postgres-ddl-demo", 14);

    public DockerWizardDialog(Frame owner,
                              Consumer<PostgresqlConfigurationSettings> onConnectCallback,
                              Runnable onRefreshCallback) {
        super(owner, "Docker & PostgreSQL Hızlı Başlangıç Asistanı", true);
        this.onConnectCallback = onConnectCallback;
        this.onRefreshCallback = onRefreshCallback;

        setSize(580, 520);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(12, 12));
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // 1. Docker Engine Status Header
        JPanel statusCard = new JPanel(new BorderLayout(8, 8));
        statusCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Docker Motoru Durumu"),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        dockerStatusLabel.setFont(dockerStatusLabel.getFont().deriveFont(Font.BOLD, 13f));
        statusCard.add(dockerStatusLabel, BorderLayout.WEST);

        JButton refreshDockerBtn = new JButton("Konteynerleri Yeniden Tara");
        refreshDockerBtn.addActionListener(e -> scanDockerContainers());
        statusCard.add(refreshDockerBtn, BorderLayout.EAST);
        mainPanel.add(statusCard);
        mainPanel.add(Box.createVerticalStrut(12));

        // 2. Existing Containers Section
        JPanel existingCard = new JPanel(new BorderLayout(8, 8));
        existingCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Mevcut PostgreSQL Konteynerleri"),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JPanel comboRow = new JPanel(new BorderLayout(8, 0));
        comboRow.add(new JLabel("Bulunan Konteyner:"), BorderLayout.WEST);
        comboRow.add(containerComboBox, BorderLayout.CENTER);
        existingCard.add(comboRow, BorderLayout.NORTH);

        JPanel existingBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        startStoppedBtn.addActionListener(e -> startSelectedContainer());
        existingBtnRow.add(startStoppedBtn);

        connectExistingBtn.setFont(connectExistingBtn.getFont().deriveFont(Font.BOLD));
        connectExistingBtn.addActionListener(e -> connectToSelectedContainer());
        existingBtnRow.add(connectExistingBtn);
        existingCard.add(existingBtnRow, BorderLayout.SOUTH);

        mainPanel.add(existingCard);
        mainPanel.add(Box.createVerticalStrut(12));

        // 3. Create New Container Section
        JPanel createCard = new JPanel(new BorderLayout(8, 8));
        createCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Yeni Demo PostgreSQL Konteyneri Oluştur"),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JPanel formGrid = new JPanel(new GridLayout(2, 2, 8, 8));
        formGrid.add(new JLabel("Konteyner Adı:"));
        formGrid.add(containerNameField);
        formGrid.add(new JLabel("Port & Parola:"));
        JPanel portPassRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        portPassRow.add(portField);
        portPassRow.add(new JLabel("Şifre:"));
        portPassRow.add(passwordField);
        formGrid.add(portPassRow);
        createCard.add(formGrid, BorderLayout.CENTER);

        JPanel createBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        createNewBtn.setFont(createNewBtn.getFont().deriveFont(Font.BOLD));
        createNewBtn.addActionListener(e -> createNewContainer());
        createBtnRow.add(createNewBtn);
        createCard.add(createBtnRow, BorderLayout.SOUTH);

        mainPanel.add(createCard);
        mainPanel.add(Box.createVerticalStrut(12));

        // 4. Seed Data Section
        JPanel seedCard = new JPanel(new BorderLayout(8, 8));
        seedCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Örnek Veritabanı Şeması Enjeksiyonu"),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JLabel seedDesc = new JLabel("<html>Mevcut veritabanına hazır <b>E-Ticaret Şeması</b> (customers, orders, products, views) yükler.</html>");
        seedCard.add(seedDesc, BorderLayout.CENTER);

        seedDataBtn.addActionListener(e -> seedEcommerceData());
        JPanel seedBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        seedBtnRow.add(seedDataBtn);
        seedCard.add(seedBtnRow, BorderLayout.EAST);

        mainPanel.add(seedCard);

        add(mainPanel, BorderLayout.CENTER);

        // Close button footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        JButton closeBtn = new JButton("Kapat");
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);
        add(footer, BorderLayout.SOUTH);

        scanDockerContainers();
    }

    private void scanDockerContainers() {
        boolean available = DockerManager.isDockerAvailable();
        if (available) {
            dockerStatusLabel.setText("Docker Motoru: Aktif (Çalışıyor)");
            dockerStatusLabel.setForeground(new Color(22, 163, 74));
        } else {
            dockerStatusLabel.setText("Docker Motoru: Kapalı / Bulunamadı");
            dockerStatusLabel.setForeground(new Color(220, 38, 38));
        }

        containerComboBox.removeAllItems();
        if (available) {
            List<DockerManager.DockerContainerInfo> list = DockerManager.listPostgresContainers();
            for (DockerManager.DockerContainerInfo info : list) {
                containerComboBox.addItem(info);
            }
            boolean hasContainers = list.size() > 0;
            connectExistingBtn.setEnabled(hasContainers);
            startStoppedBtn.setEnabled(hasContainers);
        } else {
            connectExistingBtn.setEnabled(false);
            startStoppedBtn.setEnabled(false);
        }
    }

    private void connectToSelectedContainer() {
        DockerManager.DockerContainerInfo selected = (DockerManager.DockerContainerInfo) containerComboBox.getSelectedItem();
        if (selected == null) return;

        int port = 5432;
        if (selected.ports.contains("->")) {
            try {
                // e.g. 0.0.0.0:5432->5432/tcp
                String pStr = selected.ports.split("->")[0];
                if (pStr.contains(":")) {
                    port = Integer.parseInt(pStr.substring(pStr.lastIndexOf(":") + 1).trim());
                }
            } catch (Exception ignored) {}
        }

        PostgresqlConfigurationSettings s = new PostgresqlConfigurationSettings();
        s.setServerHost("localhost");
        s.setPort(port);
        s.setUsername("postgres");
        s.setPassword("12345"); // default fallback password
        s.setDatabaseName(selected.name.contains("shop") ? "staging_shop_db" : "denemeDatabase");
        s.setSchema("public");

        if (onConnectCallback != null) {
            onConnectCallback.accept(s);
        }
        if (onRefreshCallback != null) {
            onRefreshCallback.run();
        }

        JOptionPane.showMessageDialog(this,
                "Seçili Docker konteynerine ('" + selected.name + "') bağlantı ayarları yüklendi!",
                "Bağlantı Yapılandırıldı", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    private void startSelectedContainer() {
        DockerManager.DockerContainerInfo selected = (DockerManager.DockerContainerInfo) containerComboBox.getSelectedItem();
        if (selected == null) return;

        startStoppedBtn.setEnabled(false);
        startStoppedBtn.setText("Başlatılıyor...");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return DockerManager.startContainer(selected.id);
            }

            @Override
            protected void done() {
                startStoppedBtn.setEnabled(true);
                startStoppedBtn.setText("Konteyneri Başlat");
                try {
                    if (get()) {
                        scanDockerContainers();
                        JOptionPane.showMessageDialog(DockerWizardDialog.this,
                                "Konteyner ('" + selected.name + "') başarıyla başlatıldı!",
                                "Başarılı", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(DockerWizardDialog.this,
                                "Konteyner başlatılamadı. Docker izinlerini kontrol edin.",
                                "Hata", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void createNewContainer() {
        String cName = containerNameField.getText().trim();
        String pass = passwordField.getText().trim();
        int port = 5432;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Geçersiz port numarası.", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        createNewBtn.setEnabled(false);
        createNewBtn.setText("Konteyner Başlatılıyor...");

        int finalPort = port;
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return DockerManager.createAndRunDemoContainer(cName, finalPort, pass);
            }

            @Override
            protected void done() {
                createNewBtn.setEnabled(true);
                createNewBtn.setText("Yeni Demo PostgreSQL Başlat (5432)");
                try {
                    if (get()) {
                        scanDockerContainers();
                        PostgresqlConfigurationSettings s = new PostgresqlConfigurationSettings();
                        s.setServerHost("localhost");
                        s.setPort(finalPort);
                        s.setUsername("postgres");
                        s.setPassword(pass);
                        s.setDatabaseName("postgres");
                        s.setSchema("public");

                        if (onConnectCallback != null) onConnectCallback.accept(s);
                        if (onRefreshCallback != null) onRefreshCallback.run();

                        JOptionPane.showMessageDialog(DockerWizardDialog.this,
                                "Yeni demo PostgreSQL konteyneri ('" + cName + "') oluşturuldu ve bağlandı!",
                                "Demo Başlatıldı", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(DockerWizardDialog.this,
                                "Konteyner oluşturulamadı. Aynı isimde veya portta başka bir konteyner çalışıyor olabilir.",
                                "Oluşturma Hatası", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DockerWizardDialog.this, "Hata: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void seedEcommerceData() {
        seedDataBtn.setEnabled(false);
        seedDataBtn.setText("Şema Yükleniyor...");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return DockerManager.seedDemoEcommerceSchema("localhost", 5432, "postgres", "postgres", "postgres");
            }

            @Override
            protected void done() {
                seedDataBtn.setEnabled(true);
                seedDataBtn.setText("Örnek E-Ticaret Şeması Yükle (Seed)");
                try {
                    if (get()) {
                        JOptionPane.showMessageDialog(DockerWizardDialog.this,
                                "Örnek E-Ticaret tabloları (customers, orders, products, payments) başarıyla yüklendi!",
                                "Şema Hazır", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(DockerWizardDialog.this,
                                "Şema yüklenirken hata oluştu. Aktif PostgreSQL bağlantısını kontrol edin.",
                                "Yükleme Hatası", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }
}

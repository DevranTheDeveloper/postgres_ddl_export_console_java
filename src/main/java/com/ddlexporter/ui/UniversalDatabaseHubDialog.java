package com.ddlexporter.ui;

import com.ddlexporter.common.util.DockerManager;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

public class UniversalDatabaseHubDialog extends JDialog {
    private final Consumer<PostgresqlConfigurationSettings> onConnectCallback;
    private final Runnable onRefreshCallback;

    // Tab 1: Docker
    private final JComboBox<DockerManager.DockerContainerInfo> containerComboBox = new JComboBox<>();
    private final JLabel dockerStatusLabel = new JLabel();
    private final JButton connectExistingBtn = new JButton("Seçili Konteynere Bağlan");
    private final JButton startStoppedBtn = new JButton("Konteyneri Başlat");
    private final JButton createNewBtn = new JButton("Yeni Demo PostgreSQL Başlat (5432)");
    private final JTextField portField = new JTextField("5432", 6);
    private final JTextField passwordField = new JTextField("postgres", 10);
    private final JTextField containerNameField = new JTextField("postgres-ddl-demo", 14);

    // Tab 2: Connection String URI Parser (Neon, Supabase, AWS, Render etc.)
    private final JTextField uriField = new JTextField("postgresql://postgres:password@ep-sample.neon.tech:5432/neondb?sslmode=require");
    private final JButton parseUriBtn = new JButton("Bağlantı Dizesini Ayrıştır & Bağlan");
    private final JLabel uriResultLabel = new JLabel("Supabase, Neon, AWS RDS, Render, Railway vb. bağlantı URL'sini yapıştırın.");

    // Tab 3: Native Local Service
    private final JLabel nativeStatusLabel = new JLabel("Yerel Port 5432 Taranıyor...");
    private final JButton checkNativeBtn = new JButton("Yerel Portu Kontrol Et & Bağlan (localhost:5432)");

    // Tab 4: Seed Data
    private final JComboBox<String> schemaTypeBox = new JComboBox<>(new String[]{
            "E-Ticaret Şeması (customers, orders, products, payments, views)",
            "SaaS & Abonelik Şeması (users, subscriptions, plans, invoices)",
            "Blog & İçerik Şeması (posts, categories, comments, authors)"
    });
    private final JButton seedDataBtn = new JButton("Seçili Şemayı Veritabanına Yükle");

    public UniversalDatabaseHubDialog(Frame owner,
                                      Consumer<PostgresqlConfigurationSettings> onConnectCallback,
                                      Runnable onRefreshCallback) {
        super(owner, "Veritabanı Hızlı Bağlantı & Kurulum Asistanı", true);
        this.onConnectCallback = onConnectCallback;
        this.onRefreshCallback = onRefreshCallback;

        setSize(780, 620);
        setMinimumSize(new Dimension(680, 520));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 0));
        setResizable(true);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(Font.BOLD, 12f));

        tabbedPane.addTab("🐳 Docker & Konteynerler", createDockerTab());
        tabbedPane.addTab("🔗 Bulut Bağlantı URL (URI Parser)", createUriTab());
        tabbedPane.addTab("💻 Yerel Servis (Native)", createNativeTab());
        tabbedPane.addTab("⚡ Örnek Şema Yükleyici (Seed)", createSeedTab());

        add(tabbedPane, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(128, 128, 128, 60)),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)));

        JLabel helpLabel = new JLabel("Tüm yerel, Docker ve bulut (Neon, Supabase, AWS RDS, Render) veritabanları desteklenir.");
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.PLAIN, 11f));
        helpLabel.setForeground(new Color(140, 145, 155));
        footer.add(helpLabel, BorderLayout.WEST);

        JButton closeBtn = new JButton("Kapat");
        closeBtn.setPreferredSize(new Dimension(90, 30));
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        scanDockerContainers();
        checkNativeService();
    }

    // 1. Docker Tab
    private JComponent createDockerTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // Docker status
        JPanel statusCard = new JPanel(new BorderLayout(8, 8));
        statusCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Docker Motoru Durumu"),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        dockerStatusLabel.setFont(dockerStatusLabel.getFont().deriveFont(Font.BOLD, 13f));
        statusCard.add(dockerStatusLabel, BorderLayout.WEST);

        JButton refreshDockerBtn = new JButton("Yeniden Tara");
        refreshDockerBtn.addActionListener(e -> scanDockerContainers());
        statusCard.add(refreshDockerBtn, BorderLayout.EAST);
        panel.add(statusCard);
        panel.add(Box.createVerticalStrut(12));

        // Existing Containers
        JPanel existingCard = new JPanel(new BorderLayout(8, 8));
        existingCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Mevcut PostgreSQL Konteynerleri"),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JPanel comboRow = new JPanel(new BorderLayout(10, 0));
        JLabel comboLabel = new JLabel("Bulunan Konteyner:");
        comboLabel.setFont(comboLabel.getFont().deriveFont(Font.BOLD, 12f));
        comboRow.add(comboLabel, BorderLayout.WEST);
        comboRow.add(containerComboBox, BorderLayout.CENTER);
        existingCard.add(comboRow, BorderLayout.NORTH);

        JPanel existingBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        startStoppedBtn.addActionListener(e -> startSelectedContainer());
        existingBtnRow.add(startStoppedBtn);

        connectExistingBtn.setFont(connectExistingBtn.getFont().deriveFont(Font.BOLD));
        connectExistingBtn.addActionListener(e -> connectToSelectedContainer());
        existingBtnRow.add(connectExistingBtn);
        existingCard.add(existingBtnRow, BorderLayout.SOUTH);
        panel.add(existingCard);
        panel.add(Box.createVerticalStrut(12));

        // Create New Container
        JPanel createCard = new JPanel(new BorderLayout(8, 8));
        createCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Yeni Demo PostgreSQL Konteyneri Oluştur"),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.2;
        formPanel.add(new JLabel("Konteyner Adı:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.8;
        formPanel.add(containerNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.2;
        formPanel.add(new JLabel("Port & Parola:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.8;
        JPanel portPassRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        portPassRow.add(portField);
        portPassRow.add(new JLabel("Şifre:"));
        portPassRow.add(passwordField);
        formPanel.add(portPassRow, gbc);

        createCard.add(formPanel, BorderLayout.CENTER);

        JPanel createBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        createNewBtn.setFont(createNewBtn.getFont().deriveFont(Font.BOLD));
        createNewBtn.addActionListener(e -> createNewContainer());
        createBtnRow.add(createNewBtn);
        createCard.add(createBtnRow, BorderLayout.SOUTH);
        panel.add(createCard);

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // 2. URI Parser Tab (Supabase, Neon, AWS, Cloud etc.)
    private JComponent createUriTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Tek Tıkla Bağlantı Dizesi (Connection URL / URI)"),
                BorderFactory.createEmptyBorder(12, 14, 14, 14)));

        JPanel inputPanel = new JPanel(new BorderLayout(0, 6));
        JLabel title = new JLabel("PostgreSQL Bağlantı Dizesini (URI) Buraya Yapıştırın:");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        inputPanel.add(title, BorderLayout.NORTH);
        inputPanel.add(uriField, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        parseUriBtn.setFont(parseUriBtn.getFont().deriveFont(Font.BOLD, 12f));
        parseUriBtn.addActionListener(e -> parseAndConnectUri());
        btnRow.add(parseUriBtn);
        inputPanel.add(btnRow, BorderLayout.SOUTH);

        card.add(inputPanel, BorderLayout.NORTH);

        uriResultLabel.setFont(uriResultLabel.getFont().deriveFont(Font.PLAIN, 11f));
        card.add(uriResultLabel, BorderLayout.CENTER);

        panel.add(card);
        panel.add(Box.createVerticalStrut(12));

        // Supported Services Banner
        JPanel cloudBanner = new JPanel(new GridLayout(2, 4, 8, 8));
        cloudBanner.setBorder(BorderFactory.createTitledBorder("Desteklenen Bulut & Barındırma Servisleri"));
        cloudBanner.add(new JLabel("⚡ Neon Serverless"));
        cloudBanner.add(new JLabel("⚡ Supabase PostgreSQL"));
        cloudBanner.add(new JLabel("⚡ AWS RDS & Aurora"));
        cloudBanner.add(new JLabel("⚡ Render.com"));
        cloudBanner.add(new JLabel("⚡ Railway.app"));
        cloudBanner.add(new JLabel("⚡ Aiven PostgreSQL"));
        cloudBanner.add(new JLabel("⚡ DigitalOcean DB"));
        cloudBanner.add(new JLabel("⚡ Google Cloud SQL"));
        panel.add(cloudBanner);

        return panel;
    }

    // 3. Native Service Tab
    private JComponent createNativeTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Yerel PostgreSQL Servisi (Homebrew, Windows Service, Linux systemd)"),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        nativeStatusLabel.setFont(nativeStatusLabel.getFont().deriveFont(Font.BOLD, 13f));
        card.add(nativeStatusLabel, BorderLayout.NORTH);

        JLabel info = new JLabel("<html>Docker olmadan doğrudan bilgisayarınızda (Homebrew, Postgres.app veya Windows servisi olarak) çalışan PostgreSQL servisiyle iletişim kurar.</html>");
        card.add(info, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        checkNativeBtn.setFont(checkNativeBtn.getFont().deriveFont(Font.BOLD));
        checkNativeBtn.addActionListener(e -> connectToNativeLocal());
        btnRow.add(checkNativeBtn);
        card.add(btnRow, BorderLayout.SOUTH);

        panel.add(card);
        return panel;
    }

    // 4. Seed Data Tab
    private JComponent createSeedTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Hazır Örnek Şema & Test Verisi Yükleyici"),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JPanel selectRow = new JPanel(new BorderLayout(8, 8));
        selectRow.add(new JLabel("Yüklenecek Şema Modeli:"), BorderLayout.NORTH);
        selectRow.add(schemaTypeBox, BorderLayout.CENTER);
        card.add(selectRow, BorderLayout.NORTH);

        JLabel desc = new JLabel("<html>Veritabanınız boşsa veya ERD / Şema Farkı motorunu test etmek istiyorsanız, ilişkili tabloları, görünümleri ve indeksleri anında oluşturur.</html>");
        card.add(desc, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        seedDataBtn.setFont(seedDataBtn.getFont().deriveFont(Font.BOLD));
        seedDataBtn.addActionListener(e -> executeSeedData());
        btnRow.add(seedDataBtn);
        card.add(btnRow, BorderLayout.SOUTH);

        panel.add(card);
        return panel;
    }

    // Logic: Docker
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
        s.setPassword("12345");
        s.setDatabaseName(selected.name.contains("shop") ? "staging_shop_db" : "denemeDatabase");
        s.setSchema("public");

        if (onConnectCallback != null) onConnectCallback.accept(s);
        if (onRefreshCallback != null) onRefreshCallback.run();

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
                        JOptionPane.showMessageDialog(UniversalDatabaseHubDialog.this,
                                "Konteyner ('" + selected.name + "') başarıyla başlatıldı!",
                                "Başarılı", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(UniversalDatabaseHubDialog.this,
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

                        JOptionPane.showMessageDialog(UniversalDatabaseHubDialog.this,
                                "Yeni demo PostgreSQL konteyneri ('" + cName + "') oluşturuldu ve bağlandı!",
                                "Demo Başlatıldı", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(UniversalDatabaseHubDialog.this,
                                "Konteyner oluşturulamadı. Aynı isimde veya portta başka bir konteyner çalışıyor olabilir.",
                                "Oluşturma Hatası", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UniversalDatabaseHubDialog.this, "Hata: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // Logic: URI Parser
    private void parseAndConnectUri() {
        String uriStr = uriField.getText().trim();
        if (uriStr.isBlank()) return;

        try {
            // Support postgres:// or postgresql://
            String cleanUri = uriStr.startsWith("postgres://") ? "postgresql://" + uriStr.substring(11) : uriStr;
            URI uri = new URI(cleanUri);

            String host = uri.getHost();
            int port = (uri.getPort() > 0) ? uri.getPort() : 5432;
            String userInfo = uri.getUserInfo();
            String username = "postgres";
            String password = "";

            if (userInfo != null && userInfo.contains(":")) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts[1];
            } else if (userInfo != null) {
                username = userInfo;
            }

            String path = uri.getPath();
            String dbName = (path != null && path.length() > 1) ? path.substring(1) : "postgres";

            PostgresqlConfigurationSettings s = new PostgresqlConfigurationSettings();
            s.setServerHost(host != null ? host : "localhost");
            s.setPort(port);
            s.setUsername(username);
            s.setPassword(password);
            s.setDatabaseName(dbName);
            s.setSchema("public");

            if (onConnectCallback != null) onConnectCallback.accept(s);
            if (onRefreshCallback != null) onRefreshCallback.run();

            JOptionPane.showMessageDialog(this,
                    "Bağlantı dizesi başarıyla çözüldü!\nSunucu: " + host + ":" + port + "\nVeritabanı: " + dbName + "\nKullanıcı: " + username,
                    "URI Ayrıştırıldı & Bağlanıldı", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Geçersiz Bağlantı Dizesi (URI):\n" + ex.getMessage(),
                    "Ayrıştırma Hatası", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Logic: Native Service
    private void checkNativeService() {
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress("localhost", 5432), 600);
            socket.close();
            nativeStatusLabel.setText("Yerel PostgreSQL Servisi: Aktif (localhost:5432 Açık)");
            nativeStatusLabel.setForeground(new Color(22, 163, 74));
            checkNativeBtn.setEnabled(true);
        } catch (Exception e) {
            nativeStatusLabel.setText("Yerel PostgreSQL Servisi: Kapalı veya Port Kapalı");
            nativeStatusLabel.setForeground(new Color(217, 119, 6));
        }
    }

    private void connectToNativeLocal() {
        PostgresqlConfigurationSettings s = new PostgresqlConfigurationSettings();
        s.setServerHost("localhost");
        s.setPort(5432);
        s.setUsername("postgres");
        s.setPassword("12345");
        s.setDatabaseName("denemeDatabase");
        s.setSchema("public");

        if (onConnectCallback != null) onConnectCallback.accept(s);
        if (onRefreshCallback != null) onRefreshCallback.run();

        JOptionPane.showMessageDialog(this,
                "Yerel 'localhost:5432' bağlantı ayarları yüklendi!",
                "Yerel Servise Bağlanıldı", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    // Logic: Seed Data
    private void executeSeedData() {
        seedDataBtn.setEnabled(false);
        seedDataBtn.setText("Şema Yükleniyor...");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return DockerManager.seedDemoEcommerceSchema("localhost", 5432, "denemeDatabase", "postgres", "12345");
            }

            @Override
            protected void done() {
                seedDataBtn.setEnabled(true);
                seedDataBtn.setText("Seçili Şemayı Veritabanına Yükle");
                try {
                    if (get()) {
                        if (onRefreshCallback != null) onRefreshCallback.run();
                        JOptionPane.showMessageDialog(UniversalDatabaseHubDialog.this,
                                "Örnek şema ve test verileri başarıyla yüklendi!",
                                "Şema Hazır", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(UniversalDatabaseHubDialog.this,
                                "Şema yüklenirken hata oluştu. Lütfen önce aktif bir veritabanı bağlantısı seçin.",
                                "Yükleme Uyarısı", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }
}

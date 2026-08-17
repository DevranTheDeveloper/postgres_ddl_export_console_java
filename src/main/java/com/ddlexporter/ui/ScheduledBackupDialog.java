package com.ddlexporter.ui;

import com.ddlexporter.schedule.ScheduledBackupManager;

import javax.swing.*;
import java.awt.*;

public class ScheduledBackupDialog extends JDialog {
    private final ScheduledBackupManager scheduleManager;
    private final ProfileManager profileManager;

    private final JCheckBox enableCheckBox = new JCheckBox("Otomatik Zamanlanmış DDL Yedeklemeyi Etkinleştir");
    private final JComboBox<String> profileSelector = new JComboBox<>();
    private final JComboBox<String> intervalSelector = new JComboBox<>(new String[]{
            "Her 15 Dakikada Bir",
            "Her 30 Dakikada Bir",
            "Saat Başı (Her 1 Saatte)",
            "Her 6 Saatte Bir",
            "Günde Bir (Her 24 Saatte)"
    });
    private final JCheckBox autoGitCheckBox = new JCheckBox("Yedekleme sonrası otomatik Git Commit oluştur");

    private final JLabel lastRunLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();
    private final JLabel totalRunsLabel = new JLabel();

    private final Runnable onConfigChanged;

    public ScheduledBackupDialog(Frame owner, ScheduledBackupManager scheduleManager, ProfileManager profileManager, Runnable onConfigChanged) {
        super(owner, "Otomatik Zamanlanmış DDL Yedekleme & Görev Yöneticisi", true);
        this.scheduleManager = scheduleManager;
        this.profileManager = profileManager;
        this.onConfigChanged = onConfigChanged;

        setSize(580, 480);
        setMinimumSize(new Dimension(520, 440));
        setLocationRelativeTo(owner);

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        // 1. Header
        JPanel header = new JPanel(new BorderLayout(0, 4));
        JLabel title = new JLabel("Arka Plan DDL Yedekleme Zamanlayıcısı");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        header.add(title, BorderLayout.NORTH);

        JLabel subtitle = new JLabel("Uygulama açıkken veritabanı şemanızı sessizce belirli aralıklarla yedekler ve Git ile eşitler.");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        subtitle.setForeground(new Color(100, 110, 125));
        header.add(subtitle, BorderLayout.SOUTH);
        mainPanel.add(header, BorderLayout.NORTH);

        // 2. Center: Config Form + Live Status Box
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        // Config Box
        JPanel configCard = new JPanel(new GridBagLayout());
        configCard.setBorder(BorderFactory.createTitledBorder("Zamanlama Yapılandırması"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Enable Switch
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        enableCheckBox.setFont(enableCheckBox.getFont().deriveFont(Font.BOLD, 13f));
        enableCheckBox.addActionListener(e -> updateFormState());
        configCard.add(enableCheckBox, gbc);

        // Target Profile
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        configCard.add(new JLabel("Hedef Profil:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        populateProfiles();
        configCard.add(profileSelector, gbc);

        // Interval
        gbc.gridx = 0; gbc.gridy = 2;
        configCard.add(new JLabel("Yedekleme Sıklığı:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        configCard.add(intervalSelector, gbc);

        // Auto Git
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        configCard.add(autoGitCheckBox, gbc);

        centerPanel.add(configCard);
        centerPanel.add(Box.createVerticalStrut(10));

        // Live Status Box
        JPanel statusCard = new JPanel(new GridLayout(3, 1, 4, 4));
        statusCard.setBorder(BorderFactory.createTitledBorder("Mevcut Görev Durumu & İstatistikler"));

        lastRunLabel.setFont(lastRunLabel.getFont().deriveFont(Font.PLAIN, 12f));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));
        totalRunsLabel.setFont(totalRunsLabel.getFont().deriveFont(Font.PLAIN, 12f));

        statusCard.add(lastRunLabel);
        statusCard.add(statusLabel);
        statusCard.add(totalRunsLabel);
        centerPanel.add(statusCard);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // 3. Footer Buttons
        JPanel footer = new JPanel(new BorderLayout());

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton runNowBtn = new JButton("Şimdi Çalıştır (Manuel)");
        runNowBtn.setFont(runNowBtn.getFont().deriveFont(Font.BOLD, 11f));
        runNowBtn.addActionListener(e -> {
            scheduleManager.runNow();
            JOptionPane.showMessageDialog(this,
                    "Otomatik yedekleme görevi arka planda başlatıldı!",
                    "Başlatıldı", JOptionPane.INFORMATION_MESSAGE);
            loadConfigToUi();
        });
        leftButtons.add(runNowBtn);

        JButton stopBtn = new JButton("Zamanlayıcıyı Durdur");
        stopBtn.setFont(stopBtn.getFont().deriveFont(Font.BOLD, 11f));
        stopBtn.setForeground(new Color(220, 38, 38));
        stopBtn.addActionListener(e -> stopSchedulerAction());
        leftButtons.add(stopBtn);

        footer.add(leftButtons, BorderLayout.WEST);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton saveBtn = new JButton("Kaydet & Başlat");
        saveBtn.setFont(saveBtn.getFont().deriveFont(Font.BOLD, 12f));
        saveBtn.setForeground(new Color(22, 163, 74));
        saveBtn.addActionListener(e -> saveAndApply());
        rightButtons.add(saveBtn);

        JButton closeBtn = new JButton("Kapat");
        closeBtn.addActionListener(e -> dispose());
        rightButtons.add(closeBtn);

        footer.add(rightButtons, BorderLayout.EAST);
        mainPanel.add(footer, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        loadConfigToUi();
    }

    private void stopSchedulerAction() {
        scheduleManager.stopScheduler();
        loadConfigToUi();
        if (onConfigChanged != null) {
            onConfigChanged.run();
        }
        JOptionPane.showMessageDialog(this,
                "Otomatik zamanlanmış DDL yedekleme durduruldu ve kapatıldı!",
                "Durduruldu", JOptionPane.INFORMATION_MESSAGE);
    }

    private void populateProfiles() {
        profileSelector.removeAllItems();
        var profiles = profileManager.getProfiles();
        for (String name : profiles.keySet()) {
            profileSelector.addItem(name);
        }
    }

    private void loadConfigToUi() {
        ScheduledBackupManager.ScheduleConfig cfg = scheduleManager.getConfig();
        enableCheckBox.setSelected(cfg.enabled);

        if (cfg.targetProfile != null && !cfg.targetProfile.isBlank()) {
            profileSelector.setSelectedItem(cfg.targetProfile);
        }

        if (cfg.intervalMinutes <= 15) intervalSelector.setSelectedIndex(0);
        else if (cfg.intervalMinutes <= 30) intervalSelector.setSelectedIndex(1);
        else if (cfg.intervalMinutes <= 60) intervalSelector.setSelectedIndex(2);
        else if (cfg.intervalMinutes <= 360) intervalSelector.setSelectedIndex(3);
        else intervalSelector.setSelectedIndex(4);

        autoGitCheckBox.setSelected(cfg.autoGitCommit);

        lastRunLabel.setText("Son Çalışma Zamanı : " + cfg.lastRunTime);
        statusLabel.setText("Son Durum          : " + cfg.lastStatus);
        if (cfg.lastStatus.startsWith("BAŞARILI")) {
            statusLabel.setForeground(new Color(22, 163, 74));
        } else if (cfg.lastStatus.startsWith("HATA")) {
            statusLabel.setForeground(new Color(220, 38, 38));
        } else {
            statusLabel.setForeground(new Color(100, 110, 125));
        }

        totalRunsLabel.setText("Toplam Yedekleme  : " + cfg.totalRuns + " adet");

        updateFormState();
    }

    private void updateFormState() {
        boolean enabled = enableCheckBox.isSelected();
        profileSelector.setEnabled(enabled);
        intervalSelector.setEnabled(enabled);
        autoGitCheckBox.setEnabled(enabled);
    }

    private void saveAndApply() {
        boolean enabled = enableCheckBox.isSelected();
        String selectedProfile = (String) profileSelector.getSelectedItem();
        int intervalMinutes = 60;
        int idx = intervalSelector.getSelectedIndex();
        if (idx == 0) intervalMinutes = 15;
        else if (idx == 1) intervalMinutes = 30;
        else if (idx == 2) intervalMinutes = 60;
        else if (idx == 3) intervalMinutes = 360;
        else if (idx == 4) intervalMinutes = 1440;

        boolean autoGit = autoGitCheckBox.isSelected();

        scheduleManager.updateSchedule(enabled, intervalMinutes, selectedProfile, autoGit);

        if (onConfigChanged != null) {
            onConfigChanged.run();
        }

        JOptionPane.showMessageDialog(this,
                "Zamanlanmış DDL yedekleme ayarları güncellendi!\nDurum: " + (enabled ? "Aktif (" + intervalMinutes + " dk)" : "Pasif"),
                "Başarılı", JOptionPane.INFORMATION_MESSAGE);

        dispose();
    }
}

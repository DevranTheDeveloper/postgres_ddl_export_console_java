package com.ddlexporter.ui;

import com.ddlexporter.update.UpdateManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.net.URI;

public class UpdateDialog extends JDialog {
    private final UpdateManager.ReleaseInfo releaseInfo;
    private final UpdateManager updateManager;
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Yeni bir sürüm hazır!");
    private final JButton updateBtn = new JButton("🚀 Şimdi Güncelle & Yeniden Başlat");
    private final JButton cancelBtn = new JButton("Daha Sonra");
    private final JButton browserBtn = new JButton("🌐 GitHub'da İncele");

    public UpdateDialog(Frame owner, UpdateManager.ReleaseInfo releaseInfo, UpdateManager updateManager) {
        super(owner, "Yazılım Güncellemesi", true);
        this.releaseInfo = releaseInfo;
        this.updateManager = updateManager;

        setSize(580, 480);
        setMinimumSize(new Dimension(500, 400));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 0));

        initComponents();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 16));
        mainPanel.setBorder(new EmptyBorder(20, 24, 20, 24));

        // Header Panel with Icon & Version Info
        JPanel headerPanel = new JPanel(new BorderLayout(14, 0));
        headerPanel.setOpaque(false);

        JLabel iconLabel = new JLabel("🚀");
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 36));
        headerPanel.add(iconLabel, BorderLayout.WEST);

        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        infoPanel.setOpaque(false);

        JLabel titleLabel = new JLabel(releaseInfo.updateAvailable ? "Yeni Bir Sürüm Yayınlandı!" : "Uygulamanız Güncel");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

        String versionText = "Mevcut Sürüm: v" + UpdateManager.CURRENT_VERSION +
                (releaseInfo.updateAvailable ? "  ➔  Yeni Sürüm: " + releaseInfo.tagName : " (En Son Sürüm)");
        JLabel verLabel = new JLabel(versionText);
        verLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        verLabel.setForeground(releaseInfo.updateAvailable ? new Color(34, 197, 94) : null);

        infoPanel.add(titleLabel);
        infoPanel.add(verLabel);
        headerPanel.add(infoPanel, BorderLayout.CENTER);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Center Panel with Release Notes
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);

        JLabel notesHeader = new JLabel("📋 Sürüm Değişiklikleri & Yenilikler:");
        notesHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        centerPanel.add(notesHeader, BorderLayout.NORTH);

        JTextArea changelogArea = new JTextArea();
        changelogArea.setEditable(false);
        changelogArea.setLineWrap(true);
        changelogArea.setWrapStyleWord(true);
        changelogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        changelogArea.setText(releaseInfo.body != null && !releaseInfo.body.isBlank() ?
                releaseInfo.body : "Herhangi bir sürüm notu girilmemiş.");
        changelogArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(changelogArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 65, 75), 1, true));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // Progress Section
        JPanel progressSection = new JPanel(new BorderLayout(0, 6));
        progressSection.setOpaque(false);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        progressSection.add(statusLabel, BorderLayout.NORTH);
        progressSection.add(progressBar, BorderLayout.SOUTH);
        centerPanel.add(progressSection, BorderLayout.SOUTH);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Bottom Actions Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftActions.setOpaque(false);
        browserBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        browserBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(releaseInfo.htmlUrl));
            } catch (Exception ignored) {}
        });
        leftActions.add(browserBtn);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightActions.setOpaque(false);

        cancelBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        cancelBtn.addActionListener(e -> dispose());

        updateBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        updateBtn.setEnabled(releaseInfo.updateAvailable && releaseInfo.jarDownloadUrl != null);
        updateBtn.addActionListener(e -> startDownloadAndUpdate());

        rightActions.add(cancelBtn);
        rightActions.add(updateBtn);

        bottomBar.add(leftActions, BorderLayout.WEST);
        bottomBar.add(rightActions, BorderLayout.EAST);
        mainPanel.add(bottomBar, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void startDownloadAndUpdate() {
        if (releaseInfo.jarDownloadUrl == null || releaseInfo.jarDownloadUrl.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Otomatik indirme paketi bulunamadı. Lütfen GitHub sayfasından manuel indirin.",
                    "İndirme Paketi Yok", JOptionPane.WARNING_MESSAGE);
            return;
        }

        updateBtn.setEnabled(false);
        cancelBtn.setEnabled(false);
        browserBtn.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setValue(0);
        statusLabel.setText("Yeni sürüm indiriliyor...");

        new SwingWorker<Void, Integer>() {
            private Exception downloadError = null;
            private File downloadedTemp = null;

            @Override
            protected Void doInBackground() {
                try {
                    downloadedTemp = File.createTempFile("PostgreSQL-DDL-Studio-update", ".jar");
                    downloadedTemp.deleteOnExit();

                    updateManager.downloadUpdate(releaseInfo.jarDownloadUrl, downloadedTemp, (readBytes, totalBytes) -> {
                        int percent = (int) ((readBytes * 100) / totalBytes);
                        double readMb = readBytes / (1024.0 * 1024.0);
                        double totalMb = totalBytes / (1024.0 * 1024.0);
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(percent);
                            statusLabel.setText(String.format("İndiriliyor: %.1f MB / %.1f MB (%%%d)", readMb, totalMb, percent));
                        });
                    });
                } catch (Exception ex) {
                    downloadError = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                if (downloadError == null && downloadedTemp != null && downloadedTemp.exists() && downloadedTemp.length() > 0) {
                    statusLabel.setText("İndirme tamamlandı! Uygulama yeniden başlatılıyor...");
                    progressBar.setValue(100);

                    Timer timer = new Timer(800, ev -> {
                        try {
                            UpdateManager.applyUpdateAndRestart(downloadedTemp);
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(UpdateDialog.this,
                                    "Güncelleme uygulanamadı: " + ex.getMessage(),
                                    "Hata", JOptionPane.ERROR_MESSAGE);
                            updateBtn.setEnabled(true);
                            cancelBtn.setEnabled(true);
                        }
                    });
                    timer.setRepeats(false);
                    timer.start();
                } else {
                    String msg = downloadError != null ? downloadError.getMessage() : "Bilinmeyen indirme hatası.";
                    statusLabel.setText("İndirme başarısız oldu: " + msg);
                    JOptionPane.showMessageDialog(UpdateDialog.this,
                            "Güncelleme dosyası indirilemedi:\n" + msg,
                            "Güncelleme Hatası", JOptionPane.ERROR_MESSAGE);
                    updateBtn.setEnabled(true);
                    cancelBtn.setEnabled(true);
                    browserBtn.setEnabled(true);
                }
            }
        }.execute();
    }
}

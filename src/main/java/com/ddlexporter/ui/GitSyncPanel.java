package com.ddlexporter.ui;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class GitSyncPanel extends JPanel {
    private final JTextArea statusArea;
    private final JTextField commitMsgField;
    private final JButton syncBtn;
    private final JButton refreshBtn;

    public GitSyncPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout(5, 5));
        JLabel titleLabel = new JLabel("Git & GitHub Senkronizasyon Merkezi");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        refreshBtn = new JButton("Durumu Yenile");
        refreshBtn.addActionListener(e -> refreshGitStatus());
        headerPanel.add(refreshBtn, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center: Git Status Console
        JPanel centerPanel = new JPanel(new BorderLayout(0, 6));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Değişen Dosyalar (Git Status)"));

        statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        statusArea.setBackground(new Color(25, 25, 25));
        statusArea.setForeground(new Color(150, 220, 255));
        statusArea.setMargin(new Insets(8, 8, 8, 8));

        centerPanel.add(new JScrollPane(statusArea), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Bottom: Commit Form & Push Button
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("GitHub'a Gönder"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JPanel formRow = new JPanel(new BorderLayout(8, 0));
        formRow.add(new JLabel("Commit Mesajı:"), BorderLayout.WEST);
        commitMsgField = new JTextField("feat: update PostgreSQL DDL schema export");
        formRow.add(commitMsgField, BorderLayout.CENTER);
        bottomPanel.add(formRow, BorderLayout.NORTH);

        syncBtn = new JButton("Değişiklikleri Commit Et & GitHub'a Push'la");
        syncBtn.setFont(syncBtn.getFont().deriveFont(Font.BOLD, 13f));
        syncBtn.setPreferredSize(new Dimension(200, 38));
        syncBtn.addActionListener(e -> pushToGit());
        bottomPanel.add(syncBtn, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Initial Load
        refreshGitStatus();
    }

    public void refreshGitStatus() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return runCommand("git status -s && git remote -v");
            }

            @Override
            protected void done() {
                try {
                    String output = get();
                    statusArea.setText(output.isBlank() ? "✅ Çalışma alanı temiz, bekleyen değişiklik yok." : output);
                } catch (Exception ex) {
                    statusArea.setText("Git durumu okunamadı: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void pushToGit() {
        String msg = commitMsgField.getText().trim();
        if (msg.isBlank()) {
            JOptionPane.showMessageDialog(this, "Lütfen bir commit mesajı yazın.", "Uyarı", JOptionPane.WARNING_MESSAGE);
            return;
        }

        syncBtn.setEnabled(false);
        syncBtn.setText("GitHub'a Gönderiliyor...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                StringBuilder log = new StringBuilder();
                log.append("--- git add export_output/ ---\n").append(runCommand("git add export_output/ pom.xml src/")).append("\n");
                log.append("--- git commit ---\n").append(runCommand("git commit -m \"" + msg.replace("\"", "\\\"") + "\"")).append("\n");
                log.append("--- git push origin main ---\n").append(runCommand("git push origin main")).append("\n");
                return log.toString();
            }

            @Override
            protected void done() {
                syncBtn.setEnabled(true);
                syncBtn.setText("🚀 Değişiklikleri Commit Et & GitHub'a Push'la");
                try {
                    String result = get();
                    statusArea.setText(result);
                    JOptionPane.showMessageDialog(GitSyncPanel.this,
                            "GitHub Senkronizasyon Tamamlandı!\nDetaylar konsolda listelendi.",
                            "Başarılı", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(GitSyncPanel.this,
                            "Git hatası: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private String runCommand(String cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            Process p = new ProcessBuilder("zsh", "-c", cmd).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
            }
            p.waitFor();
        } catch (Exception e) {
            sb.append("Komut çalıştırma hatası: ").append(e.getMessage());
        }
        return sb.toString();
    }
}

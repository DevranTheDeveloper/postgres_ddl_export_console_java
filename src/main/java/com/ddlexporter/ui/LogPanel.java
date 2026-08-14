package com.ddlexporter.ui;

import javax.swing.*;
import java.awt.*;

public class LogPanel extends JPanel {
    private final JTextArea logArea;
    private final JProgressBar progressBar;

    public LogPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("📊 Canlı Log Akışı & Durum"));

        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(20, 20, 20));
        logArea.setForeground(new Color(0, 255, 128)); // Hacker-green style output
        logArea.setMargin(new Insets(6, 6, 6, 6));

        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Hazır");
        bottomPanel.add(progressBar, BorderLayout.CENTER);

        JButton clearBtn = new JButton("🗑️ Temizle");
        clearBtn.addActionListener(e -> logArea.setText(""));
        bottomPanel.add(clearBtn, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void appendLog(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    public void setProgressIndeterminate(boolean indeterminate, String statusText) {
        progressBar.setIndeterminate(indeterminate);
        progressBar.setString(statusText);
    }

    public void setProgress(int value, String statusText) {
        progressBar.setIndeterminate(false);
        progressBar.setValue(value);
        progressBar.setString(statusText);
    }
}

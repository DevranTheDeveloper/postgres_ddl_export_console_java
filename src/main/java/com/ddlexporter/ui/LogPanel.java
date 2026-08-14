package com.ddlexporter.ui;

import javax.swing.*;
import java.awt.*;

public class LogPanel extends JPanel {
    private final JTextArea logArea;
    private final JProgressBar progressBar;

    public LogPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Canlı Log Akışı & Durum"));

        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setMargin(new Insets(6, 6, 6, 6));

        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Hazır");
        bottomPanel.add(progressBar, BorderLayout.CENTER);

        JButton clearBtn = new JButton("Temizle");
        clearBtn.setFont(clearBtn.getFont().deriveFont(Font.PLAIN, 11f));
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

    public void applyTheme(boolean isDark) {
        if (isDark) {
            logArea.setBackground(new Color(18, 18, 20));
            logArea.setForeground(new Color(34, 197, 94)); // Emerald green
            logArea.setCaretColor(Color.WHITE);
        } else {
            logArea.setBackground(new Color(248, 250, 252)); // Clean light slate
            logArea.setForeground(new Color(15, 118, 110)); // Crisp teal
            logArea.setCaretColor(Color.BLACK);
        }
    }
}

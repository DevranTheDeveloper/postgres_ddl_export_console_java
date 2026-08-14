package com.ddlexporter.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class DiagnosticsDialog extends JDialog {
    private final SystemDiagnosticsManager diagnosticsManager;
    private final DefaultTableModel tableModel;
    private final JTable issuesTable;
    private final JTextArea detailsArea = new JTextArea();
    private final JTextArea suggestionArea = new JTextArea();

    public DiagnosticsDialog(Frame owner, SystemDiagnosticsManager diagnosticsManager, boolean isDark) {
        super(owner, "Sistem Sağlığı, Uyarı ve Kritik Hata Raporu", true);
        this.diagnosticsManager = diagnosticsManager;

        setSize(780, 520);
        setMinimumSize(new Dimension(600, 400));
        setLocationRelativeTo(owner);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // 1. Header Summary
        JPanel header = new JPanel(new BorderLayout());
        int errs = diagnosticsManager.getErrorCount();
        int warns = diagnosticsManager.getWarningCount();

        JLabel headerTitle = new JLabel(String.format("Tespit Edilen Olaylar: %d Kritik Hata, %d Uyarı", errs, warns));
        headerTitle.setFont(headerTitle.getFont().deriveFont(Font.BOLD, 14f));
        if (errs > 0) {
            headerTitle.setForeground(new Color(220, 38, 38));
        } else if (warns > 0) {
            headerTitle.setForeground(new Color(217, 119, 6));
        } else {
            headerTitle.setForeground(new Color(22, 163, 74));
        }
        header.add(headerTitle, BorderLayout.WEST);

        JButton clearAllBtn = new JButton("Tümünü Temizle");
        clearAllBtn.setFont(clearAllBtn.getFont().deriveFont(Font.PLAIN, 11f));
        clearAllBtn.addActionListener(e -> {
            diagnosticsManager.clear();
            refreshTable();
            headerTitle.setText("Tespit Edilen Olaylar: 0 Kritik Hata, 0 Uyarı");
            headerTitle.setForeground(new Color(22, 163, 74));
            detailsArea.setText("Kayıtlı bir sorun bulunmamaktadır.");
            suggestionArea.setText("");
        });
        header.add(clearAllBtn, BorderLayout.EAST);
        mainPanel.add(header, BorderLayout.NORTH);

        // 2. Center: Split Pane (Top Table / Bottom Details + Suggestion)
        String[] cols = {"Seviye", "Zaman", "Başlık", "Kaynak"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        issuesTable = new JTable(tableModel);
        issuesTable.setRowHeight(24);
        issuesTable.getTableHeader().setFont(issuesTable.getTableHeader().getFont().deriveFont(Font.BOLD, 11f));
        issuesTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        issuesTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        issuesTable.getColumnModel().getColumn(2).setPreferredWidth(320);
        issuesTable.getColumnModel().getColumn(3).setPreferredWidth(120);

        issuesTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String val = String.valueOf(value);
                if ("KRİTİK HATA".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(220, 38, 38));
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                } else if ("UYARI".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(217, 119, 6));
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                }
                return c;
            }
        });

        issuesTable.getSelectionModel().addListSelectionListener(e -> {
            int selectedRow = issuesTable.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < diagnosticsManager.getIssues().size()) {
                SystemDiagnosticsManager.DiagnosticIssue issue = diagnosticsManager.getIssues().get(selectedRow);
                detailsArea.setText(issue.details);
                suggestionArea.setText(issue.suggestion != null && !issue.suggestion.isBlank()
                        ? issue.suggestion
                        : "Özel bir aksiyon gerekmemektedir.");
            }
        });

        JPanel detailsPanel = new JPanel(new GridLayout(2, 1, 6, 6));

        JPanel detailCard = new JPanel(new BorderLayout());
        detailCard.setBorder(BorderFactory.createTitledBorder("Olay ve Hata Detayı"));
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        detailCard.add(new JScrollPane(detailsArea), BorderLayout.CENTER);
        detailsPanel.add(detailCard);

        JPanel suggestionCard = new JPanel(new BorderLayout());
        suggestionCard.setBorder(BorderFactory.createTitledBorder("Önerilen Çözüm Aksiyonu"));
        suggestionArea.setEditable(false);
        suggestionArea.setLineWrap(true);
        suggestionArea.setWrapStyleWord(true);
        suggestionArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        suggestionCard.add(new JScrollPane(suggestionArea), BorderLayout.CENTER);
        detailsPanel.add(suggestionCard);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(issuesTable), detailsPanel);
        splitPane.setDividerLocation(180);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 3. Footer Close
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Kapat");
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);
        mainPanel.add(footer, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        refreshTable();

        if (tableModel.getRowCount() > 0) {
            issuesTable.setRowSelectionInterval(0, 0);
        } else {
            detailsArea.setText("Sistemde kayıtlı herhangi bir uyarı veya hata bulunmamaktadır. Her şey normal çalışıyor.");
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (SystemDiagnosticsManager.DiagnosticIssue issue : diagnosticsManager.getIssues()) {
            tableModel.addRow(new Object[]{
                    issue.level == SystemDiagnosticsManager.Level.ERROR ? "KRİTİK HATA" : "UYARI",
                    issue.timestamp,
                    issue.title,
                    issue.source
            });
        }
    }
}

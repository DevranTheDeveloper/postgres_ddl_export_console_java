package com.ddlexporter.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DiagnosticsDialog extends JDialog {
    private final SystemDiagnosticsManager diagnosticsManager;
    private final DefaultTableModel tableModel;
    private final JTable issuesTable;
    private final JTextArea detailsArea = new JTextArea();
    private final JTextArea suggestionArea = new JTextArea();

    private final JComboBox<String> filterCombo = new JComboBox<>(new String[]{
            "Aktif Olaylar",
            "Çözülenler (Arşiv)",
            "Tümü"
    });

    private final JButton resolveBtn = new JButton("Düzeltildi Olarak İşaretle");
    private final JButton reopenBtn = new JButton("Yeniden Aç");
    private final JLabel headerTitle = new JLabel();

    private List<SystemDiagnosticsManager.DiagnosticIssue> currentFilteredList = new ArrayList<>();

    public DiagnosticsDialog(Frame owner, SystemDiagnosticsManager diagnosticsManager, boolean isDark) {
        super(owner, "Sistem Sağlığı, Uyarı ve Kritik Hata Raporu", true);
        this.diagnosticsManager = diagnosticsManager;

        setSize(920, 580);
        setMinimumSize(new Dimension(720, 460));
        setLocationRelativeTo(owner);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // 1. Header: 2-Row Structured Header (Zero Overlap Guaranteed)
        JPanel headerContainer = new JPanel(new GridLayout(2, 1, 0, 8));
        headerContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        // Row 1: Main Title + Colored Stats Badge
        JPanel row1 = new JPanel(new BorderLayout(8, 0));
        JLabel titleLabel = new JLabel("Sistem Sağlığı & Teşhis Raporu");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        row1.add(titleLabel, BorderLayout.WEST);

        headerTitle.setFont(headerTitle.getFont().deriveFont(Font.BOLD, 13f));
        row1.add(headerTitle, BorderLayout.EAST);
        headerContainer.add(row1);

        // Row 2: Filter Toolbar (Left) + Clean Action Buttons (Right)
        JPanel row2 = new JPanel(new BorderLayout(8, 0));

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel filterLabel = new JLabel("Filtrele:");
        filterLabel.setFont(filterLabel.getFont().deriveFont(Font.BOLD, 12f));
        filterPanel.add(filterLabel);
        filterCombo.setPreferredSize(new Dimension(160, 26));
        filterCombo.addActionListener(e -> refreshTable());
        filterPanel.add(filterCombo);
        row2.add(filterPanel, BorderLayout.WEST);

        JPanel actionBtnsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton clearResolvedBtn = new JButton("Çözülenleri Temizle");
        clearResolvedBtn.setFont(clearResolvedBtn.getFont().deriveFont(Font.PLAIN, 11f));
        clearResolvedBtn.addActionListener(e -> {
            diagnosticsManager.clearResolved();
            refreshTable();
        });
        actionBtnsPanel.add(clearResolvedBtn);

        JButton clearAllBtn = new JButton("Tümünü Temizle");
        clearAllBtn.setFont(clearAllBtn.getFont().deriveFont(Font.PLAIN, 11f));
        clearAllBtn.addActionListener(e -> {
            int conf = JOptionPane.showConfirmDialog(this,
                    "Tüm hata ve uyarı kayıtları silinsin mi?", "Onay", JOptionPane.YES_NO_OPTION);
            if (conf == JOptionPane.YES_OPTION) {
                diagnosticsManager.clearAll();
                refreshTable();
            }
        });
        actionBtnsPanel.add(clearAllBtn);
        row2.add(actionBtnsPanel, BorderLayout.EAST);

        headerContainer.add(row2);
        mainPanel.add(headerContainer, BorderLayout.NORTH);

        // 2. Center: Split Pane (Top Table / Bottom Details + Suggestion)
        String[] cols = {"Durum", "Seviye", "Oluşma / Çözüm Zamanı", "Başlık", "Kaynak"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        issuesTable = new JTable(tableModel);
        issuesTable.setRowHeight(24);
        issuesTable.getTableHeader().setFont(issuesTable.getTableHeader().getFont().deriveFont(Font.BOLD, 11f));
        issuesTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        issuesTable.getColumnModel().getColumn(1).setPreferredWidth(95);
        issuesTable.getColumnModel().getColumn(2).setPreferredWidth(170);
        issuesTable.getColumnModel().getColumn(3).setPreferredWidth(320);
        issuesTable.getColumnModel().getColumn(4).setPreferredWidth(120);

        // Custom Renderers for Status and Level
        issuesTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String val = String.valueOf(value);
                if ("Çözüldü".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(22, 163, 74));
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                } else {
                    c.setForeground(new Color(220, 38, 38));
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                }
                return c;
            }
        });

        issuesTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String val = String.valueOf(value);
                if ("KRİTİK HATA".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(220, 38, 38));
                } else if ("UYARI".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(217, 119, 6));
                }
                return c;
            }
        });

        issuesTable.getSelectionModel().addListSelectionListener(e -> updateSelectedDetails());

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
        splitPane.setDividerLocation(210);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 3. Footer Action Controls
        JPanel footer = new JPanel(new BorderLayout());

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        resolveBtn.setFont(resolveBtn.getFont().deriveFont(Font.BOLD, 12f));
        resolveBtn.setForeground(new Color(22, 163, 74));
        resolveBtn.setEnabled(false);
        resolveBtn.addActionListener(e -> {
            int row = issuesTable.getSelectedRow();
            if (row >= 0 && row < currentFilteredList.size()) {
                SystemDiagnosticsManager.DiagnosticIssue issue = currentFilteredList.get(row);
                diagnosticsManager.setResolved(issue.id, true);
                refreshTable();
            }
        });
        leftActions.add(resolveBtn);

        reopenBtn.setFont(reopenBtn.getFont().deriveFont(Font.PLAIN, 12f));
        reopenBtn.setEnabled(false);
        reopenBtn.addActionListener(e -> {
            int row = issuesTable.getSelectedRow();
            if (row >= 0 && row < currentFilteredList.size()) {
                SystemDiagnosticsManager.DiagnosticIssue issue = currentFilteredList.get(row);
                diagnosticsManager.setResolved(issue.id, false);
                refreshTable();
            }
        });
        leftActions.add(reopenBtn);

        footer.add(leftActions, BorderLayout.WEST);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Kapat");
        closeBtn.addActionListener(e -> dispose());
        rightActions.add(closeBtn);
        footer.add(rightActions, BorderLayout.EAST);

        mainPanel.add(footer, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        refreshTable();
    }

    private void updateSelectedDetails() {
        int selectedRow = issuesTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < currentFilteredList.size()) {
            SystemDiagnosticsManager.DiagnosticIssue issue = currentFilteredList.get(selectedRow);
            detailsArea.setText(issue.details + (issue.resolved && issue.resolvedAt != null
                    ? "\n\n[DÜZELTİLDİ: " + issue.resolvedAt + "]"
                    : ""));
            suggestionArea.setText(issue.suggestion != null && !issue.suggestion.isBlank()
                    ? issue.suggestion
                    : "Özel bir aksiyon gerekmemektedir.");

            resolveBtn.setEnabled(!issue.resolved);
            reopenBtn.setEnabled(issue.resolved);
        } else {
            resolveBtn.setEnabled(false);
            reopenBtn.setEnabled(false);
        }
    }

    private void refreshTable() {
        int activeErrs = diagnosticsManager.getActiveErrorCount();
        int activeWarns = diagnosticsManager.getActiveWarningCount();
        int resolvedCount = diagnosticsManager.getResolvedIssues().size();

        headerTitle.setText(String.format("Aktif: %d Kritik Hata, %d Uyarı  |  Çözülen: %d",
                activeErrs, activeWarns, resolvedCount));

        if (activeErrs > 0) {
            headerTitle.setForeground(new Color(220, 38, 38));
        } else if (activeWarns > 0) {
            headerTitle.setForeground(new Color(217, 119, 6));
        } else {
            headerTitle.setForeground(new Color(22, 163, 74));
        }

        int filterIdx = filterCombo.getSelectedIndex();
        if (filterIdx == 0) {
            currentFilteredList = diagnosticsManager.getActiveIssues();
        } else if (filterIdx == 1) {
            currentFilteredList = diagnosticsManager.getResolvedIssues();
        } else {
            currentFilteredList = diagnosticsManager.getAllIssues();
        }

        tableModel.setRowCount(0);
        for (SystemDiagnosticsManager.DiagnosticIssue issue : currentFilteredList) {
            String timeStr = issue.resolved && issue.resolvedAt != null
                    ? issue.timestamp + " -> " + issue.resolvedAt
                    : issue.timestamp;

            tableModel.addRow(new Object[]{
                    issue.resolved ? "Çözüldü" : "Aktif",
                    issue.level == SystemDiagnosticsManager.Level.ERROR ? "KRİTİK HATA" : "UYARI",
                    timeStr,
                    issue.title,
                    issue.source
            });
        }

        if (tableModel.getRowCount() > 0) {
            issuesTable.setRowSelectionInterval(0, 0);
        } else {
            detailsArea.setText("Seçili filtreye uygun kayıt bulunmamaktadır.");
            suggestionArea.setText("");
            resolveBtn.setEnabled(false);
            reopenBtn.setEnabled(false);
        }
    }
}

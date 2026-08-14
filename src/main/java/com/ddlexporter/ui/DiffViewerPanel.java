package com.ddlexporter.ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class DiffViewerPanel extends JPanel {
    private final JTextArea leftTextArea;
    private final JTextArea rightTextArea;
    private final JLabel leftHeaderLabel;
    private final JLabel rightHeaderLabel;
    private final JComboBox<String> fileSelector;
    private File currentExportDir;

    public DiffViewerPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top Toolbar
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)
        ));

        JPanel leftTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel title = new JLabel("🔄 SQL Şema Farkı (Diff Viewer)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        leftTitle.add(title);

        fileSelector = new JComboBox<>();
        fileSelector.setPreferredSize(new Dimension(280, 28));
        fileSelector.addActionListener(e -> onFileSelected());
        leftTitle.add(new JLabel("Karşılaştırılacak Dosya:"));
        leftTitle.add(fileSelector);
        topBar.add(leftTitle, BorderLayout.WEST);

        JButton reloadBtn = new JButton("🔄 Yenile");
        reloadBtn.addActionListener(e -> reloadFiles());
        topBar.add(reloadBtn, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // Center Split Editor: Left (Previous / Reference), Right (Current Live Export)
        JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
        leftHeaderLabel = new JLabel("📄 Referans / Önceki Şema");
        leftHeaderLabel.setFont(leftHeaderLabel.getFont().deriveFont(Font.BOLD, 12f));
        leftHeaderLabel.setForeground(new Color(200, 120, 120));
        leftPanel.add(leftHeaderLabel, BorderLayout.NORTH);

        leftTextArea = createSqlTextArea();
        leftPanel.add(new JScrollPane(leftTextArea), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(0, 4));
        rightHeaderLabel = new JLabel("📄 Güncel Canlı DDL Şeması");
        rightHeaderLabel.setFont(rightHeaderLabel.getFont().deriveFont(Font.BOLD, 12f));
        rightHeaderLabel.setForeground(new Color(100, 200, 120));
        rightPanel.add(rightHeaderLabel, BorderLayout.NORTH);

        rightTextArea = createSqlTextArea();
        rightPanel.add(new JScrollPane(rightTextArea), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(420);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);
    }

    private JTextArea createSqlTextArea() {
        JTextArea area = new JTextArea();
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBackground(new Color(25, 25, 25));
        area.setForeground(new Color(220, 220, 220));
        area.setMargin(new Insets(6, 6, 6, 6));
        area.setEditable(false);
        return area;
    }

    public void setExportDir(String dirPath) {
        if (dirPath != null) {
            this.currentExportDir = new File(dirPath);
            reloadFiles();
        }
    }

    private void reloadFiles() {
        fileSelector.removeAllItems();
        if (currentExportDir != null && currentExportDir.exists()) {
            try {
                List<File> sqlFiles = Files.walk(currentExportDir.toPath())
                        .filter(p -> p.toString().endsWith(".sql"))
                        .map(java.nio.file.Path::toFile)
                        .sorted()
                        .toList();

                for (File file : sqlFiles) {
                    fileSelector.addItem(currentExportDir.toPath().relativize(file.toPath()).toString());
                }
            } catch (Exception ignored) {}
        }
    }

    private void onFileSelected() {
        String relativePath = (String) fileSelector.getSelectedItem();
        if (relativePath != null && currentExportDir != null) {
            File sqlFile = new File(currentExportDir, relativePath);
            if (sqlFile.exists()) {
                try {
                    String content = Files.readString(sqlFile.toPath());
                    rightTextArea.setText(content);
                    rightTextArea.setCaretPosition(0);
                    rightHeaderLabel.setText("📄 Güncel: " + sqlFile.getName());

                    // Left display placeholder or original content
                    leftTextArea.setText("-- Önceki/Referans şema karşılaştırması\n" + content);
                    leftTextArea.setCaretPosition(0);
                    leftHeaderLabel.setText("📄 Referans: " + sqlFile.getName());
                } catch (Exception e) {
                    rightTextArea.setText("Dosya okunamadı: " + e.getMessage());
                }
            }
        }
    }
}

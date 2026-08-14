package com.ddlexporter.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SchemaExplorerPanel extends JPanel {
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final JTextArea sqlTextArea;
    private final JTextArea lineNumbersArea;
    private final JLabel currentFileLabel;
    private final JLabel statsLabel;
    private final JTextField searchField;
    private final List<FileNode> allDiscoveredFiles = new ArrayList<>();
    private File currentExportDir;

    public SchemaExplorerPanel() {
        setLayout(new BorderLayout(5, 5));

        // Header / Search Toolbar
        JPanel topToolbar = new JPanel(new BorderLayout(8, 0));
        topToolbar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.add(new JLabel("🔍"), BorderLayout.WEST);
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Tablo, görünüm veya fonksiyon ara...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterTree(); }
            public void removeUpdate(DocumentEvent e) { filterTree(); }
            public void changedUpdate(DocumentEvent e) { filterTree(); }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);
        topToolbar.add(searchPanel, BorderLayout.CENTER);

        JButton copyBtn = new JButton("📋 SQL Kopyala");
        copyBtn.addActionListener(e -> copySqlToClipboard());
        topToolbar.add(copyBtn, BorderLayout.EAST);

        add(topToolbar, BorderLayout.NORTH);

        // Left: Tree View
        rootNode = new DefaultMutableTreeNode("Veritabanı Nesneleri");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setRowHeight(22);

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setLeafIcon(UIManager.getIcon("FileView.fileIcon"));
        renderer.setClosedIcon(UIManager.getIcon("Tree.closedIcon"));
        renderer.setOpenIcon(UIManager.getIcon("Tree.openIcon"));
        tree.setCellRenderer(renderer);

        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selected = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selected != null && selected.getUserObject() instanceof FileNode) {
                FileNode node = (FileNode) selected.getUserObject();
                loadFileContent(node.file);
            }
        });

        JScrollPane treeScrollPane = new JScrollPane(tree);
        treeScrollPane.setPreferredSize(new Dimension(280, 400));
        treeScrollPane.setBorder(BorderFactory.createTitledBorder("Nesne Ağacı"));

        // Right: SQL Text Editor with Line Numbers
        JPanel sqlEditorPanel = new JPanel(new BorderLayout());
        sqlEditorPanel.setBorder(BorderFactory.createTitledBorder("SQL Tanımı (DDL Script)"));

        currentFileLabel = new JLabel(" Henüz bir nesne seçilmedi");
        currentFileLabel.setFont(currentFileLabel.getFont().deriveFont(Font.ITALIC, 11f));
        currentFileLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        sqlEditorPanel.add(currentFileLabel, BorderLayout.NORTH);

        sqlTextArea = new JTextArea();
        sqlTextArea.setEditable(false);
        sqlTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        sqlTextArea.setMargin(new Insets(8, 8, 8, 8));

        lineNumbersArea = new JTextArea(" 1 ");
        lineNumbersArea.setEditable(false);
        lineNumbersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        lineNumbersArea.setMargin(new Insets(8, 6, 8, 6));

        JPanel editorWithLines = new JPanel(new BorderLayout());
        editorWithLines.add(lineNumbersArea, BorderLayout.WEST);
        editorWithLines.add(sqlTextArea, BorderLayout.CENTER);

        JScrollPane sqlScrollPane = new JScrollPane(editorWithLines);
        sqlEditorPanel.add(sqlScrollPane, BorderLayout.CENTER);

        // Stats Footer Bar
        statsLabel = new JLabel(" Satır: 0 | Karakter: 0 | UTF-8 ");
        statsLabel.setFont(statsLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statsLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        sqlEditorPanel.add(statsLabel, BorderLayout.SOUTH);

        // Apply initial light theme
        applyTheme(false);

        // Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, sqlEditorPanel);
        splitPane.setDividerLocation(290);
        splitPane.setResizeWeight(0.3);
        add(splitPane, BorderLayout.CENTER);
    }

    public void loadExportDirectory(String baseDir) {
        if (baseDir == null || baseDir.isBlank()) return;
        File dir = new File(baseDir);
        if (!dir.exists() || !dir.isDirectory()) return;

        this.currentExportDir = dir;
        allDiscoveredFiles.clear();

        File[] dbDirs = dir.listFiles(File::isDirectory);
        if (dbDirs != null) {
            for (File dbDir : dbDirs) {
                File[] typeDirs = dbDir.listFiles(File::isDirectory);
                if (typeDirs != null) {
                    for (File typeDir : typeDirs) {
                        File[] sqlFiles = typeDir.listFiles((d, name) -> name.endsWith(".sql"));
                        if (sqlFiles != null) {
                            for (File sqlFile : sqlFiles) {
                                allDiscoveredFiles.add(new FileNode(dbDir.getName(), typeDir.getName(), sqlFile.getName(), sqlFile));
                            }
                        }
                    }
                }
            }
        }

        buildTree(searchField.getText().trim());
    }

    private void filterTree() {
        buildTree(searchField.getText().trim());
    }

    private void buildTree(String filterText) {
        rootNode.removeAllChildren();
        rootNode.setUserObject("📁 Veritabanı Şeması (" + allDiscoveredFiles.size() + " Nesne)");

        String lowerFilter = filterText.toLowerCase();

        // Group by DB and Type
        java.util.Set<String> databases = new java.util.TreeSet<>();
        for (FileNode fn : allDiscoveredFiles) {
            if (lowerFilter.isEmpty() || fn.fileName.toLowerCase().contains(lowerFilter) || fn.typeName.toLowerCase().contains(lowerFilter)) {
                databases.add(fn.dbName);
            }
        }

        for (String db : databases) {
            DefaultMutableTreeNode dbNode = new DefaultMutableTreeNode("🗄️ " + db);
            rootNode.add(dbNode);

            java.util.Set<String> types = new java.util.TreeSet<>();
            for (FileNode fn : allDiscoveredFiles) {
                if (fn.dbName.equals(db) && (lowerFilter.isEmpty() || fn.fileName.toLowerCase().contains(lowerFilter) || fn.typeName.toLowerCase().contains(lowerFilter))) {
                    types.add(fn.typeName);
                }
            }

            for (String type : types) {
                String icon = getFolderIcon(type);
                DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(icon + " " + type);
                dbNode.add(typeNode);

                for (FileNode fn : allDiscoveredFiles) {
                    if (fn.dbName.equals(db) && fn.typeName.equals(type)) {
                        if (lowerFilter.isEmpty() || fn.fileName.toLowerCase().contains(lowerFilter)) {
                            typeNode.add(new DefaultMutableTreeNode(fn));
                        }
                    }
                }
            }
        }

        treeModel.reload();
        expandAllNodes(tree, 0, tree.getRowCount());
    }

    private String getFolderIcon(String typeName) {
        if (typeName.contains("TABLE")) return "📋";
        if (typeName.contains("VIEW")) return "👁️";
        if (typeName.contains("PROCEDURE") || typeName.contains("FUNCTION")) return "⚡";
        if (typeName.contains("SEQUENCE")) return "🔢";
        if (typeName.contains("TYPE")) return "🏷️";
        if (typeName.contains("INDEX")) return "🔍";
        if (typeName.contains("TRIGGER")) return "🎯";
        if (typeName.contains("SCHEMA")) return "📂";
        return "📁";
    }

    private void loadFileContent(File file) {
        try {
            String content = Files.readString(file.toPath());
            sqlTextArea.setText(content);
            sqlTextArea.setCaretPosition(0);
            currentFileLabel.setText(" 📄 " + file.getAbsolutePath());

            // Build line numbers
            String[] lines = content.split("\n", -1);
            StringBuilder lineNums = new StringBuilder();
            for (int i = 1; i <= Math.max(1, lines.length); i++) {
                lineNums.append(String.format(" %3d \n", i));
            }
            lineNumbersArea.setText(lineNums.toString());

            statsLabel.setText(String.format(" Satır: %d | Karakter: %d | Boyut: %.2f KB | UTF-8 ",
                    lines.length, content.length(), file.length() / 1024.0));
        } catch (Exception e) {
            sqlTextArea.setText("Dosya okunamadı: " + e.getMessage());
        }
    }

    private void copySqlToClipboard() {
        String text = sqlTextArea.getText();
        if (text != null && !text.isBlank()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            JOptionPane.showMessageDialog(this, "SQL panoya kopyalandı!", "Başarılı", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void applyTheme(boolean isDark) {
        if (isDark) {
            sqlTextArea.setBackground(new Color(24, 24, 27));
            sqlTextArea.setForeground(new Color(228, 228, 231));
            sqlTextArea.setCaretColor(Color.WHITE);

            lineNumbersArea.setBackground(new Color(34, 34, 38));
            lineNumbersArea.setForeground(new Color(115, 115, 125));

            statsLabel.setForeground(new Color(150, 150, 150));
        } else {
            // Crisp White Theme for SQL Editor
            sqlTextArea.setBackground(Color.WHITE);
            sqlTextArea.setForeground(new Color(31, 35, 40));
            sqlTextArea.setCaretColor(new Color(9, 105, 218));

            lineNumbersArea.setBackground(new Color(246, 248, 250));
            lineNumbersArea.setForeground(new Color(140, 149, 159));

            statsLabel.setForeground(new Color(100, 105, 115));
        }
    }

    private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; ++i) {
            tree.expandRow(i);
        }
        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
    }

    public static class FileNode {
        public final String dbName;
        public final String typeName;
        public final String fileName;
        public final File file;

        public FileNode(String dbName, String typeName, String fileName, File file) {
            this.dbName = dbName;
            this.typeName = typeName;
            this.fileName = fileName;
            this.file = file;
        }

        @Override
        public String toString() {
            return "📄 " + fileName;
        }
    }
}

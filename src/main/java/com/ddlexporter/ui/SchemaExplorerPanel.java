package com.ddlexporter.ui;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public class SchemaExplorerPanel extends JPanel {
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final JTextArea sqlTextArea;
    private final JLabel currentFileLabel;

    public SchemaExplorerPanel() {
        setLayout(new BorderLayout());

        // Header / Title
        JPanel headerPanel = new JPanel(new BorderLayout(5, 5));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JLabel titleLabel = new JLabel("📁 DDL Şema Gezgini & SQL Önizleme");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JButton copyButton = new JButton("📋 SQL Kopyala");
        copyButton.addActionListener(e -> copySqlToClipboard());
        headerPanel.add(copyButton, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Left: Tree View
        rootNode = new DefaultMutableTreeNode("Veritabanı Nesneleri");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setRowHeight(22);
        
        // Custom tree cell renderer with emoji icons
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

        // Right: SQL Text Editor
        JPanel sqlEditorPanel = new JPanel(new BorderLayout());
        sqlEditorPanel.setBorder(BorderFactory.createTitledBorder("SQL Tanımı (DDL)"));

        currentFileLabel = new JLabel(" Henüz bir nesne seçilmedi");
        currentFileLabel.setFont(currentFileLabel.getFont().deriveFont(Font.ITALIC, 11f));
        currentFileLabel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        sqlEditorPanel.add(currentFileLabel, BorderLayout.NORTH);

        sqlTextArea = new JTextArea();
        sqlTextArea.setEditable(false);
        sqlTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        sqlTextArea.setBackground(new Color(30, 30, 30));
        sqlTextArea.setForeground(new Color(220, 220, 220));
        sqlTextArea.setCaretColor(Color.WHITE);
        sqlTextArea.setMargin(new Insets(8, 8, 8, 8));

        JScrollPane sqlScrollPane = new JScrollPane(sqlTextArea);
        sqlEditorPanel.add(sqlScrollPane, BorderLayout.CENTER);

        // Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, sqlEditorPanel);
        splitPane.setDividerLocation(280);
        splitPane.setResizeWeight(0.3);
        add(splitPane, BorderLayout.CENTER);
    }

    public void loadExportDirectory(String baseDir) {
        if (baseDir == null || baseDir.isBlank()) return;
        File dir = new File(baseDir);
        if (!dir.exists() || !dir.isDirectory()) return;

        rootNode.removeAllChildren();
        rootNode.setUserObject("📁 " + dir.getName());

        File[] dbDirs = dir.listFiles(File::isDirectory);
        if (dbDirs != null) {
            Arrays.sort(dbDirs);
            for (File dbDir : dbDirs) {
                DefaultMutableTreeNode dbNode = new DefaultMutableTreeNode("🗄️ " + dbDir.getName());
                rootNode.add(dbNode);

                File[] typeDirs = dbDir.listFiles(File::isDirectory);
                if (typeDirs != null) {
                    Arrays.sort(typeDirs);
                    for (File typeDir : typeDirs) {
                        String icon = getFolderIcon(typeDir.getName());
                        DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(icon + " " + typeDir.getName());
                        dbNode.add(typeNode);

                        File[] sqlFiles = typeDir.listFiles((d, name) -> name.endsWith(".sql"));
                        if (sqlFiles != null) {
                            Arrays.sort(sqlFiles);
                            for (File sqlFile : sqlFiles) {
                                String cleanName = sqlFile.getName();
                                typeNode.add(new DefaultMutableTreeNode(new FileNode(cleanName, sqlFile)));
                            }
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

    private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; ++i) {
            tree.expandRow(i);
        }
        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
    }

    public static class FileNode {
        public final String displayName;
        public final File file;

        public FileNode(String displayName, File file) {
            this.displayName = displayName;
            this.file = file;
        }

        @Override
        public String toString() {
            return "📄 " + displayName;
        }
    }
}

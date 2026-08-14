package com.ddlexporter.ui;

import com.ddlexporter.common.util.SqlFormatter;
import com.ddlexporter.common.util.ZipArchiver;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
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

    // Edit Safety & State
    private File currentSelectedFile = null;
    private String originalFileContent = "";
    private boolean isEditMode = false;
    private boolean isModified = false;
    private final JButton editToggleBtn = new JButton("Düzenle (Kilitli)");
    private final JButton saveBtn = new JButton("Kaydet");
    private final JButton discardBtn = new JButton("Geri Al");
    private Runnable onFileSavedListener = null;

    public SchemaExplorerPanel() {
        setLayout(new BorderLayout(5, 5));

        // Header / Search & Action Toolbar
        JPanel topToolbar = new JPanel(new BorderLayout(8, 0));
        topToolbar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        JLabel searchLabel = new JLabel("Ara:");
        searchLabel.setFont(searchLabel.getFont().deriveFont(Font.BOLD, 12f));
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Tablo, view veya fonksiyon ara...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterTree(); }
            public void removeUpdate(DocumentEvent e) { filterTree(); }
            public void changedUpdate(DocumentEvent e) { filterTree(); }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);
        topToolbar.add(searchPanel, BorderLayout.CENTER);

        // Action Buttons Toolbar: [Düzenle] [Kaydet] [Geri Al] [SQL Kopyala]
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        editToggleBtn.setFont(editToggleBtn.getFont().deriveFont(Font.BOLD, 12f));
        editToggleBtn.setFocusPainted(false);
        editToggleBtn.setToolTipText("Yanlışlıkla değişiklik yapılmasını önlemek için kilitlidir. Kilidi açmak için tıklayın.");
        editToggleBtn.addActionListener(e -> toggleEditMode());
        actionsPanel.add(editToggleBtn);

        saveBtn.setFont(saveBtn.getFont().deriveFont(Font.BOLD, 12f));
        saveBtn.setEnabled(false);
        saveBtn.setToolTipText("Değişiklikleri diske kaydet (⌘+S / Ctrl+S)");
        saveBtn.addActionListener(e -> saveCurrentFile());
        actionsPanel.add(saveBtn);

        discardBtn.setFont(discardBtn.getFont().deriveFont(Font.PLAIN, 12f));
        discardBtn.setEnabled(false);
        discardBtn.setToolTipText("Yapılan değişiklikleri iptal edip orijinal haline döndür");
        discardBtn.addActionListener(e -> discardChanges());
        actionsPanel.add(discardBtn);

        JButton formatBtn = new JButton("Formatla");
        formatBtn.setFont(formatBtn.getFont().deriveFont(Font.PLAIN, 12f));
        formatBtn.setToolTipText("SQL kodunu büyük harfler ve standart girintilerle güzelleştir");
        formatBtn.addActionListener(e -> formatCurrentSql());
        actionsPanel.add(formatBtn);

        JButton copyBtn = new JButton("SQL Kopyala");
        copyBtn.setFont(copyBtn.getFont().deriveFont(Font.PLAIN, 12f));
        copyBtn.addActionListener(e -> copySqlToClipboard());
        actionsPanel.add(copyBtn);

        JButton zipBtn = new JButton("ZIP Arşivle");
        zipBtn.setFont(zipBtn.getFont().deriveFont(Font.BOLD, 12f));
        zipBtn.setForeground(new Color(9, 105, 218));
        zipBtn.setToolTipText("Tüm veritabanı şema çıktılarını tek bir .zip arşivi olarak kaydet");
        zipBtn.addActionListener(e -> exportAsZip());
        actionsPanel.add(zipBtn);

        topToolbar.add(actionsPanel, BorderLayout.EAST);
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
                if (currentSelectedFile != null && isModified) {
                    if (!promptSaveBeforeSwitch()) {
                        return;
                    }
                }
                loadFileContent(node.file);
            }
        });

        JScrollPane treeScrollPane = new JScrollPane(tree);
        treeScrollPane.setPreferredSize(new Dimension(280, 400));
        treeScrollPane.setBorder(BorderFactory.createTitledBorder("Nesne Ağacı"));

        // Right: SQL Text Editor with Line Numbers
        JPanel sqlEditorPanel = new JPanel(new BorderLayout());
        sqlEditorPanel.setBorder(BorderFactory.createTitledBorder("SQL Tanımı (DDL Script)"));

        currentFileLabel = new JLabel(" Henüz bir nesne seçilmedi (Kilitli / Salt Okunur)");
        currentFileLabel.setFont(currentFileLabel.getFont().deriveFont(Font.PLAIN, 11f));
        currentFileLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        sqlEditorPanel.add(currentFileLabel, BorderLayout.NORTH);

        sqlTextArea = new JTextArea();
        sqlTextArea.setEditable(false);
        sqlTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        sqlTextArea.setMargin(new Insets(8, 8, 8, 8));

        // Keyboard Shortcut: Cmd+S / Ctrl+S to save
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        sqlTextArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask), "saveAction");
        sqlTextArea.getActionMap().put("saveAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isEditMode && isModified) {
                    saveCurrentFile();
                }
            }
        });

        // Track text changes
        sqlTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { onTextChanged(); }
            @Override
            public void removeUpdate(DocumentEvent e) { onTextChanged(); }
            @Override
            public void changedUpdate(DocumentEvent e) { onTextChanged(); }
        });

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
        statsLabel = new JLabel(" Satır: 0 | Karakter: 0 | UTF-8 | Kilitli ");
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

    public void setOnFileSavedListener(Runnable listener) {
        this.onFileSavedListener = listener;
    }

    private void toggleEditMode() {
        if (currentSelectedFile == null) {
            JOptionPane.showMessageDialog(this, "Lütfen önce ağaçtan düzenlemek istediğiniz bir SQL dosyası seçin.",
                    "Dosya Seçilmedi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        isEditMode = !isEditMode;
        sqlTextArea.setEditable(isEditMode);

        if (isEditMode) {
            editToggleBtn.setText("Düzenleme Modu (Açık)");
            editToggleBtn.setForeground(new Color(22, 163, 74));
            updateFileLabelStatus();
        } else {
            editToggleBtn.setText("Düzenle (Kilitli)");
            editToggleBtn.setForeground(null);
            updateFileLabelStatus();
        }
    }

    private void onTextChanged() {
        if (currentSelectedFile == null) return;

        String currentText = sqlTextArea.getText();
        isModified = !currentText.equals(originalFileContent);

        saveBtn.setEnabled(isEditMode && isModified);
        discardBtn.setEnabled(isModified);

        updateFileLabelStatus();
        updateLineNumbers(currentText);
    }

    private void updateFileLabelStatus() {
        if (currentSelectedFile == null) {
            currentFileLabel.setText(" Henüz bir nesne seçilmedi");
            return;
        }

        String statusSuffix;
        if (isModified) {
            statusSuffix = " [Değiştirildi - Kaydedilmedi]";
        } else if (isEditMode) {
            statusSuffix = " [Düzenleme Modu Aktif]";
        } else {
            statusSuffix = " [Kilitli / Salt Okunur]";
        }

        currentFileLabel.setText(currentSelectedFile.getAbsolutePath() + statusSuffix);
        if (isModified) {
            currentFileLabel.setForeground(new Color(217, 119, 6)); // Amber
        } else {
            currentFileLabel.setForeground(null);
        }
    }

    private void updateLineNumbers(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder lineNums = new StringBuilder();
        for (int i = 1; i <= Math.max(1, lines.length); i++) {
            lineNums.append(String.format(" %3d \n", i));
        }
        lineNumbersArea.setText(lineNums.toString());

        statsLabel.setText(String.format(" Satır: %d | Karakter: %d | Durum: %s | UTF-8 ",
                lines.length, content.length(), isEditMode ? "Düzenlenebilir" : "Kilitli"));
    }

    public void saveCurrentFile() {
        if (currentSelectedFile == null) return;
        try {
            String newContent = sqlTextArea.getText();
            Files.writeString(currentSelectedFile.toPath(), newContent);
            originalFileContent = newContent;
            isModified = false;
            saveBtn.setEnabled(false);
            discardBtn.setEnabled(false);
            updateFileLabelStatus();

            JOptionPane.showMessageDialog(this,
                    "'" + currentSelectedFile.getName() + "' dosyası başarıyla kaydedildi.",
                    "Kaydedildi", JOptionPane.INFORMATION_MESSAGE);

            if (onFileSavedListener != null) {
                onFileSavedListener.run();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Dosya kaydedilemedi: " + ex.getMessage(),
                    "Kayıt Hatası", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void discardChanges() {
        if (currentSelectedFile == null) return;
        int conf = JOptionPane.showConfirmDialog(this,
                "'" + currentSelectedFile.getName() + "' üzerindeki kaydedilmemiş tüm değişiklikler geri alınacak.\nEmin misiniz?",
                "Değişiklikleri Geri Al",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (conf == JOptionPane.YES_OPTION) {
            sqlTextArea.setText(originalFileContent);
            isModified = false;
            saveBtn.setEnabled(false);
            discardBtn.setEnabled(false);
            updateFileLabelStatus();
        }
    }

    private boolean promptSaveBeforeSwitch() {
        int choice = JOptionPane.showConfirmDialog(this,
                "'" + currentSelectedFile.getName() + "' üzerinde kaydedilmemiş değişiklikleriniz var.\nKaydetmek istiyor musunuz?",
                "Kaydedilmemiş Değişiklik",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            saveCurrentFile();
            return true;
        } else if (choice == JOptionPane.NO_OPTION) {
            isModified = false;
            return true;
        } else {
            return false; // Cancel switch
        }
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
        rootNode.setUserObject("Veritabanı Şeması (" + allDiscoveredFiles.size() + " Nesne)");

        String lowerFilter = filterText.toLowerCase();

        java.util.Set<String> databases = new java.util.TreeSet<>();
        for (FileNode fn : allDiscoveredFiles) {
            if (lowerFilter.isEmpty() || fn.fileName.toLowerCase().contains(lowerFilter) || fn.typeName.toLowerCase().contains(lowerFilter)) {
                databases.add(fn.dbName);
            }
        }

        for (String db : databases) {
            DefaultMutableTreeNode dbNode = new DefaultMutableTreeNode(db);
            rootNode.add(dbNode);

            java.util.Set<String> types = new java.util.TreeSet<>();
            for (FileNode fn : allDiscoveredFiles) {
                if (fn.dbName.equals(db) && (lowerFilter.isEmpty() || fn.fileName.toLowerCase().contains(lowerFilter) || fn.typeName.toLowerCase().contains(lowerFilter))) {
                    types.add(fn.typeName);
                }
            }

            for (String type : types) {
                DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(type);
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

    private void loadFileContent(File file) {
        try {
            this.currentSelectedFile = file;
            String content = Files.readString(file.toPath());
            this.originalFileContent = content;
            this.isModified = false;
            this.isEditMode = false;

            sqlTextArea.setText(content);
            sqlTextArea.setCaretPosition(0);
            sqlTextArea.setEditable(false);

            editToggleBtn.setText("Düzenle (Kilitli)");
            editToggleBtn.setForeground(null);
            saveBtn.setEnabled(false);
            discardBtn.setEnabled(false);

            updateFileLabelStatus();
            updateLineNumbers(content);
        } catch (Exception e) {
            sqlTextArea.setText("Dosya okunamadı: " + e.getMessage());
        }
    }

    public boolean openTableFile(String tableName) {
        if (tableName == null || tableName.isBlank()) return false;
        String target = tableName.toLowerCase();
        if (!target.endsWith(".sql")) target += ".sql";

        for (FileNode node : allDiscoveredFiles) {
            if (node.fileName.equalsIgnoreCase(target) || node.fileName.toLowerCase().contains(tableName.toLowerCase())) {
                loadFileContent(node.file);
                searchField.setText(tableName);
                return true;
            }
        }
        return false;
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
            setBackground(new Color(30, 31, 34));
            sqlTextArea.setBackground(new Color(24, 24, 27));
            sqlTextArea.setForeground(new Color(228, 228, 231));
            sqlTextArea.setCaretColor(Color.WHITE);

            lineNumbersArea.setBackground(new Color(32, 33, 36));
            lineNumbersArea.setForeground(new Color(115, 115, 125));

            tree.setBackground(new Color(30, 31, 34));
            tree.setForeground(new Color(220, 225, 235));

            DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getCellRenderer();
            if (renderer != null) {
                renderer.setBackgroundNonSelectionColor(new Color(30, 31, 34));
                renderer.setTextNonSelectionColor(new Color(220, 225, 235));
                renderer.setTextSelectionColor(Color.WHITE);
                renderer.setBackgroundSelectionColor(new Color(45, 65, 100));
            }

            statsLabel.setForeground(new Color(150, 150, 150));
            currentFileLabel.setForeground(new Color(210, 215, 225));
        } else {
            setBackground(new Color(248, 250, 252));
            sqlTextArea.setBackground(Color.WHITE);
            sqlTextArea.setForeground(new Color(31, 35, 40));
            sqlTextArea.setCaretColor(new Color(9, 105, 218));

            lineNumbersArea.setBackground(new Color(246, 248, 250));
            lineNumbersArea.setForeground(new Color(140, 149, 159));

            tree.setBackground(Color.WHITE);
            tree.setForeground(new Color(30, 41, 59));

            DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getCellRenderer();
            if (renderer != null) {
                renderer.setBackgroundNonSelectionColor(Color.WHITE);
                renderer.setTextNonSelectionColor(new Color(30, 41, 59));
                renderer.setTextSelectionColor(Color.BLACK);
                renderer.setBackgroundSelectionColor(new Color(204, 232, 255));
            }

            statsLabel.setForeground(new Color(100, 105, 115));
            currentFileLabel.setForeground(new Color(51, 65, 85));
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

    private void formatCurrentSql() {
        String currentText = sqlTextArea.getText();
        if (currentText == null || currentText.isBlank()) return;

        String formatted = SqlFormatter.formatSql(currentText);
        sqlTextArea.setText(formatted);
        sqlTextArea.setCaretPosition(0);
        onTextChanged();
    }

    private void exportAsZip() {
        if (currentExportDir == null || !currentExportDir.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Arşivlenecek dışa aktarım klasörü bulunamadı. Lütfen önce 'Dışa Aktar' butonuna basın.",
                    "Klasör Yok", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String defaultName = "database_schema_" + java.time.LocalDateTime.now().format(dtf) + ".zip";
        fileChooser.setSelectedFile(new File(defaultName));

        int res = fileChooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File targetZip = fileChooser.getSelectedFile();
            if (!targetZip.getName().toLowerCase().endsWith(".zip")) {
                targetZip = new File(targetZip.getParentFile(), targetZip.getName() + ".zip");
            }

            try {
                ZipArchiver.zipDirectory(currentExportDir, targetZip);
                JOptionPane.showMessageDialog(this,
                        "Şema dosyaları başarıyla ZIP olarak paketlendi!\nKonum: " + targetZip.getAbsolutePath(),
                        "ZIP Arşivlendi", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "ZIP arşivi oluşturulamadı: " + ex.getMessage(),
                        "Hata", JOptionPane.ERROR_MESSAGE);
            }
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
            return fileName;
        }
    }
}

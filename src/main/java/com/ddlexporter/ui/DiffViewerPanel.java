package com.ddlexporter.ui;

import com.ddlexporter.migration.MigrationScriptGenerator;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class DiffViewerPanel extends JPanel {
    private final JTextArea leftTextArea;
    private final JTextArea rightTextArea;
    private final JTextArea migrationTextArea;
    private final JLabel leftHeaderLabel;
    private final JLabel rightHeaderLabel;
    private final JComboBox<String> fileSelector;
    private final JButton generateMigrationBtn;
    private final JButton copyMigrationBtn;
    private final JButton saveMigrationBtn;
    private final JButton testDiffBtn;
    private File currentExportDir;
    private boolean isDark = false;

    public DiffViewerPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Top Toolbar
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 215, 225)),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)
        ));

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel title = new JLabel("SQL Şema Farkı & Migration Üretici");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        leftControls.add(title);

        fileSelector = new JComboBox<>();
        fileSelector.setPreferredSize(new Dimension(240, 28));
        fileSelector.addActionListener(e -> onFileSelected());
        leftControls.add(new JLabel("Dosya:"));
        leftControls.add(fileSelector);
        topBar.add(leftControls, BorderLayout.WEST);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        testDiffBtn = new JButton("🧪 Test Farkı Oluştur");
        testDiffBtn.setFont(testDiffBtn.getFont().deriveFont(Font.PLAIN, 11f));
        testDiffBtn.setToolTipText("Yeni kolon ve indeks farkı simüle ederek Migration motorunu test edin");
        testDiffBtn.addActionListener(e -> simulateDiffForTesting());
        rightControls.add(testDiffBtn);

        generateMigrationBtn = new JButton("⚡ Migration (ALTER) Üret");
        generateMigrationBtn.setFont(generateMigrationBtn.getFont().deriveFont(Font.BOLD, 12f));
        generateMigrationBtn.setForeground(new Color(22, 163, 74));
        generateMigrationBtn.addActionListener(e -> generateMigration());
        rightControls.add(generateMigrationBtn);

        JButton reloadBtn = new JButton("Yenile");
        reloadBtn.setFont(reloadBtn.getFont().deriveFont(Font.PLAIN, 12f));
        reloadBtn.addActionListener(e -> reloadFiles());
        rightControls.add(reloadBtn);

        topBar.add(rightControls, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // 2. Center & Bottom Split: Top (Side-by-side DDL comparison) | Bottom (Generated Migration Script)
        JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
        leftHeaderLabel = new JLabel("Referans / Önceki Şema");
        leftHeaderLabel.setFont(leftHeaderLabel.getFont().deriveFont(Font.BOLD, 12f));
        leftHeaderLabel.setForeground(new Color(220, 38, 38));
        leftPanel.add(leftHeaderLabel, BorderLayout.NORTH);

        leftTextArea = createSqlTextArea(true);
        leftPanel.add(new JScrollPane(leftTextArea), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(0, 4));
        rightHeaderLabel = new JLabel("Güncel Canlı DDL Şeması");
        rightHeaderLabel.setFont(rightHeaderLabel.getFont().deriveFont(Font.BOLD, 12f));
        rightHeaderLabel.setForeground(new Color(22, 163, 74));
        rightPanel.add(rightHeaderLabel, BorderLayout.NORTH);

        rightTextArea = createSqlTextArea(true);
        rightPanel.add(new JScrollPane(rightTextArea), BorderLayout.CENTER);

        JSplitPane diffSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        diffSplitPane.setDividerLocation(460);
        diffSplitPane.setResizeWeight(0.5);

        // 3. Bottom Generated Migration Script Panel
        JPanel migrationPanel = new JPanel(new BorderLayout(0, 4));
        migrationPanel.setBorder(BorderFactory.createTitledBorder("⚡ Otomatik Üretilen PostgreSQL Migration (ALTER) Scripti"));

        JPanel migrationToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        copyMigrationBtn = new JButton("📋 Migration SQL Kopyala");
        copyMigrationBtn.setFont(copyMigrationBtn.getFont().deriveFont(Font.BOLD, 11f));
        copyMigrationBtn.addActionListener(e -> copyMigrationToClipboard());
        migrationToolbar.add(copyMigrationBtn);

        saveMigrationBtn = new JButton("💾 .sql Olarak Kaydet");
        saveMigrationBtn.setFont(saveMigrationBtn.getFont().deriveFont(Font.PLAIN, 11f));
        saveMigrationBtn.addActionListener(e -> exportMigrationSql());
        migrationToolbar.add(saveMigrationBtn);
        migrationPanel.add(migrationToolbar, BorderLayout.NORTH);

        migrationTextArea = createSqlTextArea(false);
        migrationTextArea.setText("-- Yukarıdaki şemalar karşılaştırıldığında 'Migration (ALTER) Üret' butonuna basarak canlı migration scripti üretebilirsiniz.");
        migrationPanel.add(new JScrollPane(migrationTextArea), BorderLayout.CENTER);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, diffSplitPane, migrationPanel);
        mainSplitPane.setDividerLocation(310);
        mainSplitPane.setResizeWeight(0.65);
        add(mainSplitPane, BorderLayout.CENTER);

        // Auto regenerate on text change in diff editors
        DocumentListener changeListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { generateMigration(); }
            public void removeUpdate(DocumentEvent e) { generateMigration(); }
            public void changedUpdate(DocumentEvent e) { generateMigration(); }
        };
        leftTextArea.getDocument().addDocumentListener(changeListener);
        rightTextArea.getDocument().addDocumentListener(changeListener);
    }

    private JTextArea createSqlTextArea(boolean editable) {
        JTextArea area = new JTextArea();
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setMargin(new Insets(6, 6, 6, 6));
        area.setEditable(editable);
        return area;
    }

    public void generateMigration() {
        String fileName = (String) fileSelector.getSelectedItem();
        String oldSql = leftTextArea.getText();
        String newSql = rightTextArea.getText();

        String migration = MigrationScriptGenerator.generateMigrationScript(fileName, oldSql, newSql);
        migrationTextArea.setText(migration);
        migrationTextArea.setCaretPosition(0);
    }

    private void simulateDiffForTesting() {
        String base = rightTextArea.getText();
        if (base == null || base.isBlank()) {
            base = "CREATE TABLE public.products (\n" +
                    "    id integer NOT NULL,\n" +
                    "    name character varying(255) NOT NULL,\n" +
                    "    price numeric(10,2)\n" +
                    ");";
        }

        // Set left (old) as baseline
        leftTextArea.setText(base);

        // Set right (new) with added columns and an index
        String updated = base.replace("price numeric(10,2)",
                "price numeric(10,2),\n    created_at timestamp with time zone DEFAULT now(),\n    is_active boolean DEFAULT true")
                + "\n\nCREATE INDEX idx_products_created_at ON public.products (created_at);";

        rightTextArea.setText(updated);
        generateMigration();
    }

    private void copyMigrationToClipboard() {
        String text = migrationTextArea.getText();
        if (text != null && !text.isBlank()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            JOptionPane.showMessageDialog(this, "Migration scripti panoya kopyalandı!", "Kopyalandı", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void exportMigrationSql() {
        String text = migrationTextArea.getText();
        if (text == null || text.isBlank()) return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("V1__migration_auto_generated.sql"));
        int res = fileChooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            try {
                Files.writeString(fileChooser.getSelectedFile().toPath(), text);
                JOptionPane.showMessageDialog(this,
                        "Migration scripti başarıyla kaydedildi:\n" + fileChooser.getSelectedFile().getAbsolutePath(),
                        "Başarılı", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Kaydetme hatası: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void applyTheme(boolean isDark) {
        this.isDark = isDark;
        if (isDark) {
            Color darkBg = new Color(24, 24, 27);
            Color darkFg = new Color(228, 228, 231);
            leftTextArea.setBackground(darkBg);
            leftTextArea.setForeground(darkFg);
            rightTextArea.setBackground(darkBg);
            rightTextArea.setForeground(darkFg);
            migrationTextArea.setBackground(new Color(18, 20, 24));
            migrationTextArea.setForeground(new Color(230, 235, 245));
        } else {
            leftTextArea.setBackground(Color.WHITE);
            leftTextArea.setForeground(new Color(31, 35, 40));
            rightTextArea.setBackground(Color.WHITE);
            rightTextArea.setForeground(new Color(31, 35, 40));
            migrationTextArea.setBackground(new Color(248, 250, 252));
            migrationTextArea.setForeground(new Color(15, 23, 42));
        }
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
                    rightHeaderLabel.setText("📄 Güncel Şema: " + sqlFile.getName());

                    leftTextArea.setText(content);
                    leftTextArea.setCaretPosition(0);
                    leftHeaderLabel.setText("📄 Referans / Önceki Şema: " + sqlFile.getName());

                    generateMigration();
                } catch (Exception e) {
                    rightTextArea.setText("Dosya okunamadı: " + e.getMessage());
                }
            }
        }
    }
}

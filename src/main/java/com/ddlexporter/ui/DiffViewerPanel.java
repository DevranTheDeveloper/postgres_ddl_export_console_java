package com.ddlexporter.ui;

import com.ddlexporter.migration.EnvironmentDiffEngine;
import com.ddlexporter.migration.MigrationScriptGenerator;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class DiffViewerPanel extends JPanel {
    private final ProfileManager profileManager;

    // Sub-Tab 1: File & Script Diff Controls
    private final JTextArea leftTextArea = createSqlTextArea(true);
    private final JTextArea rightTextArea = createSqlTextArea(true);
    private final JTextArea migrationTextArea = createSqlTextArea(false);
    private final JLabel leftHeaderLabel = new JLabel("Referans / Önceki Şema");
    private final JLabel rightHeaderLabel = new JLabel("Güncel Canlı DDL Şeması");
    private final JComboBox<String> fileSelector = new JComboBox<>();
    private File currentExportDir;

    // Sub-Tab 2: Live Environment Comparison Controls
    private final JComboBox<String> sourceProfileSelector = new JComboBox<>();
    private final JComboBox<String> targetProfileSelector = new JComboBox<>();
    private final JLabel matchKpiLabel = new JLabel("0");
    private final JLabel modifiedKpiLabel = new JLabel("0");
    private final JLabel missingKpiLabel = new JLabel("0");
    private final DefaultTableModel envDiffTableModel;
    private final JTable envDiffTable;
    private final JTextArea envPatchTextArea = createSqlTextArea(false);

    private boolean isDark = false;

    public DiffViewerPanel(ProfileManager profileManager) {
        this.profileManager = profileManager;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JTabbedPane rootTabbedPane = new JTabbedPane();

        // =========================================================================
        // MODE 1: File & DDL Script Diff (Tekil Dosya Karşılaştırma)
        // =========================================================================
        JPanel fileDiffPanel = new JPanel(new BorderLayout(8, 8));
        fileDiffPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Top Toolbar
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 215, 225)),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)
        ));

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel title = new JLabel("SQL Şema Farkı & Migration Üretici");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        leftControls.add(title);

        fileSelector.setPreferredSize(new Dimension(240, 28));
        fileSelector.addActionListener(e -> onFileSelected());
        leftControls.add(new JLabel("Dosya:"));
        leftControls.add(fileSelector);
        topBar.add(leftControls, BorderLayout.WEST);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        JButton testDiffBtn = new JButton("Test Farkı Oluştur");
        testDiffBtn.setFont(testDiffBtn.getFont().deriveFont(Font.PLAIN, 11f));
        testDiffBtn.setToolTipText("Yeni kolon ve indeks farkı simüle ederek Migration motorunu test edin");
        testDiffBtn.addActionListener(e -> simulateDiffForTesting());
        rightControls.add(testDiffBtn);

        JButton generateMigrationBtn = new JButton("Migration (ALTER) Üret");
        generateMigrationBtn.setFont(generateMigrationBtn.getFont().deriveFont(Font.BOLD, 12f));
        generateMigrationBtn.setForeground(new Color(22, 163, 74));
        generateMigrationBtn.addActionListener(e -> generateMigration());
        rightControls.add(generateMigrationBtn);

        JButton reloadBtn = new JButton("Yenile");
        reloadBtn.setFont(reloadBtn.getFont().deriveFont(Font.PLAIN, 12f));
        reloadBtn.addActionListener(e -> reloadFiles());
        rightControls.add(reloadBtn);

        topBar.add(rightControls, BorderLayout.EAST);
        fileDiffPanel.add(topBar, BorderLayout.NORTH);

        // Center & Bottom Split: Top (Side-by-side DDL comparison) | Bottom (Generated Migration Script)
        JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
        leftHeaderLabel.setFont(leftHeaderLabel.getFont().deriveFont(Font.BOLD, 12f));
        leftHeaderLabel.setForeground(new Color(220, 38, 38));
        leftPanel.add(leftHeaderLabel, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(leftTextArea), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(0, 4));
        rightHeaderLabel.setFont(rightHeaderLabel.getFont().deriveFont(Font.BOLD, 12f));
        rightHeaderLabel.setForeground(new Color(22, 163, 74));
        rightPanel.add(rightHeaderLabel, BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(rightTextArea), BorderLayout.CENTER);

        JSplitPane diffSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        diffSplitPane.setDividerLocation(460);
        diffSplitPane.setResizeWeight(0.5);

        // Bottom Generated Migration Script Panel
        JPanel migrationPanel = new JPanel(new BorderLayout(0, 4));
        migrationPanel.setBorder(BorderFactory.createTitledBorder("Otomatik Üretilen PostgreSQL Migration (ALTER) Scripti"));

        JPanel migrationToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        JButton copyMigrationBtn = new JButton("Migration SQL Kopyala");
        copyMigrationBtn.setFont(copyMigrationBtn.getFont().deriveFont(Font.BOLD, 11f));
        copyMigrationBtn.addActionListener(e -> copySqlToClipboard(migrationTextArea.getText(), "Migration scripti panoya kopyalandı!"));
        migrationToolbar.add(copyMigrationBtn);

        JButton saveMigrationBtn = new JButton(".sql Olarak Kaydet");
        saveMigrationBtn.setFont(saveMigrationBtn.getFont().deriveFont(Font.PLAIN, 11f));
        saveMigrationBtn.addActionListener(e -> exportSqlToFile(migrationTextArea.getText(), "V1__migration_auto_generated.sql"));
        migrationToolbar.add(saveMigrationBtn);
        migrationPanel.add(migrationToolbar, BorderLayout.NORTH);

        migrationTextArea.setText("-- Yukarıdaki şemalar karşılaştırıldığında 'Migration (ALTER) Üret' butonuna basarak canlı migration scripti üretebilirsiniz.");
        migrationPanel.add(new JScrollPane(migrationTextArea), BorderLayout.CENTER);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, diffSplitPane, migrationPanel);
        mainSplitPane.setDividerLocation(310);
        mainSplitPane.setResizeWeight(0.65);
        fileDiffPanel.add(mainSplitPane, BorderLayout.CENTER);

        // Auto regenerate on text change in diff editors
        DocumentListener changeListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { generateMigration(); }
            public void removeUpdate(DocumentEvent e) { generateMigration(); }
            public void changedUpdate(DocumentEvent e) { generateMigration(); }
        };
        leftTextArea.getDocument().addDocumentListener(changeListener);
        rightTextArea.getDocument().addDocumentListener(changeListener);

        rootTabbedPane.addTab("Dosya & DDL Sürüm Farkı", fileDiffPanel);

        // =========================================================================
        // MODE 2: Multi-Environment Comparison (Staging vs Production)
        // =========================================================================
        JPanel envDiffPanel = new JPanel(new BorderLayout(8, 8));
        envDiffPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // 1. Env Top Control Bar
        JPanel envTopBar = new JPanel(new BorderLayout(8, 8));

        JPanel profileSelectors = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        profileSelectors.add(new JLabel("Kaynak Ortam (Geliştirme / Staging):"));
        sourceProfileSelector.setPreferredSize(new Dimension(170, 26));
        profileSelectors.add(sourceProfileSelector);

        profileSelectors.add(new JLabel(" -> Hedef Ortam (Canlı / Production):"));
        targetProfileSelector.setPreferredSize(new Dimension(170, 26));
        profileSelectors.add(targetProfileSelector);
        envTopBar.add(profileSelectors, BorderLayout.WEST);

        JPanel envActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton simEnvBtn = new JButton("Test Ortam Simülasyonu");
        simEnvBtn.setFont(simEnvBtn.getFont().deriveFont(Font.PLAIN, 11f));
        simEnvBtn.addActionListener(e -> runSimulatedEnvDiff());
        envActions.add(simEnvBtn);

        JButton runEnvDiffBtn = new JButton("Ortamları Karşılaştır");
        runEnvDiffBtn.setFont(runEnvDiffBtn.getFont().deriveFont(Font.BOLD, 12f));
        runEnvDiffBtn.setForeground(new Color(22, 163, 74));
        runEnvDiffBtn.addActionListener(e -> runLiveEnvironmentDiff());
        envActions.add(runEnvDiffBtn);
        envTopBar.add(envActions, BorderLayout.EAST);

        // KPI Summary Cards for Environment Diff
        JPanel kpiGrid = new JPanel(new GridLayout(1, 3, 10, 0));
        kpiGrid.setPreferredSize(new Dimension(0, 65));
        kpiGrid.add(createMiniKpiCard("Tam Eşleşen Tablolar", matchKpiLabel, new Color(22, 163, 74)));
        kpiGrid.add(createMiniKpiCard("Farklı / Değişen Tablolar", modifiedKpiLabel, new Color(217, 119, 6)));
        kpiGrid.add(createMiniKpiCard("Hedefte Eksik Tablolar", missingKpiLabel, new Color(220, 38, 38)));

        JPanel envHeaderContainer = new JPanel(new BorderLayout(0, 8));
        envHeaderContainer.add(envTopBar, BorderLayout.NORTH);
        envHeaderContainer.add(kpiGrid, BorderLayout.CENTER);
        envDiffPanel.add(envHeaderContainer, BorderLayout.NORTH);

        // 2. Env Center Split: Left Table / Right Patch SQL
        JPanel envTablePanel = new JPanel(new BorderLayout(0, 4));
        envTablePanel.setBorder(BorderFactory.createTitledBorder("Tablo Karşılaştırma & Uyuşmazlık Listesi"));

        String[] envCols = {"Durum", "Tablo Adı", "Tespit Edilen Farklar"};
        envDiffTableModel = new DefaultTableModel(envCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        envDiffTable = new JTable(envDiffTableModel);
        envDiffTable.setRowHeight(24);
        envDiffTable.getTableHeader().setFont(envDiffTable.getTableHeader().getFont().deriveFont(Font.BOLD, 11f));
        envDiffTable.getColumnModel().getColumn(0).setPreferredWidth(110);
        envDiffTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        envDiffTable.getColumnModel().getColumn(2).setPreferredWidth(260);

        envDiffTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String val = String.valueOf(value);
                if ("EŞLEŞİYOR".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(22, 163, 74));
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                } else if ("KOLON FARKI".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(217, 119, 6));
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                } else if ("HEDEFTE EKSİK".equalsIgnoreCase(val)) {
                    c.setForeground(new Color(220, 38, 38));
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                }
                return c;
            }
        });
        envTablePanel.add(new JScrollPane(envDiffTable), BorderLayout.CENTER);

        JPanel envPatchPanel = new JPanel(new BorderLayout(0, 4));
        envPatchPanel.setBorder(BorderFactory.createTitledBorder("Hedef Ortama Dağıtım (Deploy Patch) Scripti"));

        JPanel patchToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        JButton copyPatchBtn = new JButton("Yamayı Kopyala");
        copyPatchBtn.setFont(copyPatchBtn.getFont().deriveFont(Font.BOLD, 11f));
        copyPatchBtn.addActionListener(e -> copySqlToClipboard(envPatchTextArea.getText(), "Dağıtım yaması panoya kopyalandı!"));
        patchToolbar.add(copyPatchBtn);

        JButton savePatchBtn = new JButton(".sql Kaydet");
        savePatchBtn.setFont(savePatchBtn.getFont().deriveFont(Font.PLAIN, 11f));
        savePatchBtn.addActionListener(e -> exportSqlToFile(envPatchTextArea.getText(), "deploy_patch_to_prod.sql"));
        patchToolbar.add(savePatchBtn);
        envPatchPanel.add(patchToolbar, BorderLayout.NORTH);

        envPatchTextArea.setText("-- Ortamları seçip 'Ortamları Karşılaştır' butonuna basarak hedef ortamı eşitleyecek SQL yamasını üretebilirsiniz.");
        envPatchPanel.add(new JScrollPane(envPatchTextArea), BorderLayout.CENTER);

        JSplitPane envSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, envTablePanel, envPatchPanel);
        envSplit.setDividerLocation(460);
        envSplit.setResizeWeight(0.5);
        envDiffPanel.add(envSplit, BorderLayout.CENTER);

        rootTabbedPane.addTab("Canlı Ortam Karşılaştırma (Staging vs Prod)", envDiffPanel);
        add(rootTabbedPane, BorderLayout.CENTER);

        refreshProfiles();
    }

    private JPanel createMiniKpiCard(String title, JLabel valueLabel, Color valueColor) {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 215, 225), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        card.setBackground(Color.WHITE);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 11f));
        titleLbl.setForeground(new Color(100, 105, 115));
        card.add(titleLbl, BorderLayout.NORTH);

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 16f));
        valueLabel.setForeground(valueColor);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    public void refreshProfiles() {
        if (profileManager == null) return;
        sourceProfileSelector.removeAllItems();
        targetProfileSelector.removeAllItems();

        Map<String, PostgresqlConfigurationSettings> profiles = profileManager.getProfiles();
        for (String name : profiles.keySet()) {
            sourceProfileSelector.addItem(name);
            targetProfileSelector.addItem(name);
        }

        if (targetProfileSelector.getItemCount() > 1) {
            targetProfileSelector.setSelectedIndex(1);
        }
    }

    private void runSimulatedEnvDiff() {
        String src = (String) sourceProfileSelector.getSelectedItem();
        String tgt = (String) targetProfileSelector.getSelectedItem();
        if (src == null) src = "Staging";
        if (tgt == null) tgt = "Production";

        EnvironmentDiffEngine.EnvironmentComparisonReport report =
                EnvironmentDiffEngine.generateSimulatedReport(src, tgt);

        displayEnvironmentReport(report);
    }

    private void runLiveEnvironmentDiff() {
        String srcName = (String) sourceProfileSelector.getSelectedItem();
        String tgtName = (String) targetProfileSelector.getSelectedItem();
        if (srcName == null || tgtName == null) return;

        PostgresqlConfigurationSettings srcCfg = profileManager.getProfile(srcName);
        PostgresqlConfigurationSettings tgtCfg = profileManager.getProfile(tgtName);

        if (srcCfg == null || tgtCfg == null) {
            JOptionPane.showMessageDialog(this, "Seçili profillerin ayarları okunamadı.", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new SwingWorker<EnvironmentDiffEngine.EnvironmentComparisonReport, Void>() {
            @Override
            protected EnvironmentDiffEngine.EnvironmentComparisonReport doInBackground() {
                return EnvironmentDiffEngine.compareEnvironments(srcName, srcCfg, tgtName, tgtCfg);
            }

            @Override
            protected void done() {
                try {
                    EnvironmentDiffEngine.EnvironmentComparisonReport report = get();
                    displayEnvironmentReport(report);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DiffViewerPanel.this,
                            "Ortam karşılaştırması sırasında hata oluştu:\n" + ex.getMessage(),
                            "Hata", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void displayEnvironmentReport(EnvironmentDiffEngine.EnvironmentComparisonReport report) {
        matchKpiLabel.setText(String.valueOf(report.matchingCount));
        modifiedKpiLabel.setText(String.valueOf(report.modifiedCount));
        missingKpiLabel.setText(String.valueOf(report.missingInTargetCount));

        envDiffTableModel.setRowCount(0);
        for (EnvironmentDiffEngine.TableDiffResult diff : report.tableDiffs) {
            String statusStr;
            StringBuilder desc = new StringBuilder();

            if (diff.status == EnvironmentDiffEngine.DiffStatus.MATCH) {
                statusStr = "EŞLEŞİYOR";
                desc.append("Tüm kolonlar ve veri tipleri birebir eşleşiyor");
            } else if (diff.status == EnvironmentDiffEngine.DiffStatus.MISSING_IN_TARGET) {
                statusStr = "HEDEFTE EKSİK";
                desc.append("Bu tablo hedef ortamda bulunmuyor (Yeni tablo)");
            } else if (diff.status == EnvironmentDiffEngine.DiffStatus.EXTRA_IN_TARGET) {
                statusStr = "HEDEFTE FAZLA";
                desc.append("Bu tablo sadece hedefte mevcut");
            } else {
                statusStr = "KOLON FARKI";
                if (!diff.addedColumns.isEmpty()) {
                    desc.append("Yeni: ").append(String.join(", ", diff.addedColumns)).append(" ");
                }
                if (!diff.typeMismatches.isEmpty()) {
                    desc.append("Tip Farkı: ").append(String.join(", ", diff.typeMismatches));
                }
            }

            envDiffTableModel.addRow(new Object[]{statusStr, diff.tableName, desc.toString()});
        }

        envPatchTextArea.setText(report.generatedPatchSql);
        envPatchTextArea.setCaretPosition(0);
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

        leftTextArea.setText(base);
        String updated = base.replace("price numeric(10,2)",
                "price numeric(10,2),\n    created_at timestamp with time zone DEFAULT now(),\n    is_active boolean DEFAULT true")
                + "\n\nCREATE INDEX idx_products_created_at ON public.products (created_at);";

        rightTextArea.setText(updated);
        generateMigration();
    }

    private void copySqlToClipboard(String text, String successMsg) {
        if (text != null && !text.isBlank()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            JOptionPane.showMessageDialog(this, successMsg, "Kopyalandı", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void exportSqlToFile(String text, String defaultFilename) {
        if (text == null || text.isBlank()) return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(defaultFilename));
        int res = fileChooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            try {
                Files.writeString(fileChooser.getSelectedFile().toPath(), text);
                JOptionPane.showMessageDialog(this,
                        "SQL scripti başarıyla kaydedildi:\n" + fileChooser.getSelectedFile().getAbsolutePath(),
                        "Başarılı", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Kaydetme hatası: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void applyTheme(boolean isDark) {
        this.isDark = isDark;
        Color darkBg = new Color(24, 24, 27);
        Color darkFg = new Color(228, 228, 231);

        if (isDark) {
            leftTextArea.setBackground(darkBg);
            leftTextArea.setForeground(darkFg);
            rightTextArea.setBackground(darkBg);
            rightTextArea.setForeground(darkFg);
            migrationTextArea.setBackground(new Color(18, 20, 24));
            migrationTextArea.setForeground(new Color(230, 235, 245));
            envPatchTextArea.setBackground(new Color(18, 20, 24));
            envPatchTextArea.setForeground(new Color(230, 235, 245));
            envDiffTable.setBackground(darkBg);
            envDiffTable.setForeground(darkFg);
        } else {
            leftTextArea.setBackground(Color.WHITE);
            leftTextArea.setForeground(new Color(31, 35, 40));
            rightTextArea.setBackground(Color.WHITE);
            rightTextArea.setForeground(new Color(31, 35, 40));
            migrationTextArea.setBackground(new Color(248, 250, 252));
            migrationTextArea.setForeground(new Color(15, 23, 42));
            envPatchTextArea.setBackground(new Color(248, 250, 252));
            envPatchTextArea.setForeground(new Color(15, 23, 42));
            envDiffTable.setBackground(Color.WHITE);
            envDiffTable.setForeground(new Color(31, 35, 40));
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
                    rightHeaderLabel.setText("Güncel Şema: " + sqlFile.getName());

                    leftTextArea.setText(content);
                    leftTextArea.setCaretPosition(0);
                    leftHeaderLabel.setText("Referans / Önceki Şema: " + sqlFile.getName());

                    generateMigration();
                } catch (Exception e) {
                    rightTextArea.setText("Dosya okunamadı: " + e.getMessage());
                }
            }
        }
    }
}

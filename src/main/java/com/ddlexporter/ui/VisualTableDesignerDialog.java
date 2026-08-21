package com.ddlexporter.ui;

import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;
import com.ddlexporter.common.util.SqlFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Modern Visual Table Designer & Creator for PostgreSQL DDL Studio.
 * Allows users to visually configure columns, data types, constraints, and defaults,
 * live-generating DDL SQL and executing on the active database or saving to file.
 */
public class VisualTableDesignerDialog extends JDialog {

    private final Supplier<PostgresqlConfigurationSettings> settingsSupplier;
    private final File exportDir;
    private final Runnable onSuccessCallback;

    private final JTextField tableNameField = new JTextField(20);
    private final JTextField schemaField = new JTextField("public", 10);
    private final JTextField commentField = new JTextField(30);

    private final DefaultTableModel tableModel;
    private final JTable columnsTable;
    private final JTextArea liveSqlArea = new JTextArea();

    private final String[] DATA_TYPES = {
            "BIGSERIAL", "SERIAL", "BIGINT", "INTEGER", "SMALLINT",
            "VARCHAR(255)", "VARCHAR(100)", "VARCHAR(50)", "VARCHAR(500)", "TEXT",
            "BOOLEAN", "NUMERIC(10,2)", "NUMERIC(12,2)", "NUMERIC(18,4)",
            "TIMESTAMP", "TIMESTAMPTZ", "DATE", "TIME",
            "UUID", "JSONB", "JSON", "BYTEA", "INET"
    };

    public VisualTableDesignerDialog(Frame owner,
                                      Supplier<PostgresqlConfigurationSettings> settingsSupplier,
                                      File exportDir,
                                      Runnable onSuccessCallback) {
        super(owner, "Görsel Tablo Tasarımcısı (Visual Table Designer)", true);
        this.settingsSupplier = settingsSupplier;
        this.exportDir = exportDir;
        this.onSuccessCallback = onSuccessCallback;

        setSize(980, 720);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 0));

        // Define Table Columns: [ #, Kolon Adı, Veri Tipi, PK, Not Null, Unique, Varsayılan, FK Referans ]
        String[] columnHeaders = {
                "#", "Kolon Adı", "Veri Tipi", "PK", "Not Null", "Unique", "Varsayılan Değer", "Yabancı Anahtar (FK)"
        };

        tableModel = new DefaultTableModel(columnHeaders, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Integer.class;
                if (columnIndex == 3 || columnIndex == 4 || columnIndex == 5) return Boolean.class;
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column != 0; // Row number is automatic
            }
        };

        columnsTable = new JTable(tableModel);
        setupColumnsTable();

        initComponents();
        loadInitialTemplate("standard");
        updateLiveSql();
    }

    private boolean isAdjusting = false;

    private void setupColumnsTable() {
        columnsTable.setRowHeight(28);
        columnsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        columnsTable.getTableHeader().setReorderingAllowed(false);
        columnsTable.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        // Column widths
        columnsTable.getColumnModel().getColumn(0).setPreferredWidth(35);  // #
        columnsTable.getColumnModel().getColumn(0).setMaxWidth(45);
        columnsTable.getColumnModel().getColumn(1).setPreferredWidth(160); // Name
        columnsTable.getColumnModel().getColumn(2).setPreferredWidth(130); // Type
        columnsTable.getColumnModel().getColumn(3).setPreferredWidth(45);  // PK
        columnsTable.getColumnModel().getColumn(4).setPreferredWidth(65);  // Not Null
        columnsTable.getColumnModel().getColumn(5).setPreferredWidth(60);  // Unique
        columnsTable.getColumnModel().getColumn(6).setPreferredWidth(140); // Default
        columnsTable.getColumnModel().getColumn(7).setPreferredWidth(180); // FK

        // Data Type ComboBox Editor
        JComboBox<String> typeCombo = new JComboBox<>(DATA_TYPES);
        typeCombo.setEditable(true);
        typeCombo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        columnsTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(typeCombo));

        // Center align checkboxes and row numbers
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        columnsTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);

        // Auto update SQL on table edits with recursion guard
        tableModel.addTableModelListener(e -> {
            if (isAdjusting) return;
            renumberRows();
            updateLiveSql();
        });
    }

    private void initComponents() {
        JPanel rootPanel = new JPanel(new BorderLayout(0, 10));
        rootPanel.setBorder(new EmptyBorder(12, 14, 12, 14));

        // 1. Top Panel: Table Meta Info & Template Selector
        JPanel topPanel = new JPanel(new BorderLayout(10, 8));
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(200, 205, 215)), "Tablo Genel Bilgileri"),
                new EmptyBorder(6, 10, 8, 10)
        ));

        JPanel formGrid = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 1: Table Name & Schema
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel nameLbl = new JLabel("Tablo Adı:*");
        nameLbl.setFont(nameLbl.getFont().deriveFont(Font.BOLD, 12f));
        formGrid.add(nameLbl, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.5;
        tableNameField.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        tableNameField.putClientProperty("JTextField.placeholderText", "Örn: urunler, musteriler, siparisler");
        tableNameField.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateLiveSql));
        formGrid.add(tableNameField, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0;
        formGrid.add(new JLabel("Şema:"), gbc);

        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0.2;
        schemaField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        schemaField.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateLiveSql));
        formGrid.add(schemaField, gbc);

        // Template selector button
        gbc.gridx = 4; gbc.gridy = 0; gbc.weightx = 0;
        JButton templateBtn = new JButton("Hazır Şablonlar ▾");
        templateBtn.setFont(templateBtn.getFont().deriveFont(Font.PLAIN, 11f));
        JPopupMenu templateMenu = new JPopupMenu();
        templateMenu.add(createTemplateMenuItem("Standart Varlık (ID, Ad, Durum, Tarih)", "standard"));
        templateMenu.add(createTemplateMenuItem("Kullanıcı & Giriş (ID, Kullanıcı Adı, E-posta, Şifre, Rol)", "user"));
        templateMenu.add(createTemplateMenuItem("E-Ticaret / Ürün (ID, Kategori, Fiyat, Stok)", "product"));
        templateMenu.add(createTemplateMenuItem("Finans / Ödeme & Sipariş (ID, Müşteri, Tutar, Durum)", "order"));
        templateMenu.add(createTemplateMenuItem("Log & Denetim (ID, Olay, Detay JSON, Tarih)", "audit"));
        templateBtn.addActionListener(e -> templateMenu.show(templateBtn, 0, templateBtn.getHeight()));
        formGrid.add(templateBtn, gbc);

        // Row 2: Table Comment
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        formGrid.add(new JLabel("Açıklama:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 4; gbc.weightx = 1.0;
        commentField.putClientProperty("JTextField.placeholderText", "Tablo açıklaması (isteğe bağlı)");
        commentField.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateLiveSql));
        formGrid.add(commentField, gbc);

        topPanel.add(formGrid, BorderLayout.CENTER);
        rootPanel.add(topPanel, BorderLayout.NORTH);

        // 2. Center Panel: Column Grid + Column Toolbar
        JPanel centerPanel = new JPanel(new BorderLayout(0, 6));
        centerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(200, 205, 215)), "Kolonlar ve Kısıtlamalar (Constraints)"),
                new EmptyBorder(4, 6, 6, 6)
        ));

        // Column management toolbar: [+ Kolon Ekle] [- Kolon Sil] [▲ Yukarı] [▼ Aşağı] [Temizle]
        JPanel colToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton addColBtn = new JButton("+ Kolon Ekle");
        addColBtn.setFont(addColBtn.getFont().deriveFont(Font.BOLD, 12f));
        addColBtn.setForeground(new Color(22, 163, 74));
        addColBtn.addActionListener(e -> addEmptyColumn());
        colToolbar.add(addColBtn);

        JButton deleteColBtn = new JButton("- Kolon Sil");
        deleteColBtn.setFont(deleteColBtn.getFont().deriveFont(Font.PLAIN, 12f));
        deleteColBtn.addActionListener(e -> deleteSelectedColumn());
        colToolbar.add(deleteColBtn);

        JButton moveUpBtn = new JButton("▲ Yukarı");
        moveUpBtn.setFont(moveUpBtn.getFont().deriveFont(Font.PLAIN, 11f));
        moveUpBtn.addActionListener(e -> moveSelectedColumn(-1));
        colToolbar.add(moveUpBtn);

        JButton moveDownBtn = new JButton("▼ Aşağı");
        moveDownBtn.setFont(moveDownBtn.getFont().deriveFont(Font.PLAIN, 11f));
        moveDownBtn.addActionListener(e -> moveSelectedColumn(1));
        colToolbar.add(moveDownBtn);

        JButton clearBtn = new JButton("Tümünü Temizle");
        clearBtn.setFont(clearBtn.getFont().deriveFont(Font.PLAIN, 11f));
        clearBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Tüm kolonlar silinsin mi?", "Onay", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                tableModel.setRowCount(0);
                updateLiveSql();
            }
        });
        colToolbar.add(clearBtn);

        centerPanel.add(colToolbar, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(columnsTable), BorderLayout.CENTER);

        // 3. Bottom Panel: Live Real-Time SQL DDL Preview & Action Buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 8));
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(200, 205, 215)), "Canlı SQL DDL Önizlemesi (Otomatik Üretilen Kod)"),
                new EmptyBorder(4, 6, 6, 6)
        ));

        liveSqlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        liveSqlArea.setEditable(false);
        liveSqlArea.setRows(7);
        liveSqlArea.setBackground(new Color(30, 32, 38));
        liveSqlArea.setForeground(new Color(130, 215, 247));
        liveSqlArea.setCaretColor(Color.WHITE);

        JScrollPane sqlScroll = new JScrollPane(liveSqlArea);
        bottomPanel.add(sqlScroll, BorderLayout.CENTER);

        // Bottom Action Buttons
        JPanel actionsBar = new JPanel(new BorderLayout(8, 0));
        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton copySqlBtn = new JButton("SQL Kopyala");
        copySqlBtn.setFont(copySqlBtn.getFont().deriveFont(Font.PLAIN, 12f));
        copySqlBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(liveSqlArea.getText()), null);
            JOptionPane.showMessageDialog(this, "SQL DDL kodu panoya kopyalandı!", "Bilgi", JOptionPane.INFORMATION_MESSAGE);
        });
        leftActions.add(copySqlBtn);
        actionsBar.add(leftActions, BorderLayout.WEST);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        JButton cancelBtn = new JButton("İptal");
        cancelBtn.setFont(cancelBtn.getFont().deriveFont(Font.PLAIN, 12f));
        cancelBtn.addActionListener(e -> dispose());
        rightActions.add(cancelBtn);

        JButton saveOnlyBtn = new JButton("Sadece SQL Olarak Kaydet");
        saveOnlyBtn.setFont(saveOnlyBtn.getFont().deriveFont(Font.PLAIN, 12f));
        saveOnlyBtn.setToolTipText("Veritabanına bağlanmadan yalnızca .sql dosyasını diske kaydeder");
        saveOnlyBtn.addActionListener(e -> saveSqlToFileOnly());
        rightActions.add(saveOnlyBtn);

        JButton executeAndSaveBtn = new JButton("Veritabanında Oluştur ve Şemaya Ekle");
        executeAndSaveBtn.setFont(executeAndSaveBtn.getFont().deriveFont(Font.BOLD, 13f));
        executeAndSaveBtn.setForeground(new Color(9, 105, 218));
        executeAndSaveBtn.setToolTipText("SQL'i canlı PostgreSQL veritabanında çalıştırır, dosyasını kaydeder ve Şema/ERD ekranlarını günceller");
        executeAndSaveBtn.addActionListener(e -> executeAndSaveTable());
        rightActions.add(executeAndSaveBtn);

        actionsBar.add(rightActions, BorderLayout.EAST);
        bottomPanel.add(actionsBar, BorderLayout.SOUTH);

        // Split center and bottom for resizability
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerPanel, bottomPanel);
        splitPane.setResizeWeight(0.6);
        splitPane.setDividerSize(5);

        rootPanel.add(splitPane, BorderLayout.CENTER);
        add(rootPanel);
    }

    private JMenuItem createTemplateMenuItem(String title, String templateKey) {
        JMenuItem item = new JMenuItem(title);
        item.addActionListener(e -> loadInitialTemplate(templateKey));
        return item;
    }

    private void loadInitialTemplate(String templateKey) {
        tableModel.setRowCount(0);

        if ("user".equalsIgnoreCase(templateKey)) {
            tableNameField.setText("kullanicilar");
            commentField.setText("Sistem kullanıcıları ve kimlik doğrulama tablosu");
            tableModel.addRow(new Object[]{1, "kullanici_id", "SERIAL", true, true, true, "", ""});
            tableModel.addRow(new Object[]{2, "kullanici_adi", "VARCHAR(50)", false, true, true, "", ""});
            tableModel.addRow(new Object[]{3, "email", "VARCHAR(100)", false, true, true, "", ""});
            tableModel.addRow(new Object[]{4, "sifre_hash", "VARCHAR(255)", false, true, false, "", ""});
            tableModel.addRow(new Object[]{5, "rol", "VARCHAR(30)", false, true, false, "'USER'", ""});
            tableModel.addRow(new Object[]{6, "aktif_mi", "BOOLEAN", false, true, false, "TRUE", ""});
            tableModel.addRow(new Object[]{7, "kayit_tarihi", "TIMESTAMP", false, true, false, "CURRENT_TIMESTAMP", ""});
        } else if ("product".equalsIgnoreCase(templateKey)) {
            tableNameField.setText("urunler");
            commentField.setText("Ürün kataloğu ve fiyatlandırma tablosu");
            tableModel.addRow(new Object[]{1, "urun_id", "SERIAL", true, true, true, "", ""});
            tableModel.addRow(new Object[]{2, "kategori_id", "INTEGER", false, false, false, "", "kategoriler(kategori_id)"});
            tableModel.addRow(new Object[]{3, "urun_adi", "VARCHAR(150)", false, true, false, "", ""});
            tableModel.addRow(new Object[]{4, "barkod", "VARCHAR(50)", false, false, true, "", ""});
            tableModel.addRow(new Object[]{5, "fiyat", "NUMERIC(10,2)", false, true, false, "0.00", ""});
            tableModel.addRow(new Object[]{6, "stok_adedi", "INTEGER", false, true, false, "0", ""});
            tableModel.addRow(new Object[]{7, "aktif_mi", "BOOLEAN", false, true, false, "TRUE", ""});
            tableModel.addRow(new Object[]{8, "olusturulma_tarihi", "TIMESTAMP", false, true, false, "CURRENT_TIMESTAMP", ""});
        } else if ("order".equalsIgnoreCase(templateKey)) {
            tableNameField.setText("siparisler");
            commentField.setText("Müşteri siparişleri ve ödeme durumu tablosu");
            tableModel.addRow(new Object[]{1, "siparis_id", "SERIAL", true, true, true, "", ""});
            tableModel.addRow(new Object[]{2, "musteri_id", "INTEGER", false, true, false, "", "musteriler(musteri_id)"});
            tableModel.addRow(new Object[]{3, "siparis_no", "VARCHAR(30)", false, true, true, "", ""});
            tableModel.addRow(new Object[]{4, "toplam_tutar", "NUMERIC(12,2)", false, true, false, "0.00", ""});
            tableModel.addRow(new Object[]{5, "siparis_durumu", "VARCHAR(30)", false, true, false, "'BEKLIYOR'", ""});
            tableModel.addRow(new Object[]{6, "siparis_tarihi", "TIMESTAMP", false, true, false, "CURRENT_TIMESTAMP", ""});
        } else if ("audit".equalsIgnoreCase(templateKey)) {
            tableNameField.setText("islem_loglari");
            commentField.setText("Sistem işlem ve denetim kayıtları tablosu");
            tableModel.addRow(new Object[]{1, "log_id", "BIGSERIAL", true, true, true, "", ""});
            tableModel.addRow(new Object[]{2, "kullanici_id", "INTEGER", false, false, false, "", ""});
            tableModel.addRow(new Object[]{3, "islem_turu", "VARCHAR(50)", false, true, false, "", ""});
            tableModel.addRow(new Object[]{4, "detay_json", "JSONB", false, false, false, "", ""});
            tableModel.addRow(new Object[]{5, "ip_adresi", "INET", false, false, false, "", ""});
            tableModel.addRow(new Object[]{6, "olusturulma_tarihi", "TIMESTAMP", false, true, false, "CURRENT_TIMESTAMP", ""});
        } else {
            // Standard Entity Template
            tableNameField.setText("yeni_tablo");
            commentField.setText("Yeni tablo tanımı");
            tableModel.addRow(new Object[]{1, "id", "SERIAL", true, true, true, "", ""});
            tableModel.addRow(new Object[]{2, "baslik", "VARCHAR(150)", false, true, false, "", ""});
            tableModel.addRow(new Object[]{3, "aciklama", "TEXT", false, false, false, "", ""});
            tableModel.addRow(new Object[]{4, "aktif_mi", "BOOLEAN", false, true, false, "TRUE", ""});
            tableModel.addRow(new Object[]{5, "olusturulma_tarihi", "TIMESTAMP", false, true, false, "CURRENT_TIMESTAMP", ""});
        }

        renumberRows();
        updateLiveSql();
    }

    private void addEmptyColumn() {
        int nextId = tableModel.getRowCount() + 1;
        tableModel.addRow(new Object[]{nextId, "yeni_kolon_" + nextId, "VARCHAR(100)", false, false, false, "", ""});
        renumberRows();
        columnsTable.changeSelection(tableModel.getRowCount() - 1, 1, false, false);
    }

    private void deleteSelectedColumn() {
        int selectedRow = columnsTable.getSelectedRow();
        if (selectedRow >= 0) {
            tableModel.removeRow(selectedRow);
            renumberRows();
            updateLiveSql();
        } else {
            JOptionPane.showMessageDialog(this, "Lütfen silmek istediğiniz kolonu seçin.", "Seçim Yok", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void moveSelectedColumn(int direction) {
        int selectedRow = columnsTable.getSelectedRow();
        if (selectedRow < 0) return;
        int targetRow = selectedRow + direction;
        if (targetRow < 0 || targetRow >= tableModel.getRowCount()) return;

        tableModel.moveRow(selectedRow, selectedRow, targetRow);
        renumberRows();
        columnsTable.setRowSelectionInterval(targetRow, targetRow);
        updateLiveSql();
    }

    private void renumberRows() {
        if (isAdjusting) return;
        isAdjusting = true;
        try {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                Object val = tableModel.getValueAt(i, 0);
                if (val == null || !Integer.valueOf(i + 1).equals(val)) {
                    tableModel.setValueAt(i + 1, i, 0);
                }
            }
        } finally {
            isAdjusting = false;
        }
    }

    private String generateSqlScript() {
        String tableName = tableNameField.getText().trim();
        if (tableName.isEmpty()) {
            tableName = "isimsiz_tablo";
        }
        String schema = schemaField.getText().trim();
        String fullTableName = schema.isEmpty() || "public".equalsIgnoreCase(schema) ? tableName : schema + "." + tableName;

        StringBuilder sb = new StringBuilder();
        sb.append("-- ========================================================================\n");
        sb.append("--  PostgreSQL DDL Studio - Otomatik Üretilen Tablo Tanımı\n");
        sb.append("--  Tablo: ").append(fullTableName).append("\n");
        sb.append("-- ========================================================================\n\n");

        sb.append("CREATE TABLE IF NOT EXISTS ").append(fullTableName).append(" (\n");

        List<String> columnDefs = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        List<String> fkConstraints = new ArrayList<>();

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String colName = String.valueOf(tableModel.getValueAt(i, 1)).trim();
            if (colName.isEmpty()) continue;

            String dataType = String.valueOf(tableModel.getValueAt(i, 2)).trim();
            boolean isPk = Boolean.TRUE.equals(tableModel.getValueAt(i, 3));
            boolean isNotNull = Boolean.TRUE.equals(tableModel.getValueAt(i, 4));
            boolean isUnique = Boolean.TRUE.equals(tableModel.getValueAt(i, 5));
            String defVal = tableModel.getValueAt(i, 6) != null ? String.valueOf(tableModel.getValueAt(i, 6)).trim() : "";
            String fkRef = tableModel.getValueAt(i, 7) != null ? String.valueOf(tableModel.getValueAt(i, 7)).trim() : "";

            StringBuilder col = new StringBuilder("    ").append(colName).append(" ").append(dataType);

            if (isPk) {
                primaryKeys.add(colName);
            }

            if (isNotNull && !isPk) {
                col.append(" NOT NULL");
            }

            if (isUnique && !isPk) {
                col.append(" UNIQUE");
            }

            if (!defVal.isEmpty()) {
                col.append(" DEFAULT ").append(defVal);
            }

            if (!fkRef.isEmpty()) {
                if (fkRef.toLowerCase().contains("references")) {
                    col.append(" ").append(fkRef);
                } else {
                    col.append(" REFERENCES ").append(fkRef);
                }
            }

            columnDefs.add(col.toString());
        }

        // Add Primary Key constraint
        if (!primaryKeys.isEmpty()) {
            columnDefs.add("    CONSTRAINT pk_" + tableName + " PRIMARY KEY (" + String.join(", ", primaryKeys) + ")");
        }

        sb.append(String.join(",\n", columnDefs));
        sb.append("\n);\n");

        // Table Comment
        String comment = commentField.getText().trim();
        if (!comment.isEmpty()) {
            sb.append("\nCOMMENT ON TABLE ").append(fullTableName).append(" IS '")
                    .append(comment.replace("'", "''")).append("';\n");
        }

        return SqlFormatter.formatSql(sb.toString());
    }

    private void updateLiveSql() {
        try {
            liveSqlArea.setText(generateSqlScript());
            liveSqlArea.setCaretPosition(0);
        } catch (Exception ignored) {}
    }

    private void saveSqlToFileOnly() {
        String tableName = tableNameField.getText().trim();
        if (tableName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Lütfen bir tablo adı girin!", "Tablo Adı Eksik", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String sql = generateSqlScript();
            File targetDir = exportDir != null && exportDir.isDirectory() ? exportDir : new File("export_output");
            if (!targetDir.exists()) targetDir.mkdirs();

            File sqlFile = new File(targetDir, tableName + ".sql");
            Files.writeString(sqlFile.toPath(), sql);

            JOptionPane.showMessageDialog(this,
                    "SQL DDL dosyası başarıyla kaydedildi:\n" + sqlFile.getAbsolutePath(),
                    "Kayıt Başarılı", JOptionPane.INFORMATION_MESSAGE);

            if (onSuccessCallback != null) {
                onSuccessCallback.run();
            }
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Dosya kaydedilirken hata oluştu:\n" + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void executeAndSaveTable() {
        String tableName = tableNameField.getText().trim();
        if (tableName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Lütfen bir tablo adı girin!", "Tablo Adı Eksik", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (settingsSupplier == null || settingsSupplier.get() == null) {
            JOptionPane.showMessageDialog(this, "Aktif veritabanı bağlantı bilgisi bulunamadı. Lütfen önce Bağlantı Ayarlarını kontrol edin.", "Bağlantı Gerekli", JOptionPane.WARNING_MESSAGE);
            return;
        }

        PostgresqlConfigurationSettings settings = settingsSupplier.get();
        final String sqlToExecute = generateSqlScript();

        int confirm = JOptionPane.showConfirmDialog(this,
                "'" + tableName + "' tablosu '" + settings.getDatabaseName() + "' veritabanında oluşturulsun mu?",
                "Tablo Oluşturma Onayı", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        // Execute in background
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<Void, Void>() {
            private Exception execError = null;

            @Override
            protected Void doInBackground() {
                try {
                    String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s?ApplicationName=PostgreSQL-DDL-Studio-Designer",
                            settings.getServerHost(), settings.getPort(), settings.getDatabaseName());

                    try (Connection conn = DriverManager.getConnection(jdbcUrl, settings.getUsername(), settings.getPassword());
                         Statement stmt = conn.createStatement()) {
                        stmt.execute(sqlToExecute);
                    }

                    // Also save .sql file to export directory
                    File targetDir = exportDir != null && exportDir.isDirectory() ? exportDir : new File("export_output");
                    if (!targetDir.exists()) targetDir.mkdirs();
                    File sqlFile = new File(targetDir, tableName + ".sql");
                    Files.writeString(sqlFile.toPath(), sqlToExecute);

                } catch (Exception e) {
                    execError = e;
                }
                return null;
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                if (execError != null) {
                    JOptionPane.showMessageDialog(VisualTableDesignerDialog.this,
                            "Tablo veritabanında oluşturulurken hata verdi:\n" + execError.getMessage(),
                            "SQL Hatası", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(VisualTableDesignerDialog.this,
                            "'" + tableName + "' tablosu veritabanında başarıyla oluşturuldu ve şemaya eklendi!",
                            "Başarılı", JOptionPane.INFORMATION_MESSAGE);

                    if (onSuccessCallback != null) {
                        onSuccessCallback.run();
                    }
                    dispose();
                }
            }
        }.execute();
    }

    private static class SimpleDocumentListener implements DocumentListener {
        private final Runnable onChange;
        public SimpleDocumentListener(Runnable onChange) { this.onChange = onChange; }
        public void insertUpdate(DocumentEvent e) { onChange.run(); }
        public void removeUpdate(DocumentEvent e) { onChange.run(); }
        public void changedUpdate(DocumentEvent e) { onChange.run(); }
    }
}

package com.ddlexporter.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class HelpShortcutsDialog extends JDialog {

    public HelpShortcutsDialog(Frame owner) {
        super(owner, "Klavye Kısayolları ve Hızlı Kullanım Rehberi", true);
        setSize(560, 440);
        setMinimumSize(new Dimension(500, 380));
        setLocationRelativeTo(owner);

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        // 1. Header
        JPanel header = new JPanel(new BorderLayout(0, 4));
        JLabel title = new JLabel("Klavye Kısayolları & Hızlı İpuçları");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        header.add(title, BorderLayout.NORTH);

        JLabel subtitle = new JLabel("PostgreSQL DDL Export Studio içerisinde hız kazanmanızı sağlayacak kısayollar:");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        subtitle.setForeground(new Color(100, 110, 125));
        header.add(subtitle, BorderLayout.SOUTH);
        mainPanel.add(header, BorderLayout.NORTH);

        // 2. Table of Shortcuts
        String[] cols = {"Kısayol / Eylem", "İşlev & Açıklama"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        model.addRow(new Object[]{"⌘ + S / Ctrl + S", "Açık olan SQL dosyasını diske kaydeder ve Git/Diff ile senkronize eder."});
        model.addRow(new Object[]{"Çift Tıklama (ERD)", "İlişki haritasındaki tabloya çift tıklayarak SQL kodunu açar ve imleci odaklar."});
        model.addRow(new Object[]{"Sağ Tıklama (ERD)", "Tablo DDL scriptini kopyalama, vurgulama ve diff karşılaştırma menüsünü açar."});
        model.addRow(new Object[]{"Mouse Sürükleme (ERD)", "Boş alanda tuvali kaydırır; tablo kartı üzerinde tabloyu taşır."});
        model.addRow(new Object[]{"Mouse Tekerleği (ERD)", "ERD ilişki haritasını yakınlaştırır (zoom in) veya uzaklaştırır (zoom out)."});
        model.addRow(new Object[]{"Formatla Butonu", "SQL kodlarını büyük harf ve standart girintilerle otomatik güzelleştirir."});
        model.addRow(new Object[]{"ZIP Arşivle Butonu", "Tüm şema çıktılarını tek bir .zip arşivi olarak masaüstüne paketler."});
        model.addRow(new Object[]{"Oto-Yedek Butonu", "Belirli periyotlarda (15dk, 1 saat) otomatik arka plan yedekleme başlatır."});
        model.addRow(new Object[]{"Koyu Tema Butonu", "Karanlık ve aydınlık arayüz temaları arasında anında geçiş yapar."});

        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 11f));
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(340);

        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setFont(c.getFont().deriveFont(Font.BOLD, 11f));
                c.setForeground(new Color(9, 105, 218));
                return c;
            }
        });

        mainPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        // 3. Footer Button
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton closeBtn = new JButton("Kapat");
        closeBtn.setFont(closeBtn.getFont().deriveFont(Font.BOLD, 12f));
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);
        mainPanel.add(footer, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }
}

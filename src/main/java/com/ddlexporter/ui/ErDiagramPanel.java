package com.ddlexporter.ui;

import com.ddlexporter.er.ErDiagramEngine;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Consumer;

public class ErDiagramPanel extends JPanel {
    private ErDiagramEngine.ErModel model;
    private final ErCanvas canvas;
    private final JTextField searchField = new JTextField();
    private String highlightQuery = "";
    private boolean isDark = false;
    private File currentExportDir = null;

    private Consumer<String> tableNavigateListener = null;
    private Consumer<String> diffNavigateListener = null;

    public ErDiagramPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        this.model = ErDiagramEngine.generateSampleModel();
        this.canvas = new ErCanvas();

        // 1. Top Toolbar
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 215, 225)),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)
        ));

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel title = new JLabel("Görsel Şema & İlişki Haritası (ERD)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        leftControls.add(title);

        searchField.setPreferredSize(new Dimension(180, 26));
        searchField.putClientProperty("JTextField.placeholderText", "Tablo ara / vurgula...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateHighlight(); }
            public void removeUpdate(DocumentEvent e) { updateHighlight(); }
            public void changedUpdate(DocumentEvent e) { updateHighlight(); }
        });
        leftControls.add(new JLabel("Tablo Ara:"));
        leftControls.add(searchField);

        JLabel tipLabel = new JLabel("(💡 Tabloya çift tıklayarak SQL koduna gidebilirsiniz)");
        tipLabel.setFont(tipLabel.getFont().deriveFont(Font.ITALIC, 11f));
        tipLabel.setForeground(new Color(110, 120, 135));
        leftControls.add(tipLabel);

        topBar.add(leftControls, BorderLayout.WEST);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        JButton zoomInBtn = new JButton("➕");
        zoomInBtn.setToolTipText("Yakınlaştır");
        zoomInBtn.addActionListener(e -> canvas.zoom(1.15));
        rightControls.add(zoomInBtn);

        JButton zoomOutBtn = new JButton("➖");
        zoomOutBtn.setToolTipText("Uzaklaştır");
        zoomOutBtn.addActionListener(e -> canvas.zoom(0.85));
        rightControls.add(zoomOutBtn);

        JButton resetViewBtn = new JButton("Sıfırla");
        resetViewBtn.setFont(resetViewBtn.getFont().deriveFont(Font.PLAIN, 11f));
        resetViewBtn.setToolTipText("Görünümü ve yakınlaştırmayı sıfırla");
        resetViewBtn.addActionListener(e -> canvas.resetView());
        rightControls.add(resetViewBtn);

        JButton autoArrangeBtn = new JButton("Düzenle");
        autoArrangeBtn.setFont(autoArrangeBtn.getFont().deriveFont(Font.PLAIN, 11f));
        autoArrangeBtn.setToolTipText("Tabloları ızgaraya göre otomatik hizala");
        autoArrangeBtn.addActionListener(e -> {
            ErDiagramEngine.arrangeLayout(model);
            canvas.repaint();
        });
        rightControls.add(autoArrangeBtn);

        JButton copyMermaidBtn = new JButton("📋 Mermaid Kopyala");
        copyMermaidBtn.setFont(copyMermaidBtn.getFont().deriveFont(Font.PLAIN, 11f));
        copyMermaidBtn.addActionListener(e -> copyMermaidCode());
        rightControls.add(copyMermaidBtn);

        JButton exportImgBtn = new JButton("📸 PNG Olarak Kaydet");
        exportImgBtn.setFont(exportImgBtn.getFont().deriveFont(Font.BOLD, 11f));
        exportImgBtn.setForeground(new Color(22, 163, 74));
        exportImgBtn.addActionListener(e -> exportAsImage());
        rightControls.add(exportImgBtn);

        topBar.add(rightControls, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // 2. Center: Interactive Drawing Canvas
        add(canvas, BorderLayout.CENTER);
    }

    public void setTableNavigateListener(Consumer<String> listener) {
        this.tableNavigateListener = listener;
    }

    public void setDiffNavigateListener(Consumer<String> listener) {
        this.diffNavigateListener = listener;
    }

    private void updateHighlight() {
        this.highlightQuery = searchField.getText().trim().toLowerCase();
        canvas.repaint();
    }

    public void setExportDir(String dirPath) {
        if (dirPath != null) {
            this.currentExportDir = new File(dirPath);
            reloadFromExport();
        }
    }

    public void reloadFromExport() {
        if (currentExportDir != null && currentExportDir.exists()) {
            this.model = ErDiagramEngine.buildModelFromDirectory(currentExportDir);
        } else {
            this.model = ErDiagramEngine.generateSampleModel();
        }
        canvas.resetView();
    }

    private void copyMermaidCode() {
        String mermaid = ErDiagramEngine.exportToMermaid(model);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(mermaid), null);
        JOptionPane.showMessageDialog(this,
                "Mermaid ER Diyagram kodu panoya kopyalandı!\nMarkdown veya GitHub belgelerinde doğrudan kullanabilirsiniz.",
                "Kopyalandı", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportAsImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("schema_erd_diagram.png"));
        int res = fileChooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            try {
                BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = image.createGraphics();
                canvas.paint(g2);
                g2.dispose();
                ImageIO.write(image, "png", fileChooser.getSelectedFile());
                JOptionPane.showMessageDialog(this,
                        "ER Diyagramı başarıyla kaydedildi:\n" + fileChooser.getSelectedFile().getAbsolutePath(),
                        "Kaydedildi", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Görsel kaydedilemedi: " + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void applyTheme(boolean isDark) {
        this.isDark = isDark;
        canvas.repaint();
    }

    // Interactive Java2D Canvas
    private class ErCanvas extends JPanel {
        private double scale = 1.0;
        private int offsetX = 0;
        private int offsetY = 0;
        private Point dragStart = null;
        private ErDiagramEngine.ErTable draggedTable = null;
        private int dragTableOffsetX = 0;
        private int dragTableOffsetY = 0;

        public ErCanvas() {
            setBackground(new Color(248, 250, 252));

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Point worldPt = screenToWorld(e.getPoint());

                    if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                        handleContextMenu(e, worldPt);
                        return;
                    }

                    // Check if clicked on a table
                    draggedTable = findTableAt(worldPt.x, worldPt.y);
                    if (draggedTable != null) {
                        dragTableOffsetX = worldPt.x - draggedTable.x;
                        dragTableOffsetY = worldPt.y - draggedTable.y;
                    } else {
                        // Canvas pan
                        dragStart = e.getPoint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                        Point worldPt = screenToWorld(e.getPoint());
                        handleContextMenu(e, worldPt);
                        return;
                    }
                    draggedTable = null;
                    dragStart = null;
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        Point worldPt = screenToWorld(e.getPoint());
                        ErDiagramEngine.ErTable table = findTableAt(worldPt.x, worldPt.y);
                        if (table != null && tableNavigateListener != null) {
                            tableNavigateListener.accept(table.name);
                        }
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (draggedTable != null) {
                        Point worldPt = screenToWorld(e.getPoint());
                        draggedTable.x = worldPt.x - dragTableOffsetX;
                        draggedTable.y = worldPt.y - dragTableOffsetY;
                        repaint();
                    } else if (dragStart != null) {
                        offsetX += (e.getX() - dragStart.x);
                        offsetY += (e.getY() - dragStart.y);
                        dragStart = e.getPoint();
                        repaint();
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    if (e.getWheelRotation() < 0) {
                        zoom(1.1);
                    } else {
                        zoom(0.9);
                    }
                }
            };

            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
            addMouseWheelListener(mouseHandler);
        }

        private void handleContextMenu(MouseEvent e, Point worldPt) {
            ErDiagramEngine.ErTable table = findTableAt(worldPt.x, worldPt.y);
            if (table == null) return;

            JPopupMenu menu = new JPopupMenu();

            JMenuItem openSqlItem = new JMenuItem("📄 SQL Gezgininde Aç / Düzenle (" + table.name + ".sql)");
            openSqlItem.setFont(openSqlItem.getFont().deriveFont(Font.BOLD));
            openSqlItem.addActionListener(ev -> {
                if (tableNavigateListener != null) tableNavigateListener.accept(table.name);
            });
            menu.add(openSqlItem);

            JMenuItem copyDdlItem = new JMenuItem("📋 Tablo DDL Scriptini Kopyala");
            copyDdlItem.addActionListener(ev -> {
                String ddl = ErDiagramEngine.generateTableDdl(table);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(ddl), null);
                JOptionPane.showMessageDialog(ErDiagramPanel.this,
                        "'" + table.name + "' tablosunun DDL tanımı panoya kopyalandı!",
                        "Kopyalandı", JOptionPane.INFORMATION_MESSAGE);
            });
            menu.add(copyDdlItem);

            menu.addSeparator();

            JMenuItem focusItem = new JMenuItem("🔍 Sadece Bu Tabloyu Vurgula");
            focusItem.addActionListener(ev -> {
                searchField.setText(table.name);
            });
            menu.add(focusItem);

            JMenuItem diffItem = new JMenuItem("🌐 Şema Farkında (Diff) Karşılaştır");
            diffItem.addActionListener(ev -> {
                if (diffNavigateListener != null) diffNavigateListener.accept(table.name);
            });
            menu.add(diffItem);

            menu.show(this, e.getX(), e.getY());
        }

        public void zoom(double factor) {
            scale = Math.max(0.4, Math.min(2.5, scale * factor));
            repaint();
        }

        public void resetView() {
            scale = 1.0;
            offsetX = 0;
            offsetY = 0;
            repaint();
        }

        private Point screenToWorld(Point p) {
            int wx = (int) ((p.x - offsetX) / scale);
            int wy = (int) ((p.y - offsetY) / scale);
            return new Point(wx, wy);
        }

        private ErDiagramEngine.ErTable findTableAt(int wx, int wy) {
            for (ErDiagramEngine.ErTable tbl : model.tables.values()) {
                if (wx >= tbl.x && wx <= tbl.x + tbl.width && wy >= tbl.y && wy <= tbl.y + tbl.height) {
                    return tbl;
                }
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Background
            if (isDark) {
                g2.setColor(new Color(15, 17, 23));
            } else {
                g2.setColor(new Color(245, 247, 250));
            }
            g2.fillRect(0, 0, getWidth(), getHeight());

            // Grid background dots
            drawBackgroundGrid(g2);

            // Apply pan & zoom transform
            g2.translate(offsetX, offsetY);
            g2.scale(scale, scale);

            // 1. Draw Relations (Bézier connecting curves)
            drawRelations(g2);

            // 2. Draw Table Cards
            for (ErDiagramEngine.ErTable table : model.tables.values()) {
                drawTableCard(g2, table);
            }

            g2.dispose();
        }

        private void drawBackgroundGrid(Graphics2D g2) {
            g2.setColor(isDark ? new Color(28, 32, 42) : new Color(225, 230, 240));
            int step = (int) (24 * scale);
            if (step < 8) return;

            int startX = offsetX % step;
            int startY = offsetY % step;
            for (int x = startX; x < getWidth(); x += step) {
                for (int y = startY; y < getHeight(); y += step) {
                    g2.fillRect(x, y, 2, 2);
                }
            }
        }

        private void drawRelations(Graphics2D g2) {
            for (ErDiagramEngine.ErRelation rel : model.relations) {
                ErDiagramEngine.ErTable src = model.tables.get(rel.sourceTable);
                ErDiagramEngine.ErTable tgt = model.tables.get(rel.targetTable);
                if (src == null || tgt == null) continue;

                int x1 = src.x + src.width / 2;
                int y1 = src.y + src.height / 2;
                int x2 = tgt.x + tgt.width / 2;
                int y2 = tgt.y + tgt.height / 2;

                // Edge attachment points
                if (x1 < tgt.x) {
                    x1 = src.x + src.width;
                    x2 = tgt.x;
                } else if (x1 > tgt.x + tgt.width) {
                    x1 = src.x;
                    x2 = tgt.x + tgt.width;
                }

                // Smooth Bézier curve
                int ctrlDist = Math.abs(x2 - x1) / 2 + 30;
                CubicCurve2D curve = new CubicCurve2D.Float(
                        x1, y1,
                        x1 + (x2 > x1 ? ctrlDist : -ctrlDist), y1,
                        x2 + (x2 > x1 ? -ctrlDist : ctrlDist), y2,
                        x2, y2
                );

                g2.setStroke(new BasicStroke(2.0f));
                g2.setColor(isDark ? new Color(96, 165, 250, 180) : new Color(59, 130, 246, 200));
                g2.draw(curve);

                // Small arrow head at target
                drawArrowHead(g2, x2, y2, x1 < x2 ? -1 : 1);
            }
        }

        private void drawArrowHead(Graphics2D g2, int x, int y, int dir) {
            int size = 6;
            Polygon arrow = new Polygon();
            arrow.addPoint(x, y);
            arrow.addPoint(x + dir * size * 2, y - size);
            arrow.addPoint(x + dir * size * 2, y + size);
            g2.fill(arrow);
        }

        private void drawTableCard(Graphics2D g2, ErDiagramEngine.ErTable table) {
            boolean isHighlighted = !highlightQuery.isEmpty() && table.name.toLowerCase().contains(highlightQuery);

            // Card Shadow
            g2.setColor(new Color(0, 0, 0, isDark ? 60 : 25));
            g2.fillRoundRect(table.x + 3, table.y + 3, table.width, table.height, 12, 12);

            // Card Body
            g2.setColor(isDark ? new Color(24, 28, 38) : Color.WHITE);
            g2.fillRoundRect(table.x, table.y, table.width, table.height, 12, 12);

            // Card Border
            if (isHighlighted) {
                g2.setColor(new Color(234, 179, 8)); // Yellow highlight
                g2.setStroke(new BasicStroke(2.5f));
            } else {
                g2.setColor(isDark ? new Color(50, 56, 72) : new Color(215, 220, 230));
                g2.setStroke(new BasicStroke(1.2f));
            }
            g2.drawRoundRect(table.x, table.y, table.width, table.height, 12, 12);

            // Header Background
            g2.setColor(isDark ? new Color(34, 40, 56) : new Color(238, 242, 248));
            g2.fillRoundRect(table.x, table.y, table.width, 28, 12, 12);
            g2.fillRect(table.x, table.y + 16, table.width, 12);

            // Header Separator
            g2.setColor(isDark ? new Color(50, 56, 72) : new Color(215, 220, 230));
            g2.drawLine(table.x, table.y + 28, table.x + table.width, table.y + 28);

            // Header Title: Table Name
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g2.setColor(isDark ? new Color(240, 245, 255) : new Color(30, 41, 59));
            g2.drawString(table.name, table.x + 10, table.y + 19);

            // Columns List
            int rowY = table.y + 46;
            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

            for (ErDiagramEngine.ErColumn col : table.columns) {
                // Key Badges: PK 🔑 / FK 🔗
                if (col.isPk) {
                    g2.setColor(new Color(234, 179, 8));
                    g2.drawString("🔑", table.x + 8, rowY);
                } else if (col.isFk) {
                    g2.setColor(new Color(59, 130, 246));
                    g2.drawString("🔗", table.x + 8, rowY);
                }

                // Column Name
                g2.setColor(isDark ? new Color(220, 225, 235) : new Color(51, 65, 85));
                g2.drawString(col.name, table.x + 26, rowY);

                // Column Type (Right Aligned)
                g2.setColor(isDark ? new Color(130, 140, 160) : new Color(140, 150, 165));
                int typeWidth = g2.getFontMetrics().stringWidth(col.type);
                g2.drawString(col.type, table.x + table.width - typeWidth - 8, rowY);

                rowY += 20;
            }
        }
    }
}

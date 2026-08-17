package com.ddlexporter.app;

import com.ddlexporter.common.config.JsonConfigurationReader;
import com.ddlexporter.common.logger.ConsoleLogger;
import com.ddlexporter.common.logger.ILogger;
import com.ddlexporter.common.scripter.IScripter;
import com.ddlexporter.common.scripter.ScripterBuilder;
import com.ddlexporter.common.writer.FileWriter;

public class Main {
    private final String[] args;
    private String databaseType;
    private String outputDir;
    private String settingsFile;

    public Main(String[] args) {
        this.args = args;
    }

    public static void main(String[] args) {
        try {
            Main app = new Main(args);
            app.run();
        } catch (Throwable ex) {
            System.err.println("Beklenmedik bir hata oluştu: ");
            System.err.println(ex.getMessage());
            ex.printStackTrace(System.err);
            try {
                javax.swing.JOptionPane.showMessageDialog(null,
                        "Uygulama Başlatılamadı:\n" + ex.toString() + "\n\nJava Sürümü: " + System.getProperty("java.version"),
                        "PostgreSQL DDL Studio - Başlatma Hatası",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignored) {}
            System.exit(1);
        }
    }

    public void run() {
        if (args == null || args.length == 0) {
            // Masaüstü GUI Modunu Başlat
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    com.ddlexporter.ui.MainFrame mainFrame = new com.ddlexporter.ui.MainFrame();
                    mainFrame.setVisible(true);
                } catch (Throwable t) {
                    t.printStackTrace();
                    try {
                        javax.swing.JOptionPane.showMessageDialog(null,
                                "Masaüstü Arayüzü Başlatılamadı:\n" + t.toString() + "\n\nJava Sürümü: " + System.getProperty("java.version"),
                                "PostgreSQL DDL Studio - Arayüz Hatası",
                                javax.swing.JOptionPane.ERROR_MESSAGE);
                    } catch (Throwable ignored) {}
                }
            });
            return;
        }

        if (!parseArgs()) {
            showHelp();
            return;
        }

        ILogger logger = new ConsoleLogger();
        logger.log("DDL Export Konsol Uygulaması Başlatılıyor...");
        logger.log("Veritabanı Tipi: " + databaseType);
        logger.log("Çıktı Dizini: " + outputDir);
        logger.log("Ayar Dosyası: " + settingsFile);

        IScripter scripter = ScripterBuilder.get(databaseType)
                .addConfigurationReader(new JsonConfigurationReader(settingsFile))
                .addWriter(new FileWriter(outputDir, logger))
                .addLogger(logger)
                .build();

        scripter.execute();
    }

    private boolean parseArgs() {
        if (args == null || args.length != 3) {
            return false;
        }

        for (String arg : args) {
            if (arg.startsWith("-db:")) {
                databaseType = arg.substring("-db:".length()).trim();
            } else if (arg.startsWith("-od:")) {
                outputDir = arg.substring("-od:".length()).trim();
            } else if (arg.startsWith("-s:")) {
                settingsFile = arg.substring("-s:".length()).trim();
            }
        }

        return databaseType != null && !databaseType.isBlank()
                && outputDir != null && !outputDir.isBlank()
                && settingsFile != null && !settingsFile.isBlank();
    }

    private void showHelp() {
        System.out.println("Kullanım: java -jar postgres_ddl_export_console_java.jar -db:POSTGRESQL -od:<export_directory> -s:<settings_file>");
        System.out.println("Örnek: java -jar postgres_ddl_export_console_java.jar -db:POSTGRESQL -od:./export_output -s:postgresql_settings.json");
    }
}

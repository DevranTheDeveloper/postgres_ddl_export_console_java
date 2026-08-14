package com.ddlexporter.common.util;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipArchiver {

    public static void zipDirectory(File sourceDir, File zipFile) throws Exception {
        if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new IllegalArgumentException("Geçerli bir kaynak dizin bulunamadı.");
        }

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            Path sourcePath = sourceDir.toPath();

            Files.walk(sourcePath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        String relativeName = sourcePath.relativize(path).toString().replace("\\", "/");
                        ZipEntry zipEntry = new ZipEntry(relativeName);
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}

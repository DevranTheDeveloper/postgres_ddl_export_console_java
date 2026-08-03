package com.ddlexporter;

import com.ddlexporter.common.logger.ConsoleLogger;
import com.ddlexporter.common.writer.FileWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class FileWriterTest {

    @Test
    public void testFileWriterFolderStructureAndFileCreation(@TempDir Path tempDir) throws Exception {
        FileWriter writer = new FileWriter(tempDir.toString(), new ConsoleLogger());

        writer.start("TestDb", "TABLE", "dbo.Users");
        writer.writeLine("CREATE TABLE dbo.Users (id INT PRIMARY KEY);");
        writer.finish();

        File dbFolder = new File(tempDir.toFile(), "TestDb");
        assertTrue(dbFolder.exists() && dbFolder.isDirectory(), "Database folder should exist");

        File tableFolder = new File(dbFolder, "TABLE");
        assertTrue(tableFolder.exists() && tableFolder.isDirectory(), "TABLE folder should exist");

        File sqlFile = new File(tableFolder, "dbo_Users.sql");
        assertTrue(sqlFile.exists() && sqlFile.isFile(), "SQL file should exist");

        String content = Files.readString(sqlFile.toPath());
        assertTrue(content.contains("CREATE TABLE dbo.Users"));
    }
}

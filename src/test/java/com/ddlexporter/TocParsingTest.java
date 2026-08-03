package com.ddlexporter;

import com.ddlexporter.common.logger.ConsoleLogger;
import com.ddlexporter.common.model.TocEntry;
import com.ddlexporter.common.writer.FileWriter;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;
import com.ddlexporter.postgresql.extractor.PgDumpDdlExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TocParsingTest {

    @Test
    public void testParseTocLines() {
        PgDumpDdlExtractor extractor = new PgDumpDdlExtractor(
                new PostgresqlConfigurationSettings(),
                new FileWriter("/tmp", new ConsoleLogger()),
                new ConsoleLogger()
        );

        String[] tocLines = new String[]{
                "; Archive created at 2026-08-03 12:00:00",
                "200; 1259 16384 TABLE public Users postgres",
                "201; 1259 16390 VIEW public vw_ActiveUsers postgres",
                "202; 1255 16400 FUNCTION public fn_calculate_age(integer) postgres",
                "203; 2615 2200 SCHEMA - public postgres"
        };

        List<TocEntry> entries = extractor.parseToc(tocLines);

        assertEquals(4, entries.size());

        TocEntry tableEntry = entries.get(0);
        assertEquals("200", tableEntry.getArchiveId());
        assertEquals("TABLE", tableEntry.getType());
        assertEquals("public", tableEntry.getSchema());
        assertEquals("Users", tableEntry.getName());

        TocEntry schemaEntry = entries.get(3);
        assertEquals("SCHEMA", schemaEntry.getType());
        assertEquals("", schemaEntry.getSchema());
        assertEquals("public", schemaEntry.getName());
    }
}

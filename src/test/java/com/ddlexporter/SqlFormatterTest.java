package com.ddlexporter;

import com.ddlexporter.common.util.SqlFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SqlFormatterTest {

    @Test
    public void testSqlFormattingKeywords() {
        String input = "create table users (id serial primary key, name varchar(100));";
        String formatted = SqlFormatter.formatSql(input);

        assertNotNull(formatted);
        assertTrue(formatted.contains("CREATE TABLE"));
        assertTrue(formatted.contains("PRIMARY KEY"));
    }

    @Test
    public void testEmptyOrNullHandling() {
        assertNull(SqlFormatter.formatSql(null));
        assertEquals("", SqlFormatter.formatSql(""));
        assertEquals("   ", SqlFormatter.formatSql("   "));
    }
}

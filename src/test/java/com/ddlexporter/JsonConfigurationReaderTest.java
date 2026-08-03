package com.ddlexporter;

import com.ddlexporter.common.config.JsonConfigurationReader;
import com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JsonConfigurationReaderTest {

    @Test
    public void testReadValidJson() {
        String json = "{\n" +
                "  \"serverHost\": \"localhost\",\n" +
                "  \"port\": 5432,\n" +
                "  \"databaseName\": \"test_db\",\n" +
                "  \"username\": \"postgres\",\n" +
                "  \"password\": \"secret\",\n" +
                "  \"schema\": \"public\",\n" +
                "  \"pgDumpPath\": \"/usr/bin/pg_dump\"\n" +
                "}";

        JsonConfigurationReader reader = new JsonConfigurationReader(json);
        PostgresqlConfigurationSettings settings = reader.read(PostgresqlConfigurationSettings.class);

        assertNotNull(settings);
        assertEquals("localhost", settings.getServerHost());
        assertEquals(5432, settings.getPort());
        assertEquals("test_db", settings.getDatabaseName());
        assertEquals("postgres", settings.getUsername());
        assertEquals("secret", settings.getPassword());
        assertEquals("public", settings.getSchema());
        assertEquals("/usr/bin/pg_dump", settings.getPgDumpPath());
    }

    @Test
    public void testCaseInsensitiveJsonProperties() {
        String json = "{\n" +
                "  \"ServerHost\": \"127.0.0.1\",\n" +
                "  \"Port\": 5433,\n" +
                "  \"DatabaseName\": \"demo_db\"\n" +
                "}";

        JsonConfigurationReader reader = new JsonConfigurationReader(json);
        PostgresqlConfigurationSettings settings = reader.read(PostgresqlConfigurationSettings.class);

        assertNotNull(settings);
        assertEquals("127.0.0.1", settings.getServerHost());
        assertEquals(5433, settings.getPort());
        assertEquals("demo_db", settings.getDatabaseName());
    }
}

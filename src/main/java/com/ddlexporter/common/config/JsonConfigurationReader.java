package com.ddlexporter.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;

public class JsonConfigurationReader implements IConfigurationReader {
    private final String jsonContentOrPath;
    private final ObjectMapper mapper;

    public JsonConfigurationReader(String jsonContentOrPath) {
        this.jsonContentOrPath = jsonContentOrPath;
        this.mapper = JsonMapper.builder()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    @Override
    public <T> T read(Class<T> clazz) {
        try {
            File file = new File(jsonContentOrPath);
            if (file.exists() && file.isFile()) {
                return mapper.readValue(file, clazz);
            }
            return mapper.readValue(jsonContentOrPath, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON configuration: " + e.getMessage(), e);
        }
    }
}

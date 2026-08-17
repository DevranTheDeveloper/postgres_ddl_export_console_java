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
            com.fasterxml.jackson.databind.JsonNode rootNode;
            File file = new File(jsonContentOrPath);
            if (file.exists() && file.isFile()) {
                rootNode = mapper.readTree(file);
            } else {
                rootNode = mapper.readTree(jsonContentOrPath);
            }

            if (clazz.equals(com.ddlexporter.postgresql.config.PostgresqlConfigurationSettings.class)
                    && rootNode.has("profiles") && rootNode.get("profiles").isArray()) {
                String activeName = rootNode.has("activeProfile") ? rootNode.get("activeProfile").asText() : null;
                com.fasterxml.jackson.databind.JsonNode selectedNode = null;
                for (com.fasterxml.jackson.databind.JsonNode pNode : rootNode.get("profiles")) {
                    if (activeName != null && activeName.equals(pNode.path("name").asText())) {
                        selectedNode = pNode;
                        break;
                    }
                }
                if (selectedNode == null && rootNode.get("profiles").size() > 0) {
                    selectedNode = rootNode.get("profiles").get(0);
                }
                if (selectedNode != null) {
                    return mapper.treeToValue(selectedNode, clazz);
                }
            }

            return mapper.treeToValue(rootNode, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON configuration: " + e.getMessage(), e);
        }
    }
}

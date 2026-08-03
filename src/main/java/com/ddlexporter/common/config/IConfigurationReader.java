package com.ddlexporter.common.config;

public interface IConfigurationReader {
    <T> T read(Class<T> clazz);
}

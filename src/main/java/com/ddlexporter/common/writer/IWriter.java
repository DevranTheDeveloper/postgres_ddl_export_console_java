package com.ddlexporter.common.writer;

public interface IWriter {
    void start(String databaseName, String objectType, String objectNameWithSchema);
    void writeLine(String text);
    void finish();
}

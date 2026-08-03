package com.ddlexporter.common.logger;

public interface ILogger {
    void log(String message);
    void logError(String message, Throwable throwable);
}

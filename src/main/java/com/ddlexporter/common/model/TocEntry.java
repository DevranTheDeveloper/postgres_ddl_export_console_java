package com.ddlexporter.common.model;

public class TocEntry {
    private String archiveId;
    private String type;
    private String schema;
    private String name;
    private String originalLine;

    public TocEntry() {}

    public TocEntry(String archiveId, String type, String schema, String name, String originalLine) {
        this.archiveId = archiveId;
        this.type = type;
        this.schema = schema;
        this.name = name;
        this.originalLine = originalLine;
    }

    public String getArchiveId() { return archiveId; }
    public void setArchiveId(String archiveId) { this.archiveId = archiveId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOriginalLine() { return originalLine; }
    public void setOriginalLine(String originalLine) { this.originalLine = originalLine; }

    @Override
    public String toString() {
        return "TocEntry{" +
                "archiveId='" + archiveId + '\'' +
                ", type='" + type + '\'' +
                ", schema='" + schema + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}

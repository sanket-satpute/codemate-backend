package com.codescope.backend.dto.upload;

public class FileDocumentDto {

    private String fileName;
    private String fileType;
    private String content;
    private String url;

    public FileDocumentDto(String fileName, String fileType, String content, String url) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.content = content;
        this.url = url;
    }

    // Getters and Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

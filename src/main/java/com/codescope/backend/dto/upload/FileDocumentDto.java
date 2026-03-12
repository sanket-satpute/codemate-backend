package com.codescope.backend.dto.upload;

public class FileDocumentDto {

    private String fileName;
    private String fileType;
    private String content;
    private String url;
    private String cloudinaryPublicId;
    private Long fileSize;

    public FileDocumentDto(String fileName, String fileType, String content, String url) {
        this(fileName, fileType, content, url, null, null);
    }

    public FileDocumentDto(String fileName, String fileType, String content, String url, String cloudinaryPublicId,
            Long fileSize) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.content = content;
        this.url = url;
        this.cloudinaryPublicId = cloudinaryPublicId;
        this.fileSize = fileSize;
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

    public String getCloudinaryPublicId() {
        return cloudinaryPublicId;
    }

    public void setCloudinaryPublicId(String cloudinaryPublicId) {
        this.cloudinaryPublicId = cloudinaryPublicId;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
}

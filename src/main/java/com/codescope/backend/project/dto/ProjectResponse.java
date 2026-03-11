package com.codescope.backend.project.dto;

import com.codescope.backend.project.model.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectResponse {
    private String id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private ProjectStatus status;
    private String lastAnalysisJobId;

    // File stats
    private int totalFiles;
    private long totalFileSize; // bytes
    private Map<String, Integer> fileTypeBreakdown; // extension → count, e.g. {"java": 5, "py": 3}
    private List<FileInfo> files; // individual file info

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FileInfo {
        private String id;
        private String filename;
        private long fileSize;
        private String fileType;
        private String fileExtension;
        private String cloudinaryUrl;
        private LocalDateTime uploadedAt;
    }
}

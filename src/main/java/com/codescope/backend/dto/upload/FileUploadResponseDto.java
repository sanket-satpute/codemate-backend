package com.codescope.backend.dto.upload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponseDto {
    private String projectId;
    private String jobId;
    private int filesProcessed;
    private String status;
    private List<String> fileUrls;
    private List<String> fileRelativePaths;
    private String message;
}

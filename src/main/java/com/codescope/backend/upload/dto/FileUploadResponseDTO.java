package com.codescope.backend.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponseDTO {
    private String id;
    private String filename;
    private Long fileSize;
    private String fileType;
    private String downloadUrl; // This will be a generated URL for download
    private LocalDateTime uploadedAt;
}

package com.codescope.backend.dto.upload;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class FileUploadRequestDTO {
    private String projectId;
    private List<MultipartFile> files; // For multiple files and zip files
    // Note: For a single file, the list will contain one element.
    // For zip files, we'll need to handle extraction logic separately.
}

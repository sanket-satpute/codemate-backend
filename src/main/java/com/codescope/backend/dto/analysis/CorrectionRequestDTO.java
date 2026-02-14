package com.codescope.backend.dto.analysis;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class CorrectionRequestDTO {
    private String projectId; // Optional, if correcting a file within an existing project
    private String fileId;    // Optional, if correcting an existing file in Cloudinary/Firebase
    @NotBlank(message = "Original code or file content is required for correction")
    private String originalContent; // The code/text to be corrected
    @NotBlank(message = "Correction instruction is required")
    private String instruction; // e.g., "make this error-free", "optimize this function"
    private String fileName; // Optional, for new files or identifying existing ones
    private String fileType; // Optional, for new files or identifying existing ones
}

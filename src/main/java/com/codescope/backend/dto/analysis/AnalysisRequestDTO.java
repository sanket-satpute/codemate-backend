package com.codescope.backend.dto.analysis;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AnalysisRequestDTO {
    private String projectId;

    @NotBlank(message = "Code is required for analysis")
    private String code;
}

package com.codescope.backend.project.dto;

import com.codescope.backend.project.model.ProjectStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectUpdateRequest {

    @Size(min = 3, message = "Project name must be at least 3 characters long")
    private String name;

    @Size(max = 200, message = "Description cannot exceed 200 characters")
    private String description;

    private ProjectStatus status;

    private String lastAnalysisJobId;
}

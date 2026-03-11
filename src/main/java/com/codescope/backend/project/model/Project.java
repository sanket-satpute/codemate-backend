package com.codescope.backend.project.model;

import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.model.Report; // Assuming Report is in backend.model for now, will verify later.
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "projects")
public class Project {

    @Id
    private String id; // MongoDB ID

    private String projectId; // Public API identifier (stable across migrations)

    private String ownerId;

    private String name;
    private String description;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private ProjectStatus status;

    private String lastAnalysisJobId; // nullable
    private LocalDateTime lastCorrectedAt;
    private String lastCorrectedByModel;
    private String lastCorrectionSummary;

    // These fields will be stored as nested documents in MongoDB
    private List<ProjectFile> files;
    private List<AnalysisJob> jobs;
    private List<Report> reports;
}

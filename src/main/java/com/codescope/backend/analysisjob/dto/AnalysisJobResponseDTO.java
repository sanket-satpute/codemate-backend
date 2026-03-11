package com.codescope.backend.analysisjob.dto;

import com.codescope.backend.analysisjob.enums.JobStatus;
import com.codescope.backend.analysisjob.enums.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisJobResponseDTO {
    private Long id;
    private String jobId; // Added jobId
    private String projectId; // Changed to String
    private JobType jobType;
    private JobStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String result; // optional
    private String model; // AI model used for the job
}

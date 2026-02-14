package com.codescope.backend.analysisjob.model;

import com.codescope.backend.analysisjob.enums.JobStatus;
import com.codescope.backend.analysisjob.enums.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.redis.core.RedisHash; // Import for Redis

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("analysisJob") // Annotation for Redis entity
public class AnalysisJob {

    @Id // Spring Data Id for Redis
    private String jobId; // Using jobId as the Redis ID

    private String projectId;

    private JobType jobType;

    private JobStatus status;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private String result; // JSON string or text

    private String initiatedBy; // userId

    private String model; // Stores the AI model used for the job

    public void markCompleted(String reportId) {
        this.status = JobStatus.COMPLETED;
        this.result = reportId;
    }

    public void markFailed(String message) {
        this.status = JobStatus.FAILED;
        this.result = message;
    }
}

package com.codescope.backend.dto.job;

import com.codescope.backend.dto.analysis.AIResponseDto;

import java.time.LocalDateTime;

public class JobDto {

    private String jobId;
    private String projectId;
    private String status;
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private AIResponseDto aiResponse;

    // Getters and Setters
    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public AIResponseDto getAiResponse() {
        return aiResponse;
    }

    public void setAiResponse(AIResponseDto aiResponse) {
        this.aiResponse = aiResponse;
    }
}

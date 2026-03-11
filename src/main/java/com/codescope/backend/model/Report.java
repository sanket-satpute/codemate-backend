package com.codescope.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Document(collection = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report {
    @Id
    private String reportId;     // ✅ new field added
    private String jobId;        // links to AnalysisJob
    private String projectId;    // links to Project
    private String summary;      // AI summary or final analysis
    private List<Map<String, String>> findings; // structured results
    private String status;       // e.g. "completed", "failed", "in_progress"

    @CreatedDate
    private Date generatedAt;

    private List<String> details; // <-- Add this

    public Report(String jobId, String projectId, String summary,
                  List<Map<String, String>> findings, String status) {
        this.jobId = jobId;
        this.projectId = projectId;
        this.summary = summary;
        this.findings = findings;
        this.status = status;
    }
}

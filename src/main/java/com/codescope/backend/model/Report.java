package com.codescope.backend.model;

import com.google.cloud.firestore.annotation.ServerTimestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report {
    private String reportId;     // ✅ new field added
    private String jobId;        // links to AnalysisJob
    private String projectId;    // links to Project
    private String summary;      // AI summary or final analysis
    private List<Map<String, String>> findings; // structured results
    private String status;       // e.g. "completed", "failed", "in_progress"

    @ServerTimestamp
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

package com.codescope.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

@Document(collection = "ai_responses")
public class AIResponse {

    @Id
    private String id;

    private String jobId;
    private String projectId;
    private String summary;
    private String codeAnalysis;
    private String reportLink;

    // ✅ Add missing fields
    private String provider;
    private String status;

    private Date generatedAt = new Date();

    private Date createdAt = new Date();

    private List<Map<String, Object>> findings = new ArrayList<>();

    private Map<String, Object> metadata = new HashMap<>();

    // ✅ Default constructor
    public AIResponse() {}

    // ✅ Constructor used by AIService.java
    public AIResponse(String jobId, String projectId,
                      List<Map<String, Object>> findings,
                      String summary,
                      Map<String, String> metadata,
                      Date createdAt) {
        this.jobId = jobId;
        this.projectId = projectId;
        this.findings = findings;
        this.summary = summary;
        this.metadata = new HashMap<>(metadata);
        this.createdAt = createdAt;
    }

    // ✅ Full constructor (optional)
    public AIResponse(String id, String jobId, String projectId, String summary, String codeAnalysis,
                      String reportLink, String provider, String status,
                      Date generatedAt, Date createdAt,
                      List<Map<String, Object>> findings, Map<String, Object> metadata) {
        this.id = id;
        this.jobId = jobId;
        this.projectId = projectId;
        this.summary = summary;
        this.codeAnalysis = codeAnalysis;
        this.reportLink = reportLink;
        this.provider = provider;
        this.status = status;
        this.generatedAt = generatedAt;
        this.createdAt = createdAt;
        this.findings = findings;
        this.metadata = metadata;
    }

    // ✅ Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getCodeAnalysis() { return codeAnalysis; }
    public void setCodeAnalysis(String codeAnalysis) { this.codeAnalysis = codeAnalysis; }

    public String getReportLink() { return reportLink; }
    public void setReportLink(String reportLink) { this.reportLink = reportLink; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Date generatedAt) { this.generatedAt = generatedAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public List<Map<String, Object>> getFindings() { return findings; }
    public void setFindings(List<Map<String, Object>> findings) { this.findings = findings; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}

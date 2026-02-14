package com.codescope.backend.dto.analysis;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AIResponseDto {

    private String id;
    private String jobId;
    private String projectId;
    private String summary;
    private String codeAnalysis;
    private String reportLink;
    private String provider;
    private String status;
    private Date generatedAt;
    private Date createdAt;
    private List<Map<String, String>> findings = new ArrayList<>();
    private Map<String, Object> metadata = new HashMap<>();

    // Default constructor
    public AIResponseDto() {}

    public AIResponseDto(com.codescope.backend.model.AIResponse aiResponse) {
        this.id = aiResponse.getId();
        this.jobId = aiResponse.getJobId();
        this.projectId = aiResponse.getProjectId();
        this.summary = aiResponse.getSummary();
        this.codeAnalysis = aiResponse.getCodeAnalysis();
        this.reportLink = aiResponse.getReportLink();
        this.provider = aiResponse.getProvider();
        this.status = aiResponse.getStatus();
        this.generatedAt = aiResponse.getGeneratedAt();
        this.createdAt = aiResponse.getCreatedAt();
        
        // Handle the case where findings might be List<Map<String, Object>>
        List<Map<String, String>> findingsAsStringMap = new ArrayList<>();
        if (aiResponse.getFindings() != null) {
            for (Map<String, Object> finding : aiResponse.getFindings()) {
                Map<String, String> stringMap = new HashMap<>();
                for (Map.Entry<String, Object> entry : finding.entrySet()) {
                    stringMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
                }
                findingsAsStringMap.add(stringMap);
            }
        }
        this.findings = findingsAsStringMap;
        
        this.metadata = aiResponse.getMetadata();
    }

    // Getters and Setters
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

    public List<Map<String, String>> getFindings() { return findings; }
    public void setFindings(List<Map<String, String>> findings) { this.findings = findings; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}

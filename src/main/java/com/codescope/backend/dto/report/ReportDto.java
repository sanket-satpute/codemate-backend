package com.codescope.backend.dto.report;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ReportDto {

    private String reportId;
    private String projectId;
    private String generatedBy;
    private Date generatedAt;
    private String summary;
    private List<Map<String, String>> findings;

    // Getters and Setters
    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }

    public Date getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Date generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<Map<String, String>> getFindings() {
        return findings;
    }

    public void setFindings(List<Map<String, String>> findings) {
        this.findings = findings;
    }
}

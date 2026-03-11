package com.codescope.backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {

    private long totalProjects;
    private long totalJobs;
    private long successfulJobs;
    private long failedJobs;
    private long totalFiles;
    private String lastActive;
    private Map<String, Long> modelUsage;
}

package com.codescope.backend.service;

import com.codescope.backend.dto.analysis.AIResponseDto;
import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.model.Report;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class JobRunner {

    private final FirebaseService firebaseService;
    private final AnalysisService analysisService;
    private final ReportService reportService;

    public JobRunner(FirebaseService firebaseService, AnalysisService analysisService, ReportService reportService) {
        this.firebaseService = firebaseService;
        this.analysisService = analysisService;
        this.reportService = reportService;
    }

    public Mono<Void> executeJob(AnalysisJob job, String code) {
        Mono<Void> updateStatusRunning = firebaseService.updateJobStatus(job.getJobId(), "RUNNING", "AI job started");

        Mono<AIResponseDto> aiResponseMono = analysisService.analyzeAndBuildResponse(code);

        Mono<String> saveReportMono = aiResponseMono.flatMap(aiResponseDto -> {
            Report report = new Report();
            report.setJobId(job.getJobId());
            report.setProjectId(job.getProjectId());
            report.setSummary(aiResponseDto.getSummary());
            report.setFindings(aiResponseDto.getFindings());
            report.setDetails(List.of(aiResponseDto.getSummary()));
            report.setStatus("COMPLETED");
            return reportService.saveReport(report);
        });

        Mono<Void> updateStatusCompleted = saveReportMono.flatMap(reportId -> {
            job.markCompleted(reportId);
            return firebaseService.saveJob(job)
                    .then(firebaseService.updateJobStatus(job.getJobId(), "COMPLETED", "Report saved: " + reportId));
        });

        return updateStatusRunning
                .then(updateStatusCompleted)
                .onErrorResume(e -> {
                    job.markFailed(e.getMessage());
                    return firebaseService.saveJob(job)
                            .then(firebaseService.updateJobStatus(job.getJobId(), "FAILED", e.getMessage()))
                            .onErrorResume(ex -> {
                                log.error("Failed to mark job as failed: {}", ex.getMessage());
                                return Mono.empty();
                            });
                });
    }
}

package com.codescope.backend.controller;

import com.codescope.backend.dto.BaseResponse;
import com.codescope.backend.dto.report.ReportCreationResponseDto;
import com.codescope.backend.dto.report.ReportDto;
import com.codescope.backend.exception.ResourceNotFoundException;
import com.codescope.backend.model.Report;
import com.codescope.backend.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@Slf4j
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<BaseResponse<ReportCreationResponseDto>>> createDummyReport(@RequestBody(required = false) Report report) {
        log.info("Received request to create dummy report.");
        Report finalReport = (report != null) ? report : new Report();
        if (finalReport.getReportId() == null || finalReport.getReportId().isEmpty()) {
            finalReport.setReportId(UUID.randomUUID().toString());
            finalReport.setProjectId("dummy-project-id");
            finalReport.setJobId("dummy-job-id");
            finalReport.setStatus("COMPLETED");
            finalReport.setSummary("AI Code Analysis summary for sample project upload.");
            finalReport.setDetails(List.of(
                    "Code successfully analyzed.",
                    "No major vulnerabilities found.",
                    "Performance optimizations suggested."
            ));
            finalReport.setFindings(List.of(
                    Map.of("type", "INFO", "message", "Build passed successfully."),
                    Map.of("type", "WARNING", "message", "Minor unused imports found.")
            ));
        }

        return reportService.saveReport(finalReport)
                .map(reportId -> ResponseEntity.ok(BaseResponse.success(new ReportCreationResponseDto(reportId, "Report saved successfully"), "Report saved successfully!")))
                .onErrorResume(e -> {
                    log.error("Error creating dummy report: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.error("Failed to create dummy report: " + e.getMessage())));
                });
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<BaseResponse<List<ReportDto>>>> getReportsByProject(@PathVariable String projectId) {
        log.info("Fetching reports for project ID: {}", projectId);
        return reportService.getReportsByProject(projectId)
                .map(reports -> ResponseEntity.ok(BaseResponse.success(reports, "Reports retrieved successfully")))
                .onErrorResume(e -> {
                    log.error("Error fetching reports for project ID {}: {}", projectId, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.error("Failed to retrieve reports: " + e.getMessage())));
                });
    }

    @GetMapping("/{reportId}/download")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<BaseResponse<byte[]>>> downloadReportPdf(@PathVariable String reportId) {
        log.info("Downloading PDF for report ID: {}", reportId);
        return reportService.getReportById(reportId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Report not found with ID: " + reportId)))
                .flatMap(reportDto -> {
                    try {
                        byte[] pdfBytes = reportService.generateReportPdf(reportDto);
                        BaseResponse<byte[]> successResponse = BaseResponse.success(pdfBytes, "PDF report generated successfully");
                        return Mono.just(ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report_" + reportId + ".pdf\"")
                                .contentType(MediaType.APPLICATION_PDF)
                                .body(successResponse));
                    } catch (Exception e) {
                        log.error("Error generating PDF for report ID {}: {}", reportId, e.getMessage());
                        BaseResponse<byte[]> errorResponse = BaseResponse.error("Failed to generate PDF: " + e.getMessage());
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(errorResponse));
                    }
                })
                .onErrorResume(ResourceNotFoundException.class, e -> {
                    log.warn("Report not found with ID {}: {}", reportId, e.getMessage());
                    BaseResponse<byte[]> errorResponse = BaseResponse.error("Report not found with ID: " + reportId);
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(errorResponse));
                })
                .onErrorResume(e -> {
                    log.error("An unexpected error occurred while downloading PDF for report ID {}: {}", reportId, e.getMessage());
                    BaseResponse<byte[]> errorResponse = BaseResponse.error("An unexpected error occurred while downloading PDF: " + e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(errorResponse));
                });
    }
}

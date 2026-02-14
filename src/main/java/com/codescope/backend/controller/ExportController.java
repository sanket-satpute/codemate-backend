package com.codescope.backend.controller;

import com.codescope.backend.dto.BaseResponse;
import com.codescope.backend.dto.report.ReportDto;
import com.codescope.backend.exception.ResourceNotFoundException;
import com.codescope.backend.service.FirebaseService;
import com.codescope.backend.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/export")
@Slf4j
public class ExportController {

    private final FirebaseService firebaseService;
    private final ReportService reportService;

    public ExportController(FirebaseService firebaseService, ReportService reportService) {
        this.firebaseService = firebaseService;
        this.reportService = reportService;
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<BaseResponse<ReportDto>>> getReportByJobId(@PathVariable String jobId) {
        log.info("Fetching report for job ID: {}", jobId);
        return firebaseService.getReportByProjectId(jobId)
                .map(reportDto -> ResponseEntity.ok(BaseResponse.success(reportDto, "Report retrieved successfully")))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Report not found for job ID: " + jobId)))
                .onErrorResume(e -> {
                    log.error("Error fetching report for job ID {}: {}", jobId, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.error("Failed to retrieve report: " + e.getMessage())));
                });
    }

    @GetMapping("/{reportId}/pdf")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<BaseResponse<byte[]>>> exportReportPdf(@PathVariable String reportId) {
        log.info("Generating PDF for report ID: {}", reportId);
        return reportService.getReportById(reportId)
                .flatMap(reportDto -> {
                    try {
                        byte[] pdfBytes = reportService.generateReportPdf(reportDto);
                        BaseResponse<byte[]> successResponse = BaseResponse.success(pdfBytes, "PDF report generated successfully");
                        return Mono.just(ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"report-" + reportId + ".pdf\"")
                                .header("Content-Type", "application/pdf")
                                .body(successResponse));
                    } catch (Exception e) {
                        log.error("Error generating PDF for report ID {}: {}", reportId, e.getMessage());
                        BaseResponse<byte[]> errorResponse = BaseResponse.error("Failed to generate PDF: " + e.getMessage());
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(errorResponse));
                    }
                })
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Report not found with ID: " + reportId)))
                .onErrorResume(e -> {
                    log.error("Error exporting PDF for report ID {}: {}", reportId, e.getMessage());
                    BaseResponse<byte[]> errorResponse = BaseResponse.error("Failed to export PDF: " + e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(errorResponse));
                });
    }
}

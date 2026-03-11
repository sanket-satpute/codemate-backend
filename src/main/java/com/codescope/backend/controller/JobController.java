package com.codescope.backend.controller;

import com.codescope.backend.analysisjob.dto.JobStatusResponseDTO;
import com.codescope.backend.analysisjob.exception.JobNotFoundException;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import com.codescope.backend.dto.BaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Standalone job status endpoint: GET /api/job/{jobId}
 * 
 * The frontend JobService calls GET /api/job/{jobId} to poll job status
 * without needing a projectId. This controller provides that endpoint
 * as a simpler alternative to the project-scoped AnalysisJobController.
 */
@RestController
@RequestMapping("/api/job")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class JobController {

    private final AnalysisJobService analysisJobService;

    /**
     * GET /api/job/{jobId} — Get the current status of an analysis job.
     * Used by frontend JobService.getJobOnce().
     */
    @GetMapping("/{jobId}")
    public Mono<ResponseEntity<BaseResponse<JobStatusResponseDTO>>> getJobStatus(
            @PathVariable String jobId) {
        return analysisJobService.getJobStatus(jobId)
                .map(dto -> ResponseEntity.ok(BaseResponse.success(dto, "Job status retrieved successfully")))
                .onErrorResume(JobNotFoundException.class, e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(BaseResponse.error(e.getMessage()))))
                .onErrorResume(e -> {
                    log.error("Error fetching job status for {}: {}", jobId, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.error("Failed to retrieve job status: " + e.getMessage())));
                });
    }
}

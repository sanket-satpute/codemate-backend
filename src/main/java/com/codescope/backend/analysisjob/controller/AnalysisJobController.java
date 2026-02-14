package com.codescope.backend.analysisjob.controller;

import com.codescope.backend.analysisjob.dto.AnalysisJobResponseDTO;
import com.codescope.backend.analysisjob.dto.CreateJobRequestDTO;
import com.codescope.backend.analysisjob.dto.JobStatusResponseDTO;
import com.codescope.backend.analysisjob.exception.JobNotFoundException;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import com.codescope.backend.dto.BaseResponse;
import com.codescope.backend.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/analysis")
@RequiredArgsConstructor
public class AnalysisJobController {

    private final AnalysisJobService analysisJobService;

    @PostMapping("/start")
    @PreAuthorize("@projectSecurity.isOwner(authentication, #projectId)")
    public ResponseEntity<BaseResponse<AnalysisJobResponseDTO>> startAnalysisJob(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateJobRequestDTO request,
            @AuthenticationPrincipal User user) {
        try {
            AnalysisJobResponseDTO job = analysisJobService.createJob(projectId.toString(), request.getJobType(), user.getUsername()).block();
            analysisJobService.runAnalysisAsync(job.getId().toString());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new BaseResponse<>(true, "Analysis job started successfully", job));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(false, "Failed to start analysis job: " + e.getMessage(), null));
        }
    }

    @GetMapping("/jobs")
    @PreAuthorize("@projectSecurity.isOwner(authentication, #projectId)")
    public ResponseEntity<BaseResponse<List<AnalysisJobResponseDTO>>> getProjectAnalysisJobs(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user) {
        try {
            List<AnalysisJobResponseDTO> jobs = analysisJobService.getProjectJobs(projectId.toString(), user.getUsername()).collectList().block();
            return ResponseEntity.ok(new BaseResponse<>(true, "Project analysis jobs retrieved successfully", jobs));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(false, "Failed to retrieve project analysis jobs: " + e.getMessage(), null));
        }
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("@projectSecurity.isOwner(authentication, #projectId)")
    public ResponseEntity<BaseResponse<JobStatusResponseDTO>> getAnalysisJobStatus(
            @PathVariable Long projectId,
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user) {
        try {
            JobStatusResponseDTO jobStatus = analysisJobService.getJobStatus(jobId.toString(), projectId.toString(), user.getUsername()).block();
            return ResponseEntity.ok(new BaseResponse<>(true, "Analysis job status retrieved successfully", jobStatus));
        } catch (JobNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(false, "Failed to retrieve analysis job status: " + e.getMessage(), null));
        }
    }
}

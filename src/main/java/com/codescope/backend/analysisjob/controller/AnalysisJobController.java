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
        public Mono<ResponseEntity<BaseResponse<AnalysisJobResponseDTO>>> startAnalysisJob(
                        @PathVariable String projectId,
                        @Valid @RequestBody CreateJobRequestDTO request,
                        @AuthenticationPrincipal User user) {
                return analysisJobService.createJob(projectId, request.getJobType(), resolveUsername(user), resolveUserId(user))
                                .map(job -> {
                                        return ResponseEntity.status(HttpStatus.CREATED)
                                                        .body(new BaseResponse<>(true,
                                                                        "Analysis job started successfully", job));
                                })
                                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(new BaseResponse<>(false,
                                                                "Failed to start analysis job: " + e.getMessage(),
                                                                null))));
        }

        @GetMapping("/jobs")
        @PreAuthorize("@projectSecurity.isOwner(authentication, #projectId)")
        public Mono<ResponseEntity<BaseResponse<List<AnalysisJobResponseDTO>>>> getProjectAnalysisJobs(
                        @PathVariable String projectId,
                        @AuthenticationPrincipal User user) {
                return analysisJobService.getProjectJobs(projectId, resolveUsername(user))
                                .collectList()
                                .map(jobs -> ResponseEntity
                                                .ok(new BaseResponse<>(true,
                                                                "Project analysis jobs retrieved successfully", jobs)))
                                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(new BaseResponse<>(false,
                                                                "Failed to retrieve project analysis jobs: "
                                                                                + e.getMessage(),
                                                                null))));
        }

        @GetMapping("/jobs/{jobId}")
        @PreAuthorize("@projectSecurity.isOwner(authentication, #projectId)")
        public Mono<ResponseEntity<BaseResponse<JobStatusResponseDTO>>> getAnalysisJobStatus(
                        @PathVariable String projectId,
                        @PathVariable String jobId,
                        @AuthenticationPrincipal User user) {
                return analysisJobService.getJobStatus(jobId, projectId, resolveUsername(user))
                                .map(jobStatus -> ResponseEntity
                                                .ok(new BaseResponse<>(true,
                                                                "Analysis job status retrieved successfully",
                                                                jobStatus)))
                                .onErrorResume(JobNotFoundException.class,
                                                e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                                                .body(new BaseResponse<>(false, e.getMessage(), null))))
                                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(new BaseResponse<>(false,
                                                                "Failed to retrieve analysis job status: "
                                                                                + e.getMessage(),
                                                                null))));
        }

        private String resolveUsername(User user) {
                return user != null ? user.getUsername() : "system";
        }

        private String resolveUserId(User user) {
                return user != null ? user.getId() : "system";
        }
}

package com.codescope.backend.controller;

import com.codescope.backend.analysisjob.controller.AnalysisJobController;
import com.codescope.backend.analysisjob.dto.AnalysisJobResponseDTO;
import com.codescope.backend.analysisjob.dto.JobStatusResponseDTO;
import com.codescope.backend.analysisjob.enums.JobStatus;
import com.codescope.backend.analysisjob.enums.JobType;
import com.codescope.backend.analysisjob.exception.JobNotFoundException;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import com.codescope.backend.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(AnalysisJobController.class)
@Import(TestSecurityConfig.class)
class AIControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AnalysisJobService jobService;

    @Test
    @DisplayName("Should start an analysis job")
    void startAnalysis_shouldReturnCreated() {
        String projectId = "projectId123";
        AnalysisJobResponseDTO mockJobResponse = AnalysisJobResponseDTO.builder()
                .jobId("jobId123")
                .projectId(projectId)
                .jobType(JobType.PROJECT_ANALYSIS)
                .status(JobStatus.PENDING)
                .build();

        when(jobService.createJob(eq(projectId), eq(JobType.PROJECT_ANALYSIS), anyString(), anyString()))
                .thenReturn(Mono.just(mockJobResponse));

        webTestClient.post().uri("/api/projects/{projectId}/analysis/start", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"jobType\":\"PROJECT_ANALYSIS\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.projectId").isEqualTo(projectId)
                .jsonPath("$.data.status").isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should return bad request when job type is missing")
    void startAnalysis_shouldReturnBadRequestWhenJobTypeMissing() {
        String projectId = "projectId123";

        webTestClient.post().uri("/api/projects/{projectId}/analysis/start", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Should return analysis job status")
    void getJobStatus_shouldReturnOk() {
        String jobId = "jobId123";
        String projectId = "projectId123";

        JobStatusResponseDTO mockStatus = JobStatusResponseDTO.builder()
                .jobId(jobId)
                .status(JobStatus.COMPLETED)
                .result("done")
                .build();

        when(jobService.getJobStatus(eq(jobId), eq(projectId), anyString())).thenReturn(Mono.just(mockStatus));

        webTestClient.get().uri("/api/projects/{projectId}/analysis/jobs/{jobId}", projectId, jobId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.jobId").isEqualTo(jobId)
                .jsonPath("$.data.status").isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Should return not found for missing job")
    void getJobStatus_shouldReturnNotFound() {
        String jobId = "missingJob";
        String projectId = "projectId123";

        when(jobService.getJobStatus(eq(jobId), eq(projectId), anyString()))
                .thenReturn(Mono.error(new JobNotFoundException("Job not found with ID: " + jobId)));

        webTestClient.get().uri("/api/projects/{projectId}/analysis/jobs/{jobId}", projectId, jobId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Job not found with ID: " + jobId);
    }
}

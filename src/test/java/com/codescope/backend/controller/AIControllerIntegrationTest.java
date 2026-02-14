package com.codescope.backend.controller;

import com.codescope.backend.analysisjob.controller.AnalysisJobController;
import com.codescope.backend.analysisjob.dto.AnalysisJobResponseDTO;
import com.codescope.backend.analysisjob.enums.JobStatus;
import com.codescope.backend.analysisjob.exception.JobNotFoundException;
import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import com.codescope.backend.dto.analysis.AnalysisRequestDTO;
import com.codescope.backend.dto.upload.FileDocumentDto;
import com.codescope.backend.dto.upload.FileUploadRequestDTO;
import com.codescope.backend.service.AIService;
import com.codescope.backend.service.FirebaseService;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.analysisjob.controller.AnalysisJobController;
import com.codescope.backend.analysisjob.dto.AnalysisJobResponseDTO;
import com.codescope.backend.analysisjob.dto.JobStatusResponseDTO;
import com.codescope.backend.analysisjob.enums.JobStatus;
import com.codescope.backend.analysisjob.exception.JobNotFoundException;
import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import com.codescope.backend.dto.analysis.AnalysisRequestDTO;
import com.codescope.backend.dto.upload.FileDocumentDto;
import com.codescope.backend.service.AIService;
import com.codescope.backend.service.FirebaseService;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.upload.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration-level test (controller + service interaction)
 * Verifies that the AI analysis API works correctly end-to-end.
 */
@WebFluxTest(AnalysisJobController.class)
class AIControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AnalysisJobService jobService;

    @MockBean
    private FirebaseService firebaseService;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private AIService aiService;

    @Test
    @DisplayName("Should return 200 OK with AI analysis result for code snippet")
    void analyzeCode_shouldReturnOk() {
        String projectId = "projectId123";
        String code = "public class Test {}";
        AnalysisRequestDTO request = new AnalysisRequestDTO();
        request.setProjectId(projectId);
        request.setCode(code);

        AnalysisJobResponseDTO mockJobResponse = AnalysisJobResponseDTO.builder()
                .id(1L) // Assuming ID is Long
                .projectId(projectId)
                .status(JobStatus.PENDING)
                .build();

        when(jobService.createJob(anyString(), any(), anyString())).thenReturn(Mono.just(mockJobResponse));

        webTestClient.post().uri("/api/projects/{projectId}/analysis/start", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("jobType", "CODE_ANALYSIS_SNIPPET", "code", code))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.projectId").isEqualTo(projectId)
                .jsonPath("$.data.status").isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should return 400 Bad Request when code is missing for snippet analysis")
    void analyzeCode_shouldReturnBadRequestForMissingCode() {
        String projectId = "projectId123";
        AnalysisRequestDTO request = new AnalysisRequestDTO();
        request.setProjectId(projectId);
        request.setCode("");

        webTestClient.post().uri("/api/projects/{projectId}/analysis/start", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("jobType", "CODE_ANALYSIS_SNIPPET", "code", ""))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Should return 200 OK with AI analysis result for uploaded files")
    void analyzeFiles_shouldReturnOk() {
        String projectId = "projectId456";
        String jobId = "jobId456";
        String fileContent = "public class AnotherTest {}";
        String fileName = "AnotherTest.java";

        AnalysisJobResponseDTO mockJobResponse = AnalysisJobResponseDTO.builder()
                .id(2L) // Assuming ID is Long
                .projectId(projectId)
                .status(JobStatus.PENDING)
                .build();

        when(fileStorageService.processAndStoreFile(any())).thenReturn(Mono.just(List.of(
                new FileDocumentDto(fileName, "text/plain", fileContent, "http://cloudinary.com/file1")
        )));
        when(firebaseService.addFileToProject(anyString(), any(ProjectFile.class), anyString(), anyString())).thenReturn(Mono.just("fileId123"));
        when(jobService.startNewJob(anyString(), anyString())).thenReturn(Mono.just(mockJobResponse));
        when(aiService.analyzeCode(anyString(), anyString())).thenReturn(Mono.just("AI Analysis Result"));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(fileContent.getBytes()))
                .filename(fileName)
                .contentType(MediaType.TEXT_PLAIN);
        builder.part("projectId", projectId);

        webTestClient.post().uri("/api/upload/file")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.projectId").isEqualTo(projectId)
                .jsonPath("$.data.jobId").isEqualTo(jobId)
                .jsonPath("$.data.status").isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should return 400 Bad Request when no files are uploaded for file analysis")
    void analyzeFiles_shouldReturnBadRequestForNoFiles() {
        String projectId = "projectId456";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("projectId", projectId);
        // No file part added

        webTestClient.post().uri("/api/upload/file")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Should return 200 OK with corrected code details")
    void correctCode_shouldReturnOk() {
        String originalContent = "public class MyClass { int x; }";
        String instruction = "Add a constructor.";
        String correctedCode = "public class MyClass { int x; public MyClass() { } }";
        String projectId = "projectId789";
        String fileId = "fileId789";
        String fileName = "MyClass.java";
        String fileType = "java";

        when(aiService.generateCorrectedCode(anyString(), anyString())).thenReturn(Mono.just(correctedCode));
        when(fileStorageService.processAndStoreFile(any())).thenReturn(Mono.just(List.of(
                new FileDocumentDto("corrected_" + fileName, "text/plain", correctedCode, "http://cloudinary.com/corrected_file")
        )));
        when(firebaseService.addFileToProject(anyString(), any(ProjectFile.class), anyString(), anyString())).thenReturn(Mono.just("newFileId"));
        when(jobService.startNewJob(anyString(), anyString())).thenReturn(Mono.just(AnalysisJobResponseDTO.builder().jobId("jobIdCorrected").build()));


        webTestClient.post().uri("/api/correct")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "originalContent", originalContent,
                        "instruction", instruction,
                        "projectId", projectId,
                        "fileId", fileId,
                        "fileName", fileName,
                        "fileType", fileType
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.message").isEqualTo("Code corrected and stored successfully.")
                .jsonPath("$.data.correctedFileUrl").isEqualTo("http://cloudinary.com/corrected_file")
                .jsonPath("$.data.correctedFileName").isEqualTo("corrected_MyClass.java");
    }

    @Test
    @DisplayName("Should return 400 Bad Request when original content is missing for correction")
    void correctCode_shouldReturnBadRequestForMissingOriginalContent() {
        webTestClient.post().uri("/api/correct")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("instruction", "Add a constructor."))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Original code content is required for correction.");
    }

    @Test
    @DisplayName("Should return 400 Bad Request when instruction is missing for correction")
    void correctCode_shouldReturnBadRequestForMissingInstruction() {
        webTestClient.post().uri("/api/correct")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("originalContent", "public class MyClass { int x; }"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Correction instruction is required.");
    }

    @Test
    @DisplayName("Should return 200 OK with job status")
    void getJobStatus_shouldReturnOk() {
        String jobId = "jobId123";
        String projectId = "projectId123";

        JobStatusResponseDTO mockJobStatusResponse = JobStatusResponseDTO.builder()
                .jobId(jobId)
                .status(JobStatus.COMPLETED)
                .result("Analysis completed successfully")
                .build();

        when(jobService.getJobStatus(jobId, projectId, anyString())).thenReturn(Mono.just(mockJobStatusResponse));

        webTestClient.get().uri("/api/projects/{projectId}/analysis/jobs/{jobId}", projectId, jobId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.jobId").isEqualTo(jobId)
                .jsonPath("$.data.status").isEqualTo("COMPLETED")
                .jsonPath("$.data.result").isEqualTo("Analysis completed successfully");
    }

    @Test
    @DisplayName("Should return 404 Not Found when job status is not found")
    void getJobStatus_shouldReturnNotFound() {
        String jobId = "nonExistentJob";
        String projectId = "projectId123";

        when(jobService.getJobStatus(jobId, projectId, anyString())).thenReturn(Mono.error(new JobNotFoundException("Job not found with ID: " + jobId)));

        webTestClient.get().uri("/api/projects/{projectId}/analysis/jobs/{jobId}", projectId, jobId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Job not found with ID: " + jobId);
    }
}

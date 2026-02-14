package com.codescope.backend.controller;

import com.codescope.backend.analysisjob.dto.AnalysisJobResponseDTO;
import com.codescope.backend.analysisjob.enums.JobStatus;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import com.codescope.backend.dto.upload.FileDocumentDto;
import com.codescope.backend.dto.upload.FileUploadResponseDto;
import com.codescope.backend.service.AIService;
import com.codescope.backend.service.FirebaseService;
import com.codescope.backend.upload.controller.UploadController;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.upload.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(UploadController.class)
class UploadControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private FirebaseService firebaseService;

    @MockBean
    private AnalysisJobService analysisJobService;

    @MockBean
    private AIService aiService;

    @Test
    @WithMockUser
    @DisplayName("Should successfully upload a file and return its details")
    void uploadFile_success() {
        // 5MB file for testing
        byte[] fileContent = new byte[5 * 1024 * 1024];
        String filename = "testfile.txt";
        String fileType = MediaType.TEXT_PLAIN_VALUE;
        String downloadUrl = "http://localhost:8080/uploads/" + filename;
        String projectId = "testProjectId";
        String jobId = "testJobId";

        FileDocumentDto mockFileDocument = new FileDocumentDto(filename, fileType, "content", downloadUrl);
        AnalysisJobResponseDTO mockJobResponse = AnalysisJobResponseDTO.builder()
                .jobId(jobId)
                .status(JobStatus.PENDING)
                .projectId(projectId)
                .build();

        when(fileStorageService.processAndStoreFile(any()))
                .thenReturn(Mono.just(List.of(mockFileDocument)));
        when(firebaseService.addFileToProject(anyString(), any(ProjectFile.class), anyString(), anyString())).thenReturn(Mono.just("mockFileId"));
        when(analysisJobService.startNewJob(anyString(), anyString())).thenReturn(Mono.just(mockJobResponse));
        when(aiService.analyzeCode(anyString(), anyString())).thenReturn(Mono.just("AI analysis result"));


        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ByteArrayResource(fileContent))
                .filename(filename)
                .contentType(MediaType.TEXT_PLAIN);
        bodyBuilder.part("projectId", projectId);

        webTestClient.post()
                .uri("/api/upload/file")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.projectId").isEqualTo(projectId)
                .jsonPath("$.data.jobId").isEqualTo(jobId)
                .jsonPath("$.data.status").isEqualTo("PENDING")
                .jsonPath("$.data.uploadedFilesCount").isEqualTo(1);
    }

    @Test
    @WithMockUser
    @DisplayName("Should return 400 Bad Request for no file attached")
    void uploadFile_noFileAttached() {
        String projectId = "testProjectId";
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("projectId", projectId);

        webTestClient.post()
                .uri("/api/upload/file")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser
    @DisplayName("Should handle file processing errors and return 500 Internal Server Error")
    void uploadFile_processingError() {
        String filename = "errorfile.txt";
        byte[] fileContent = new byte[100]; // Small file
        String projectId = "testProjectId";

        when(fileStorageService.processAndStoreFile(any()))
                .thenReturn(Mono.error(new RuntimeException("Simulated file processing error")));

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ByteArrayResource(fileContent))
                .filename(filename)
                .contentType(MediaType.TEXT_PLAIN);
        bodyBuilder.part("projectId", projectId);

        webTestClient.post()
                .uri("/api/upload/file")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

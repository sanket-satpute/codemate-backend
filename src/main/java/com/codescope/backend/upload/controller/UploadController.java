package com.codescope.backend.upload.controller;

import com.codescope.backend.dto.BaseResponse;
import com.codescope.backend.project.dto.ProjectCreateRequest;
import com.codescope.backend.project.dto.ProjectResponse;
import com.codescope.backend.analysisjob.dto.AnalysisJobResponseDTO;
import com.codescope.backend.dto.upload.FileDocumentDto;
import com.codescope.backend.dto.upload.FileUploadResponseDto;
import com.codescope.backend.exception.InvalidInputException;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.project.model.Project;
import com.codescope.backend.project.model.ProjectStatus;
import com.codescope.backend.service.AIService;
import com.codescope.backend.upload.service.FileStorageService;
import com.codescope.backend.service.FirebaseService;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
@Slf4j
public class UploadController {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
            "java", "py", "js", "ts", "html", "css", "json", "xml", "yml", "yaml", "md", "txt",
            "zip", "pdf", "docx" // Supported document types and zip
    );

    private final FirebaseService firebaseService;
    private final AnalysisJobService analysisJobService;
    private final FileStorageService fileStorageService;
    private final AIService aiService;

    public UploadController(FirebaseService firebaseService, AnalysisJobService analysisJobService, FileStorageService fileStorageService, AIService aiService) {
        this.firebaseService = firebaseService;
        this.analysisJobService = analysisJobService;
        this.fileStorageService = fileStorageService;
        this.aiService = aiService;
    }

    @PostMapping("/project")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<BaseResponse<ProjectResponse>>> uploadProject(@Valid @RequestBody ProjectCreateRequest request, Authentication authentication) {
        log.info("Received request to upload project: {}", request.getName());

        Project project = Project.builder()
                .projectId(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(ProjectStatus.ACTIVE)
                .ownerId(authentication.getName()) // Set ownerId from authenticated user
                .files(new ArrayList<>())
                .build();

        return firebaseService.saveProject(project)
                .flatMap(savedProjectId -> {
                    String summary = "Project named " + project.getName() +
                            " with " + project.getFiles().size() + " files.";

                    String aiMode = aiService.isLiveMode() ? "🧠 LIVE AI Mode" : "⚙️ MOCK Mode";
                    log.info("Current AI Mode: {}", aiMode);

                    // Create ProjectResponse from the saved project
                    ProjectResponse projectResponse = ProjectResponse.builder()
                            .id(project.getId()) // Assuming ID is set after saving
                            .name(project.getName())
                            .description(project.getDescription())
                            .createdAt(project.getCreatedAt())
                            .updatedAt(project.getUpdatedAt())
                            .status(project.getStatus())
                            .lastAnalysisJobId(project.getLastAnalysisJobId())
                            .build();

                    return analysisJobService.startNewJob(savedProjectId, "INITIAL_ANALYSIS")
                            .thenReturn(ResponseEntity.ok(BaseResponse.success(projectResponse, "Project uploaded and analysis job started")));
                })
                .onErrorResume(e -> {
                    log.error("Error uploading project: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.error("Failed to upload project: " + e.getMessage())));
                });
    }

    @PostMapping(value = "/file", consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<BaseResponse<FileUploadResponseDto>>> uploadFile(@RequestParam("file") MultipartFile file,
                                                                               @RequestParam("projectId") String projectId) {
        log.info("Received request to upload file for project: {}", projectId);
        if (file == null || file.isEmpty()) {
            return Mono.error(new InvalidInputException("No file uploaded."));
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return Mono.error(new InvalidInputException("File size exceeds the maximum limit of " + (MAX_FILE_SIZE_BYTES / (1024 * 1024)) + "MB."));
        }

        String filename = file.getOriginalFilename();
        String fileExtension = "";
        if (filename != null && filename.contains(".")) {
            fileExtension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        }

        if (!ALLOWED_FILE_EXTENSIONS.contains(fileExtension)) {
            return Mono.error(new InvalidInputException("Unsupported file type. Allowed types are: " + String.join(", ", ALLOWED_FILE_EXTENSIONS) + "."));
        }

        return fileStorageService.processAndStoreFile(file)
                .flatMap(processedFiles -> {
                    if (processedFiles.isEmpty()) {
                        return Mono.error(new InvalidInputException("No supported files found or extracted from upload"));
                    }

                    List<String> cloudinaryUrls = processedFiles.stream()
                            .map(FileDocumentDto::getUrl)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    String concatenatedFileContent = processedFiles.stream()
                            .map(FileDocumentDto::getContent)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining("\n\n--- FILE SEPARATOR ---\n\n"));

                    ProjectFile projectFile = convertToProjectFile(processedFiles.get(0));
                    return firebaseService.addFileToProject(projectId, projectFile, "AI_MODEL_UNKNOWN", "File uploaded")
                            .flatMap(v -> analysisJobService.startNewJob(projectId, "FILE_UPLOAD"))
                            .flatMap(jobDto ->
                                    aiService.analyzeCode(jobDto.getJobId(), concatenatedFileContent)
                                            .doOnSuccess(result -> log.info("AI analysis initiated for job {}. Result: {}", jobDto.getJobId(), result))
                                            .doOnError(e -> log.error("Error initiating AI analysis for job {}: {}", jobDto.getJobId(), e.getMessage()))
                                            .thenReturn(ResponseEntity.ok(BaseResponse.success(new FileUploadResponseDto(
                                                    projectId,
                                                    jobDto.getJobId(),
                                                    processedFiles.size(),
                                                    jobDto.getStatus().toString(),
                                                    cloudinaryUrls,
                                                    "File uploaded and analysis initiated"
                                            ), "File uploaded and analysis initiated")))
                            )
                            .onErrorResume(e -> {
                                log.error("Error uploading file or starting analysis: {}", e.getMessage());
                                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(BaseResponse.error("Failed to upload file or start analysis: " + e.getMessage())));
                            });
                })
                .onErrorResume(e -> {
                    log.error("Error processing file: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.error("Failed to process file: " + e.getMessage())));
                });
    }

    private ProjectFile convertToProjectFile(FileDocumentDto dto) {
        ProjectFile projectFile = new ProjectFile();
        projectFile.setFilename(dto.getFileName());
        projectFile.setFileType(dto.getFileType());
        return projectFile;
    }
}

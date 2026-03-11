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
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.service.AIService;
import com.codescope.backend.utils.MultipartFileUtil;
import com.codescope.backend.upload.service.FileStorageService;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
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
@Slf4j
public class UploadController {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
            "java", "py", "js", "ts", "html", "css", "json", "xml", "yml", "yaml", "md", "txt",
            "zip", "pdf", "docx", "doc", "xlsx", "xls", "csv",
            "sql", "sh", "bat", "properties", "jsx", "tsx", "scss", "less",
            "kt", "swift", "go", "rs", "c", "cpp", "h", "cs", "rb", "php", "r", "scala",
            "gradle", "toml", "ini", "cfg", "env", "log"
    );

    private final ProjectRepository projectRepository;
    private final AnalysisJobService analysisJobService;
    private final FileStorageService fileStorageService;
    private final AIService aiService;

    public UploadController(ProjectRepository projectRepository, AnalysisJobService analysisJobService, FileStorageService fileStorageService, AIService aiService) {
        this.projectRepository = projectRepository;
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

        return projectRepository.save(project)
                .flatMap(savedProject -> {
                    String canonicalProjectId = savedProject.getProjectId();
                    String summary = "Project named " + savedProject.getName() +
                            " with " + savedProject.getFiles().size() + " files.";

                    String aiMode = aiService.isLiveMode() ? "🧠 LIVE AI Mode" : "⚙️ MOCK Mode";
                    log.info("Current AI Mode: {}", aiMode);

                    // Create ProjectResponse from the saved project
                    ProjectResponse projectResponse = ProjectResponse.builder()
                            .id(canonicalProjectId)
                            .name(savedProject.getName())
                            .description(savedProject.getDescription())
                            .createdAt(savedProject.getCreatedAt())
                            .updatedAt(savedProject.getUpdatedAt())
                            .status(savedProject.getStatus())
                            .lastAnalysisJobId(savedProject.getLastAnalysisJobId())
                            .build();

                    return analysisJobService.startNewJob(canonicalProjectId, "INITIAL_ANALYSIS")
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
    public Mono<ResponseEntity<BaseResponse<FileUploadResponseDto>>> uploadFile(@RequestPart("file") Mono<FilePart> filePartMono,
                                                                               @RequestPart("projectId") String projectId) {
        log.info("Received request to upload file for project: {}", projectId);
        return filePartMono
                .switchIfEmpty(Mono.error(new InvalidInputException("No file uploaded.")))
                .flatMap(MultipartFileUtil::toMultipartFile)
                .flatMap(file -> validateAndProcessFile(file, projectId));
    }

    private Mono<ResponseEntity<BaseResponse<FileUploadResponseDto>>> validateAndProcessFile(MultipartFile file, String projectId) {
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

                    ProjectFile projectFile = convertToProjectFile(processedFiles.get(0), projectId);
                    return projectRepository.findByProjectId(projectId)
                            .switchIfEmpty(projectRepository.findById(projectId))
                            .switchIfEmpty(Mono.error(new InvalidInputException("Project not found with ID: " + projectId)))
                            .flatMap(project -> {
                                List<ProjectFile> updatedFiles = project.getFiles() == null
                                        ? new ArrayList<>()
                                        : new ArrayList<>(project.getFiles());
                                updatedFiles.add(projectFile);
                                project.setFiles(updatedFiles);
                                project.setLastCorrectedAt(LocalDateTime.now());
                                project.setLastCorrectedByModel("AI_MODEL_UNKNOWN");
                                project.setLastCorrectionSummary("File uploaded");
                                return projectRepository.save(project);
                            })
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
                    HttpStatus status = e instanceof InvalidInputException ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
                    return Mono.just(ResponseEntity.status(status)
                            .body(BaseResponse.error("Failed to process file: " + e.getMessage())));
                });
    }

    private ProjectFile convertToProjectFile(FileDocumentDto dto, String projectId) {
        ProjectFile projectFile = new ProjectFile();
        projectFile.setProjectId(projectId);
        projectFile.setFilename(dto.getFileName());
        projectFile.setFileType(dto.getFileType());
        projectFile.setFilepath(dto.getUrl());
        projectFile.setUploadedAt(LocalDateTime.now());
        return projectFile;
    }
}

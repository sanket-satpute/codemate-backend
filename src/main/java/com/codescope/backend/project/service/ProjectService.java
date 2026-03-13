package com.codescope.backend.project.service;

import com.codescope.backend.project.dto.ProjectCreateRequest;
import com.codescope.backend.project.dto.ProjectResponse;
import com.codescope.backend.project.dto.ProjectUpdateRequest;
import com.codescope.backend.project.exception.ProjectNotFoundException;
import com.codescope.backend.project.model.Project;
import com.codescope.backend.project.model.ProjectStatus;
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.service.CloudinaryService;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.upload.repository.ProjectFileRepository;
import com.codescope.backend.utils.MultipartFileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final CloudinaryService cloudinaryService;

    /**
     * Creates a project (name + description only, no files).
     */
    public Mono<ProjectResponse> createProject(ProjectCreateRequest request, String ownerId) {
        Project project = Project.builder()
                .projectId(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(ownerId)
                .status(ProjectStatus.ACTIVE)
                .build();
        return projectRepository.save(project)
                .map(this::mapToProjectResponse);
    }

    /**
     * Creates a project with files: uploads each file to Cloudinary, saves ProjectFile docs,
     * attaches them to the project, returns the full response.
     */
    public Mono<ProjectResponse> createProjectWithFiles(String name, String description,
                                                         List<FilePart> files, String ownerId) {
        String projectId = UUID.randomUUID().toString();
        String cloudinaryFolder = "codescope/projects/" + projectId;

        Project project = Project.builder()
                .projectId(projectId)
                .name(name)
                .description(description)
                .ownerId(ownerId)
                .status(ProjectStatus.ACTIVE)
                .files(new ArrayList<>())
                .build();

        return projectRepository.save(project)
                .flatMap(savedProject -> {
                    if (files == null || files.isEmpty()) {
                        return Mono.just(savedProject);
                    }

                    // Upload each file (extracting zips) to Cloudinary, save ProjectFile records
                    List<Flux<ProjectFile>> uploadFluxes = files.stream()
                            .filter(Objects::nonNull)
                            .map(filePart -> processFilePart(filePart, projectId, cloudinaryFolder))
                            .collect(Collectors.toList());

                    return Flux.merge(uploadFluxes)
                            .collectList()
                            .flatMap(savedFiles -> {
                                savedProject.setFiles(savedFiles);
                                return projectRepository.save(savedProject);
                            });
                })
                .map(this::mapToProjectResponse);
    }

    public Flux<ProjectResponse> getUserProjects(String ownerId) {
        return projectRepository.findByOwnerId(ownerId)
                .map(this::mapToProjectResponse);
    }

    public Mono<ProjectResponse> getProjectById(String projectId, String ownerId) {
        return findOwnedProject(projectId, ownerId)
                .map(this::mapToProjectResponse);
    }

    public Mono<ProjectResponse> updateProject(String projectId, ProjectUpdateRequest request, String ownerId) {
        return findOwnedProject(projectId, ownerId)
                .flatMap(project -> {
                    if (request.getName() != null) {
                        project.setName(request.getName());
                    }
                    if (request.getDescription() != null) {
                        project.setDescription(request.getDescription());
                    }
                    if (request.getStatus() != null) {
                        project.setStatus(request.getStatus());
                    }
                    if (request.getLastAnalysisJobId() != null) {
                        project.setLastAnalysisJobId(request.getLastAnalysisJobId());
                    }
                    return projectRepository.save(project);
                })
                .map(this::mapToProjectResponse);
    }

    public Mono<Void> deleteProject(String projectId, String ownerId) {
        return findOwnedProject(projectId, ownerId)
                .flatMap(project -> {
                    // Delete associated Cloudinary files
                    List<ProjectFile> files = project.getFiles();
                    if (files != null && !files.isEmpty()) {
                        List<Mono<Void>> deletions = files.stream()
                                .filter(f -> f.getCloudinaryPublicId() != null)
                                .map(f -> cloudinaryService.deleteFile(f.getCloudinaryPublicId()))
                                .collect(Collectors.toList());
                        return Flux.merge(deletions).then(projectFileRepository.deleteAll(files))
                                .then(projectRepository.delete(project));
                    }
                    return projectRepository.delete(project);
                });
    }

    // ─── File Management ───────────────────────────────────────────────────

    /**
     * Adds files to an existing project: uploads to Cloudinary, saves ProjectFile docs,
     * and updates the project's embedded files list.
     */
    public Mono<ProjectResponse> addFilesToProject(String projectId, List<FilePart> files, String ownerId) {
        return findOwnedProject(projectId, ownerId)
                .flatMap(project -> {
                    String cloudinaryFolder = "codescope/projects/" + (project.getProjectId() != null ? project.getProjectId() : project.getId());

                    String actualProjectId = project.getProjectId() != null ? project.getProjectId() : project.getId();

                    List<Flux<ProjectFile>> uploadFluxes = files.stream()
                            .filter(Objects::nonNull)
                            .map(filePart -> processFilePart(filePart, actualProjectId, cloudinaryFolder))
                            .collect(Collectors.toList());

                    return Flux.merge(uploadFluxes)
                            .collectList()
                            .flatMap(savedFiles -> {
                                List<ProjectFile> existing = project.getFiles() != null
                                        ? new ArrayList<>(project.getFiles()) : new ArrayList<>();
                                existing.addAll(savedFiles);
                                project.setFiles(existing);
                                return projectRepository.save(project);
                            });
                })
                .map(this::mapToProjectResponse);
    }

    /**
     * Replaces a specific file in a project: deletes old Cloudinary resource, uploads new one,
     * updates the ProjectFile record and the project's embedded list.
     */
    public Mono<ProjectResponse> replaceFileInProject(String projectId, String fileId, FilePart newFile, String ownerId) {
        return findOwnedProject(projectId, ownerId)
                .flatMap(project -> {
                    List<ProjectFile> files = project.getFiles();
                    if (files == null) {
                        return Mono.error(new ProjectNotFoundException("File not found in project"));
                    }
                    ProjectFile existingFile = files.stream()
                            .filter(f -> fileId.equals(f.getId()))
                            .findFirst()
                            .orElse(null);
                    if (existingFile == null) {
                        return Mono.error(new ProjectNotFoundException("File not found in project"));
                    }

                    String cloudinaryFolder = "codescope/projects/" + (project.getProjectId() != null ? project.getProjectId() : project.getId());

                    // Delete old Cloudinary resource, then upload new
                    Mono<Void> deleteOld = existingFile.getCloudinaryPublicId() != null
                            ? cloudinaryService.deleteFile(existingFile.getCloudinaryPublicId())
                            : Mono.empty();

                    String originalFilename = newFile.filename();
                    String extension = getFileExtension(originalFilename);
                    String contentType = newFile.headers().getContentType() != null
                            ? newFile.headers().getContentType().toString() : "application/octet-stream";

                    return deleteOld
                            .then(cloudinaryService.uploadFile(newFile, cloudinaryFolder))
                            .flatMap(result -> {
                                long fileSize = result.get("bytes") != null
                                        ? ((Number) result.get("bytes")).longValue() : 0L;

                                existingFile.setFilename(originalFilename);
                                existingFile.setFileSize(fileSize);
                                existingFile.setFileType(contentType);
                                existingFile.setFileExtension(extension);
                                existingFile.setCloudinaryUrl((String) result.get("secure_url"));
                                existingFile.setCloudinaryPublicId((String) result.get("public_id"));
                                existingFile.setUploadedAt(LocalDateTime.now());

                                return projectFileRepository.save(existingFile);
                            })
                            .flatMap(updatedFile -> {
                                // Update embedded list
                                List<ProjectFile> updatedFiles = project.getFiles().stream()
                                        .map(f -> fileId.equals(f.getId()) ? updatedFile : f)
                                        .collect(Collectors.toList());
                                project.setFiles(updatedFiles);
                                return projectRepository.save(project);
                            });
                })
                .map(this::mapToProjectResponse);
    }

    /**
     * Deletes a specific file from a project: removes from Cloudinary, deletes the ProjectFile doc,
     * and updates the project's embedded files list.
     */
    public Mono<ProjectResponse> deleteFileFromProject(String projectId, String fileId, String ownerId) {
        return findOwnedProject(projectId, ownerId)
                .flatMap(project -> {
                    List<ProjectFile> files = project.getFiles();
                    if (files == null) {
                        return Mono.error(new ProjectNotFoundException("File not found in project"));
                    }
                    ProjectFile fileToDelete = files.stream()
                            .filter(f -> fileId.equals(f.getId()))
                            .findFirst()
                            .orElse(null);
                    if (fileToDelete == null) {
                        return Mono.error(new ProjectNotFoundException("File not found in project"));
                    }

                    // Delete from Cloudinary
                    Mono<Void> deleteFromCloud = fileToDelete.getCloudinaryPublicId() != null
                            ? cloudinaryService.deleteFile(fileToDelete.getCloudinaryPublicId())
                            : Mono.empty();

                    return deleteFromCloud
                            .then(projectFileRepository.delete(fileToDelete))
                            .then(Mono.defer(() -> {
                                List<ProjectFile> updatedFiles = project.getFiles().stream()
                                        .filter(f -> !fileId.equals(f.getId()))
                                        .collect(Collectors.toList());
                                project.setFiles(updatedFiles);
                                return projectRepository.save(project);
                            }));
                })
                .map(this::mapToProjectResponse);
    }

    private Mono<Project> findOwnedProject(String projectId, String ownerId) {
        return projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .switchIfEmpty(projectRepository.findByProjectId(projectId)
                        .filter(project -> ownerId.equals(project.getOwnerId())))
                .switchIfEmpty(Mono.error(new ProjectNotFoundException("Project not found or unauthorized")));
    }

    private ProjectResponse mapToProjectResponse(Project project) {
        List<ProjectFile> files = project.getFiles() != null ? project.getFiles() : Collections.emptyList();

        // Build file type breakdown: extension → count
        Map<String, Integer> fileTypeBreakdown = new HashMap<>();
        long totalFileSize = 0;
        List<ProjectResponse.FileInfo> fileInfoList = new ArrayList<>();

        for (ProjectFile pf : files) {
            String ext = pf.getFileExtension() != null ? pf.getFileExtension() : getFileExtension(pf.getFilename());
            if (!ext.isEmpty()) {
                fileTypeBreakdown.merge(ext, 1, Integer::sum);
            }
            if (pf.getFileSize() != null) {
                totalFileSize += pf.getFileSize();
            }
            fileInfoList.add(ProjectResponse.FileInfo.builder()
                    .id(pf.getId())
                    .filename(pf.getFilename())
                    .fileSize(pf.getFileSize() != null ? pf.getFileSize() : 0)
                    .fileType(pf.getFileType())
                    .fileExtension(ext)
                    .cloudinaryUrl(pf.getCloudinaryUrl())
                    .uploadedAt(pf.getUploadedAt())
                    .build());
        }

        return ProjectResponse.builder()
                .id(project.getProjectId() != null ? project.getProjectId() : project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .status(project.getStatus())
                .lastAnalysisJobId(project.getLastAnalysisJobId())
                .totalFiles(files.size())
                .totalFileSize(totalFileSize)
                .fileTypeBreakdown(fileTypeBreakdown)
                .files(fileInfoList)
                .build();
    }

    // ─── Zip Extraction Support ──────────────────────────────────────────

    private Flux<ProjectFile> processFilePart(FilePart filePart, String projectId, String cloudinaryFolder) {
        String originalFilename = filePart.filename();
        String extension = getFileExtension(originalFilename);
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString() : "application/octet-stream";

        if (isZipFile(contentType, extension)) {
            log.info("Detected zip upload '{}', extracting entries for project {}", originalFilename, projectId);
            return DataBufferUtils.join(filePart.content())
                    .flatMapMany(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        return extractAndUploadZipEntries(bytes, originalFilename, projectId, cloudinaryFolder);
                    });
        }

        // Regular (non-zip) file
        return cloudinaryService.uploadFile(filePart, cloudinaryFolder)
                .flatMap(result -> {
                    long fileSize = result.get("bytes") != null
                            ? ((Number) result.get("bytes")).longValue() : 0L;
                    ProjectFile pf = ProjectFile.builder()
                            .projectId(projectId)
                            .filename(originalFilename)
                            .fileSize(fileSize)
                            .fileType(contentType)
                            .fileExtension(extension)
                            .cloudinaryUrl((String) result.get("secure_url"))
                            .cloudinaryPublicId((String) result.get("public_id"))
                            .uploadedAt(LocalDateTime.now())
                            .build();
                    return projectFileRepository.save(pf);
                })
                .flux();
    }

    private boolean isZipFile(String contentType, String fileExtension) {
        return "application/zip".equals(contentType)
                || "application/x-zip-compressed".equals(contentType)
                || "zip".equalsIgnoreCase(fileExtension);
    }

    private Flux<ProjectFile> extractAndUploadZipEntries(byte[] zipBytes, String zipFilename,
                                                         String projectId, String cloudinaryFolder) {
        String zipFolderName = StringUtils.stripFilenameExtension(StringUtils.getFilename(zipFilename));
        String zipFolder = cloudinaryFolder + "/"
                + (StringUtils.hasText(zipFolderName)
                        ? zipFolderName.replaceAll("[\\\\/]+", "-").trim()
                        : "archive");

        return Mono.fromCallable(() -> readZipEntries(zipBytes, zipFilename))
                .flatMapMany(Flux::fromIterable)
                .concatMap(entry -> cloudinaryService.uploadBytes(entry.bytes(), entry.relativePath(), zipFolder)
                        .flatMap(result -> {
                            long fileSize = result.get("bytes") != null
                                    ? ((Number) result.get("bytes")).longValue() : 0L;
                            ProjectFile pf = ProjectFile.builder()
                                    .projectId(projectId)
                                    .filename(entry.relativePath())
                                    .fileSize(fileSize)
                                    .fileType(entry.fileType())
                                    .fileExtension(entry.fileExtension())
                                    .cloudinaryUrl((String) result.get("secure_url"))
                                    .cloudinaryPublicId((String) result.get("public_id"))
                                    .uploadedAt(LocalDateTime.now())
                                    .build();
                            return projectFileRepository.save(pf);
                        }));
    }

    private List<ZipUploadEntry> readZipEntries(byte[] zipBytes, String zipFilename) throws IOException {
        List<ZipUploadEntry> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (!zipEntry.isDirectory()) {
                    String relativePath = normalizeZipEntryPath(zipEntry.getName());
                    if (relativePath == null) {
                        log.warn("Skipped unsafe zip entry {} from zip {}", zipEntry.getName(), zipFilename);
                        zis.closeEntry();
                        continue;
                    }
                    String ext = getFileExtension(relativePath);
                    if (MultipartFileUtil.ALLOWED_FILE_EXTENSIONS.contains(ext)) {
                        byte[] contentBytes = zis.readAllBytes();
                        entries.add(new ZipUploadEntry(relativePath, detectContentType(ext), ext, contentBytes));
                        log.info("Prepared file {} from zip {}", relativePath, zipFilename);
                    } else {
                        log.debug("Skipped unsupported file {} from zip {}", relativePath, zipFilename);
                    }
                }
                zis.closeEntry();
            }
        }
        return entries;
    }

    private String normalizeZipEntryPath(String entryName) {
        String normalized = StringUtils.cleanPath(entryName).replace('\\', '/');
        if (!StringUtils.hasText(normalized)
                || normalized.startsWith("/")
                || normalized.startsWith("../")
                || normalized.contains("/../")
                || normalized.equals("..")) {
            return null;
        }
        return normalized;
    }

    private String detectContentType(String extension) {
        return switch (extension) {
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "csv" -> "text/csv";
            case "yml", "yaml", "txt", "md", "java", "py", "ts", "tsx", "jsx", "properties", "sql" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private record ZipUploadEntry(String relativePath, String fileType, String fileExtension, byte[] bytes) {
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}

package com.codescope.backend.upload.service;

import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.upload.dto.FileUploadResponseDTO;
import com.codescope.backend.upload.exception.FileNotFoundException;
import com.codescope.backend.upload.exception.FileUploadException;
import com.codescope.backend.upload.exception.InvalidFileTypeException;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.upload.repository.ProjectFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.codec.multipart.FilePart; // Not used but might be from previous changes
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ServerWebExchange; // Not used but might be from previous changes
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.codescope.backend.dto.upload.FileDocumentDto;
import com.codescope.backend.utils.MultipartFileUtil;
import lombok.extern.slf4j.Slf4j;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository; // To validate projectId

    private final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private final List<String> ALLOWED_FILE_TYPES = List.of(
            "text/plain", "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "text/csv",
            "image/jpeg", "image/png", "application/zip", "application/x-zip-compressed",
            "text/x-java-source", "text/html", "text/css", "application/javascript",
            "application/json", "application/xml", "text/xml");

    public Mono<FileUploadResponseDTO> saveProjectFile(MultipartFile file, String projectId) {
        return projectRepository.findByProjectId(projectId)
                .switchIfEmpty(projectRepository.findById(projectId))
                .switchIfEmpty(Mono.error(new FileNotFoundException("Project not found with ID: " + projectId)))
                .flatMap(project -> {
                    if (file.getSize() > MAX_FILE_SIZE) {
                        return Mono.error(new FileUploadException(
                                "File size exceeds the limit of " + MAX_FILE_SIZE / (1024 * 1024) + "MB"));
                    }

                    String contentType = file.getContentType();
                    if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType)) {
                        return Mono.error(new InvalidFileTypeException("Unsupported file type: " + contentType));
                    }

                    String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
                    String uniqueFilename = UUID.randomUUID().toString() + "_" + originalFilename;

                    return Mono.fromCallable(() -> {
                        Path projectUploadDir = Paths.get(uploadDir + "/" + projectId).toAbsolutePath().normalize();
                        Files.createDirectories(projectUploadDir);
                        Path targetLocation = projectUploadDir.resolve(uniqueFilename);
                        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
                        return targetLocation.toString();
                    }).flatMap(filePath -> {
                        ProjectFile projectFile = ProjectFile.builder()
                                .projectId(projectId)
                                .filename(originalFilename)
                                .filepath(filePath)
                                .fileSize(file.getSize())
                                .fileType(contentType)
                                .uploadedAt(LocalDateTime.now())
                                .build();
                        return projectFileRepository.save(projectFile);
                    }).map(savedFile -> {
                        // The mapToFileUploadResponseDTO method needs the full ProjectFile, not just
                        // parts.
                        return mapToFileUploadResponseDTO(savedFile);
                    }).onErrorResume(IOException.class, ex -> Mono.error(new FileUploadException(
                            "Could not store file " + originalFilename + ". Please try again!", ex)));
                });
    }

    public Mono<List<FileDocumentDto>> processAndStoreFile(MultipartFile file) {
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String fileExtension = MultipartFileUtil.getFileExtension(originalFilename);
        String fileType = file.getContentType();

        return Mono.fromCallable(() -> {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            if (fileType != null && (fileType.equals("application/zip")
                    || fileType.equals("application/x-zip-compressed") || fileExtension.equals("zip"))) {
                log.info("Processing zip file: {}", originalFilename);
                return MultipartFileUtil.extractZipContent(file.getInputStream(), uploadPath, originalFilename);
            }

            String uniqueFilename = UUID.randomUUID() + "_" + originalFilename;
            Path targetLocation = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String content = MultipartFileUtil.extractContent(file.getInputStream(), fileExtension);
            String url = UriComponentsBuilder.fromPath("/uploads/")
                    .path(uniqueFilename)
                    .toUriString();

            return List.of(new FileDocumentDto(originalFilename, fileType, content, url));
        }).onErrorResume(IOException.class, e -> {
            log.error("Failed to process and store file: {}", originalFilename, e);
            return Mono.error(new FileUploadException("Failed to process and store file: " + originalFilename, e));
        });
    }

    public Flux<FileUploadResponseDTO> listProjectFiles(String projectId) {
        return projectFileRepository.findByProjectId(projectId)
                .map(this::mapToFileUploadResponseDTO);
    }

    public Mono<Resource> loadFileAsResource(String projectId, String fileId) {
        return projectFileRepository.findByProjectIdAndId(projectId, fileId)
                .switchIfEmpty(Mono.error(
                        new FileNotFoundException("File not found with ID " + fileId + " in project " + projectId)))
                .flatMap(projectFile -> Mono.fromCallable(() -> {
                    Path filePath = Paths.get(projectFile.getFilepath()).normalize();
                    Resource resource = new UrlResource(filePath.toUri());
                    if (resource.exists()) {
                        return resource;
                    } else {
                        throw new FileNotFoundException("File not found " + projectFile.getFilename());
                    }
                }).onErrorResume(MalformedURLException.class, ex -> Mono
                        .error(new FileNotFoundException("File not found " + projectFile.getFilename(), ex))));
    }

    public Mono<Void> deleteFile(String projectId, String fileId) {
        return projectFileRepository.findByProjectIdAndId(projectId, fileId)
                .switchIfEmpty(Mono.error(
                        new FileNotFoundException("File not found with ID " + fileId + " in project " + projectId)))
                .flatMap(projectFile -> Mono.fromCallable(() -> {
                    Path filePath = Paths.get(projectFile.getFilepath()).normalize();
                    Files.deleteIfExists(filePath);
                    return projectFile;
                }).onErrorMap(IOException.class, ex ->
                        new FileUploadException("Could not delete file " + projectFile.getFilename(), ex))
                .then(projectFileRepository.delete(projectFile)));
    }

    private FileUploadResponseDTO mapToFileUploadResponseDTO(ProjectFile projectFile) {
        String fileDownloadUri = UriComponentsBuilder.fromPath("/api/projects/")
                .path(projectFile.getProjectId())
                .path("/files/")
                .path(projectFile.getId())
                .toUriString();

        return FileUploadResponseDTO.builder()
                .id(projectFile.getId())
                .filename(projectFile.getFilename())
                .fileSize(projectFile.getFileSize())
                .fileType(projectFile.getFileType())
                .downloadUrl(fileDownloadUri)
                .uploadedAt(projectFile.getUploadedAt())
                .build();
    }
}

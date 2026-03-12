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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.codescope.backend.dto.upload.FileDocumentDto;
import com.codescope.backend.service.CloudinaryService;
import com.codescope.backend.utils.MultipartFileUtil;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository; // To validate projectId
    private final CloudinaryService cloudinaryService;

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

    public Mono<List<FileDocumentDto>> processAndStoreFile(MultipartFile file, String projectId) {
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String fileExtension = MultipartFileUtil.getFileExtension(originalFilename);
        String fileType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String cloudinaryFolder = "codescope/uploads/" + projectId;

        return Mono.fromCallable(file::getBytes)
                .flatMap(bytes -> {
                    if (isZipUpload(fileType, fileExtension)) {
                        log.info("Processing zip file via Cloudinary: {}", originalFilename);
                        return processZipUpload(bytes, originalFilename, cloudinaryFolder);
                    }
                    return uploadDocument(bytes, originalFilename, fileType, fileExtension, cloudinaryFolder)
                            .map(List::of);
                }).onErrorResume(IOException.class, e -> {
            log.error("Failed to process and store file: {}", originalFilename, e);
            return Mono.error(new FileUploadException("Failed to process and store file: " + originalFilename, e));
        });
    }

    private boolean isZipUpload(String fileType, String fileExtension) {
        return "application/zip".equals(fileType)
                || "application/x-zip-compressed".equals(fileType)
                || "zip".equals(fileExtension);
    }

    private Mono<List<FileDocumentDto>> processZipUpload(byte[] zipBytes, String originalFilename, String cloudinaryFolder) {
        String zipFolder = buildZipCloudinaryFolder(cloudinaryFolder, originalFilename);
        return Mono.fromCallable(() -> readZipEntries(zipBytes, originalFilename))
                .flatMapMany(Flux::fromIterable)
                .concatMap(entry -> uploadDocument(entry.bytes(), entry.relativePath(), entry.fileType(),
                        entry.fileExtension(), zipFolder))
                .collectList();
    }

    private List<ZipUploadEntry> readZipEntries(byte[] zipBytes, String originalFilename) throws IOException {
        List<ZipUploadEntry> extractedFiles = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    String relativePath = normalizeZipEntryPath(zipEntry.getName());
                    if (relativePath == null) {
                        log.warn("Skipped unsafe zip entry {} from zip {}", zipEntry.getName(), originalFilename);
                        zis.closeEntry();
                        zipEntry = zis.getNextEntry();
                        continue;
                    }

                    String extension = MultipartFileUtil.getFileExtension(relativePath);

                    if (MultipartFileUtil.ALLOWED_FILE_EXTENSIONS.contains(extension)) {
                        byte[] contentBytes = zis.readAllBytes();
                        extractedFiles.add(new ZipUploadEntry(
                                relativePath,
                                detectContentType(extension),
                                extension,
                                contentBytes));
                        log.info("Prepared supported file {} from zip {} for Cloudinary upload", relativePath,
                                originalFilename);
                    } else {
                        log.debug("Skipped unsupported file {} from zip {}", relativePath, originalFilename);
                    }
                }
                zis.closeEntry();
                zipEntry = zis.getNextEntry();
            }
        }
        return extractedFiles;
    }

        private Mono<FileDocumentDto> uploadDocument(byte[] bytes, String filename, String fileType, String fileExtension,
            String cloudinaryFolder) {
        return Mono.fromCallable(() -> MultipartFileUtil.extractContent(new ByteArrayInputStream(bytes), fileExtension))
                .flatMap(content -> cloudinaryService.uploadBytes(bytes, filename, cloudinaryFolder)
                .map(result -> toFileDocumentDto(filename, fileType, content, filename, bytes.length, result)));
    }

        private FileDocumentDto toFileDocumentDto(String filename, String fileType, String content, String relativePath,
            int size,
            Map<String, Object> uploadResult) {
        return new FileDocumentDto(
                filename,
                fileType,
                content,
                (String) uploadResult.get("secure_url"),
            relativePath,
                (String) uploadResult.get("public_id"),
                ((Number) uploadResult.getOrDefault("bytes", size)).longValue());
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

    private String normalizeZipEntryPath(String entryName) {
        String normalizedPath = StringUtils.cleanPath(entryName).replace('\\', '/');

        if (!StringUtils.hasText(normalizedPath)
                || normalizedPath.startsWith("/")
                || normalizedPath.startsWith("../")
                || normalizedPath.contains("/../")
                || normalizedPath.equals("..")) {
            return null;
        }

        return normalizedPath;
    }

    private String buildZipCloudinaryFolder(String cloudinaryFolder, String originalFilename) {
        String zipFolderName = StringUtils.stripFilenameExtension(StringUtils.getFilename(originalFilename));
        if (!StringUtils.hasText(zipFolderName)) {
            return cloudinaryFolder + "/archive";
        }

        String sanitizedFolderName = zipFolderName.replaceAll("[\\\\/]+", "-").trim();
        return cloudinaryFolder + "/" + sanitizedFolderName;
    }

    private record ZipUploadEntry(String relativePath, String fileType, String fileExtension, byte[] bytes) {
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

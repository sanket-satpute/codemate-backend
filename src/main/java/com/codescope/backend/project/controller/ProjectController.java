package com.codescope.backend.project.controller;

import com.codescope.backend.dto.BaseResponse;
import com.codescope.backend.exception.CustomException;
import com.codescope.backend.model.User;
import com.codescope.backend.project.dto.ProjectCreateRequest;
import com.codescope.backend.project.dto.ProjectResponse;
import com.codescope.backend.project.dto.ProjectUpdateRequest;
import com.codescope.backend.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
public class ProjectController {

        private final ProjectService projectService;

        @PostMapping
        @PreAuthorize("hasRole('USER')")
        public Mono<ResponseEntity<BaseResponse<ProjectResponse>>> createProject(
                        @Valid @RequestBody ProjectCreateRequest request,
                        @AuthenticationPrincipal User user) {
                return projectService.createProject(request, user.getUsername())
                                .map(projectResponse -> ResponseEntity.status(HttpStatus.CREATED)
                                                .body(new BaseResponse<>(true, "Project created successfully",
                                                                projectResponse)))
                                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(new BaseResponse<>(false,
                                                                "Failed to create project: " + e.getMessage(), null))));
        }

        /**
         * Creates a project with file uploads.
         * Files are uploaded to Cloudinary and metadata stored in MongoDB.
         * Accepts multipart/form-data with fields: name (required), description (optional), files (required).
         */
        @PostMapping(value = "/create-with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasRole('USER')")
        public Mono<ResponseEntity<BaseResponse<ProjectResponse>>> createProjectWithFiles(
                        @RequestPart("name") FormFieldPart namePart,
                        @RequestPart(value = "description", required = false) FormFieldPart descriptionPart,
                        @RequestPart("files") Flux<FilePart> fileFlux,
                        @AuthenticationPrincipal User user) {

                String name = namePart.value();
                String description = descriptionPart != null ? descriptionPart.value() : "";

                if (name == null || name.trim().length() < 3) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(new BaseResponse<>(false,
                                        "Project name is required and must be at least 3 characters", null)));
                }

                return fileFlux.collectList().flatMap(files -> {
                if (files.isEmpty()) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(new BaseResponse<>(false,
                                        "At least one file is required", (ProjectResponse) null)));
                }

                log.info("Creating project '{}' with {} file(s) for user {}", name.trim(), files.size(), user.getUsername());

                return projectService.createProjectWithFiles(name.trim(), description != null ? description.trim() : "", files, user.getUsername())
                                .map(projectResponse -> ResponseEntity.status(HttpStatus.CREATED)
                                                .body(new BaseResponse<>(true,
                                                        "Project created with " + projectResponse.getTotalFiles() + " file(s) uploaded to cloud",
                                                        projectResponse)))
                                .onErrorResume(e -> {
                                        log.error("Failed to create project with files: {}", e.getMessage(), e);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(new BaseResponse<>(false,
                                                        "Failed to create project: " + e.getMessage(), null)));
                                });
                });
        }

        @GetMapping
        @PreAuthorize("hasRole('USER')")
        public Mono<ResponseEntity<BaseResponse<List<ProjectResponse>>>> getUserProjects(
                        @AuthenticationPrincipal User user) {
                return projectService.getUserProjects(user.getUsername())
                                .collectList()
                                .map(projects -> ResponseEntity
                                                .ok(new BaseResponse<>(true, "Projects retrieved successfully",
                                                                projects)))
                                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(new BaseResponse<>(false,
                                                                "Failed to retrieve projects: " + e.getMessage(),
                                                                null))));
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasRole('USER')")
        public Mono<ResponseEntity<BaseResponse<ProjectResponse>>> getProjectDetails(
                        @PathVariable("id") String projectId,
                        @AuthenticationPrincipal User user) {
                return projectService.getProjectById(projectId, user.getUsername())
                                .map(projectResponse -> ResponseEntity
                                                .ok(new BaseResponse<>(true, "Project details retrieved successfully",
                                                                projectResponse)))
                                .onErrorResume(CustomException.class,
                                                e -> Mono.just(ResponseEntity.status(e.getStatus())
                                                                .body(new BaseResponse<>(false, e.getMessage(), null))))
                                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(new BaseResponse<>(false,
                                                                "Failed to retrieve project details: " + e.getMessage(),
                                                                null))));
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasRole('USER')")
        public Mono<ResponseEntity<BaseResponse<ProjectResponse>>> updateProject(
                        @PathVariable("id") String projectId,
                        @Valid @RequestBody ProjectUpdateRequest request,
                        @AuthenticationPrincipal User user) {
                return projectService.updateProject(projectId, request, user.getUsername())
                                .map(projectResponse -> ResponseEntity
                                                .ok(new BaseResponse<>(true, "Project updated successfully",
                                                                projectResponse)))
                                .onErrorResume(CustomException.class,
                                                e -> Mono.just(ResponseEntity.status(e.getStatus())
                                                                .body(new BaseResponse<>(false, e.getMessage(), null))))
                                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(new BaseResponse<>(false,
                                                                "Failed to update project: " + e.getMessage(), null))));
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasRole('USER')")
        public Mono<ResponseEntity<BaseResponse<Void>>> deleteProject(
                        @PathVariable("id") String projectId,
                        @AuthenticationPrincipal User user) {
                return projectService.deleteProject(projectId, user.getUsername())
                                .then(Mono.just(ResponseEntity.ok(
                                                new BaseResponse<Void>(true, "Project deleted successfully", null))))
                                .onErrorResume(CustomException.class,
                                                e -> Mono.just(ResponseEntity.status(e.getStatus())
                                                                .body(new BaseResponse<>(false, e.getMessage(), null))))
                                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(new BaseResponse<>(false,
                                                                "Failed to delete project: " + e.getMessage(), null))));
        }

        // ─── File Management Endpoints ─────────────────────────────────────

        @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasRole('USER')")
        public Mono<ResponseEntity<BaseResponse<ProjectResponse>>> addFilesToProject(
                        @PathVariable("id") String projectId,
                        @RequestPart("files") Flux<FilePart> fileFlux,
                        @AuthenticationPrincipal User user) {

                return fileFlux.collectList().flatMap(files -> {
                        if (files.isEmpty()) {
                                return Mono.just(ResponseEntity.badRequest()
                                        .body(new BaseResponse<>(false, "At least one file is required", (ProjectResponse) null)));
                        }

                        log.info("Adding {} file(s) to project {} for user {}", files.size(), projectId, user.getUsername());

                        return projectService.addFilesToProject(projectId, files, user.getUsername())
                                .map(response -> ResponseEntity.ok(
                                        new BaseResponse<>(true, files.size() + " file(s) added successfully", response)))
                                .onErrorResume(e -> {
                                        log.error("Failed to add files to project: {}", e.getMessage(), e);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(new BaseResponse<>(false, "Failed to add files: " + e.getMessage(), null)));
                                });
                });
        }

        @PutMapping(value = "/{id}/files/{fileId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasRole('USER')")
        public Mono<ResponseEntity<BaseResponse<ProjectResponse>>> replaceFile(
                        @PathVariable("id") String projectId,
                        @PathVariable("fileId") String fileId,
                        @RequestPart("file") Mono<FilePart> filePartMono,
                        @AuthenticationPrincipal User user) {

                return filePartMono.flatMap(filePart -> {
                        log.info("Replacing file {} in project {} for user {}", fileId, projectId, user.getUsername());

                        return projectService.replaceFileInProject(projectId, fileId, filePart, user.getUsername())
                                .map(response -> ResponseEntity.ok(
                                        new BaseResponse<>(true, "File replaced successfully", response)))
                                .onErrorResume(e -> {
                                        log.error("Failed to replace file: {}", e.getMessage(), e);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .body(new BaseResponse<>(false, "Failed to replace file: " + e.getMessage(), null)));
                                });
                });
        }

        @DeleteMapping("/{id}/files/{fileId}")
        @PreAuthorize("hasRole('USER')")
        public Mono<ResponseEntity<BaseResponse<ProjectResponse>>> deleteFileFromProject(
                        @PathVariable("id") String projectId,
                        @PathVariable("fileId") String fileId,
                        @AuthenticationPrincipal User user) {

                log.info("Deleting file {} from project {} for user {}", fileId, projectId, user.getUsername());

                return projectService.deleteFileFromProject(projectId, fileId, user.getUsername())
                                .map(response -> ResponseEntity.ok(
                                                new BaseResponse<>(true, "File deleted successfully", response)))
                                .onErrorResume(e -> {
                                        log.error("Failed to delete file: {}", e.getMessage(), e);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new BaseResponse<>(false,
                                                                        "Failed to delete file: " + e.getMessage(), null)));
                                });
        }
}

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BaseResponse<ProjectResponse>> createProject(
            @Valid @RequestBody ProjectCreateRequest request,
            @AuthenticationPrincipal User user) {
        try {
            ProjectResponse projectResponse = projectService.createProject(request, user.getUsername()).block();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new BaseResponse<>(true, "Project created successfully", projectResponse));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(false, "Failed to create project: " + e.getMessage(), null));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BaseResponse<List<ProjectResponse>>> getUserProjects(@AuthenticationPrincipal User user) {
        try {
            List<ProjectResponse> projects = projectService.getUserProjects(user.getUsername()).collectList().block();
            return ResponseEntity.ok(new BaseResponse<>(true, "Projects retrieved successfully", projects));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(false, "Failed to retrieve projects: " + e.getMessage(), null));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BaseResponse<ProjectResponse>> getProjectDetails(
            @PathVariable("id") Long projectId,
            @AuthenticationPrincipal User user) {
        try {
            ProjectResponse projectResponse = projectService.getProjectById(projectId.toString(), user.getUsername()).block();
            return ResponseEntity.ok(new BaseResponse<>(true, "Project details retrieved successfully", projectResponse));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(new BaseResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(false, "Failed to retrieve project details: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BaseResponse<ProjectResponse>> updateProject(
            @PathVariable("id") Long projectId,
            @Valid @RequestBody ProjectUpdateRequest request,
            @AuthenticationPrincipal User user) {
        try {
            ProjectResponse projectResponse = projectService.updateProject(projectId.toString(), request, user.getUsername()).block();
            return ResponseEntity.ok(new BaseResponse<>(true, "Project updated successfully", projectResponse));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(new BaseResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(false, "Failed to update project: " + e.getMessage(), null));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BaseResponse<Void>> deleteProject(
            @PathVariable("id") Long projectId,
            @AuthenticationPrincipal User user) {
        try {
            projectService.deleteProject(projectId.toString(), user.getUsername()).block();
            return ResponseEntity.ok(new BaseResponse<>(true, "Project deleted successfully", null));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(new BaseResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(false, "Failed to delete project: " + e.getMessage(), null));
        }
    }
}

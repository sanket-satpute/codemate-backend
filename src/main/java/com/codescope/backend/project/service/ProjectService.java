package com.codescope.backend.project.service;

import com.codescope.backend.project.dto.ProjectCreateRequest;
import com.codescope.backend.project.dto.ProjectResponse;
import com.codescope.backend.project.dto.ProjectUpdateRequest;
import com.codescope.backend.project.exception.ProjectNotFoundException;
import com.codescope.backend.project.model.Project;
import com.codescope.backend.project.model.ProjectStatus;
import com.codescope.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    public Mono<ProjectResponse> createProject(ProjectCreateRequest request, String ownerId) {
        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(ownerId)
                .status(ProjectStatus.ACTIVE)
                .build();
        return projectRepository.save(project)
                .map(this::mapToProjectResponse);
    }

    public Flux<ProjectResponse> getUserProjects(String ownerId) {
        return projectRepository.findByOwnerId(ownerId)
                .map(this::mapToProjectResponse);
    }

    public Mono<ProjectResponse> getProjectById(String projectId, String ownerId) {
        return projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException("Project not found or unauthorized")))
                .map(this::mapToProjectResponse);
    }

    public Mono<ProjectResponse> updateProject(String projectId, ProjectUpdateRequest request, String ownerId) {
        return projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException("Project not found or unauthorized")))
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
        return projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .switchIfEmpty(Mono.error(new ProjectNotFoundException("Project not found or unauthorized")))
                .flatMap(projectRepository::delete);
    }

    private ProjectResponse mapToProjectResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .status(project.getStatus())
                .lastAnalysisJobId(project.getLastAnalysisJobId())
                .build();
    }
}

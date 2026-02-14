package com.codescope.backend.service;

import com.codescope.backend.project.dto.ProjectCreateRequest;
import com.codescope.backend.project.dto.ProjectResponse;
import com.codescope.backend.project.dto.ProjectUpdateRequest;
import com.codescope.backend.project.exception.ProjectNotFoundException;
import com.codescope.backend.project.model.Project;
import com.codescope.backend.project.model.ProjectStatus;
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProjectService (mocking ProjectRepository)
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @InjectMocks
    private ProjectService projectService;

    @Mock
    private ProjectRepository projectRepository;

    private String testOwnerId = "testOwner";
    private String testProjectId = "1";
    private Project mockProject;
    private ProjectResponse mockProjectResponse;

    @BeforeEach
    void setUp() {
        mockProject = Project.builder()
                .id(testProjectId) // Project model expects String ID
                .name("Test Project")
                .description("Description for Test Project")
                .ownerId(testOwnerId)
                .status(ProjectStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        mockProjectResponse = ProjectResponse.builder()
                .id(testProjectId) // ProjectResponse expects Long ID
                .name("Test Project")
                .description("Description for Test Project")
                .status(ProjectStatus.ACTIVE)
                .createdAt(mockProject.getCreatedAt())
                .updatedAt(mockProject.getUpdatedAt())
                .build();
    }

    // CREATE PROJECT TEST
    @Test
    @DisplayName("Should create a new project and return its response DTO")
    void createProject_shouldReturnProjectResponse() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setName("New Project");
        request.setDescription("New Project Description");

        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(mockProject));

        ProjectResponse result = projectService.createProject(request, testOwnerId).block();

        assertNotNull(result);
        assertEquals(mockProjectResponse.getName(), result.getName());
        verify(projectRepository, times(1)).save(any(Project.class));
    }

    // GET PROJECT BY ID TEST
    @Test
    @DisplayName("Should fetch project by ID and owner ID if it exists")
    void getProjectById_shouldReturnProjectResponse() {
        when(projectRepository.findByIdAndOwnerId(testProjectId, testOwnerId)).thenReturn(Mono.just(mockProject));

        ProjectResponse result = projectService.getProjectById(testProjectId, testOwnerId).block();

        assertNotNull(result);
        assertEquals(mockProjectResponse.getName(), result.getName());
        verify(projectRepository, times(1)).findByIdAndOwnerId(testProjectId, testOwnerId);
    }

    @Test
    @DisplayName("Should throw ProjectNotFoundException when project not found by ID or owner ID")
    void getProjectById_shouldThrowNotFound() {
        when(projectRepository.findByIdAndOwnerId(anyString(), anyString())).thenReturn(Mono.empty());

        assertThrows(ProjectNotFoundException.class, () -> projectService.getProjectById(testProjectId, testOwnerId).block());
        verify(projectRepository, times(1)).findByIdAndOwnerId(testProjectId, testOwnerId);
    }

    // GET USER PROJECTS TEST
    @Test
    @DisplayName("Should list projects by owner ID")
    void getUserProjects_shouldReturnListOfProjectResponses() {
        Project anotherMockProject = Project.builder()
                .id("2") // Project model expects String ID
                .name("Another Project")
                .description("Another Description")
                .ownerId(testOwnerId)
                .status(ProjectStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        List<Project> mockProjects = List.of(mockProject, anotherMockProject);

        when(projectRepository.findByOwnerId(testOwnerId)).thenReturn(Flux.fromIterable(mockProjects));

        List<ProjectResponse> results = projectService.getUserProjects(testOwnerId).collectList().block();

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(mockProjectResponse.getName(), results.get(0).getName());
        verify(projectRepository, times(1)).findByOwnerId(testOwnerId);
    }

    @Test
    @DisplayName("Should return empty list when no projects are found for owner ID")
    void getUserProjects_shouldReturnEmptyList() {
        when(projectRepository.findByOwnerId(testOwnerId)).thenReturn(Flux.empty());

        List<ProjectResponse> results = projectService.getUserProjects(testOwnerId).collectList().block();

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(projectRepository, times(1)).findByOwnerId(testOwnerId);
    }

    // UPDATE PROJECT TEST
    @Test
    @DisplayName("Should update an existing project and return its response DTO")
    void updateProject_shouldReturnUpdatedProjectResponse() {
        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setName("Updated Project Name");
        request.setDescription("Updated Description");

        when(projectRepository.findByIdAndOwnerId(testProjectId, testOwnerId)).thenReturn(Mono.just(mockProject));
        when(projectRepository.save(any(Project.class))).thenReturn(Mono.just(mockProject)); // Mock save after update

        ProjectResponse result = projectService.updateProject(testProjectId, request, testOwnerId).block();

        assertNotNull(result);
        assertEquals("Updated Project Name", result.getName());
        assertEquals("Updated Description", result.getDescription());
        verify(projectRepository, times(1)).findByIdAndOwnerId(testProjectId, testOwnerId);
        verify(projectRepository, times(1)).save(any(Project.class));
    }

    @Test
    @DisplayName("Should throw ProjectNotFoundException when updating non-existent project")
    void updateProject_shouldThrowNotFound() {
        ProjectUpdateRequest request = new ProjectUpdateRequest();
        when(projectRepository.findByIdAndOwnerId(anyString(), anyString())).thenReturn(Mono.empty());

        assertThrows(ProjectNotFoundException.class, () -> projectService.updateProject(testProjectId, request, testOwnerId).block());
        verify(projectRepository, times(1)).findByIdAndOwnerId(testProjectId, testOwnerId);
        verify(projectRepository, never()).save(any(Project.class));
    }

    // DELETE PROJECT TEST
    @Test
    @DisplayName("Should delete project successfully")
    void deleteProject_shouldCompleteSuccessfully() {
        when(projectRepository.findByIdAndOwnerId(testProjectId, testOwnerId)).thenReturn(Mono.just(mockProject));
        when(projectRepository.delete(any(Project.class))).thenReturn(Mono.empty());

        projectService.deleteProject(testProjectId, testOwnerId).block();
        verify(projectRepository, times(1)).findByIdAndOwnerId(testProjectId, testOwnerId);
        verify(projectRepository, times(1)).delete(mockProject);
    }

    @Test
    @DisplayName("Should throw ProjectNotFoundException when deleting non-existent project")
    void deleteProject_shouldThrowNotFound() {
        when(projectRepository.findByIdAndOwnerId(anyString(), anyString())).thenReturn(Mono.empty());

        assertThrows(ProjectNotFoundException.class, () -> projectService.deleteProject(testProjectId, testOwnerId).block());
        verify(projectRepository, times(1)).findByIdAndOwnerId(testProjectId, testOwnerId);
        verify(projectRepository, never()).delete(any(Project.class));
    }
}

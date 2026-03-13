package com.codescope.backend.service;

import com.codescope.backend.project.dto.ProjectCreateRequest;
import com.codescope.backend.project.dto.ProjectResponse;
import com.codescope.backend.project.dto.ProjectUpdateRequest;
import com.codescope.backend.project.exception.ProjectNotFoundException;
import com.codescope.backend.project.model.Project;
import com.codescope.backend.project.model.ProjectStatus;
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.project.service.ProjectService;
import com.codescope.backend.service.CloudinaryService;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.upload.repository.ProjectFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private ProjectFileRepository projectFileRepository;

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

        lenient().when(projectRepository.findByProjectId(anyString())).thenReturn(Mono.empty());
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

    // ZIP EXTRACTION TESTS
    @Test
    @DisplayName("createProjectWithFiles should extract zip and upload individual files")
    void createProjectWithFiles_shouldExtractZipAndUploadIndividualFiles() throws Exception {
        // Build an in-memory zip with two Java files
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("src/Main.java"));
            zos.write("public class Main {}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("src/util/Helper.java"));
            zos.write("public class Helper {}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();

        // Mock FilePart for the zip
        FilePart zipFilePart = mock(FilePart.class);
        when(zipFilePart.filename()).thenReturn("project.zip");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        when(zipFilePart.headers()).thenReturn(headers);
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(zipBytes);
        when(zipFilePart.content()).thenReturn(Flux.just(dataBuffer));

        // Mock repository: save project
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Mock Cloudinary: uploadBytes for each extracted file
        when(cloudinaryService.uploadBytes(any(byte[].class), anyString(), anyString()))
                .thenAnswer(inv -> {
                    String filename = inv.getArgument(1);
                    return Mono.just(Map.<String, Object>of(
                            "secure_url", "https://cloudinary.com/" + filename,
                            "public_id", "pid_" + filename,
                            "bytes", 100
                    ));
                });

        // Mock projectFileRepository save
        when(projectFileRepository.save(any(ProjectFile.class)))
                .thenAnswer(inv -> {
                    ProjectFile pf = inv.getArgument(0);
                    pf.setId("id_" + pf.getFilename());
                    return Mono.just(pf);
                });

        ProjectResponse result = projectService.createProjectWithFiles(
                "Test Zip Project", "desc", List.of(zipFilePart), testOwnerId).block();

        assertNotNull(result);
        // Should have 2 files (extracted from zip), not 1 zip
        assertEquals(2, result.getTotalFiles());
        // Verify uploadBytes was called for each extracted file, not uploadFile for the zip
        verify(cloudinaryService, never()).uploadFile(any(FilePart.class), anyString());
        verify(cloudinaryService, times(2)).uploadBytes(any(byte[].class), anyString(), anyString());
        // Verify filenames are the zip-relative paths, not "project.zip"
        List<String> filenames = result.getFiles().stream()
                .map(ProjectResponse.FileInfo::getFilename)
                .toList();
        assertTrue(filenames.contains("src/Main.java"));
        assertTrue(filenames.contains("src/util/Helper.java"));
    }

    @Test
    @DisplayName("createProjectWithFiles should upload non-zip files directly")
    void createProjectWithFiles_shouldUploadNonZipDirectly() {
        // Mock FilePart for a regular Java file
        FilePart javaFilePart = mock(FilePart.class);
        when(javaFilePart.filename()).thenReturn("App.java");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        when(javaFilePart.headers()).thenReturn(headers);

        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(cloudinaryService.uploadFile(any(FilePart.class), anyString()))
                .thenReturn(Mono.just(Map.<String, Object>of(
                        "secure_url", "https://cloudinary.com/App.java",
                        "public_id", "pid_App.java",
                        "bytes", 50
                )));

        when(projectFileRepository.save(any(ProjectFile.class)))
                .thenAnswer(inv -> {
                    ProjectFile pf = inv.getArgument(0);
                    pf.setId("id_" + pf.getFilename());
                    return Mono.just(pf);
                });

        ProjectResponse result = projectService.createProjectWithFiles(
                "Test Project", "desc", List.of(javaFilePart), testOwnerId).block();

        assertNotNull(result);
        assertEquals(1, result.getTotalFiles());
        // Regular file should use uploadFile, not uploadBytes
        verify(cloudinaryService, times(1)).uploadFile(any(FilePart.class), anyString());
        verify(cloudinaryService, never()).uploadBytes(any(byte[].class), anyString(), anyString());
        assertEquals("App.java", result.getFiles().get(0).getFilename());
    }
}

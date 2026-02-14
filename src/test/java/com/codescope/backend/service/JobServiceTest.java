package com.codescope.backend.service;

import com.codescope.backend.analysisjob.dto.AnalysisJobResponseDTO;
import com.codescope.backend.analysisjob.dto.JobStatusResponseDTO;
import com.codescope.backend.analysisjob.enums.JobStatus;
import com.codescope.backend.analysisjob.enums.JobType;
import com.codescope.backend.analysisjob.exception.JobNotFoundException;
import com.codescope.backend.analysisjob.exception.UnauthorizedJobAccessException;
import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.analysisjob.repository.AnalysisJobRepository;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.realtime.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = AnalysisJobService.class)
@Import({WebSocketEventPublisher.class})
class JobServiceTest {

    @Autowired
    private AnalysisJobService analysisJobService;

    @MockBean
    private AnalysisJobRepository analysisJobRepository;

    @MockBean
    private ProjectRepository projectRepository;

    @MockBean
    private FirebaseService firebaseService;

    @MockBean
    private WebSocketEventPublisher webSocketEventPublisher;

    @BeforeEach
    void setUp() {
        // Mocking the async method to prevent execution
        AnalysisJobService spyAnalysisJobService = spy(analysisJobService);
        doNothing().when(spyAnalysisJobService).runAnalysisAsync(anyString());
        
        when(projectRepository.findByProjectId(anyString())).thenReturn(Mono.just(new com.codescope.backend.project.model.Project()));

        when(analysisJobRepository.save(any(AnalysisJob.class))).thenAnswer(invocation -> {
            AnalysisJob job = invocation.getArgument(0);
            if (job.getJobId() == null) {
                job.setJobId(UUID.randomUUID().toString());
            }
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            return job;
        });

        when(firebaseService.saveJob(any(AnalysisJob.class))).thenReturn(Mono.just("success"));

        doNothing().when(webSocketEventPublisher).publishToProject(anyString(), any());
    }

    @Test
    @DisplayName("Should successfully start a new analysis job")
    void startNewJob_success() {
        String projectId = "test-project-1";
        String jobType = JobType.PROJECT_ANALYSIS.name();

        AnalysisJobResponseDTO response = analysisJobService.startNewJob(projectId, jobType).block();

        assertNotNull(response);
        assertEquals(projectId, response.getProjectId());
        assertEquals(JobStatus.PENDING, response.getStatus());
        assertEquals(JobType.PROJECT_ANALYSIS, response.getJobType());
        assertNotNull(response.getJobId());

        verify(analysisJobRepository).save(any(AnalysisJob.class));
        verify(webSocketEventPublisher).publishToProject(anyString(), any());
    }

    @Test
    @DisplayName("Should return JobNotFoundException if project does not exist when creating job")
    void createJob_projectNotFound() {
        String projectId = "non-existent-project";
        String jobType = JobType.PROJECT_ANALYSIS.name();

        when(projectRepository.findByProjectId(projectId)).thenReturn(Mono.empty());

        assertThrows(JobNotFoundException.class, () -> {
            analysisJobService.startNewJob(projectId, jobType).block(); // Block to trigger exception
        });
    }

    @Test
    @DisplayName("Should successfully update job status")
    void updateJobStatus_success() {
        String jobId = "job-to-update";
        AnalysisJob existingJob = AnalysisJob.builder()
                .jobId(jobId)
                .projectId("project-id")
                .jobType(JobType.PROJECT_ANALYSIS)
                .status(JobStatus.PENDING)
                .initiatedBy("user")
                .build();
        when(analysisJobRepository.findByJobId(jobId)).thenReturn(Optional.of(existingJob));

        analysisJobService.updateJobStatus(jobId, JobStatus.IN_PROGRESS);

        verify(analysisJobRepository).findByJobId(jobId);
        verify(analysisJobRepository).save(any(AnalysisJob.class));
        verify(webSocketEventPublisher).publishToProject(anyString(), any());
        assertEquals(JobStatus.IN_PROGRESS, existingJob.getStatus());
    }

    @Test
    @DisplayName("Should throw JobNotFoundException when updating status for non-existent job")
    void updateJobStatus_jobNotFound() {
        String jobId = "non-existent-job-for-update";
        when(analysisJobRepository.findByJobId(jobId)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> {
            analysisJobService.updateJobStatus(jobId, JobStatus.IN_PROGRESS);
        });
    }

    @Test
    @DisplayName("Should retrieve job status successfully")
    void getJobStatus_success() {
        String jobId = "job-status-id";
        String projectId = "project-status-id";
        String userId = "user-status";
        AnalysisJob mockJob = AnalysisJob.builder()
                .jobId(jobId)
                .projectId(projectId)
                .initiatedBy(userId)
                .status(JobStatus.COMPLETED)
                .result("Test result")
                .build();
        when(analysisJobRepository.findByJobIdAndProjectId(jobId, projectId)).thenReturn(Optional.of(mockJob));

        JobStatusResponseDTO response = analysisJobService.getJobStatus(jobId, projectId, userId).block();

        assertNotNull(response);
        assertEquals(jobId, response.getJobId());
        assertEquals(JobStatus.COMPLETED, response.getStatus());
        assertEquals("Test result", response.getResult());
    }

    @Test
    @DisplayName("Should throw JobNotFoundException when retrieving status for non-existent job")
    void getJobStatus_jobNotFound() {
        String jobId = "non-existent-job-status";
        String projectId = "project-status-id";
        String userId = "user-status";
        when(analysisJobRepository.findByJobIdAndProjectId(jobId, projectId)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> {
            analysisJobService.getJobStatus(jobId, projectId, userId).block();
        });
    }

    @Test
    @DisplayName("Should throw UnauthorizedJobAccessException when retrieving status for unauthorized user")
    void getJobStatus_unauthorized() {
        String jobId = "job-status-id";
        String projectId = "project-status-id";
        String userId = "unauthorized-user";
        AnalysisJob mockJob = AnalysisJob.builder()
                .jobId(jobId)
                .projectId(projectId)
                .initiatedBy("authorized-user") // Different user
                .status(JobStatus.COMPLETED)
                .build();
        when(analysisJobRepository.findByJobIdAndProjectId(jobId, projectId)).thenReturn(Optional.of(mockJob));

        assertThrows(UnauthorizedJobAccessException.class, () -> {
            analysisJobService.getJobStatus(jobId, projectId, userId).block();
        });
    }
}

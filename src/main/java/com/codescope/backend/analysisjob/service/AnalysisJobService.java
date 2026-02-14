package com.codescope.backend.analysisjob.service;

import com.codescope.backend.analysisjob.dto.AnalysisJobResponseDTO;
import com.codescope.backend.analysisjob.enums.JobStatus;
import com.codescope.backend.analysisjob.dto.JobStatusResponseDTO;
import com.codescope.backend.analysisjob.enums.JobType;
import com.codescope.backend.analysisjob.exception.JobNotFoundException;
import com.codescope.backend.analysisjob.exception.UnauthorizedJobAccessException;
import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.analysisjob.repository.AnalysisJobRepository;
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.realtime.websocket.WebSocketEventPayload;
import com.codescope.backend.realtime.websocket.WebSocketEventPublisher;
import com.codescope.backend.realtime.websocket.WebSocketEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalysisJobService {

    private final AnalysisJobRepository analysisJobRepository;
    private final ProjectRepository projectRepository; // For project ownership validation
    private final WebSocketEventPublisher webSocketEventPublisher;

    public Mono<AnalysisJobResponseDTO> startNewJob(String projectId, String jobType) {
        return createJob(projectId, JobType.valueOf(jobType), "user");
    }

    public Mono<AnalysisJobResponseDTO> createJob(String projectId, JobType jobType, String initiatedBy) {
        return projectRepository.findByProjectId(projectId)
                .switchIfEmpty(Mono.error(new JobNotFoundException("Project not found with ID: " + projectId)))
                .flatMap(project -> {
                    AnalysisJob job = AnalysisJob.builder()
                            .jobId(UUID.randomUUID().toString()) // Generate jobId
                            .projectId(projectId)
                            .jobType(jobType)
                            .status(JobStatus.PENDING)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .initiatedBy(initiatedBy)
                            .build();

                    return Mono.fromCallable(() -> analysisJobRepository.save(job))
                            .map(savedJob -> {
                                webSocketEventPublisher.publishToProject(projectId,
                                        new WebSocketEventPayload(WebSocketEventType.JOB_QUEUED, projectId, Map.of("job", mapToAnalysisJobResponseDTO(savedJob), "jobId", savedJob.getJobId())));
                                runAnalysisAsync(savedJob.getJobId());
                                return mapToAnalysisJobResponseDTO(savedJob);
                            });
                });
    }

    @Async
    public void runAnalysisAsync(String jobId) {
        // This is a placeholder for actual async analysis logic
        // In a real scenario, this would involve calling external AI services,
        // processing files, etc.
        try {
            updateJobStatus(jobId, JobStatus.IN_PROGRESS);
            // Simulate long-running task
            Thread.sleep(5000);
            saveJobResult(jobId, "Analysis completed successfully for job " + jobId);
            updateJobStatus(jobId, JobStatus.COMPLETED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            updateJobStatus(jobId, JobStatus.FAILED);
            saveJobResult(jobId, "Analysis interrupted for job " + jobId + ": " + e.getMessage());
        } catch (Exception e) {
            updateJobStatus(jobId, JobStatus.FAILED);
            saveJobResult(jobId, "Analysis failed for job " + jobId + ": " + e.getMessage());
        }
    }

    @Transactional
    public void updateJobStatus(String jobId, JobStatus status) {
        AnalysisJob job = analysisJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found with ID: " + jobId));
        job.setStatus(status);
        job.setUpdatedAt(LocalDateTime.now());
        AnalysisJob savedJob = analysisJobRepository.save(job);
        WebSocketEventType eventType = getEventTypeForStatus(status);
        if (eventType != null) {
            webSocketEventPublisher.publishToProject(savedJob.getProjectId(),
                    new WebSocketEventPayload(eventType, savedJob.getProjectId(), Map.of("job", mapToAnalysisJobResponseDTO(savedJob), "jobId", savedJob.getJobId())));
        }
    }

    @Transactional
    public void saveJobResult(String jobId, String result) {
        AnalysisJob job = analysisJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found with ID: " + jobId));
        job.setResult(result);
        job.setUpdatedAt(LocalDateTime.now());
        AnalysisJob savedJob = analysisJobRepository.save(job);
        webSocketEventPublisher.publishToProject(savedJob.getProjectId(),
                new WebSocketEventPayload(WebSocketEventType.JOB_RUNNING, savedJob.getProjectId(), Map.of("result", result, "jobId", savedJob.getJobId())));
    }

    public Mono<JobStatusResponseDTO> getJobStatus(String jobId, String projectId, String userId) {
        return Mono.fromCallable(() -> analysisJobRepository.findByJobIdAndProjectId(jobId, projectId)
                .orElseThrow(() -> new JobNotFoundException("Job not found with ID: " + jobId + " for project: " + projectId)))
                .map(job -> {
                    if (!job.getInitiatedBy().equals(userId)) {
                        throw new UnauthorizedJobAccessException("User is not authorized to access this job.");
                    }
                    return mapToJobStatusResponseDTO(job);
                });
    }

    public Flux<AnalysisJobResponseDTO> getProjectJobs(String projectId, String userId) {
        return Flux.fromIterable(analysisJobRepository.findByProjectId(projectId))
                .filter(job -> job.getInitiatedBy().equals(userId))
                .map(this::mapToAnalysisJobResponseDTO);
    }

    private AnalysisJobResponseDTO mapToAnalysisJobResponseDTO(AnalysisJob job) {
        return AnalysisJobResponseDTO.builder()
                .jobId(job.getJobId())
                .projectId(job.getProjectId())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .result(job.getResult())
                .build();
    }

    private JobStatusResponseDTO mapToJobStatusResponseDTO(AnalysisJob job) {
        return JobStatusResponseDTO.builder()
                .jobId(job.getJobId()) // Use jobId here for JobStatusResponseDTO
                .status(job.getStatus())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .result(job.getResult())
                .build();
    }

    private WebSocketEventType getEventTypeForStatus(JobStatus status) {
        switch (status) {
            case PENDING:
                return WebSocketEventType.JOB_QUEUED;
            case IN_PROGRESS:
                return WebSocketEventType.JOB_RUNNING;
            case COMPLETED:
                return WebSocketEventType.JOB_COMPLETED;
            case FAILED:
                return WebSocketEventType.JOB_FAILED;
            default:
                return null;
        }
    }
}

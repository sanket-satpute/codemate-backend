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
import com.codescope.backend.ai.AIProcessingService;
import com.codescope.backend.model.Notification;
import com.codescope.backend.repository.NotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisJobService {

    private final AnalysisJobRepository analysisJobRepository;
    private final ProjectRepository projectRepository;
    private final WebSocketEventPublisher webSocketEventPublisher;
    private final AIProcessingService aiProcessingService;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public Mono<AnalysisJobResponseDTO> startNewJob(String projectId, String jobType) {
        return createJob(projectId, JobType.valueOf(jobType), "user", null);
    }

    public Mono<AnalysisJobResponseDTO> createJob(String projectId, JobType jobType, String initiatedBy, String userId) {
        return projectRepository.findByProjectId(projectId)
                .switchIfEmpty(projectRepository.findById(projectId))
                .switchIfEmpty(Mono.error(new JobNotFoundException("Project not found with ID: " + projectId)))
                .flatMap(project -> {
                    AnalysisJob job = AnalysisJob.builder()
                            .jobId(UUID.randomUUID().toString())
                            .projectId(projectId)
                            .jobType(jobType)
                            .status(JobStatus.PENDING)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .initiatedBy(initiatedBy)
                            .build();

                    return analysisJobRepository.save(job)
                            .map(savedJob -> {
                                webSocketEventPublisher.publishToProject(projectId,
                                        new WebSocketEventPayload(WebSocketEventType.JOB_QUEUED, projectId,
                                                Map.of("job", mapToAnalysisJobResponseDTO(savedJob), "jobId",
                                                        savedJob.getJobId())));
                                runAnalysisAsync(savedJob.getJobId(), project.getName(), userId)
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .subscribe();
                                return mapToAnalysisJobResponseDTO(savedJob);
                            });
                });
    }

    public Mono<Void> runAnalysisAsync(String jobId, String projectName, String userId) {
        return analysisJobRepository.findByJobId(jobId)
                .switchIfEmpty(Mono.error(new JobNotFoundException("Job not found: " + jobId)))
                .flatMap(job -> updateJobStatus(jobId, JobStatus.IN_PROGRESS)
                        .then(aiProcessingService.generateAIResult(job))
                        .flatMap(aiResult -> saveJobResult(jobId, aiResult)
                                .then(createAnalysisNotification(userId, projectName, aiResult, true)))
                        .then(updateJobStatus(jobId, JobStatus.COMPLETED))
                        .doOnSuccess(v -> log.info("Analysis job {} completed successfully", jobId)))
                .onErrorResume(e -> {
                    log.error("Analysis job {} failed: {}", jobId, e.getMessage(), e);
                    return createAnalysisNotification(userId, projectName, null, false)
                            .then(safeFailJob(jobId, "Analysis failed: " + e.getMessage()));
                });
    }

    private Mono<Void> createAnalysisNotification(String userId, String projectName, String aiResult, boolean success) {
        if (userId == null || userId.isBlank()) return Mono.empty();

        String title;
        String message;

        if (success && aiResult != null) {
            title = "Analysis Completed — " + (projectName != null ? projectName : "Project");
            message = buildCompletionMessage(aiResult, projectName);
        } else {
            title = "Analysis Failed — " + (projectName != null ? projectName : "Project");
            message = "Code analysis for " + (projectName != null ? projectName : "your project") + " failed. Please try again.";
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .read(false)
                .timestamp(LocalDateTime.now())
                .build();

        return notificationRepository.save(notification)
                .doOnNext(saved -> log.info("Analysis notification created for user {}", userId))
                .onErrorResume(e -> {
                    log.warn("Failed to create analysis notification: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private String buildCompletionMessage(String aiResult, String projectName) {
        try {
            JsonNode root = objectMapper.readTree(aiResult);
            String riskLevel = root.has("riskLevel") ? root.get("riskLevel").asText() : "UNKNOWN";
            int issueCount = root.has("issues") && root.get("issues").isArray() ? root.get("issues").size() : 0;

            int errors = 0, warnings = 0, infos = 0;
            if (root.has("issues") && root.get("issues").isArray()) {
                for (JsonNode issue : root.get("issues")) {
                    String severity = issue.has("severity") ? issue.get("severity").asText().toLowerCase() : "";
                    switch (severity) {
                        case "error": errors++; break;
                        case "warning": warnings++; break;
                        default: infos++; break;
                    }
                }
            }

            String proj = projectName != null ? projectName : "Your project";
            if (issueCount == 0) {
                return proj + " passed analysis with no issues found. Risk Level: " + riskLevel + ". Great job!";
            }
            return proj + " analysis complete. Found " + issueCount + " findings: " +
                    errors + " errors, " + warnings + " warnings, " + infos + " info. Risk Level: " + riskLevel + ".";
        } catch (Exception e) {
            return "Analysis completed for " + (projectName != null ? projectName : "your project") + ".";
        }
    }

    private Mono<Void> safeFailJob(String jobId, String message) {
        return updateJobStatus(jobId, JobStatus.FAILED)
                .onErrorResume(ignored -> Mono.empty())
                .then(saveJobResult(jobId, message).onErrorResume(ignored -> Mono.empty()));
    }

    public Mono<Void> updateJobStatus(String jobId, JobStatus status) {
        return analysisJobRepository.findByJobId(jobId)
                .switchIfEmpty(Mono.error(new JobNotFoundException("Job not found with ID: " + jobId)))
                .flatMap(job -> {
                    job.setStatus(status);
                    job.setUpdatedAt(LocalDateTime.now());
                    return analysisJobRepository.save(job);
                })
                .doOnNext(savedJob -> {
                    WebSocketEventType eventType = getEventTypeForStatus(status);
                    if (eventType != null) {
                        webSocketEventPublisher.publishToProject(savedJob.getProjectId(),
                                new WebSocketEventPayload(eventType, savedJob.getProjectId(), Map.of("job",
                                        mapToAnalysisJobResponseDTO(savedJob), "jobId", savedJob.getJobId())));
                    }
                })
                .then();
    }

    public Mono<Void> saveJobResult(String jobId, String result) {
        return analysisJobRepository.findByJobId(jobId)
                .switchIfEmpty(Mono.error(new JobNotFoundException("Job not found with ID: " + jobId)))
                .flatMap(job -> {
                    job.setResult(result);
                    job.setUpdatedAt(LocalDateTime.now());
                    return analysisJobRepository.save(job);
                })
                .doOnNext(savedJob -> webSocketEventPublisher.publishToProject(savedJob.getProjectId(),
                        new WebSocketEventPayload(WebSocketEventType.JOB_RUNNING, savedJob.getProjectId(),
                                Map.of("result", result, "jobId", savedJob.getJobId()))))
                .then();
    }

    public Mono<JobStatusResponseDTO> getJobStatus(String jobId, String projectId, String userId) {
        return analysisJobRepository.findByJobIdAndProjectId(jobId, projectId)
                .switchIfEmpty(Mono.error(
                        new JobNotFoundException("Job not found with ID: " + jobId + " for project: " + projectId)))
                .map(job -> {
                    if (!job.getInitiatedBy().equals(userId)) {
                        throw new UnauthorizedJobAccessException("User is not authorized to access this job.");
                    }
                    return mapToJobStatusResponseDTO(job);
                });
    }

    public Flux<AnalysisJobResponseDTO> getProjectJobs(String projectId, String userId) {
        return analysisJobRepository.findByProjectId(projectId)
                .filter(job -> job.getInitiatedBy().equals(userId))
                .map(this::mapToAnalysisJobResponseDTO);
    }

    public Mono<JobStatusResponseDTO> getJobStatus(String jobId) {
        return analysisJobRepository.findByJobId(jobId)
                .switchIfEmpty(Mono.error(new JobNotFoundException("Job not found with ID: " + jobId)))
                .map(this::mapToJobStatusResponseDTO);
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
                .model(job.getModel())
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
                throw new IllegalArgumentException("Unknown JobStatus: " + status);
        }
    }
}

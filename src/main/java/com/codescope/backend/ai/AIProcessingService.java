package com.codescope.backend.ai;

import com.codescope.backend.ai.exception.AIServiceException;
import com.codescope.backend.ai.exception.PromptGenerationException;
import com.codescope.backend.ai.exception.ResponseParsingException;
import com.codescope.backend.analysisjob.enums.JobStatus;
import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import com.codescope.backend.project.model.Project;
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.upload.repository.ProjectFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.codescope.backend.chat.ChatMessage;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIProcessingService {

    private final AnalysisJobService analysisJobService;
    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final PromptBuilder promptBuilder;
    private final AIResponseParser responseParser;
    private final HuggingFaceClient huggingFaceClient;
    private final OpenAIClient openAIClient;

    @Value("${ai.provider.default:openai}")
    private String defaultAiProvider;

    @Value("${ai.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${ai.retry.delay-seconds:5}")
    private int retryDelaySeconds;

    @Async
    @Transactional
    public void processAnalysisJob(AnalysisJob job) {
        try {
            analysisJobService.updateJobStatus(job.getJobId().toString(), JobStatus.IN_PROGRESS);
            String aiResult = generateAIResult(job);
            analysisJobService.saveJobResult(job.getJobId().toString(), aiResult);
            analysisJobService.updateJobStatus(job.getJobId().toString(), JobStatus.COMPLETED);
            log.info("Analysis job {} completed successfully.", job.getJobId());
        } catch (Exception e) {
            log.error("Analysis job {} failed: {}", job.getJobId(), e.getMessage(), e);
            analysisJobService.saveJobResult(job.getJobId().toString(), "Failed: " + e.getMessage());
            analysisJobService.updateJobStatus(job.getJobId().toString(), JobStatus.FAILED);
        }
    }

    public String generateAIResult(AnalysisJob job) {
        int attempts = 0;
        while (attempts < maxRetryAttempts) {
            try {
                Project project = projectRepository.findByProjectId(job.getProjectId())
                        .switchIfEmpty(Mono.error(new PromptGenerationException("Project not found for job: " + job.getProjectId())))
                        .block(); // Block to get the Project synchronously for now

                List<ProjectFile> projectFiles = projectFileRepository.findByProjectId(job.getProjectId()).collectList().block();

                String prompt = promptBuilder.buildPrompt(job.getJobType(), project, projectFiles);

                LLMClient client = getLlmClient(defaultAiProvider);
                String rawResponse = client.sendPrompt(prompt);

                return responseParser.parseAndValidateJson(rawResponse);
            } catch (PromptGenerationException | ResponseParsingException | AIServiceException e) {
                log.warn("Attempt {} for job {} failed: {}. Retrying in {} seconds...",
                        attempts + 1, job.getJobId(), e.getMessage(), retryDelaySeconds);
                attempts++;
                if (attempts < maxRetryAttempts) {
                    try {
                        TimeUnit.SECONDS.sleep(retryDelaySeconds);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AIServiceException("AI processing interrupted during retry.", ie);
                    }
                } else {
                    throw new AIServiceException("All AI processing attempts failed for job " + job.getJobId(), e);
                }
            }
        }
        throw new AIServiceException("Unexpected error: Should not reach here.");
    }

    public String process(String prompt) {
        int attempts = 0;
        while (attempts < maxRetryAttempts) {
            try {
                LLMClient client = getLlmClient(defaultAiProvider);
                String rawResponse = client.sendPrompt(prompt);
                return responseParser.parseAndValidateJson(rawResponse);
            } catch (AIServiceException | ResponseParsingException e) {
                log.warn("Attempt {} for AI processing failed: {}. Retrying in {} seconds...",
                        attempts + 1, e.getMessage(), retryDelaySeconds);
                attempts++;
                if (attempts < maxRetryAttempts) {
                    try {
                        TimeUnit.SECONDS.sleep(retryDelaySeconds);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AIServiceException("AI processing interrupted during retry.", ie);
                    }
                } else {
                    throw new AIServiceException("All AI processing attempts failed.", e);
                }
            }
        }
        throw new AIServiceException("Unexpected error: Should not reach here.");
    }

    private LLMClient getLlmClient(String provider) {
        return switch (provider.toLowerCase()) {
            case "huggingface" -> huggingFaceClient;
            case "openai" -> openAIClient;
            default -> throw new AIServiceException("Unsupported AI provider: " + provider);
        };
    }
}

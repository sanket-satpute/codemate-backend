package com.codescope.backend.ai;

import com.codescope.backend.ai.chunking.ChunkingService;
import com.codescope.backend.ai.exception.AIServiceException;
import com.codescope.backend.ai.exception.PromptGenerationException;
import com.codescope.backend.ai.exception.ResponseParsingException;
import com.codescope.backend.ai.client.ILLMClient;
import com.codescope.backend.ai.model.FileChunk;
import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.upload.repository.ProjectFileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIProcessingService {

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final AIResponseParser responseParser;
    private final ILLMClient llmClient;
    private final ChunkingService chunkingService;
    private final ObjectMapper objectMapper;

    @Value("${ai.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${ai.retry.delay-seconds:5}")
    private int retryDelaySeconds;

    public Mono<String> generateAIResult(AnalysisJob job) {
        return projectRepository.findByProjectId(job.getProjectId())
                .switchIfEmpty(projectRepository.findById(job.getProjectId()))
                .switchIfEmpty(
                        Mono.error(new PromptGenerationException("Project not found for job: " + job.getProjectId())))
                .flatMap(project -> projectFileRepository.findByProjectId(job.getProjectId())
                        .collectList()
                        .flatMap(projectFiles -> {
                            // Fallback to embedded project.files if repository returns empty
                            if (projectFiles.isEmpty() && project.getFiles() != null && !project.getFiles().isEmpty()) {
                                log.info("No files in projectFileRepository, using embedded project.files for job {}", job.getJobId());
                                projectFiles = project.getFiles();
                            }
                            if (projectFiles.isEmpty()) {
                                return Mono.error(new PromptGenerationException("No files found for project: " + job.getProjectId()));
                            }
                            var chunks = chunkingService.generateChunks(job.getProjectId(), projectFiles);
                            log.info("Processing {} chunks for job {}", chunks.size(), job.getJobId());

                            return Flux.fromIterable(chunks)
                                    .concatMap(chunk -> processReactive(chunk))
                                    .collectList()
                                    .map(this::combineChunkResults);
                        }));
    }

    private String combineChunkResults(java.util.List<String> results) {
        if (results.isEmpty())
            return "{\"summary\":\"No results\",\"issues\":[],\"suggestions\":[],\"riskLevel\":\"LOW\"}";
        if (results.size() == 1)
            return results.get(0);

        // Merge multiple chunk JSON results into a single result
        try {
            var merged = objectMapper.createObjectNode();
            var allIssues = objectMapper.createArrayNode();
            var allSuggestions = objectMapper.createArrayNode();
            StringBuilder summaryBuilder = new StringBuilder();
            String worstRisk = "LOW";

            for (String result : results) {
                JsonNode node = objectMapper.readTree(result);
                if (node.has("summary")) {
                    if (summaryBuilder.length() > 0) summaryBuilder.append(" ");
                    summaryBuilder.append(node.get("summary").asText());
                }
                if (node.has("issues") && node.get("issues").isArray()) {
                    node.get("issues").forEach(allIssues::add);
                }
                if (node.has("suggestions") && node.get("suggestions").isArray()) {
                    node.get("suggestions").forEach(allSuggestions::add);
                }
                if (node.has("riskLevel")) {
                    String risk = node.get("riskLevel").asText().toUpperCase();
                    if ("HIGH".equals(risk) || ("MEDIUM".equals(risk) && !"HIGH".equals(worstRisk))) {
                        worstRisk = risk;
                    }
                }
            }

            merged.put("summary", summaryBuilder.toString());
            merged.set("issues", allIssues);
            merged.set("suggestions", allSuggestions);
            merged.put("riskLevel", worstRisk);
            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            log.error("Failed to merge chunk results, returning first result", e);
            return results.get(0);
        }
    }

    public Mono<String> processReactive(FileChunk chunk) {
        int retries = Math.max(0, maxRetryAttempts - 1);

        return Mono.defer(() -> queryProvider(chunk))
                .retryWhen(Retry.fixedDelay(retries, Duration.ofSeconds(retryDelaySeconds))
                        .filter(this::isRetryableAiError)
                        .doBeforeRetry(retrySignal -> log.warn(
                                "Retry {} for AI processing due to: {}",
                                retrySignal.totalRetries() + 1,
                                retrySignal.failure().getMessage())))
                .onErrorMap(throwable -> throwable instanceof AIServiceException
                        ? throwable
                        : new AIServiceException("All AI processing attempts failed.", throwable));
    }

    private Mono<String> queryProvider(FileChunk chunk) {
        return Mono.fromCallable(() -> {
            var response = llmClient.performAnalysis(chunk);
            if (response.getError() != null) {
                throw new AIServiceException("AI Fallback chain exhausted. Exhaustion error: " + response.getError());
            }
            return responseParser.parseAndValidateJson(response.getRawResponse());
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private boolean isRetryableAiError(Throwable throwable) {
        return throwable instanceof AIServiceException
                || throwable instanceof ResponseParsingException
                || throwable instanceof PromptGenerationException;
    }
}

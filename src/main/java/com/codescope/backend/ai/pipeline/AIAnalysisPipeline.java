package com.codescope.backend.ai.pipeline;

import com.codescope.backend.ai.aggregation.AnalysisAggregationService;
import com.codescope.backend.ai.chunking.ChunkingService;
import com.codescope.backend.ai.client.ILLMClient;
import com.codescope.backend.ai.model.FileChunk;
import com.codescope.backend.ai.model.LLMAnalysisAggregate;
import com.codescope.backend.ai.model.LLMChunkResponse;
import com.codescope.backend.ai.model.LLMNormalizedResult;
import com.codescope.backend.ai.normalization.NormalizationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AIAnalysisPipeline {

    private final ChunkingService chunkingService;
    private final ILLMClient llmClient;
    private final NormalizationService normalizationService;
    private final AnalysisAggregationService aggregationService;

    public AIAnalysisPipeline(ChunkingService chunkingService, ILLMClient llmClient, NormalizationService normalizationService, AnalysisAggregationService aggregationService) {
        this.chunkingService = chunkingService;
        this.llmClient = llmClient;
        this.normalizationService = normalizationService;
        this.aggregationService = aggregationService;
    }

    public LLMAnalysisAggregate run(String projectId) {
        List<FileChunk> chunks = chunkingService.generateChunks(projectId);
        List<LLMChunkResponse> responses = chunks.stream()
                .map(llmClient::performAnalysis)
                .collect(Collectors.toList());
        List<LLMNormalizedResult> normalizedResults = responses.stream()
                .map(normalizationService::normalize)
                .collect(Collectors.toList());
        return aggregationService.aggregate(normalizedResults);
    }
}

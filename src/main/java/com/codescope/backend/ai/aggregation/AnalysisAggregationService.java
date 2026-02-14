package com.codescope.backend.ai.aggregation;

import com.codescope.backend.ai.model.LLMAnalysisAggregate;
import com.codescope.backend.ai.model.LLMNormalizedResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalysisAggregationService {

    public LLMAnalysisAggregate aggregate(List<LLMNormalizedResult> results) {
        // TODO: Implement sophisticated aggregation logic
        LLMAnalysisAggregate aggregate = new LLMAnalysisAggregate();
        // Combine issues, suggestions, dependencies, etc.
        return aggregate;
    }
}

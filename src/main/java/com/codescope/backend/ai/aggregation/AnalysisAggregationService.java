package com.codescope.backend.ai.aggregation;

import com.codescope.backend.ai.model.LLMAnalysisAggregate;
import com.codescope.backend.ai.model.LLMNormalizedResult;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

@Service
public class AnalysisAggregationService {

    public LLMAnalysisAggregate aggregate(String projectId, List<LLMNormalizedResult> results) {
        if (results == null || results.isEmpty()) {
            return new LLMAnalysisAggregate(projectId, List.of(), List.of(), List.of(), List.of(), List.of());
        }

        Set<String> allIssues = new HashSet<>();
        Set<String> allSuggestions = new HashSet<>();
        Set<String> allDependencies = new HashSet<>();
        Set<String> allSecurityIssues = new HashSet<>();
        Set<String> allTests = new HashSet<>();

        for (LLMNormalizedResult result : results) {
            safeAddAll(allIssues, result.getIssues());
            safeAddAll(allSuggestions, result.getSuggestions());
            safeAddAll(allDependencies, result.getDependencies());
            safeAddAll(allSecurityIssues, result.getSecurityIssues());
            safeAddAll(allTests, result.getTests());
        }

        return LLMAnalysisAggregate.builder()
                .projectId(projectId)
                .issues(allIssues.stream().collect(Collectors.toList()))
                .suggestions(allSuggestions.stream().collect(Collectors.toList()))
                .dependencies(allDependencies.stream().collect(Collectors.toList()))
                .securityIssues(allSecurityIssues.stream().collect(Collectors.toList()))
                .tests(allTests.stream().collect(Collectors.toList()))
                .build();
    }

    private void safeAddAll(Set<String> set, Collection<String> collection) {
        if (collection != null) {
            collection.stream()
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .forEach(set::add);
        }
    }
}

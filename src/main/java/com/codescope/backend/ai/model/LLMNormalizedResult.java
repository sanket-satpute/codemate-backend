package com.codescope.backend.ai.model;

import lombok.*;

import java.util.List;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LLMNormalizedResult {
    private String chunkId;
    private List<String> issues;
    private List<String> suggestions;
    private List<String> dependencies;
    private List<String> securityIssues;
    private List<String> tests;
}

package com.codescope.backend.ai.model;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AnalysisContext {
    private String projectId;
    private Map<String, String> fileContents;
    private List<FileProcessingResult> processingResults;
}

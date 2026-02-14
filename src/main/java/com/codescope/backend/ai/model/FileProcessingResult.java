package com.codescope.backend.ai.model;

import lombok.*;

import java.util.List;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FileProcessingResult {
    private String filePath;
    private List<LLMNormalizedResult> chunkResults;
    private AIError error;
}

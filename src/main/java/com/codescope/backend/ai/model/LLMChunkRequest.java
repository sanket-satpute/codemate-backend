package com.codescope.backend.ai.model;

import lombok.*;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LLMChunkRequest {
    private String model;
    private String prompt;
    private double temperature;
    private int maxTokens;
}

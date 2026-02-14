package com.codescope.backend.ai.model;

import lombok.*;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LLMChunkResponse {
    private String chunkId;
    private String rawResponse;
    private AIError error;
}

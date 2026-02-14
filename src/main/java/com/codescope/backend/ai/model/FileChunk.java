package com.codescope.backend.ai.model;

import lombok.*;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FileChunk {
    private String projectId;
    private String filePath;
    private int chunkNumber;
    private int totalChunks;
    private String hash;
    private String content;
}

package com.codescope.backend.ai.chunking;

import com.codescope.backend.ai.model.FileChunk;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.utils.FileContentExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkingService {

    private final FileContentExtractor fileContentExtractor;

    // Conservative limit for an LLM context window (roughly 15-20k tokens depending
    // on the model)
    // 60,000 characters is approximately 15,000 tokens
    private static final int MAX_CHARS_PER_CHUNK = 60000;

    /**
     * Groups a list of ProjectFiles into FileChunks to prevent LLM context
     * overflow.
     */
    public List<FileChunk> generateChunks(String projectId, List<ProjectFile> projectFiles) {
        List<FileChunk> chunks = new ArrayList<>();
        StringBuilder currentChunkContent = new StringBuilder();
        int chunkIndex = 1;

        for (ProjectFile file : projectFiles) {
            String fileContent = readFileContent(file);
            String formattedFile = String.format("File: %s (Type: %s)\nContent:\n```\n%s\n```\n\n---\n\n",
                    file.getFilename(), file.getFileType(), fileContent);

            // If a single file is obscenely large, we split it internally (simplified as
            // just adding it to its own chunks)
            if (formattedFile.length() > MAX_CHARS_PER_CHUNK) {
                // If we already have accumulated content, flush it first
                if (currentChunkContent.length() > 0) {
                    chunks.add(createChunk(projectId, "Multiple Files", chunkIndex++, currentChunkContent.toString()));
                    currentChunkContent.setLength(0);
                }

                // Split the huge file into its own sub-chunks
                int startIndex = 0;
                while (startIndex < fileContent.length()) {
                    int endIndex = Math.min(startIndex + MAX_CHARS_PER_CHUNK, fileContent.length());
                    String subContent = fileContent.substring(startIndex, endIndex);
                    String subFormatted = String.format(
                            "File: %s (Part %d) (Type: %s)\nContent:\n```\n%s\n```\n\n---\n\n",
                            file.getFilename(), (startIndex / MAX_CHARS_PER_CHUNK) + 1, file.getFileType(), subContent);
                    chunks.add(createChunk(projectId, file.getFilename(), chunkIndex++, subFormatted));
                    startIndex = endIndex;
                }
            } else {
                // Standard grouping
                if (currentChunkContent.length() + formattedFile.length() > MAX_CHARS_PER_CHUNK) {
                    chunks.add(createChunk(projectId, "Multiple Files", chunkIndex++, currentChunkContent.toString()));
                    currentChunkContent.setLength(0);
                }
                currentChunkContent.append(formattedFile);
            }
        }

        // Flush any remaining content
        if (currentChunkContent.length() > 0) {
            chunks.add(createChunk(projectId, "Multiple Files", chunkIndex++, currentChunkContent.toString()));
        }

        // Update total chunks count
        int total = chunks.size();
        chunks.forEach(chunk -> chunk.setTotalChunks(total));

        log.info("Splitted project {} into {} chunks for AI analysis.", projectId, total);
        return chunks;
    }

    private FileChunk createChunk(String projectId, String filePathInfo, int chunkNumber, String content) {
        return FileChunk.builder()
                .projectId(projectId)
                .filePath(filePathInfo)
                .chunkNumber(chunkNumber)
                .content(content)
                .build();
    }

    private String readFileContent(ProjectFile file) {
        // Try local file first
        String filepath = file.getFilepath();
        if (filepath != null && !filepath.isBlank()) {
            // Handle URL-style paths like /uploads/uuid_file.csv
            if (filepath.startsWith("/uploads/")) {
                filepath = filepath.substring(1); // strip leading /
            }
            Path path = Paths.get(filepath);
            if (Files.exists(path)) {
                try {
                    return fileContentExtractor.extract(path);
                } catch (Exception e) {
                    log.warn("Failed to extract local file {}: {}", filepath, e.getMessage());
                }
            }
        }

        // Fallback: download from Cloudinary
        String url = file.getCloudinaryUrl();
        if (url != null && !url.isBlank()) {
            try {
                Path tempFile = Files.createTempFile("analysis_", "_" + file.getFilename());
                try (InputStream in = URI.create(url).toURL().openStream()) {
                    Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                String content = fileContentExtractor.extract(tempFile);
                Files.deleteIfExists(tempFile);
                return content;
            } catch (Exception e) {
                log.warn("Failed to download from Cloudinary for {}: {}", file.getFilename(), e.getMessage());
            }
        }

        return "// Error: could not read file content for " + file.getFilename();
    }
}

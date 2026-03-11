package com.codescope.backend.ai;

import com.codescope.backend.ai.exception.PromptGenerationException;
import com.codescope.backend.analysisjob.enums.JobType;
import com.codescope.backend.project.model.Project;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.ai.model.FileChunk;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {

    private static final String PROJECT_ANALYSIS_TEMPLATE = """
            Perform a comprehensive analysis of the provided project files.
            Focus on:
            1. Overall code quality and maintainability.
            2. Potential bugs or vulnerabilities.
            3. Adherence to best practices and coding standards.
            4. Architectural patterns and design flaws.
            5. Performance bottlenecks.

            Project Name: %s
            Project Description: %s

            Files:
            %s

            Provide the response in a structured JSON format, including a summary, a list of issues, suggestions for improvement, and an overall risk level (LOW, MEDIUM, HIGH).
            """;

    private static final String CODE_REVIEW_TEMPLATE = """
            Perform a detailed code review on the following code snippet from file '%s'.
            Focus on:
            1. Readability and clarity.
            2. Efficiency and performance.
            3. Error handling.
            4. Security vulnerabilities.
            5. Adherence to common coding standards.

            Code:
            ```%s
            %s
            ```

            Provide the response in a structured JSON format, including a summary, a list of issues, suggestions for improvement, and an overall risk level (LOW, MEDIUM, HIGH).
            """;

    private static final String ARCHITECTURE_SCAN_TEMPLATE = """
            Analyze the architecture of the provided project based on the file structure and content.
            Focus on:
            1. Modularity and separation of concerns.
            2. Scalability and extensibility.
            3. Use of design patterns.
            4. Dependencies between modules.
            5. Potential architectural debt.

            Project Name: %s
            Project Description: %s

            Files and their content:
            %s

            Provide the response in a structured JSON format, including a summary, a list of issues, suggestions for improvement, and an overall risk level (LOW, MEDIUM, HIGH).
            """;

    private static final String BUG_SCAN_TEMPLATE = """
            Perform a bug scan on the provided project files.
            Identify:
            1. Common programming errors.
            2. Logic flaws.
            3. Potential runtime exceptions.
            4. Security vulnerabilities that could lead to bugs.

            Project Name: %s
            Project Description: %s

            Files:
            %s

            Provide the response in a structured JSON format, including a summary, a list of issues, suggestions for improvement, and an overall risk level (LOW, MEDIUM, HIGH).
            """;

    public String buildPrompt(JobType jobType, Project project, FileChunk chunk) {
        String chunkContext = "";
        if (chunk.getTotalChunks() > 1) {
            chunkContext = String.format("Note: This is chunk %d out of %d for the project analysis.\n\n",
                    chunk.getChunkNumber(), chunk.getTotalChunks());
        }

        String filesContent = chunk.getContent();

        return switch (jobType) {
            case PROJECT_ANALYSIS -> String.format(PROJECT_ANALYSIS_TEMPLATE,
                    project.getName(), project.getDescription(), chunkContext + filesContent);
            case AI_REVIEW -> {
                // For review, we ideally want smaller specific snippets, but adhering to the
                // old structure
                yield String.format(CODE_REVIEW_TEMPLATE,
                        chunk.getFilePath(), "txt", chunkContext + filesContent);
            }
            case ARCHITECTURE_SCAN -> String.format(ARCHITECTURE_SCAN_TEMPLATE,
                    project.getName(), project.getDescription(), chunkContext + filesContent);
            case BUG_SCAN -> String.format(BUG_SCAN_TEMPLATE,
                    project.getName(), project.getDescription(), chunkContext + filesContent);
            default -> throw new PromptGenerationException("Unsupported job type for prompt generation: " + jobType);
        };
    }

    public String buildChatPrompt(String projectContext, List<com.codescope.backend.chat.ChatMessage> chatHistory,
            String userMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Project Context:\n").append(projectContext).append("\n\n");
        if (!chatHistory.isEmpty()) {
            prompt.append("Chat History:\n");
            chatHistory.forEach(msg -> prompt.append(msg.getSender()).append(": ").append(msg.getMessage()).append("\n"));
            prompt.append("\n");
        }
        prompt.append("User: ").append(userMessage).append("\n\n");
        prompt.append("Provide a concise and helpful response based on the project files and conversation above. Use markdown formatting.");
        return prompt.toString();
    }

    private String readFileContent(String filePath) {
        try {
            return new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            throw new PromptGenerationException("Failed to read file content from path: " + filePath, e);
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }
}

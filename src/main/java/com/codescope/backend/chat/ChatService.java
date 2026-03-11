package com.codescope.backend.chat;

import com.codescope.backend.ai.PromptBuilder;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import com.codescope.backend.chat.ChatMessage.Sender;
import com.codescope.backend.chat.exception.MissingProjectException;
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.realtime.websocket.WebSocketEventPayload;
import com.codescope.backend.realtime.websocket.WebSocketEventPublisher;
import com.codescope.backend.realtime.websocket.WebSocketEventType;
import com.codescope.backend.ai.client.ILLMClient;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.upload.repository.ProjectFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.codescope.backend.utils.FileContentExtractor;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

        private final ChatMessageRepository chatMessageRepository;
        private final ProjectRepository projectRepository;
        private final AnalysisJobService analysisJobService;
        private final PromptBuilder promptBuilder;
        private final WebSocketEventPublisher webSocketEventPublisher;
        private final ILLMClient llmClient;
        private final ProjectFileRepository projectFileRepository;
        private final FileContentExtractor fileContentExtractor;

        public Mono<ChatMessage> addUserMessage(String projectId, String message) {
                return projectRepository.findByProjectId(projectId)
                                .switchIfEmpty(projectRepository.findById(projectId))
                                .switchIfEmpty(Mono.error(new MissingProjectException(
                                                "Project with ID " + projectId + " not found")))
                                .flatMap(project -> {
                                        ChatMessage userMessage = ChatMessage.builder()
                                                        .projectId(projectId)
                                                        .sender(Sender.USER)
                                                        .message(message)
                                                        .build();
                                        return chatMessageRepository.save(userMessage) // Reactive save
                                                        .doOnSuccess(savedMessage -> webSocketEventPublisher
                                                                        .publishToProject(projectId,
                                                                                        new WebSocketEventPayload(
                                                                                                        WebSocketEventType.CHAT_USER_MESSAGE,
                                                                                                        projectId,
                                                                                                        Map.of("message",
                                                                                                                        savedMessage,
                                                                                                                        "messageId",
                                                                                                                        savedMessage.getId()
                                                                                                                                        .toString()))));
                                });
        }

        public Mono<ChatMessage> addAIMessage(String projectId, String message) {
                ChatMessage aiMessage = ChatMessage.builder()
                                .projectId(projectId)
                                .sender(Sender.AI)
                                .message(message)
                                .build();

                return chatMessageRepository.save(aiMessage) // Reactive save
                                .doOnSuccess(savedMessage -> webSocketEventPublisher.publishToProject(projectId,
                                                new WebSocketEventPayload(WebSocketEventType.CHAT_AI_MESSAGE, projectId,
                                                                Map.of("message", savedMessage, "messageId",
                                                                                savedMessage.getId().toString()))));
        }

        public Flux<ChatMessage> getChatHistory(String projectId) {
                return chatMessageRepository.findByProjectIdOrderByTimestampAsc(projectId);
        }

        public Mono<Void> clearChat(String projectId) {
                return chatMessageRepository.deleteByProjectId(projectId);
        }

        public Mono<ChatMessage> processUserMessage(String projectId, String userMessage) {
                log.info("Processing user message for project {}", projectId);
                return projectRepository.findByProjectId(projectId)
                                .switchIfEmpty(projectRepository.findById(projectId))
                                .switchIfEmpty(Mono.error(new MissingProjectException(
                                                "Project with ID " + projectId + " not found")))
                                .flatMap(project -> Mono.zip(
                                                getChatHistory(projectId).collectList(),
                                                buildProjectContext(projectId, project.getName()))
                                                .flatMap(tuple -> {
                                                        List<ChatMessage> chatHistory = tuple.getT1();
                                                        String projectContext = tuple.getT2();
                                                        String prompt = promptBuilder.buildChatPrompt(projectContext,
                                                                        chatHistory, userMessage);

                                                        return llmClient.chat(prompt)
                                                                        .flatMap(aiResponse -> addAIMessage(
                                                                                        projectId,
                                                                                        aiResponse));
                                                }));
        }

        public Flux<String> streamUserMessage(String projectId, String userMessage) {
                return addUserMessage(projectId, userMessage)
                                .flatMapMany(savedUserMsg -> projectRepository
                                                .findByProjectId(projectId)
                                                .switchIfEmpty(projectRepository.findById(projectId))
                                                .switchIfEmpty(Mono.error(new MissingProjectException(
                                                                "Project with ID " + projectId + " not found")))
                                                .flatMapMany(project -> Mono.zip(
                                                                getChatHistory(projectId).collectList(),
                                                                buildProjectContext(projectId, project.getName()))
                                                                .flatMapMany(tuple -> {
                                                                        String projectContext = tuple.getT2();
                                                                        String prompt = promptBuilder.buildChatPrompt(
                                                                                        projectContext,
                                                                                        tuple.getT1(), userMessage);
                                                                        StringBuilder accumulated = new StringBuilder();
                                                                        return Flux.concat(
                                                                                        llmClient.chatStream(prompt)
                                                                                                        .doOnNext(accumulated::append),
                                                                                        Mono.defer(() -> addAIMessage(
                                                                                                        projectId,
                                                                                                        accumulated.toString()))
                                                                                                        .then(Mono.empty()));
                                                                })));
        }

        private static final int MAX_CONTEXT_CHARS = 15_000;

        private Mono<String> buildProjectContext(String projectId, String projectName) {
                return projectFileRepository.findByProjectId(projectId)
                                .collectList()
                                .flatMap(files -> {
                                        // Also check embedded files if projectFileRepository has none
                                        if (files.isEmpty()) {
                                                return projectRepository.findByProjectId(projectId)
                                                        .switchIfEmpty(projectRepository.findById(projectId))
                                                        .map(project -> project.getFiles() != null ? project.getFiles() : List.<ProjectFile>of())
                                                        .defaultIfEmpty(List.of());
                                        }
                                        return Mono.just(files);
                                })
                                .map(files -> {
                                        StringBuilder context = new StringBuilder();
                                        context.append("Project: ").append(projectName).append("\n");
                                        context.append("Files in this project:\n\n");

                                        for (int i = 0; i < files.size(); i++) {
                                                ProjectFile file = files.get(i);
                                                String header = "--- File: " + file.getFilename() + " ---\n";
                                                String content = readFileContent(file);
                                                String entry = header + content + "\n\n";

                                                if (context.length() + entry.length() > MAX_CONTEXT_CHARS) {
                                                        context.append("\n(Context limit reached. Remaining files listed by name only:)\n");
                                                        for (int j = i; j < files.size(); j++) {
                                                                context.append("- ").append(files.get(j).getFilename()).append("\n");
                                                        }
                                                        break;
                                                }
                                                context.append(entry);
                                        }

                                        if (files.isEmpty()) {
                                                context.append("(No files uploaded yet)\n");
                                        }

                                        return context.toString();
                                });
        }

        private String readFileContent(ProjectFile file) {
                // Try local file first
                String filePath = file.getFilepath();
                if (filePath != null && !filePath.isBlank()) {
                        try {
                                // Handle URL-style paths like "/uploads/uuid_file.csv"
                                String cleanPath = filePath;
                                if (cleanPath.startsWith("/uploads/")) {
                                        cleanPath = "uploads/" + cleanPath.substring("/uploads/".length());
                                } else if (cleanPath.startsWith("/")) {
                                        cleanPath = cleanPath.substring(1);
                                }

                                Path path = Paths.get(cleanPath).normalize().toAbsolutePath();
                                Path uploadBase = Paths.get("uploads").normalize().toAbsolutePath();
                                if (!path.startsWith(uploadBase)) {
                                        log.warn("Path traversal attempt blocked: {}", filePath);
                                        return "// Access denied";
                                }
                                if (Files.exists(path)) {
                                        return fileContentExtractor.extract(path);
                                }
                                log.debug("File not found locally: {}", path);
                        } catch (Exception e) {
                                log.warn("Failed to read local file: {}", filePath, e);
                        }
                }

                // Fallback: download from Cloudinary URL
                String cloudinaryUrl = file.getCloudinaryUrl();
                if (cloudinaryUrl != null && !cloudinaryUrl.isBlank()) {
                        return downloadAndExtract(cloudinaryUrl, file.getFilename());
                }

                return "// File content not available";
        }

        private String downloadAndExtract(String url, String filename) {
                try {
                        // Download to a temp file so FileContentExtractor can handle it by extension
                        Path tempFile = Files.createTempFile("codemate-", "-" + filename);
                        try (InputStream in = URI.create(url).toURL().openStream()) {
                                Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        String content = fileContentExtractor.extract(tempFile);
                        Files.deleteIfExists(tempFile);
                        return content;
                } catch (Exception e) {
                        log.warn("Failed to download file from URL for {}: {}", filename, e.getMessage());
                        return "// Could not download file content";
                }
        }
}

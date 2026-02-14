package com.codescope.backend.chat;

import com.codescope.backend.ai.AIProcessingService;
import com.codescope.backend.ai.PromptBuilder;
import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.analysisjob.service.AnalysisJobService;
import com.codescope.backend.chat.ChatMessage.Sender;
import com.codescope.backend.chat.exception.MissingProjectException;
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.realtime.websocket.WebSocketEventPayload;
import com.codescope.backend.realtime.websocket.WebSocketEventPublisher;
import com.codescope.backend.realtime.websocket.WebSocketEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ProjectRepository projectRepository;
    private final AIProcessingService aiProcessingService;
    private final AnalysisJobService analysisJobService;
    private final PromptBuilder promptBuilder;
    private final WebSocketEventPublisher webSocketEventPublisher;

    public Mono<ChatMessage> addUserMessage(String projectId, String message) {
        return projectRepository.findByProjectId(projectId)
                .switchIfEmpty(Mono.error(new MissingProjectException("Project with ID " + projectId + " not found")))
                .flatMap(project -> {
                    ChatMessage userMessage = ChatMessage.builder()
                            .projectId(projectId)
                            .sender(Sender.USER)
                            .message(message)
                            .build();
                    return chatMessageRepository.save(userMessage) // Reactive save
                            .doOnSuccess(savedMessage ->
                                    webSocketEventPublisher.publishToProject(projectId,
                                            new WebSocketEventPayload(WebSocketEventType.CHAT_USER_MESSAGE, projectId, Map.of("message", savedMessage, "messageId", savedMessage.getId().toString()))));
                });
    }

    public Mono<ChatMessage> addAIMessage(String projectId, String message) {
        ChatMessage aiMessage = ChatMessage.builder()
                .projectId(projectId)
                .sender(Sender.AI)
                .message(message)
                .build();

        return chatMessageRepository.save(aiMessage) // Reactive save
                .doOnSuccess(savedMessage ->
                        webSocketEventPublisher.publishToProject(projectId,
                                new WebSocketEventPayload(WebSocketEventType.CHAT_AI_MESSAGE, projectId, Map.of("message", savedMessage, "messageId", savedMessage.getId().toString()))));
    }

    public Flux<ChatMessage> getChatHistory(String projectId) {
        return chatMessageRepository.findByProjectIdOrderByTimestampAsc(projectId);
    }

    @Transactional
    public Mono<Void> clearChat(String projectId) {
        return chatMessageRepository.deleteByProjectId(projectId);
    }

    @Transactional
    public Mono<ChatMessage> processUserMessage(String projectId, String userMessage) {
        return addUserMessage(projectId, userMessage)
                .flatMap(savedUserMessage ->
                        getChatHistory(projectId).collectList()
                                .flatMap(chatHistory -> {
                                    String projectContext = "Sample project context"; // Simplified for now
                                    String prompt = promptBuilder.buildChatPrompt(projectContext, chatHistory, userMessage);

                                    return analysisJobService.createJob(projectId, com.codescope.backend.analysisjob.enums.JobType.CHAT_TURN, "user")
                                            .flatMap(job ->
                                                Mono.fromCallable(() -> aiProcessingService.process(prompt)) // Wrap blocking call
                                                    .flatMap(aiResponse -> Mono.fromRunnable(() -> analysisJobService.saveJobResult(job.getJobId(), aiResponse)) // Wrap blocking call
                                                        .thenReturn(aiResponse)) // Pass aiResponse to the next step
                                                    .flatMap(aiResponse -> addAIMessage(projectId, aiResponse))
                                            );
                                })
                );
    }
}

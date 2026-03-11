package com.codescope.backend.ai.client;

import com.codescope.backend.ai.model.FileChunk;
import com.codescope.backend.ai.model.LLMChunkResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
@Primary
@Slf4j
public class CodeMateLLMClient implements ILLMClient {

    private static final String SYSTEM_PROMPT =
            "You are CodeMate AI, a powerful code review, file analysis, and file generation assistant. "
            + "You can read and analyze all types of files including code files, Excel spreadsheets (.xlsx/.xls), "
            + "CSV files, PDFs, Word documents (.docx), and other text formats. "
            + "When you see spreadsheet data (rows separated by | ), treat it as tabular data and analyze it fully. "
            + "IMPORTANT: You CAN and SHOULD directly generate, create, modify, and transform file content when the user asks. "
            + "Never say 'I cannot modify files' or 'I am unable to edit files'. Instead, always produce the complete "
            + "updated content directly. For spreadsheets, output the full updated data as a markdown table. "
            + "For code files, output the complete modified code. For new files, output the full file content. "
            + "When the user asks you to edit, update, create, or transform any file, produce the result directly in your response. "
            + "Help the user understand, improve, debug, and transform their project files. "
            + "Use markdown formatting in your answers.";

    private static final String[] PROVIDER_NAMES = {
            "GitHub GPT-4o-mini", "Groq Llama 3.3 70B", "Groq Llama 3.1 8B",
            "Gemini 2.0 Flash", "Gemini 2.0 Flash-Lite"
    };

    private final List<ChatClient> clients;

    public CodeMateLLMClient(
            @Qualifier("githubChatModel") ChatModel githubModel,
            @Qualifier("groqChatModel") ChatModel groqModel,
            @Qualifier("groqFastChatModel") ChatModel groqFastModel,
            @Qualifier("geminiChatModel") ChatModel geminiModel,
            @Qualifier("geminiLiteChatModel") ChatModel geminiLiteModel) {

        this.clients = List.of(
                ChatClient.create(githubModel),
                ChatClient.create(groqModel),
                ChatClient.create(groqFastModel),
                ChatClient.create(geminiModel),
                ChatClient.create(geminiLiteModel));
    }

    @Override
    public LLMChunkResponse performAnalysis(FileChunk chunk) {
        String chunkId = chunk.getFilePath() + "-" + chunk.getChunkNumber();
        String prompt = "Perform a thorough code review of the following project files. " +
                "Analyze each file for bugs, errors, warnings, code smells, security issues, performance problems, and good practices. " +
                "You MUST return the result EXACTLY as a JSON object with NO markdown formatting, NO code fences, NO additional text. " +
                "Return ONLY the raw JSON object.\n\n" +
                "Required JSON structure:\n" +
                "{\n" +
                "  \"summary\": \"A brief overall summary of the analysis\",\n" +
                "  \"issues\": [\n" +
                "    {\n" +
                "      \"file\": \"filename.ext\",\n" +
                "      \"lineNumber\": 10,\n" +
                "      \"severity\": \"error|warning|info\",\n" +
                "      \"message\": \"Description of the issue\",\n" +
                "      \"suggestion\": \"How to fix it\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"suggestions\": [\n" +
                "    {\n" +
                "      \"file\": \"filename.ext\",\n" +
                "      \"description\": \"General improvement suggestion\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"riskLevel\": \"LOW|MEDIUM|HIGH\"\n" +
                "}\n\n" +
                "severities: 'error' for bugs/critical issues, 'warning' for code smells/potential problems, 'info' for style/best-practice improvements.\n" +
                "Include file name for EVERY issue. Include line numbers when possible.\n\n" +
                "Files to analyze:\n" + chunk.getContent();

        log.info("Starting analysis for chunk {}", chunkId);

        for (int i = 0; i < clients.size(); i++) {
            try {
                log.info("Attempting analysis with {}...", PROVIDER_NAMES[i]);
                String rawResponse = clients.get(i).prompt().user(prompt).call().content();
                log.info("Successfully retrieved AI response for chunk {} via {}", chunkId, PROVIDER_NAMES[i]);
                return LLMChunkResponse.builder()
                        .chunkId(chunkId)
                        .rawResponse(rawResponse)
                        .build();
            } catch (Exception e) {
                if (i < clients.size() - 1) {
                    log.warn("{} failed: {}. Trying {}...", PROVIDER_NAMES[i], e.getMessage(),
                            PROVIDER_NAMES[i + 1]);
                } else {
                    log.error("All AI providers exhausted! Analysis failed for chunk {}", chunkId, e);
                }
            }
        }

        return LLMChunkResponse.builder()
                .chunkId(chunkId)
                .rawResponse("")
                .build();
    }

    @Override
    public Mono<String> chat(String prompt) {
        return tryCall(prompt, 0);
    }

    private Mono<String> tryCall(String prompt, int index) {
        if (index >= clients.size()) {
            return Mono.error(new RuntimeException("All AI providers exhausted for chat"));
        }
        return Mono.fromCallable(() -> {
                    String content = clients.get(index).prompt()
                            .system(SYSTEM_PROMPT)
                            .user(prompt)
                            .call().content();
                    if (content == null || content.isBlank()) {
                        throw new RuntimeException(PROVIDER_NAMES[index] + " returned empty response");
                    }
                    log.info("{} returned response ({} chars)", PROVIDER_NAMES[index], content.length());
                    return content;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    if (index < clients.size() - 1) {
                        log.warn("{} failed for chat: {}. Trying {}...",
                                PROVIDER_NAMES[index], e.getMessage(), PROVIDER_NAMES[index + 1]);
                    } else {
                        log.error("All AI providers exhausted for chat!", e);
                    }
                    return tryCall(prompt, index + 1);
                });
    }

    @Override
    public Flux<String> chatStream(String prompt) {
        return tryStream(prompt, 0);
    }

    private Flux<String> tryStream(String prompt, int index) {
        if (index >= clients.size()) {
            return Flux.error(new RuntimeException("All AI providers exhausted for streaming"));
        }
        return clients.get(index).prompt().system(SYSTEM_PROMPT).user(prompt).stream().content()
                .onErrorResume(e -> {
                    if (index < clients.size() - 1) {
                        log.warn("{} stream failed: {}. Trying {}...",
                                PROVIDER_NAMES[index], e.getMessage(), PROVIDER_NAMES[index + 1]);
                    } else {
                        log.error("All AI providers exhausted for streaming!", e);
                    }
                    return tryStream(prompt, index + 1);
                });
    }
}

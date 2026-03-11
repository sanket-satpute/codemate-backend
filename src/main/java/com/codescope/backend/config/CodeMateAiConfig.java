package com.codescope.backend.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class CodeMateAiConfig {

    private RestClient.Builder aiRestClientBuilder() {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(30))
                .withReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings));
    }

    // 1. Primary: GitHub Models (GPT-4o-mini)
    @Bean
    @org.springframework.context.annotation.Primary
    public ChatModel githubChatModel(@Value("${api.github.key}") String apiKey) {
        var api = OpenAiApi.builder()
                .baseUrl("https://models.inference.ai.azure.com")
                .completionsPath("/chat/completions")
                .apiKey(apiKey)
                .restClientBuilder(aiRestClientBuilder())
                .build();
        var options = OpenAiChatOptions.builder().model("gpt-4o-mini").build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    // 2. Fallback 1: Groq Llama 3.3 70B (fast, reliable)
    @Bean
    public ChatModel groqChatModel(@Value("${api.groq.key}") String apiKey) {
        var api = OpenAiApi.builder()
                .baseUrl("https://api.groq.com/openai")
                .apiKey(apiKey)
                .restClientBuilder(aiRestClientBuilder())
                .build();
        var options = OpenAiChatOptions.builder().model("llama-3.3-70b-versatile").build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    // 3. Fallback 2: Groq Llama 3.1 8B Instant (fast, high availability)
    @Bean
    public ChatModel groqFastChatModel(@Value("${api.groq.key}") String apiKey) {
        var api = OpenAiApi.builder()
                .baseUrl("https://api.groq.com/openai")
                .apiKey(apiKey)
                .restClientBuilder(aiRestClientBuilder())
                .build();
        var options = OpenAiChatOptions.builder().model("llama-3.1-8b-instant").build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    // 4. Fallback 3: Google Gemini 2.0 Flash
    @Bean
    public ChatModel geminiChatModel(@Value("${api.gemini.key}") String apiKey) {
        var api = OpenAiApi.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/openai")
                .completionsPath("/chat/completions")
                .apiKey(apiKey)
                .restClientBuilder(aiRestClientBuilder())
                .build();
        var options = OpenAiChatOptions.builder().model("gemini-2.0-flash").build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    // 5. Fallback 4: Google Gemini 2.0 Flash-Lite (separate quota bucket)
    @Bean
    public ChatModel geminiLiteChatModel(@Value("${api.gemini.key}") String apiKey) {
        var api = OpenAiApi.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/openai")
                .completionsPath("/chat/completions")
                .apiKey(apiKey)
                .restClientBuilder(aiRestClientBuilder())
                .build();
        var options = OpenAiChatOptions.builder().model("gemini-2.0-flash-lite").build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }
}

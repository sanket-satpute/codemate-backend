package com.codescope.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {
		RedisRepositoriesAutoConfiguration.class,
		JpaRepositoriesAutoConfiguration.class,
		OpenAiAudioSpeechAutoConfiguration.class,
		OpenAiAudioTranscriptionAutoConfiguration.class,
		OpenAiChatAutoConfiguration.class,
		OpenAiEmbeddingAutoConfiguration.class,
		OpenAiImageAutoConfiguration.class,
		OpenAiModerationAutoConfiguration.class
})
@EnableAsync
public class BackendApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		dotenv.entries().forEach(entry ->
			System.setProperty(entry.getKey(), entry.getValue())
		);
		SpringApplication.run(BackendApplication.class, args);

	}

}

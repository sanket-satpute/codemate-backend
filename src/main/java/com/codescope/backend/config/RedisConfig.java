package com.codescope.backend.config;

import com.codescope.backend.analysisjob.model.AnalysisJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.url:}") String redisUrl,
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.username:}") String username,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean sslEnabled) {

        RedisStandaloneConfiguration configuration;
        LettuceClientConfiguration clientConfiguration;

        if (redisUrl != null && !redisUrl.isBlank()) {
            URI uri = URI.create(redisUrl);
            configuration = new RedisStandaloneConfiguration(uri.getHost(), uri.getPort());

            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String[] credentials = userInfo.split(":", 2);
                if (credentials.length > 0 && !credentials[0].isBlank()) {
                    configuration.setUsername(credentials[0]);
                }
                if (credentials.length > 1 && !credentials[1].isBlank()) {
                    configuration.setPassword(RedisPassword.of(credentials[1]));
                }
            }

            boolean useSsl = "rediss".equalsIgnoreCase(uri.getScheme());
            clientConfiguration = useSsl
                    ? LettuceClientConfiguration.builder().useSsl().build()
                    : LettuceClientConfiguration.builder().build();
        } else {
            configuration = new RedisStandaloneConfiguration(host, port);
            if (username != null && !username.isBlank()) {
                configuration.setUsername(username);
            }
            if (password != null && !password.isBlank()) {
                configuration.setPassword(RedisPassword.of(password));
            }

            clientConfiguration = sslEnabled
                    ? LettuceClientConfiguration.builder().useSsl().build()
                    : LettuceClientConfiguration.builder().build();
        }

        return new LettuceConnectionFactory(configuration, clientConfiguration);
    }

    @Bean
    public ReactiveRedisTemplate<String, AnalysisJob> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<AnalysisJob> serializer = new Jackson2JsonRedisSerializer<>(AnalysisJob.class);

        RedisSerializationContext.RedisSerializationContextBuilder<String, AnalysisJob> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

        RedisSerializationContext<String, AnalysisJob> context = builder.value(serializer).build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}

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
            ParsedRedisUrl parsed = parseRedisUrl(redisUrl);
            configuration = new RedisStandaloneConfiguration(parsed.host(), parsed.port());
            if (parsed.username() != null && !parsed.username().isBlank()) {
                configuration.setUsername(parsed.username());
            }
            if (parsed.password() != null && !parsed.password().isBlank()) {
                configuration.setPassword(RedisPassword.of(parsed.password()));
            }

            boolean useSsl = parsed.ssl();
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

    private ParsedRedisUrl parseRedisUrl(String redisUrl) {
        int schemeSeparator = redisUrl.indexOf("://");
        if (schemeSeparator < 0) {
            throw new IllegalArgumentException("Redis URL must start with redis:// or rediss://");
        }

        String scheme = redisUrl.substring(0, schemeSeparator);
        String remainder = redisUrl.substring(schemeSeparator + 3);
        boolean ssl = "rediss".equalsIgnoreCase(scheme);
        if (!ssl && !"redis".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Unsupported Redis URL scheme: " + scheme);
        }

        int atIndex = remainder.lastIndexOf('@');
        String userInfo = atIndex >= 0 ? remainder.substring(0, atIndex) : "";
        String hostPort = atIndex >= 0 ? remainder.substring(atIndex + 1) : remainder;

        int colonIndex = userInfo.indexOf(':');
        String username = colonIndex >= 0 ? userInfo.substring(0, colonIndex) : userInfo;
        String password = colonIndex >= 0 ? userInfo.substring(colonIndex + 1) : "";

        int portSeparator = hostPort.lastIndexOf(':');
        if (portSeparator < 0) {
            throw new IllegalArgumentException("Redis URL must include host and port");
        }

        String parsedHost = hostPort.substring(0, portSeparator);
        String parsedPort = hostPort.substring(portSeparator + 1);
        if (parsedHost.isBlank()) {
            throw new IllegalArgumentException("Redis host must not be empty");
        }

        int portNumber;
        try {
            portNumber = Integer.parseInt(parsedPort);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Redis port is invalid: " + parsedPort, ex);
        }

        return new ParsedRedisUrl(parsedHost, portNumber, username, password, ssl);
    }

    private record ParsedRedisUrl(String host, int port, String username, String password, boolean ssl) {
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

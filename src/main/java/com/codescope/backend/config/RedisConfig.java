package com.codescope.backend.config;

import com.codescope.backend.analysisjob.model.AnalysisJob;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, AnalysisJob> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<AnalysisJob> serializer = new Jackson2JsonRedisSerializer<>(AnalysisJob.class);

        RedisSerializationContext.RedisSerializationContextBuilder<String, AnalysisJob> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

        RedisSerializationContext<String, AnalysisJob> context = builder.value(serializer).build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}

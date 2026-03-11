package com.codescope.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@ConditionalOnProperty(name = "app.repositories.enabled", havingValue = "true", matchIfMissing = false)
@EnableReactiveMongoRepositories(basePackages = {
                "com.codescope.backend.project.repository",
                "com.codescope.backend.chat",
                "com.codescope.backend.repository",
                "com.codescope.backend.upload.repository",
                "com.codescope.backend.analysisjob.repository"
})
@EnableRedisRepositories(basePackages = {
                "com.codescope.backend.chat"
})
public class RepositoryConfig {
}

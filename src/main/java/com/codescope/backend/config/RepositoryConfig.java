package com.codescope.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

@Configuration
@ConditionalOnProperty(name = "app.repositories.enabled", havingValue = "true", matchIfMissing = false)
@EnableReactiveMongoRepositories(basePackages = {
                "com.codescope.backend.project.repository",
                "com.codescope.backend.chat",
                "com.codescope.backend.repository",
                "com.codescope.backend.upload.repository",
                "com.codescope.backend.analysisjob.repository"
})
public class RepositoryConfig {
}

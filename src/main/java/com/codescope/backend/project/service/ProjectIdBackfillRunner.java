package com.codescope.backend.project.service;

import com.codescope.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "project.id.backfill.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ProjectIdBackfillRunner implements ApplicationRunner {

    private final ProjectRepository projectRepository;

    @Override
    public void run(ApplicationArguments args) {
        Long updatedCount = projectRepository.findAll()
                .filter(project -> project.getProjectId() == null || project.getProjectId().isBlank())
                .flatMap(project -> {
                    if (project.getId() == null || project.getId().isBlank()) {
                        return projectRepository.save(project);
                    }
                    project.setProjectId(project.getId());
                    return projectRepository.save(project);
                })
                .count()
                .onErrorResume(error -> {
                    log.warn("Project ID backfill skipped due to error: {}", error.getMessage());
                    return reactor.core.publisher.Mono.just(0L);
                })
                .block();

        if (updatedCount != null && updatedCount > 0) {
            log.info("Project ID backfill completed. Updated records: {}", updatedCount);
        }
    }
}

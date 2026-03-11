package com.codescope.backend.upload.repository;

import com.codescope.backend.upload.model.ProjectFile;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ProjectFileRepository extends ReactiveMongoRepository<ProjectFile, String> {
    Flux<ProjectFile> findByProjectId(String projectId);

    Mono<ProjectFile> findByProjectIdAndId(String projectId, String id);

    Mono<Void> deleteByProjectIdAndId(String projectId, String id);

    Mono<Long> countByProjectIdIn(java.util.List<String> projectIds);
}

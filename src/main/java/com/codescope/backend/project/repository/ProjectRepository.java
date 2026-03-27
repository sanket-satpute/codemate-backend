package com.codescope.backend.project.repository;

import com.codescope.backend.project.model.Project;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ProjectRepository extends ReactiveMongoRepository<Project, String> {
    Flux<Project> findByOwnerId(String ownerId);
    Mono<Project> findByIdAndOwnerId(String id, String ownerId);
    Mono<Project> findByProjectId(String projectId);
    Mono<Project> findByProjectIdAndOwnerId(String projectId, String ownerId);
}

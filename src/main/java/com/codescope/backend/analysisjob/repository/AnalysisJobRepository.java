package com.codescope.backend.analysisjob.repository;

import com.codescope.backend.analysisjob.model.AnalysisJob;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface AnalysisJobRepository extends ReactiveMongoRepository<AnalysisJob, String> {
    Mono<AnalysisJob> findByJobId(String jobId);
    Mono<AnalysisJob> findByJobIdAndProjectId(String jobId, String projectId);
    Flux<AnalysisJob> findByProjectId(String projectId);
    Flux<AnalysisJob> findByProjectIdIn(List<String> projectIds);
    Flux<AnalysisJob> findTop10ByProjectIdOrderByCreatedAtDesc(String projectId);
}

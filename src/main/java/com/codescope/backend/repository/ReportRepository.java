package com.codescope.backend.repository;

import com.codescope.backend.model.Report;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ReportRepository extends ReactiveMongoRepository<Report, String> {
    Flux<Report> findByProjectId(String projectId);
    Mono<Report> findByJobId(String jobId);
}

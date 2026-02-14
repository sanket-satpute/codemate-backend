package com.codescope.backend.analysisjob.repository;

import com.codescope.backend.analysisjob.model.AnalysisJob;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisJobRepository extends CrudRepository<AnalysisJob, String> {
    Optional<AnalysisJob> findByJobId(String jobId);
    Optional<AnalysisJob> findByJobIdAndProjectId(String jobId, String projectId);
    List<AnalysisJob> findByProjectId(String projectId);
    List<AnalysisJob> findTop10ByProjectIdOrderByCreatedAtDesc(String projectId);
    Optional<AnalysisJob> findByIdAndProjectId(String id, String projectId); // Changed ID type to String
}

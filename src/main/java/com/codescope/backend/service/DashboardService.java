package com.codescope.backend.service;

import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.project.model.Project;
import com.codescope.backend.dto.dashboard.DashboardDTO;
import com.codescope.backend.analysisjob.enums.JobStatus;
import com.codescope.backend.analysisjob.repository.AnalysisJobRepository;
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.upload.repository.ProjectFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final ProjectFileRepository projectFileRepository;

    public DashboardService(ProjectRepository projectRepository, AnalysisJobRepository analysisJobRepository,
            ProjectFileRepository projectFileRepository) {
        this.projectRepository = projectRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.projectFileRepository = projectFileRepository;
    }

    public Mono<DashboardDTO> getDashboardData(String userId) {
        log.info("Fetching dashboard data for user: {}", userId);
        Mono<List<Project>> projectsMono = projectRepository.findByOwnerId(userId).collectList();

        return projectsMono.flatMap(projects -> {
            List<String> projectIds = projects.stream()
                    .map(Project::getProjectId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();

            if (projectIds.isEmpty()) {
                DashboardDTO emptyDto = new DashboardDTO();
                emptyDto.setModelUsage(Map.of());
                return Mono.just(emptyDto);
            }

            Mono<List<AnalysisJob>> jobsMono = analysisJobRepository.findByProjectIdIn(projectIds).collectList();
            Mono<Long> filesCountMono = projectFileRepository.countByProjectIdIn(projectIds).defaultIfEmpty(0L);

            return Mono.zip(Mono.just(projects), jobsMono, filesCountMono)
                    .map(tuple -> {
                        List<Project> projs = tuple.getT1();
                        List<AnalysisJob> jobs = tuple.getT2();
                        Long totalFiles = tuple.getT3();

                        DashboardDTO dto = new DashboardDTO();
                        dto.setTotalProjects(projs.size());
                        dto.setTotalJobs(jobs.size());
                        dto.setTotalFiles(totalFiles);

                        long successfulJobs = jobs.stream().filter(job -> JobStatus.COMPLETED.equals(job.getStatus()))
                                .count();
                        dto.setSuccessfulJobs(successfulJobs);
                        dto.setFailedJobs(jobs.size() - successfulJobs);

                        Map<String, Long> modelUsage = jobs.stream()
                                .filter(job -> job.getModel() != null)
                                .collect(Collectors.groupingBy(AnalysisJob::getModel, Collectors.counting()));
                        dto.setModelUsage(modelUsage);

                        String lastActive = jobs.stream()
                                .map(AnalysisJob::getUpdatedAt)
                                .filter(java.util.Objects::nonNull)
                                .max(Comparator.naturalOrder())
                                .map(java.time.LocalDateTime::toString)
                                .orElse(null);

                        // Fallback to project creation if no jobs
                        if (lastActive == null) {
                            lastActive = projs.stream()
                                    .map(Project::getUpdatedAt)
                                    .filter(java.util.Objects::nonNull)
                                    .max(Comparator.naturalOrder())
                                    .map(java.time.LocalDateTime::toString)
                                    .orElse(null);
                        }

                        dto.setLastActive(lastActive);

                        return dto;
                    });
        });
    }
}

package com.codescope.backend.service;

import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.project.model.Project;
import com.codescope.backend.dto.dashboard.DashboardDTO;
import com.codescope.backend.analysisjob.enums.JobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DashboardService {

    private final FirebaseService firebaseService;

    public DashboardService(FirebaseService firebaseService) {
        this.firebaseService = firebaseService;
    }

    public Mono<DashboardDTO> getDashboardData(String userId) {
        log.info("Fetching dashboard data for user: {}", userId);
        Mono<List<Project>> projectsMono = firebaseService.getProjectsByUserId(userId);
        Mono<List<AnalysisJob>> jobsMono = firebaseService.getJobsByUserId(userId);

        return Mono.zip(projectsMono, jobsMono)
                .map(tuple -> {
                    List<Project> projects = tuple.getT1();
                    List<AnalysisJob> jobs = tuple.getT2();

                    DashboardDTO dto = new DashboardDTO();
                    dto.setTotalProjects(projects.size());
                    dto.setTotalJobs(jobs.size());

                    long successfulJobs = jobs.stream().filter(job -> JobStatus.COMPLETED.equals(job.getStatus())).count();
                    dto.setSuccessfulJobs(successfulJobs);
                    dto.setFailedJobs(jobs.size() - successfulJobs);

                    Map<String, Long> modelUsage = jobs.stream()
                            .filter(job -> job.getModel() != null)
                            .collect(Collectors.groupingBy(AnalysisJob::getModel, Collectors.counting()));
                    dto.setModelUsage(modelUsage);

                    return dto;
                });
    }
}

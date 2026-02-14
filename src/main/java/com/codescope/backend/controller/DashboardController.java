package com.codescope.backend.controller;

import com.codescope.backend.dto.BaseResponse;
import com.codescope.backend.dto.dashboard.DashboardDTO;
import com.codescope.backend.exception.ResourceNotFoundException;
import com.codescope.backend.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/dashboard")
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<BaseResponse<DashboardDTO>>> getDashboardData(@RequestParam String userId) {
        log.info("Fetching dashboard data for user: {}", userId);
        return dashboardService.getDashboardData(userId)
                .map(dashboardDTO -> ResponseEntity.ok(new BaseResponse<>(true, "Dashboard data retrieved successfully", dashboardDTO)))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Dashboard data not found for user: " + userId)))
                .onErrorResume(e -> {
                    log.error("Error fetching dashboard data for user {}: {}", userId, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new BaseResponse<>(false, "Failed to retrieve dashboard data: " + e.getMessage(), null)));
                });
    }
}

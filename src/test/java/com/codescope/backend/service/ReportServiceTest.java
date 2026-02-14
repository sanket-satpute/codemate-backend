package com.codescope.backend.service;

import com.codescope.backend.dto.report.ReportDto;
import com.codescope.backend.exception.ResourceNotFoundException;
import com.codescope.backend.model.Report; // Keep this for ReportService.saveReport argument
import com.codescope.backend.dto.report.ReportCreationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportService (mocking FirebaseService)
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @InjectMocks
    private ReportService reportService;

    @Mock
    private FirebaseService firebaseService;

    private String testProjectId = "project123";
    private String testReportId = "report456";
    private Report mockReport;
    private ReportDto mockReportDto;

    @BeforeEach
    void setUp() {
        mockReport = new Report();
        mockReport.setReportId(testReportId);
        mockReport.setProjectId(testProjectId);
        mockReport.setGeneratedAt(Date.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)));
        mockReport.setSummary("Summary of findings");
        mockReport.setFindings(List.of(Map.of("type", "INFO", "message", "Test finding")));

        mockReportDto = new ReportDto();
        mockReportDto.setReportId(testReportId);
        mockReportDto.setProjectId(testProjectId);
        mockReportDto.setGeneratedAt(mockReport.getGeneratedAt());
        mockReportDto.setSummary("Summary of findings");
        mockReportDto.setFindings(List.of(Map.of("type", "INFO", "message", "Test finding")));
    }

    // SAVE REPORT TEST
    @Test
    @DisplayName("Should save a new report and return its ID")
    void saveReport_shouldReturnReportId() {
        Report newReport = new Report(); // FirebaseService.saveReport expects a Report model
        when(firebaseService.saveReport(any(Report.class))).thenReturn(Mono.just(testReportId));

        Mono<String> resultMono = reportService.saveReport(newReport);

        StepVerifier.create(resultMono)
                .expectNext(testReportId)
                .verifyComplete();
        verify(firebaseService, times(1)).saveReport(any(Report.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException if saving report fails")
    void saveReport_shouldThrowExceptionOnFailure() {
        Report newReport = new Report();
        when(firebaseService.saveReport(any(Report.class))).thenReturn(Mono.error(new RuntimeException("Firebase error")));

        Mono<String> resultMono = reportService.saveReport(newReport);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Firebase error"))
                .verify();
        verify(firebaseService, times(1)).saveReport(any(Report.class));
    }

    // GET REPORTS BY PROJECT TEST
    @Test
    @DisplayName("Should fetch all reports for a project")
    void getReportsByProject_shouldReturnListOfReportDtos() {
        List<ReportDto> mockReportDtos = List.of(mockReportDto);
        when(firebaseService.getReportsByProject(testProjectId)).thenReturn(Mono.just(mockReportDtos));

        Mono<List<ReportDto>> resultMono = reportService.getReportsByProject(testProjectId);

        StepVerifier.create(resultMono)
                .expectNext(mockReportDtos)
                .verifyComplete();
        verify(firebaseService, times(1)).getReportsByProject(testProjectId);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when no reports found for project")
    void getReportsByProject_shouldThrowNotFound() {
        when(firebaseService.getReportsByProject(testProjectId)).thenReturn(Mono.empty());

        Mono<List<ReportDto>> resultMono = reportService.getReportsByProject(testProjectId);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof ResourceNotFoundException &&
                        throwable.getMessage().contains("No reports found for project ID: " + testProjectId))
                .verify();
        verify(firebaseService, times(1)).getReportsByProject(testProjectId);
    }

    // GET REPORT BY ID TEST
    @Test
    @DisplayName("Should fetch single report by ID")
    void getReportById_shouldReturnReportDto() {
        when(firebaseService.getReportById(testReportId)).thenReturn(Mono.just(mockReportDto));

        Mono<ReportDto> resultMono = reportService.getReportById(testReportId);

        StepVerifier.create(resultMono)
                .expectNext(mockReportDto)
                .verifyComplete();
        verify(firebaseService, times(1)).getReportById(testReportId);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when report not found by ID")
    void getReportById_shouldThrowNotFound() {
        when(firebaseService.getReportById(testReportId)).thenReturn(Mono.empty());

        Mono<ReportDto> resultMono = reportService.getReportById(testReportId);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof ResourceNotFoundException &&
                        throwable.getMessage().contains("Report not found with ID: " + testReportId))
                .verify();
        verify(firebaseService, times(1)).getReportById(testReportId);
    }

    // GENERATE PDF TEST (unit test for the helper method)
    @Test
    @DisplayName("Should generate PDF bytes for a given ReportDto")
    void generateReportPdf_shouldReturnPdfBytes() throws Exception {
        byte[] pdfBytes = reportService.generateReportPdf(mockReportDto);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }
}

package com.codescope.backend.service;

import com.codescope.backend.dto.report.ReportDto;
import com.codescope.backend.exception.ResourceNotFoundException;
import com.codescope.backend.model.Report; // Keep this for ReportService.saveReport argument
import com.codescope.backend.repository.ReportRepository;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportService (mocking ReportRepository)
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @InjectMocks
    private ReportService reportService;

    @Mock
    private ReportRepository reportRepository;

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
        Report newReport = new Report();
        when(reportRepository.save(any(Report.class))).thenReturn(Mono.just(mockReport));

        Mono<String> resultMono = reportService.saveReport(newReport);

        StepVerifier.create(resultMono)
                .expectNext(testReportId)
                .verifyComplete();
        verify(reportRepository, times(1)).save(any(Report.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException if saving report fails")
    void saveReport_shouldThrowExceptionOnFailure() {
        Report newReport = new Report();
        when(reportRepository.save(any(Report.class))).thenReturn(Mono.error(new RuntimeException("Database error")));

        Mono<String> resultMono = reportService.saveReport(newReport);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Database error"))
                .verify();
        verify(reportRepository, times(1)).save(any(Report.class));
    }

    // GET REPORTS BY PROJECT TEST
    @Test
    @DisplayName("Should fetch all reports for a project")
    void getReportsByProject_shouldReturnListOfReportDtos() {
        when(reportRepository.findByProjectId(testProjectId)).thenReturn(reactor.core.publisher.Flux.just(mockReport));

        Mono<List<ReportDto>> resultMono = reportService.getReportsByProject(testProjectId);

        StepVerifier.create(resultMono)
                .assertNext(reportDtos -> {
                    assertEquals(1, reportDtos.size());
                    assertEquals(testReportId, reportDtos.get(0).getReportId());
                })
                .verifyComplete();
        verify(reportRepository, times(1)).findByProjectId(testProjectId);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when no reports found for project")
    void getReportsByProject_shouldThrowNotFound() {
        when(reportRepository.findByProjectId(testProjectId)).thenReturn(reactor.core.publisher.Flux.empty());

        Mono<List<ReportDto>> resultMono = reportService.getReportsByProject(testProjectId);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof ResourceNotFoundException &&
                        throwable.getMessage().contains("No reports found for project ID: " + testProjectId))
                .verify();
        verify(reportRepository, times(1)).findByProjectId(testProjectId);
    }

    // GET REPORT BY ID TEST
    @Test
    @DisplayName("Should fetch single report by ID")
    void getReportById_shouldReturnReportDto() {
        when(reportRepository.findById(testReportId)).thenReturn(Mono.just(mockReport));

        Mono<ReportDto> resultMono = reportService.getReportById(testReportId);

        StepVerifier.create(resultMono)
                .assertNext(reportDto -> assertEquals(testReportId, reportDto.getReportId()))
                .verifyComplete();
        verify(reportRepository, times(1)).findById(testReportId);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when report not found by ID")
    void getReportById_shouldThrowNotFound() {
        when(reportRepository.findById(testReportId)).thenReturn(Mono.empty());

        Mono<ReportDto> resultMono = reportService.getReportById(testReportId);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable instanceof ResourceNotFoundException &&
                        throwable.getMessage().contains("Report not found with ID: " + testReportId))
                .verify();
        verify(reportRepository, times(1)).findById(testReportId);
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

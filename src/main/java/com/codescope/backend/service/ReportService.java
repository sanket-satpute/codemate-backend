package com.codescope.backend.service;

import com.codescope.backend.dto.report.ReportDto;
import com.codescope.backend.exception.ResourceNotFoundException;
import com.codescope.backend.model.Report;
import com.itextpdf.text.Document;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReportService {

    private final FirebaseService firebaseService;

    public ReportService(FirebaseService firebaseService) {
        this.firebaseService = firebaseService;
    }

    /**
     * ✅ 1️⃣ Save Report to Firebase
     */
    public Mono<String> saveReport(Report report) {
        if (report.getReportId() == null || report.getReportId().isEmpty()) {
            report.setReportId(java.util.UUID.randomUUID().toString());
        }
        return firebaseService.saveReport(report)
                .thenReturn(report.getReportId())
                .switchIfEmpty(Mono.error(new RuntimeException("Failed to save report to Firebase.")));
    }

    /**
     * ✅ 2️⃣ Fetch all reports for a project
     */
    public Mono<List<ReportDto>> getReportsByProject(String projectId) {
        log.info("Fetching all reports for project ID: {}", projectId);
        return firebaseService.getReportsByProject(projectId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("No reports found for project ID: " + projectId)));
    }

    /**
     * ✅ 3️⃣ Fetch single report by ID
     */
    public Mono<ReportDto> getReportById(String reportId) {
        log.info("Fetching report by ID: {}", reportId);
        return firebaseService.getReportById(reportId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Report not found with ID: " + reportId)));
    }

    /**
     * ✅ 4️⃣ Generate PDF for a given report
     */
    public byte[] generateReportPdf(ReportDto reportDto) throws Exception {
        log.info("Generating PDF for report ID: {}", reportDto.getReportId());
        Document document = new Document();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);
        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font contentFont = FontFactory.getFont(FontFactory.HELVETICA, 12);

        document.add(new Paragraph("AI CodeScope - Project Report", titleFont));
        document.add(new Paragraph("Report ID: " + reportDto.getReportId(), contentFont));
        document.add(new Paragraph("Project ID: " + reportDto.getProjectId(), contentFont));
        document.add(new Paragraph("Summary: " + reportDto.getSummary(), contentFont));
        document.add(new Paragraph("Generated At: " + reportDto.getGeneratedAt(), contentFont));
        document.add(new Paragraph("--------------------------------------------------------"));

        if (reportDto.getFindings() != null && !reportDto.getFindings().isEmpty()) {
            document.add(new Paragraph("Findings:", titleFont));
            for (var finding : reportDto.getFindings()) {
                document.add(new Paragraph(
                        "• [" + finding.get("type") + "] " + finding.get("message"), contentFont
                ));
            }
        }

        document.close();
        return baos.toByteArray();
    }
}

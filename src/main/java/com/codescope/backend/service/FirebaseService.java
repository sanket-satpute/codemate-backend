package com.codescope.backend.service;

import com.codescope.backend.dto.chat.ChatMessageDto;
import com.codescope.backend.dto.job.JobDto;
import com.codescope.backend.dto.project.ProjectDto;
import com.codescope.backend.dto.report.ReportDto;
import com.codescope.backend.dto.upload.FileDocumentDto;
import com.codescope.backend.analysisjob.model.AnalysisJob;
import com.codescope.backend.chat.ChatMessage;
import com.codescope.backend.upload.model.ProjectFile;
import com.codescope.backend.project.model.Project;
import com.codescope.backend.model.Report;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.firebase.cloud.FirestoreClient;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import com.google.cloud.firestore.FirestoreException;
import io.grpc.Status.Code;

@Service
@Slf4j
public class FirebaseService {

    private Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }

    /**
     * Adds a FileDocument to an existing Project in Firestore.
     * If the project does not exist, it will return an error.
     * If the project exists, it will add the file to the 'files' array.
     * @param projectId The ID of the project to add the file to.
     * @param fileDocument The FileDocument to add.
     * @param aiModelUsed The AI model used for correction.
     * @param correctionSummary A summary of the correction made.
     * @return A Mono emitting the projectId upon successful update.
     */
    public Mono<String> addFileToProject(String projectId, ProjectFile fileDocument, String aiModelUsed, String correctionSummary) {
        DocumentReference projectRef = getFirestore().collection("projects").document(projectId);

        // Create a map for the update operation
        Map<String, Object> updates = new HashMap<>();
        updates.put("files", FieldValue.arrayUnion(fileDocument));
        updates.put("lastCorrectedAt", Date.from(LocalDateTime.now().toInstant(ZoneOffset.UTC))); // Use java.util.Date as per Project model
        updates.put("lastCorrectedByModel", aiModelUsed);
        updates.put("lastCorrectionSummary", correctionSummary);

        return toMono(projectRef.update(updates))
                .thenReturn(projectId)
                .onErrorResume(e -> {
                    if (e instanceof ExecutionException && e.getCause() instanceof FirestoreException && ((FirestoreException) e.getCause()).getCode() == Code.NOT_FOUND.value()) {
                        return Mono.error(new IllegalArgumentException("Project with ID " + projectId + " not found."));
                    }
                    return Mono.error(new RuntimeException("Failed to add file to project " + projectId + ": " + e.getMessage(), e));
                });
    }

    // Helper to convert ApiFuture to Mono
    private <T> Mono<T> toMono(ApiFuture<T> apiFuture) {
        return Mono.create(sink -> {
            apiFuture.addListener(() -> {
                try {
                    sink.success(apiFuture.get());
                } catch (Exception e) {
                    sink.error(e);
                }
            }, MoreExecutors.directExecutor()); // Use directExecutor for simplicity, consider a proper executor for production
        });
    }

    // ✅ Save or update a project
    public Mono<String> saveProject(Project project) {
        Firestore db = getFirestore();
        DocumentReference docRef;
        if (project.getProjectId() == null || project.getProjectId().isEmpty()) {
            docRef = db.collection("projects").document();
            project.setProjectId(docRef.getId());
        } else {
            docRef = db.collection("projects").document(project.getProjectId());
        }
        // Use reactive set operation
        return toMono(docRef.set(project)).thenReturn(project.getProjectId());
    }

    // ✅ Get a project by ID
    public Mono<Project> getProjectById(String projectId) {
        DocumentReference docRef = getFirestore().collection("projects").document(projectId);
        return toMono(docRef.get())
                .map(snapshot -> snapshot.exists() ? snapshot.toObject(Project.class) : null);
    }

    // ✅ Get jobs by ownerId for DashboardService
    public Mono<List<AnalysisJob>> getJobsByUserId(String ownerId) {
        CollectionReference jobsRef = getFirestore().collection("jobs");
        Query query = jobsRef.whereEqualTo("ownerId", ownerId);
        return toMono(query.get())
                .map(queryDocumentSnapshot -> {
                    return queryDocumentSnapshot.getDocuments().stream()
                            .map(doc -> doc.toObject(AnalysisJob.class))
                            .collect(Collectors.toList());
                });
    }

    // ✅ List projects by ownerId
    public Mono<List<Project>> getProjectsByUserId(String ownerId) {
        CollectionReference projectsRef = getFirestore().collection("projects");
        Query query = projectsRef.whereEqualTo("ownerId", ownerId);
        return toMono(query.get())
                .map(queryDocumentSnapshot -> {
                    return queryDocumentSnapshot.getDocuments().stream()
                            .map(doc -> doc.toObject(Project.class))
                            .collect(Collectors.toList());
                });
    }

    // ✅ Save or update a job
    public Mono<String> saveJob(AnalysisJob job) {
        Firestore db = getFirestore();
        DocumentReference docRef;
        if (job.getJobId() == null || job.getJobId().isEmpty()) {
            docRef = db.collection("jobs").document();
            job.setJobId(docRef.getId());
        } else {
            docRef = db.collection("jobs").document(job.getJobId());
        }
        // Use reactive set operation
        return toMono(docRef.set(job)).thenReturn(job.getJobId());
    }

    // ✅ Update job status only (lightweight)
    public Mono<Void> updateJobStatus(String jobId, String status, String message) {
        DocumentReference docRef = getFirestore().collection("jobs").document(jobId);
        Map<String, Object> update = new HashMap<>();
        update.put("status", status);
        update.put("message", message);
        update.put("updatedAt", Date.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)));
        // Use reactive update operation and then discard the result to return Mono<Void>
        return toMono(docRef.update(update)).then();
    }

    // ✅ Save report
    public Mono<String> saveReport(Report report) {
        Firestore db = getFirestore();
        DocumentReference docRef;
        if (report.getReportId() == null || report.getReportId().isEmpty()) {
            docRef = db.collection("reports").document();
            report.setReportId(docRef.getId());
        } else {
            docRef = db.collection("reports").document(report.getReportId());
        }
        // Use reactive set operation
        return toMono(docRef.set(report)).thenReturn(report.getReportId());
    }

    // ✅ Get report by project ID
    public Mono<ReportDto> getReportByProjectId(String projectId) {
        Query query = getFirestore().collection("reports").whereEqualTo("projectId", projectId);
        return toMono(query.get())
                .map(queryDocumentSnapshot -> {
                    if (queryDocumentSnapshot.isEmpty()) return null;
                    return convertToDto(queryDocumentSnapshot.getDocuments().get(0).toObject(Report.class));
                });
    }

    // Fetch all jobs for a project
    public Mono<List<JobDto>> getJobsByProjectId(String projectId) {
        Query query = getFirestore().collection("jobs").whereEqualTo("projectId", projectId);
        return toMono(query.get())
                .map(queryDocumentSnapshot -> {
                    return queryDocumentSnapshot.getDocuments().stream()
                            .map(doc -> convertToDto(doc.toObject(AnalysisJob.class)))
                            .collect(Collectors.toList());
                });
    }

    // Fetch all reports for a project
    public Mono<List<ReportDto>> getReportsByProjectId(String projectId) {
        Query query = getFirestore().collection("reports").whereEqualTo("projectId", projectId);
        return toMono(query.get())
                .map(queryDocumentSnapshot -> {
                    return queryDocumentSnapshot.getDocuments().stream()
                            .map(doc -> convertToDto(doc.toObject(Report.class)))
                            .collect(Collectors.toList());
                });
    }

    // Fetch project with jobs & reports
    public Mono<ProjectDto> getProjectWithJobsAndReports(String projectId) {
        return getProjectById(projectId)
                .flatMap(project -> {
                    if (project != null) {
                        return Mono.zip(
                                getJobsByProjectId(projectId),
                                getReportsByProjectId(projectId)
                        ).map(tuple -> {
                            ProjectDto projectDto = convertToDto(project);
                            projectDto.setJobs(tuple.getT1());
                            projectDto.setReports(tuple.getT2());
                            return projectDto;
                        });
                    } else {
                        return Mono.empty();
                    }
                });
    }

    // Fetch all projects with jobs & reports
    public Mono<List<ProjectDto>> getAllProjectsWithJobsAndReports() {
        return getAllProjects()
                .flatMapMany(Flux::fromIterable)
                .flatMap(p -> getProjectWithJobsAndReports(p.getProjectId()))
                .collectList();
    }

    // New method to get all projects
    public Mono<List<Project>> getAllProjects() {
        CollectionReference projectsRef = getFirestore().collection("projects");
        return toMono(projectsRef.get())
                .map(queryDocumentSnapshot -> {
                    return queryDocumentSnapshot.getDocuments().stream()
                            .map(doc -> doc.toObject(Project.class))
                            .collect(Collectors.toList());
                });
    }

    public Mono<List<ReportDto>> getReportsByProject(String projectId) {
        Query query = getFirestore().collection("reports").whereEqualTo("projectId", projectId);
        return toMono(query.get())
                .map(queryDocumentSnapshot -> {
                    return queryDocumentSnapshot.getDocuments().stream()
                            .map(doc -> convertToDto(doc.toObject(Report.class)))
                            .collect(Collectors.toList());
                });
    }

    public Mono<ReportDto> getReportById(String reportId) {
        DocumentReference docRef = getFirestore().collection("reports").document(reportId);
        return toMono(docRef.get())
                .map(doc -> {
                    if (doc.exists()) {
                        Report report = doc.toObject(Report.class);
                        report.setReportId(doc.getId());
                        return convertToDto(report);
                    } else {
                        return null;
                    }
                });
    }

    // ✅ Get job by ID
    public Mono<JobDto> getJob(String jobId) {
        DocumentReference docRef = getFirestore().collection("jobs").document(jobId);
        return toMono(docRef.get())
                .map(doc -> {
                    if (doc.exists()) {
                        AnalysisJob job = doc.toObject(AnalysisJob.class);
                        job.setJobId(doc.getId());
                        return convertToDto(job);
                    } else {
                        return null;
                    }
                });
    }

    // ✅ Get report by ID (alias for getReportById)
    public Mono<ReportDto> getReport(String reportId) {
        return getReportById(reportId);
    }

    /**
     * Retrieves a specific FileDocument from a Project by its ID.
     * @param projectId The ID of the project.
     * @param fileId The ID of the file document.
     * @return A Mono emitting the FileDocumentDto, or empty if not found.
     */
    public Mono<FileDocumentDto> getFileDocumentById(String projectId, String fileId) {
        DocumentReference docRef = getFirestore().collection("projects").document(projectId);
        return toMono(docRef.get())
                .map(snapshot -> {
                    if (snapshot.exists()) {
                        Project project = snapshot.toObject(Project.class);
                        if (project != null && project.getFiles() != null) {
                            return project.getFiles().stream()
                                    .filter(file -> file.getId().equals(Long.parseLong(fileId)))
                                    .findFirst()
                                    .map(this::convertToDto)
                                    .orElse(null);
                        }
                    }
                    return null;
                });
    }

    /**
     * Saves a chat message to a dedicated 'chatMessages' collection.
     * The document ID will be auto-generated.
     * @param chatMessage The ChatMessage object to save.
     * @return A Mono emitting the ID of the saved chat message.
     */
    public Mono<String> saveChatMessage(ChatMessage chatMessage) {
        Firestore db = getFirestore();
        DocumentReference docRef = db.collection("chatMessages").document();
        chatMessage.setId(docRef.getId());

        return toMono(docRef.set(chatMessage)).thenReturn(docRef.getId());
    }

    public Mono<Void> deleteProject(String projectId) {
        DocumentReference docRef = getFirestore().collection("projects").document(projectId);
        return toMono(docRef.delete()).then();
    }

    /**
     * Retrieves chat messages for a specific file within a project, ordered by timestamp.
     * @param projectId The ID of the project.
     * @param fileId The ID of the file document.
     * @return A Mono emitting a list of ChatMessageDto objects.
     */
    public Mono<List<ChatMessageDto>> getChatMessagesForFile(String projectId, String fileId) {
        CollectionReference chatMessagesRef = getFirestore().collection("chatMessages");
        Query query = chatMessagesRef
                .whereEqualTo("projectId", projectId)
                .whereEqualTo("fileId", fileId)
                .orderBy("timestamp", Query.Direction.ASCENDING);

        return toMono(query.get())
                .map(queryDocumentSnapshot -> {
                    return queryDocumentSnapshot.getDocuments().stream()
                            .map(doc -> convertToDto(doc.toObject(ChatMessage.class)))
                            .collect(Collectors.toList());
                });
    }

    // Conversion helpers
    private ProjectDto convertToDto(Project project) {
        if (project == null) return null;
        ProjectDto dto = new ProjectDto();
        dto.setProjectId(project.getProjectId());
        dto.setOwnerId(project.getOwnerId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        if (project.getFiles() != null) {
            dto.setFiles(project.getFiles().stream().map(this::convertToDto).collect(Collectors.toList()));
        }
        if (project.getJobs() != null) {
            dto.setJobs(project.getJobs().stream().map(this::convertToDto).collect(Collectors.toList()));
        }
        if (project.getReports() != null) {
            dto.setReports(project.getReports().stream().map(this::convertToDto).collect(Collectors.toList()));
        }
        return dto;
    }

    private JobDto convertToDto(AnalysisJob job) {
        if (job == null) return null;
        JobDto dto = new JobDto();
        dto.setJobId(job.getJobId());
        dto.setProjectId(job.getProjectId());
        dto.setStatus(job.getStatus().name());
        dto.setModel(job.getModel());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setUpdatedAt(job.getUpdatedAt());
        // AIResponse conversion is handled in its own DTO
        return dto;
    }

    private ReportDto convertToDto(Report report) {
        if (report == null) return null;
        ReportDto dto = new ReportDto();
        dto.setReportId(report.getReportId());
        dto.setProjectId(report.getProjectId());
        dto.setGeneratedAt(report.getGeneratedAt());
        dto.setSummary(report.getSummary());
        dto.setFindings(report.getFindings());
        return dto;
    }

    private FileDocumentDto convertToDto(ProjectFile fileDocument) {
        if (fileDocument == null) return null;
        return new FileDocumentDto(
                fileDocument.getFilename(),
                fileDocument.getFileType(),
                null,
                null
        );
    }

    private ChatMessageDto convertToDto(ChatMessage chatMessage) {
        if (chatMessage == null) return null;
        return new ChatMessageDto(
                chatMessage.getId(),
                Long.valueOf(chatMessage.getProjectId()),
                chatMessage.getSender(),
                chatMessage.getMessage(),
                chatMessage.getTimestamp()
        );
    }
}

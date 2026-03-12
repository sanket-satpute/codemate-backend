package com.codescope.backend.upload.service;

import com.codescope.backend.dto.upload.FileDocumentDto;
import com.codescope.backend.project.repository.ProjectRepository;
import com.codescope.backend.service.CloudinaryService;
import com.codescope.backend.upload.repository.ProjectFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private ProjectFileRepository projectFileRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(projectFileRepository, projectRepository, cloudinaryService);
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", "uploads");
    }

    @Test
    @DisplayName("Should preserve zip folder structure and upload order")
    void processAndStoreFile_zipUploadPreservesPathsAndOrder() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("src/App.java", "class App {}");
        entries.put("src/utils/Helper.java", "class Helper {}");
        entries.put("README.md", "# docs");

        byte[] zipBytes = createZip(entries);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "backend.zip",
                "application/zip",
                zipBytes
        );

        List<String> uploadedPaths = new ArrayList<>();
        List<String> uploadedFolders = new ArrayList<>();

        when(cloudinaryService.uploadBytes(any(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String filename = invocation.getArgument(1, String.class);
                    String folder = invocation.getArgument(2, String.class);
                    uploadedPaths.add(filename);
                    uploadedFolders.add(folder);

                    Map<String, Object> result = new HashMap<>();
                    result.put("secure_url", "https://cloudinary.example/" + folder + "/" + filename);
                    result.put("public_id", folder + "/" + filename);
                    result.put("bytes", 128L);
                    return reactor.core.publisher.Mono.just(result);
                });

        StepVerifier.create(fileStorageService.processAndStoreFile(file, "project-123"))
                .assertNext(files -> {
                    assertEquals(List.of("src/App.java", "src/utils/Helper.java", "README.md"),
                            files.stream().map(FileDocumentDto::getFileName).toList());
                    assertEquals(List.of(
                                    "codescope/uploads/project-123/backend",
                                    "codescope/uploads/project-123/backend",
                                    "codescope/uploads/project-123/backend"
                            ),
                            uploadedFolders);
                    assertEquals(List.of("src/App.java", "src/utils/Helper.java", "README.md"), uploadedPaths);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should skip unsafe zip entry paths")
    void processAndStoreFile_zipUploadSkipsUnsafePaths() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("../secrets.txt", "nope");
        entries.put("src/Safe.java", "class Safe {}");

        byte[] zipBytes = createZip(entries);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "backend.zip",
                "application/zip",
                zipBytes
        );

        when(cloudinaryService.uploadBytes(any(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String filename = invocation.getArgument(1, String.class);
                    String folder = invocation.getArgument(2, String.class);
                    Map<String, Object> result = new HashMap<>();
                    result.put("secure_url", "https://cloudinary.example/" + folder + "/" + filename);
                    result.put("public_id", folder + "/" + filename);
                    result.put("bytes", 64L);
                    return reactor.core.publisher.Mono.just(result);
                });

        StepVerifier.create(fileStorageService.processAndStoreFile(file, "project-123"))
                .assertNext(files -> assertEquals(List.of("src/Safe.java"),
                        files.stream().map(FileDocumentDto::getFileName).toList()))
                .verifyComplete();
    }

    private byte[] createZip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }
}
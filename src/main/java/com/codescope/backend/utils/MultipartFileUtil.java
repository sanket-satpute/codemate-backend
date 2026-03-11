package com.codescope.backend.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.codescope.backend.dto.upload.FileDocumentDto;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.UUID;
import org.springframework.util.StreamUtils;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

public final class MultipartFileUtil { // Add final to make it a true utility class
    private static final Logger log = LoggerFactory.getLogger(MultipartFileUtil.class);

    public static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
            "java", "py", "js", "ts", "html", "css", "json", "xml", "yml", "yaml", "md", "txt",
            "xlsx", "xls", "csv", "pdf", "docx", "doc", "sql", "sh", "bat", "properties",
            "jsx", "tsx", "scss", "less", "kt", "swift", "go", "rs", "c", "cpp", "h", "cs",
            "rb", "php", "r", "scala", "gradle", "toml", "ini", "cfg", "env", "log");

    private MultipartFileUtil() {
        // Private constructor to prevent instantiation
    }

    public static String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    public static String extractContent(InputStream inputStream, String fileExtension) throws IOException {
        // For simplicity, we'll only extract text content for common code files.
        // For PDFs, DOCX, etc., this would require dedicated libraries (e.g., Apache
        // Tika).
        // Since we're focusing on code analysis, we prioritize text-based content.

        // This is a basic implementation. For production, consider using Apache Tika
        // for robust content extraction.
        return new String(StreamUtils.copyToByteArray(inputStream), StandardCharsets.UTF_8);
    }

    public static List<FileDocumentDto> extractZipContent(InputStream zipInputStream, Path uploadPath,
            String originalZipFilename) throws IOException {
        List<FileDocumentDto> extractedFiles = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    String filename = zipEntry.getName();
                    // Extract just the filename, ignoring the directory structure within the zip
                    String[] pathParts = filename.split("[\\\\/]");
                    String baseFilename = pathParts[pathParts.length - 1];
                    String extension = getFileExtension(baseFilename);

                    if (ALLOWED_FILE_EXTENSIONS.contains(extension)) {
                        String uniqueFilename = UUID.randomUUID() + "_" + baseFilename;
                        Path targetLocation = uploadPath.resolve(uniqueFilename);

                        // Security check against Path Traversal (Zip Slip vulnerability)
                        if (!targetLocation.normalize().startsWith(uploadPath.normalize())) {
                            throw new IOException("Bad zip entry: " + zipEntry.getName());
                        }

                        // Read content into memory for DTO and save to disk
                        byte[] contentBytes = StreamUtils.copyToByteArray(zis);
                        Files.write(targetLocation, contentBytes);

                        String content = new String(contentBytes, StandardCharsets.UTF_8);
                        String url = UriComponentsBuilder.fromPath("/uploads/")
                                .path(uniqueFilename)
                                .toUriString();

                        // Determine a basic content type based on extension
                        String contentType = "text/plain";
                        if (extension.equals("json"))
                            contentType = "application/json";
                        else if (extension.equals("xml"))
                            contentType = "application/xml";
                        else if (extension.equals("html"))
                            contentType = "text/html";
                        else if (extension.equals("css"))
                            contentType = "text/css";
                        else if (extension.equals("js"))
                            contentType = "application/javascript";

                        extractedFiles.add(new FileDocumentDto(baseFilename, contentType, content, url));
                        log.info("Extracted supported file {} from zip {}", baseFilename, originalZipFilename);
                    } else {
                        log.debug("Skipped unsupported file {} from zip {}", baseFilename, originalZipFilename);
                    }
                }
                zis.closeEntry();
                zipEntry = zis.getNextEntry();
            }
        }
        return extractedFiles;
    }

    public static Mono<MultipartFile> toMultipartFile(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String contentType = filePart.headers().getContentType() != null
                            ? filePart.headers().getContentType().toString()
                            : null;
                    return (MultipartFile) new InMemoryMultipartFile(
                            filePart.name(),
                            filePart.filename(),
                            contentType,
                            bytes);
                });
    }

    /**
     * Inner class to implement MultipartFile for in-memory files.
     */
    public static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content; // Mark as final

        public InMemoryMultipartFile(final String name, final String originalFilename, final String contentType,
                final byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = Arrays.copyOf(content, content.length); // Defensive copy
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Arrays.copyOf(content, content.length); // Return defensive copy
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(final Path dest) throws IOException, IllegalStateException {
            Files.write(dest, content);
        }

        @Override
        public void transferTo(final java.io.File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content); // Use Files.write for consistency and modern NIO.2 API
        }
    }
}

package com.codescope.backend.utils;

import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MultipartFileUtil { // Add final to make it a true utility class
    private static final Logger log = LoggerFactory.getLogger(MultipartFileUtil.class);

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
        // For PDFs, DOCX, etc., this would require dedicated libraries (e.g., Apache Tika).
        // Since we're focusing on code analysis, we prioritize text-based content.
        
        // This is a basic implementation. For production, consider using Apache Tika for robust content extraction.
        return new String(StreamUtils.copyToByteArray(inputStream), StandardCharsets.UTF_8);
    }

    /**
     * Inner class to implement MultipartFile for in-memory files.
     */
    public static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content; // Mark as final

        public InMemoryMultipartFile(final String name, final String originalFilename, final String contentType, final byte[] content) {
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

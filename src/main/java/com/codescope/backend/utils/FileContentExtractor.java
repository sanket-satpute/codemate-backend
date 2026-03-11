package com.codescope.backend.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Component
public class FileContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(FileContentExtractor.class);

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "py", "js", "ts", "jsx", "tsx", "html", "css", "scss", "less",
            "json", "xml", "yml", "yaml", "md", "txt", "csv", "sql", "sh", "bat",
            "properties", "cfg", "ini", "toml", "gradle", "kt", "swift", "go",
            "rs", "c", "cpp", "h", "hpp", "cs", "rb", "php", "r", "scala",
            "dockerfile", "makefile", "gitignore", "env", "log");

    private static final Set<String> EXCEL_EXTENSIONS = Set.of("xlsx", "xls");

    private final Tika tika = new Tika();

    /**
     * Extract readable text content from a file based on its type.
     */
    public String extract(Path filePath) {
        String filename = filePath.getFileName().toString().toLowerCase();
        String ext = getExtension(filename);

        if (TEXT_EXTENSIONS.contains(ext) || isLikelyTextFile(filename)) {
            return readAsText(filePath);
        }

        if (EXCEL_EXTENSIONS.contains(ext)) {
            return readExcel(filePath);
        }

        if ("pdf".equals(ext) || "docx".equals(ext) || "doc".equals(ext)
                || "pptx".equals(ext) || "rtf".equals(ext)) {
            return readWithTika(filePath);
        }

        // For unknown types, try Tika as a fallback
        return readWithTika(filePath);
    }

    private String readAsText(Path filePath) {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read text file: {}", filePath, e);
            return "// Error reading file content";
        }
    }

    private String readExcel(Path filePath) {
        String ext = getExtension(filePath.getFileName().toString().toLowerCase());
        try (InputStream is = new BufferedInputStream(Files.newInputStream(filePath));
             Workbook workbook = "xlsx".equals(ext) ? new XSSFWorkbook(is) : new HSSFWorkbook(is)) {

            StringBuilder sb = new StringBuilder();
            DataFormatter formatter = new DataFormatter();

            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");

                for (Row row : sheet) {
                    boolean first = true;
                    for (Cell cell : row) {
                        if (!first) sb.append(" | ");
                        sb.append(formatter.formatCellValue(cell));
                        first = false;
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }

            String result = sb.toString();
            if (result.isBlank()) {
                return "// Excel file is empty";
            }
            return result;

        } catch (Exception e) {
            log.warn("Failed to parse Excel file: {}", filePath, e);
            return "// Error parsing Excel file: " + e.getMessage();
        }
    }

    private String readWithTika(Path filePath) {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(filePath))) {
            String content = tika.parseToString(is);
            if (content == null || content.isBlank()) {
                return "// File content could not be extracted";
            }
            return content;
        } catch (Exception e) {
            log.warn("Tika failed to parse file: {}", filePath, e);
            return "// Error extracting content from file: " + e.getMessage();
        }
    }

    private boolean isLikelyTextFile(String filename) {
        return filename.equals("dockerfile") || filename.equals("makefile")
                || filename.equals(".gitignore") || filename.equals(".env")
                || filename.endsWith(".config") || filename.endsWith(".rc");
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? filename : filename.substring(dot + 1);
    }
}

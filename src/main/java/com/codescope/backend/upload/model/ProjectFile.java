package com.codescope.backend.upload.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "project_files")
public class ProjectFile {

    @Id
    private String id;

    @Field("projectId")
    private String projectId;

    private String filename;

    private String filepath; // Stored path on the server

    private Long fileSize; // in bytes
    private String fileType; // MIME type

    @CreatedDate
    private LocalDateTime uploadedAt;
}

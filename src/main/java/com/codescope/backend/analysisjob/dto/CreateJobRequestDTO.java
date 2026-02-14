package com.codescope.backend.analysisjob.dto;

import com.codescope.backend.analysisjob.enums.JobType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobRequestDTO {
    @NotNull(message = "Job type is required")
    private JobType jobType;
}

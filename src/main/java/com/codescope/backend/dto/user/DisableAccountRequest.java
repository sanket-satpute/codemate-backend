package com.codescope.backend.dto.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisableAccountRequest {

    @NotNull(message = "Number of days is required")
    @Min(value = 1, message = "Minimum 1 day")
    @Max(value = 365, message = "Maximum 365 days")
    private Integer days;

    private String password; // Current password for verification
}

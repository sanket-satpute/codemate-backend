package com.codescope.backend.ai.model;

import lombok.*;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AIError {
    private String errorCode;
    private String message;
    private int httpStatus;
}

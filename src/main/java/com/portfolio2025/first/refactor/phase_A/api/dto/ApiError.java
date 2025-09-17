package com.portfolio2025.first.refactor.phase_A.api.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {
    private String code;             // e.g., VALIDATION_ERROR, CONFLICT, NOT_FOUND
    private String message;
    private List<ErrorDetail> details;
    private String traceId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String field;            // e.g., "limitPrice"
        private String reason;           // e.g., "required_for_LIMIT"
    }
}

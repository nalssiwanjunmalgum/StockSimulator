package com.portfolio2025.first.refactor.phase_A.shared.api;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // @Valid/@Validated @RequestBody 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {

        List<ApiError.ErrorDetail> details = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(this::toDetail) // Spring FieldError -> ApiError.ErrorDetail
                .toList();

        ApiError body = ApiError.builder()
                .code("VALIDATION_ERROR")
                .message("Invalid request")
                .details(details)
                .traceId(request.getHeader("X-Request-Id"))
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    // @RequestParam/@PathVariable 등 파라미터 검증 실패
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex,
                                                     HttpServletRequest request) {

        ApiError body = ApiError.builder()
                .code("VALIDATION_ERROR")
                .message(ex.getMessage())
                .traceId(request.getHeader("X-Request-Id"))
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    // DB 무결성 위반(유니크/외래키 등) → 409 (선택적으로 유지)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        ApiError body = ApiError.builder()
                .code("CONFLICT")
                .message("Data integrity violation")
                .traceId(request.getHeader("X-Request-Id"))
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // 기타 예외 → 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOthers(Exception ex,
                                                 HttpServletRequest request) {
        ApiError body = ApiError.builder()
                .code("INTERNAL_ERROR")
                .message(ex.getMessage())
                .traceId(request.getHeader("X-Request-Id"))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // 변환 : Spring FieldError → ApiError.ErrorDetail
    private ApiError.ErrorDetail toDetail(FieldError fe) {
        return ApiError.ErrorDetail.builder()
                .field(fe.getField())
                .reason(fe.getDefaultMessage())
                .build();
    }
}

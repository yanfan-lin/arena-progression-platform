package com.yanfan.arena.platform.common;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Converts every exception into one consistent error response
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex,
                                        HttpServletRequest request) {
        return toProblem(
                HttpStatus.NOT_FOUND,
                ex.getCode(),
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex,
                                        HttpServletRequest request) {
        return toProblem(
                HttpStatus.CONFLICT,
                ex.getCode(),
                ex.getMessage(),
                request);

    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        ProblemDetail problem = toProblem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                request);

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        problem.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.badRequest().body(problem);

    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedJson(HttpMessageNotReadableException ex,
                                             HttpServletRequest request) {
        return toProblem(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON",
                "Request body is not valid JSON",
                request);

    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex,
                                          HttpServletRequest request) {
        return toProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Unexpected server error",
                request);

    }

    private ProblemDetail toProblem(HttpStatus status,
                                    String code,
                                    String detail,
                                    HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);

        problem.setTitle(status.getReasonPhrase());

        problem.setProperty("code", code);
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("timestamp", Instant.now().toString());

        return problem;
    }


}

package com.yanfan.arena.platform.error;

import com.yanfan.arena.platform.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Handle application and Spring MVC errors with consistent API responses
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex,
                                        HttpServletRequest request)
    {
        return toProblem(
                HttpStatus.NOT_FOUND,
                ex.getCode(),
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex,
                                          HttpServletRequest request)
    {
        return toProblem(
                HttpStatus.BAD_REQUEST,
                ex.getCode(),
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex,
                                        HttpServletRequest request)
    {
        return toProblem(
                HttpStatus.CONFLICT,
                ex.getCode(),
                ex.getMessage(),
                request);

    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request)
    {
        ProblemDetail problem = toProblem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                request);

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(error ->
                        fieldErrors.put(error.getField(), error.getDefaultMessage()));

        problem.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.badRequest().body(problem);

    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request)
    {
        return ResponseEntity.badRequest().body(toProblem(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON",
                "Request body is not valid JSON",
                request));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex,
                                          HttpServletRequest request)
    {
        LOGGER.error("Unexpected API error", ex);

        return toProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Unexpected server error",
                request);
    }

    // Build a consistent API error response
    private ProblemDetail toProblem(HttpStatus status,
                                    String code,
                                    String detail,
                                    HttpServletRequest request)
    {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);

        problem.setTitle(status.getReasonPhrase());

        problem.setProperty("code", code);
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("requestId",
                request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
        problem.setProperty("timestamp", Instant.now().toString());

        return problem;
    }

    // Convert Spring MVC requests before building the error
    private ProblemDetail toProblem(HttpStatus status,
                                    String code,
                                    String detail,
                                    WebRequest request)
    {
        return toProblem(
                status,
                code,
                detail,
                ((ServletWebRequest) request).getRequest());
    }

}

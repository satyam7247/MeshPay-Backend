package com.demo.upimesh.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException error) {
        String message = error.getReason() == null ? "Request failed." : error.getReason();
        ResponseEntity.BodyBuilder response = ResponseEntity.status(error.getStatusCode());
        if (error.getStatusCode().value() == 429) response.header("Retry-After", "900");
        return response.body(Map.of("message", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException error) {
        FieldError field = error.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = field == null ? "Request validation failed."
                : field.getDefaultMessage() == null ? field.getField() + " is invalid." : field.getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableRequest() {
        return ResponseEntity.badRequest().body(Map.of("message", "Request body must be valid JSON."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException error) {
        log.error("Database integrity constraint rejected request.", error.getMostSpecificCause());
        String details = error.getMostSpecificCause().getMessage();
        String message = details != null && (details.contains("uk_app_users_email") || details.contains("uk_app_users_phone"))
                ? "An account already uses that email or phone number."
                : "This request conflicted with existing account or device data. Please retry.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badArgument(IllegalArgumentException error) {
        String message = error.getMessage() == null ? "Request is invalid." : error.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "The requested resource was not found."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception error) {
        if (error instanceof ErrorResponse frameworkError) {
            HttpStatus status = HttpStatus.valueOf(frameworkError.getStatusCode().value());
            String message = status == HttpStatus.METHOD_NOT_ALLOWED ? "Method not allowed."
                    : status == HttpStatus.UNSUPPORTED_MEDIA_TYPE ? "Unsupported request content type."
                    : "Request is invalid.";
            return ResponseEntity.status(frameworkError.getStatusCode()).body(Map.of("message", message));
        }
        log.error("Unhandled API request failure.", error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "The request could not be completed. Please try again."));
    }
}

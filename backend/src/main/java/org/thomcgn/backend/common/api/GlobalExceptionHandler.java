package org.thomcgn.backend.common.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.thomcgn.backend.common.logging.SafeExceptionDetails;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.thomcgn.backend.common.exception.ApiException;

import java.time.Instant;
import java.util.stream.Collectors;

/** Keeps the existing error envelope while retaining Spring MVC status codes and headers. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        HttpStatus status = exception.getStatus();
        if (status.is5xxServerError()) logFailure(exception, Level.ERROR);
        String message = status.is5xxServerError() ? "Unexpected server error" : exception.getMessage();
        return ResponseEntity.status(status).body(error(status, message, request));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + " " + field.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return response(status, message.isBlank() ? "Invalid request" : message, headers, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "Invalid request parameters", request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataConflict(DataIntegrityViolationException exception,
            HttpServletRequest request) {
        logFailure(exception, Level.WARN);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error(HttpStatus.CONFLICT, "Request conflicts with existing data", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception exception, HttpServletRequest request) {
        logFailure(exception, Level.ERROR);
        return ResponseEntity.internalServerError()
                .body(error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request));
    }

    private void logFailure(Exception exception, Level level) {
        log.atLevel(level).addKeyValue("diagnostic", SafeExceptionDetails.describe(exception))
                .log("api_processing_failed");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) logFailure(exception, Level.ERROR);
        return super.handleExceptionInternal(exception, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // Framework exception details can contain rejected values, parser internals or method signatures.
        String message = status.is5xxServerError() ? "Unexpected server error" : switch (status.value()) {
            case 400 -> "Invalid request";
            case 404 -> "Resource not found";
            case 405 -> "HTTP method not allowed";
            case 406 -> "Requested response format is not supported";
            case 415 -> "Request content type is not supported";
            default -> "Request could not be processed";
        };
        return response(status, message, headers, request);
    }

    private ResponseEntity<Object> response(HttpStatusCode status, String message,
            HttpHeaders headers, WebRequest request) {
        HttpHeaders safeHeaders = new HttpHeaders();
        safeHeaders.addAll(headers);
        safeHeaders.setContentType(MediaType.APPLICATION_JSON);
        return ResponseEntity.status(status).headers(safeHeaders)
                .body(error(status, message, ((ServletWebRequest) request).getRequest()));
    }

    private ApiErrorResponse error(HttpStatusCode status, String message, HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE);
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = pattern != null && pattern.toString().contains("{token}")
                ? pattern.toString() : request.getRequestURI();
        return new ApiErrorResponse(Instant.now(), status.value(),
                knownStatus == null ? "Request error" : knownStatus.getReasonPhrase(), message,
                path, requestId == null ? null : requestId.toString());
    }
}

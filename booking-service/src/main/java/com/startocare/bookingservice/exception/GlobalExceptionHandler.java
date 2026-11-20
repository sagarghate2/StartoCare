package com.startocare.bookingservice.exception;

import com.startocare.bookingservice.dto.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ApiError> handleBookingNotFound(BookingNotFoundException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ForbiddenBookingAccessException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenBookingAccessException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidBookingRequestException.class)
    public ResponseEntity<ApiError> handleInvalidBookingRequest(InvalidBookingRequestException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidBookingStateException.class)
    public ResponseEntity<ApiError> handleInvalidState(InvalidBookingStateException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "INVALID_STATE", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(EstimateUnavailableException.class)
    public ResponseEntity<ApiError> handleEstimateUnavailable(
            EstimateUnavailableException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ESTIMATE_UNAVAILABLE",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationErrors(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolations(ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        exception.getConstraintViolations().forEach(violation -> details.put(violation.getPropertyPath().toString(), violation.getMessage()));
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Constraint validation failed", request, details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception exception, HttpServletRequest request) {
        logger.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(),
                exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request, Map.of());
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        ApiError apiError = new ApiError(
                Instant.now(),
                status.value(),
                errorCode,
                message,
                request.getRequestURI(),
                details
        );
        return ResponseEntity.status(status).body(apiError);
    }
}

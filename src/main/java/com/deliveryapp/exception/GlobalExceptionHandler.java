package com.deliveryapp.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException; // Add this import

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String REFRESH_PATH = "/api/auth/refresh";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "غير موجود", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidDataException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidDataException(
            InvalidDataException ex, WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "طلب غير صالح", ex.getMessage(), request);
    }

    // The session is over — the "code" tells the app to show the login screen
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRefreshTokenException(
            InvalidRefreshTokenException ex, WebRequest request) {
        ResponseEntity<Map<String, Object>> response =
                buildResponse(HttpStatus.BAD_REQUEST, "طلب غير صالح", ex.getMessage(), request);
        response.getBody().put("code", InvalidRefreshTokenException.CODE);
        return response;
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResourceException(
            DuplicateResourceException ex, WebRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "تعارض", ex.getMessage(), request);
    }

    @ExceptionHandler({ AccessDeniedException.class, AuthorizationDeniedException.class })
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(Exception ex, WebRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "مرفوض", ex.getMessage(), request);
    }

    // ADD THIS METHOD
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipartException(MultipartException ex, WebRequest request) {

        // This will print the REAL error to your IntelliJ console
        System.err.println("❌ FILE UPLOAD ERROR DETAILS:");
        if (ex.getCause() != null) {
            ex.getCause().printStackTrace();
        } else {
            ex.printStackTrace();
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "طلب غير صالح", ex.getMessage(), request);

    }

    // @Valid failures (e.g. offer / flash sale requests) — return the field's own message instead of a 500
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("البيانات المرسلة غير صالحة");
        return buildResponse(HttpStatus.BAD_REQUEST, "طلب غير صالح", message, request);
    }

    // Malformed JSON or an unknown enum value (e.g. a wrong offerType)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(
            HttpMessageNotReadableException ex, WebRequest request) {
        // A refresh the server can't read can never succeed on retry, so it gets the same answer
        // as any other failed refresh: every 400 from /refresh tells the app to show the login screen
        if (REFRESH_PATH.equals(request.getDescription(false).replace("uri=", ""))) {
            log.warn("Refresh rejected (request body could not be read)");
            return handleInvalidRefreshTokenException(
                    new InvalidRefreshTokenException("رمز التحديث مفقود. يرجى تسجيل الدخول مرة أخرى."), request);
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "طلب غير صالح", "صيغة البيانات المرسلة غير صحيحة", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(
            Exception ex, WebRequest request) {
        // Log the actual error here (ex.printStackTrace()) in a real app
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "خطأ داخلي في الخادم", "حدث خطأ غير متوقع", request);
    }

    // Helper method to build the response map exactly as you requested
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, String message,
            WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(body, status);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, WebRequest request) {

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "خطأ في قاعدة البيانات"); // Database Error

        // Extract the root cause message so you can read it in Postman
        String specificError = ex.getMostSpecificCause().getMessage();
        body.put("message", "لا يمكن تنفيذ العملية لارتباط البيانات بسجلات أخرى. التفاصيل: " + specificError);

        body.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(body, HttpStatus.CONFLICT); // Returns 409 Conflict
    }
}
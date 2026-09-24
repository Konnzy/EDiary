package com.ediary.backend.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleOversizedUpload(
        MaxUploadSizeExceededException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.CONTENT_TOO_LARGE, "AVATAR_TOO_LARGE",
            "Розмір зображення не має перевищувати 5 МБ", Map.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
        AccessDeniedException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.FORBIDDEN,
            "ACCESS_DENIED",
            "Недостатньо прав для виконання цієї дії",
            Map.of(),
            request
        );
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationException(
        ApplicationException exception,
        HttpServletRequest request
    ) {
        return response(
            exception.getStatus(),
            exception.getCode(),
            exception.getMessage(),
            Map.of(),
            request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        return response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            "Перевірте правильність введених даних",
            fieldErrors,
            request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
        DataIntegrityViolationException exception,
        HttpServletRequest request
    ) {
        String causeMessage = exception.getMostSpecificCause().getMessage();
        if (causeMessage != null && (
            causeMessage.contains("uq_app_user_login_ci")
                || causeMessage.contains("app_user_user_login_key")
        )) {
            return response(
                HttpStatus.CONFLICT,
                "LOGIN_ALREADY_EXISTS",
                "Цей логін уже використовується. Введіть інший логін",
                Map.of("login", "Логін має бути унікальним"),
                request
            );
        }
        if (causeMessage != null && (
            causeMessage.contains("uq_app_user_email_ci")
                || causeMessage.contains("app_user_user_email_key")
        )) {
            return response(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED",
                "Для цієї електронної пошти вже існує обліковий запис",
                Map.of("email", "Електронна пошта має бути унікальною"),
                request
            );
        }
        if (causeMessage != null && causeMessage.contains("unique_class_number_letter")) {
            return response(
                HttpStatus.CONFLICT,
                "SCHOOL_CLASS_ALREADY_EXISTS",
                "Клас із таким номером і літерою вже існує",
                Map.of("letter", "Поєднання номера та літери має бути унікальним"),
                request
            );
        }
        if (causeMessage != null && causeMessage.contains("school_class_class_teacher_id_key")) {
            return response(
                HttpStatus.CONFLICT,
                "CLASS_TEACHER_ALREADY_ASSIGNED",
                "Цей учитель уже є класним керівником іншого класу",
                Map.of("classTeacherId", "Оберіть іншого класного керівника"),
                request
            );
        }
        return response(
            HttpStatus.CONFLICT,
            "DATA_CONFLICT",
            "Дані конфліктують з уже наявним записом",
            Map.of(),
            request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
        Exception exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "Не вдалося виконати операцію",
            Map.of(),
            request
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        Map<String, String> fieldErrors,
        HttpServletRequest request
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
            code,
            message,
            fieldErrors,
            request.getRequestURI(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(status).body(body);
    }
}

package com.gestordevendas.api.common.error;

import com.gestordevendas.api.common.request.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(exception.status())
                .body(new ApiError(
                        exception.code(),
                        exception.getMessage(),
                        requestId(request)
                ));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class
    })
    ResponseEntity<ApiError> handleValidation(
            Exception exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest()
                .body(new ApiError(
                        "VALIDATION_ERROR",
                        "Dados inválidos.",
                        requestId(request)
                ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = requestId(request);

        log.error(
                "Erro inesperado | requestId={} | method={} | uri={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(
                        "INTERNAL_ERROR",
                        "Não foi possível concluir a operação.",
                        requestId
                ));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }
}
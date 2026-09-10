package com.crowdfunding.backend.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice 
public class GlobalExceptionHandler {
    // Manejo de Validaciones (Cuerpo de la petición incorrecto - 400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        List<ErrorResponse.ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.ErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();

        ErrorResponse response = new ErrorResponse(
                Instant.now().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Error de Validación",
                request.getRequestURI(),
                details
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // Manejo de Recursos Duplicados (Ej: Email ya existe - 409)
    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleResourceAlreadyExists(
            ResourceAlreadyExistsException ex, HttpServletRequest request) {
        
        ErrorResponse response = new ErrorResponse(
                Instant.now().toString(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage(),
                request.getRequestURI(),
                null 
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // Manejo de Recursos No Encontrados (Ej: Proyecto no existe - 404)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        
        ErrorResponse response = new ErrorResponse(
                Instant.now().toString(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
}

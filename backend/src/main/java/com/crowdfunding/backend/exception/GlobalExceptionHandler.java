package com.crowdfunding.backend.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
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
                                .map(error -> new ErrorResponse.ErrorDetail(error.getField(),
                                                error.getDefaultMessage()))
                                .toList();

                ErrorResponse response = new ErrorResponse(
                                Instant.now().toString(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Bad Request",
                                "Error de Validación",
                                request.getRequestURI(),
                                details);

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
                                null);

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
                                null);

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        // Manejo de credeneciales inválidas durante el inicio de sesión - (Ej: correo
        // incorrecto - error 401)
        @ExceptionHandler(InvalidCredentialsException.class)
        public ResponseEntity<ErrorResponse> handleInvalidCredentials(
                        InvalidCredentialsException ex, HttpServletRequest request) {
                ErrorResponse response = new ErrorResponse(
                                Instant.now().toString(),
                                HttpStatus.UNAUTHORIZED.value(),
                                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                                ex.getMessage(),
                                request.getRequestURI(),
                                null);

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);

        }

        // Manejo de cuenta bloqueada tras varios intentos - error 429
        @ExceptionHandler(AccountLockedException.class)
        public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex,
                        HttpServletRequest request) {
                ErrorResponse response = new ErrorResponse(
                                Instant.now().toString(),
                                HttpStatus.TOO_MANY_REQUESTS.value(),
                                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                                ex.getMessage(),
                                request.getRequestURI(),
                                null);

                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }

        @ExceptionHandler(DuplicatedEmailException.class)
        public ResponseEntity<ErrorResponse> handleDuplicatedEmailException(HttpServletRequest request,
                        DuplicatedEmailException ex) {
                ErrorResponse response = new ErrorResponse(
                                Instant.now().toString(),
                                HttpStatus.CONFLICT.value(),
                                HttpStatus.CONFLICT.getReasonPhrase(),
                                ex.getMessage(),
                                request.getRequestURI(),
                                null);

                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        // Manejo de montos inválidos (400 Bad Request)
        @ExceptionHandler(InvalidContributionAmountException.class)
        public ResponseEntity<ErrorResponse> handleInvalidContribution(
                        InvalidContributionAmountException ex, HttpServletRequest request) {
                return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request.getRequestURI());
        }

        // Manejo de proyectos bloqueados o inactivos (403 Forbidden o 409 Conflict)
        @ExceptionHandler(ProjectLockedException.class)
        public ResponseEntity<ErrorResponse> handleProjectLocked(
                        ProjectLockedException ex, HttpServletRequest request) {
                return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request.getRequestURI());
        }

        // Manejo de Ataques de Repetición (409 Conflict)
        @ExceptionHandler(ReplayAttackException.class)
        public ResponseEntity<ErrorResponse> handleReplayAttack(
                        ReplayAttackException ex, HttpServletRequest request) {
                return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request.getRequestURI());
        }

        // Método auxiliar para no repetir código
        private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message,
                        String path) {
                ErrorResponse response = new ErrorResponse(
                                Instant.now().toString(),
                                status.value(),
                                error,
                                message,
                                path,
                                null);
                return ResponseEntity.status(status).body(response);
        }

        // Manejo de violaciones de integridad en la Base de Datos (Ej: UNIQUE
        // constraint)
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
                        DataIntegrityViolationException ex, HttpServletRequest request) {

                String errorMsg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";

                // Atrapamos si menciona "nonce", "unique", "duplicate" o
                // "transactions_nonce_key"
                if (errorMsg.contains("nonce") || errorMsg.contains("unique") || errorMsg.contains("duplicate")) {
                        return buildResponse(
                                        HttpStatus.UNPROCESSABLE_ENTITY, // 422 según contrato
                                        "Unprocessable Content",
                                        "El token de seguridad (nonce) ya ha sido utilizado en una transacción anterior",
                                        request.getRequestURI());
                }

                // Fallback para otras violaciones de integridad no esperadas
                return buildResponse(
                                HttpStatus.CONFLICT,
                                "Conflict",
                                "Error de integridad en la base de datos",
                                request.getRequestURI());
        }

        // Manejo del error durante el cálculo del scoring de riesgo
        @ExceptionHandler(RiskScoreCalculationException.class)
        public ResponseEntity<ErrorResponse> handleRiskScoreCalculationException(RiskScoreCalculationException ex,
                        HttpServletRequest request) {
                List<ErrorResponse.ErrorDetail> errors = ex.getIssuesList().stream()
                                .map(p -> new ErrorResponse.ErrorDetail(p.field(), p.detail())).toList();

                ErrorResponse response = new ErrorResponse(
                                Instant.now().toString(),
                                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                                ex.getMessage(),
                                request.getRequestURI(),
                                errors);

                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }

        // Manejo de acceso denegado a un proyecto ajeno (403)
        @ExceptionHandler(ProjectAccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleProjectAccessDenied(
                        ProjectAccessDeniedException ex, HttpServletRequest request) {
                return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request.getRequestURI());
        }

        // Manejo de campos obligatorios faltantes al publicar (400)
        @ExceptionHandler(MissingProjectFieldsException.class)
        public ResponseEntity<ErrorResponse> handleMissingProjectFields(
                        MissingProjectFieldsException ex, HttpServletRequest request) {

                List<ErrorResponse.ErrorDetail> details = ex.getMissingFields().stream()
                                .map(field -> new ErrorResponse.ErrorDetail(field, "Campo obligatorio para publicar"))
                                .toList();

                ErrorResponse response = new ErrorResponse(
                                Instant.now().toString(),
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                ex.getMessage(),
                                request.getRequestURI(),
                                details);

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
}

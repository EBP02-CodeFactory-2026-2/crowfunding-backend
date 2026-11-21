package com.crowdfunding.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

/**
 * UT-33 · Mapeo de excepciones a respuestas HTTP.
 * Componente bajo prueba: GlobalExceptionHandler.
 * Trazabilidad: Manejo transversal de errores y contrato de ErrorResponse.
 *
 * No requiere contexto de Spring ni MockMvc: el handler es una clase plana
 * que recibe la excepción y el request, y devuelve un ResponseEntity.
 */
@DisplayName("UT-33 · GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/api/v1/test");
    }

    // ==================== Validaciones y errores HTTP estándar ====================

    @Test
    @DisplayName("UT-33 · MethodArgumentNotValidException responde 400 con detalles por campo")
    void handleValidationExceptions() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "email", "El formato del correo no es válido");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidationExceptions(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).isEqualTo("Error de Validación");
        assertThat(response.getBody().details()).hasSize(1);
        assertThat(response.getBody().details().get(0).field()).isEqualTo("email");
        assertThat(response.getBody().details().get(0).issue()).isEqualTo("El formato del correo no es válido");
    }

    @Test
    @DisplayName("UT-33 · ResponseStatusException mapea al código HTTP lanzado")
    void handleResponseStatusException() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido");

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatusException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Estado inválido");
    }

    // ==================== Excepciones de negocio mapeadas ====================

    @Test
    @DisplayName("UT-33 · ResourceAlreadyExistsException responde 409 Conflict")
    void handleResourceAlreadyExists() {
        ResponseEntity<ErrorResponse> response = handler.handleResourceAlreadyExists(
                new ResourceAlreadyExistsException("El recurso ya existe"), request);

        assertError(response, HttpStatus.CONFLICT, "El recurso ya existe");
    }

    @Test
    @DisplayName("UT-33 · ResourceNotFoundException responde 404 Not Found")
    void handleResourceNotFound() {
        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(
                new ResourceNotFoundException("Proyecto no encontrado"), request);

        assertError(response, HttpStatus.NOT_FOUND, "Proyecto no encontrado");
    }

    @Test
    @DisplayName("UT-33 · InvalidCredentialsException responde 401 Unauthorized")
    void handleInvalidCredentials() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidCredentials(
                new InvalidCredentialsException("Contraseña o correo inválidos"), request);

        assertError(response, HttpStatus.UNAUTHORIZED, "Contraseña o correo inválidos");
    }

    @Test
    @DisplayName("UT-33 · AccountLockedException responde 429 Too Many Requests")
    void handleAccountLocked() {
        ResponseEntity<ErrorResponse> response = handler.handleAccountLocked(
                new AccountLockedException(125), request); // 2 min 5 segs

        assertError(response, HttpStatus.TOO_MANY_REQUESTS, 
                "Cuenta bloqueada temporalmente. Intenta de nuevo en 2 min 5 segs");
    }

    @Test
    @DisplayName("UT-33 · DuplicatedEmailException responde 409 Conflict")
    void handleDuplicatedEmail() {
        ResponseEntity<ErrorResponse> response = handler.handleDuplicatedEmailException(
                request, new DuplicatedEmailException("Este correo ya está registrado"));

        assertError(response, HttpStatus.CONFLICT, "Este correo ya está registrado");
    }

    @Test
    @DisplayName("UT-33 · InvalidContributionAmountException responde 400 Bad Request")
    void handleInvalidContribution() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidContribution(
                new InvalidContributionAmountException("Debes confirmar la advertencia"), request);

        assertError(response, HttpStatus.BAD_REQUEST, "Debes confirmar la advertencia");
    }

    @Test
    @DisplayName("UT-33 · ProjectLockedException responde 409 Conflict")
    void handleProjectLocked() {
        ResponseEntity<ErrorResponse> response = handler.handleProjectLocked(
                new ProjectLockedException("La meta y el plazo no pueden modificarse"), request);

        assertError(response, HttpStatus.CONFLICT, "La meta y el plazo no pueden modificarse");
    }

    @Test
    @DisplayName("UT-33 · ReplayAttackException responde 409 Conflict")
    void handleReplayAttack() {
        ResponseEntity<ErrorResponse> response = handler.handleReplayAttack(
                new ReplayAttackException("El nonce ya ha sido utilizado"), request);

        assertError(response, HttpStatus.CONFLICT, "El nonce ya ha sido utilizado");
    }

    @Test
    @DisplayName("UT-33 · ProjectAccessDeniedException responde 403 Forbidden")
    void handleProjectAccessDenied() {
        ResponseEntity<ErrorResponse> response = handler.handleProjectAccessDenied(
                new ProjectAccessDeniedException("No tienes permiso"), request);

        assertError(response, HttpStatus.FORBIDDEN, "No tienes permiso");
    }

    @Test
    @DisplayName("UT-33 · MissingProjectFieldsException responde 400 con lista de campos")
    void handleMissingProjectFields() {
        MissingProjectFieldsException ex = new MissingProjectFieldsException(
                "Faltan campos obligatorios", List.of("fundingGoal", "deadline"));

        ResponseEntity<ErrorResponse> response = handler.handleMissingProjectFields(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().details()).hasSize(2);
        assertThat(response.getBody().details().get(0).issue()).isEqualTo("Campo obligatorio para publicar");
    }

    @Test
    @DisplayName("UT-33 · DeleteConflictException responde 409 Conflict")
    void handleDeleteConflict() {
        ResponseEntity<ErrorResponse> response = handler.handleDeleteConflictException(
                new DeleteConflictException("No se puede eliminar un proyecto con contribuciones"), request);

        assertError(response, HttpStatus.CONFLICT, "No se puede eliminar un proyecto con contribuciones");
    }

    @Test
    @DisplayName("UT-33 · RiskScoreCalculationException responde 422 con issues por campo")
    void handleRiskScoreCalculation() {
        RiskScoreCalculationException ex = new RiskScoreCalculationException(
                "No se pudo calcular el riesgo",
                List.of(new RiskScoreCalculationException.Issue("fundingGoal", "Debe ser mayor a cero")));

        ResponseEntity<ErrorResponse> response = handler.handleRiskScoreCalculationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().details()).hasSize(1);
        assertThat(response.getBody().details().get(0).field()).isEqualTo("fundingGoal");
    }

    // ==================== D-15 · DataIntegrityViolationException ====================

    @Test
    @DisplayName("UT-33 · DataIntegrityViolation con 'nonce' responde 422 (Comportamiento correcto)")
    void dataIntegrity_Nonce() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"transactions_nonce_key\"");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().message()).contains("nonce");
    }

    @Test
    @DisplayName("UT-33 · D-15 · DataIntegrityViolation por EMAIL duplicado responde 422 (Defecto)")
    void dataIntegrity_EmailDuplicado() {
        // PostgreSQL lanza esto cuando dos registros concurrentes violan el UNIQUE de users.email
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"users_email_key\"");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        // HU-1.1.1 CA2 esperaría un 409 Conflict con "Este correo ya está registrado".
        // Comportamiento actual (D-15): El handler busca la palabra "duplicate" en el mensaje,
        // la encuentra, asume que es un nonce repetido y responde 422 con el mensaje del nonce.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().message()).contains("nonce");
    }

    @Test
    @DisplayName("UT-33 · DataIntegrityViolation genérica responde 409 Conflict (Fallback)")
    void dataIntegrity_Generic() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ERROR: insert or update on table \"projects\" violates foreign key constraint");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Error de integridad en la base de datos");
    }

    // ==================== Auxiliar ====================

    private void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String message) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().status()).isEqualTo(status.value());
        assertThat(response.getBody().error()).isEqualTo(status.getReasonPhrase());
        assertThat(response.getBody().message()).isEqualTo(message);
        assertThat(response.getBody().path()).isEqualTo("/api/v1/test");
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
package com.crowdfunding.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.AccountLockedException;
import com.crowdfunding.backend.exception.InvalidCredentialsException;
import com.crowdfunding.backend.login.LoginRequest;
import com.crowdfunding.backend.login.LoginResponse;
import com.crowdfunding.backend.login.LoginResponse.UserSummaryLoginResponse;
import com.crowdfunding.backend.persistence.UserRepository;

/**
 * UT-11 a UT-13 · Orquestación del inicio de sesión.
 * Componente bajo prueba: AuthService.login(LoginRequest).
 * Trazabilidad: HU-1.1.2 (CA1, CA2, CA4, CA5) y diagrama de secuencia SEQ-01A.
 *
 * AuthService no calcula nada: coordina AuthenticationManager, LoginAttemptService,
 * UserRepository y JwtService. Por eso las pruebas verifican interacciones y su orden,
 * no solo el valor de retorno.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UT-11..UT-13 · AuthService")
class AuthServiceTest {

    private static final String EMAIL = "camila@example.com";
    private static final String PASSWORD = "Password123";
    private static final String MENSAJE_GENERICO = "Contraseña o correo inválidos";
    private static final String TOKEN = "jwt-de-prueba";

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository repository;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthService service;

    // ---------- UT-11 ----------
    @Test
    @DisplayName("UT-11 · CA4 · Con la cuenta bloqueada se rechaza incluso con credenciales correctas")
    void cuentaBloqueadaNoIntentaAutenticar() {
        when(loginAttemptService.isLocked(EMAIL)).thenReturn(true);
        when(loginAttemptService.getRemainingSeconds(EMAIL)).thenReturn(600L);
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        AccountLockedException ex = assertThrows(AccountLockedException.class,
                () -> service.login(request));

        assertThat(ex.getRemainingSeconds()).isEqualTo(600L);
        assertThat(ex.getMessage())
                .isEqualTo("Cuenta bloqueada temporalmente. Intenta de nuevo en 10 min 0 segs");

        // La guarda del bloqueo vive aquí: no se autentica, no se emite token y
        // tampoco se registra un nuevo fallo (que extendería el bloqueo otros 15 min).
        verify(authenticationManager, never()).authenticate(any());
        verify(loginAttemptService, never()).registerFailedAttempt(any());
        verify(loginAttemptService, never()).resetAttempts(any());
        verifyNoInteractions(jwtService, repository);
    }

    // ---------- UT-12 ----------
    static Stream<Arguments> fallosDeAutenticacion() {
        return Stream.of(
                Arguments.of("contraseña incorrecta", new BadCredentialsException("Bad credentials")),
                Arguments.of("correo inexistente", new UsernameNotFoundException("Usuario no encontrado")));
    }

    @ParameterizedTest(name = "UT-12 · {0} -> mismo mensaje genérico")
    @MethodSource("fallosDeAutenticacion")
    @DisplayName("UT-12 · CA2 · Cualquier AuthenticationException produce el mismo mensaje indiferenciado")
    void credencialesInvalidas(String escenario, AuthenticationException fallo) {
        when(loginAttemptService.isLocked(EMAIL)).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenThrow(fallo);
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(MENSAJE_GENERICO);

        verify(loginAttemptService).registerFailedAttempt(EMAIL);
        verify(loginAttemptService, never()).resetAttempts(any());
        verifyNoInteractions(jwtService, repository);
    }

    // ---------- UT-13 ----------
    @Test
    @DisplayName("UT-13 · CA1/CA5 · Login exitoso: contrato completo, contador reiniciado y orden de SEQ-01A")
    void loginExitoso() {
        when(loginAttemptService.isLocked(EMAIL)).thenReturn(false);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(EMAIL, null, List.of()));
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario()));
        when(jwtService.generateToken(EMAIL)).thenReturn(TOKEN);
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        LoginResponse response = service.login(new LoginRequest(EMAIL, PASSWORD));

        assertThat(response.accessToken()).isEqualTo(TOKEN);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(86400L);
        // El record UserSummaryLoginResponse solo declara id, fullName y email:
        // la igualdad exacta comprueba que no se filtra ningún otro dato del usuario.
        assertThat(response.user())
                .isEqualTo(new UserSummaryLoginResponse(1L, "Camila Torres", EMAIL));

        // Las credenciales que se delegan al AuthenticationManager son las recibidas.
        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(EMAIL);
        assertThat(captor.getValue().getCredentials()).isEqualTo(PASSWORD);

        verify(loginAttemptService, never()).registerFailedAttempt(any());

        // Orden del diagrama de secuencia SEQ-01A.
        InOrder orden = inOrder(loginAttemptService, authenticationManager, jwtService);
        orden.verify(loginAttemptService).isLocked(EMAIL);
        orden.verify(authenticationManager).authenticate(any());
        orden.verify(loginAttemptService).resetAttempts(EMAIL);
        orden.verify(jwtService).generateToken(EMAIL);
    }

    @Test
    @DisplayName("UT-13 · Rama defensiva · Autenticado pero el usuario ya no existe en el repositorio")
    void usuarioAutenticadoPeroInexistente() {
        when(loginAttemptService.isLocked(EMAIL)).thenReturn(false);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(EMAIL, null, List.of()));
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(MENSAJE_GENERICO);

        verify(jwtService, never()).generateToken(any());
        // Obs-06: resetAttempts se ejecuta ANTES de la consulta al repositorio,
        // así que en esta rama el contador se limpia aunque el login termine fallando.
        verify(loginAttemptService).resetAttempts(EMAIL);
        verify(loginAttemptService, never()).registerFailedAttempt(any());
    }

    // ---------- fixture ----------
    private User usuario() {
        User user = new User();
        user.setId(1L);
        user.setFullName("Camila Torres");
        user.setEmail(EMAIL);
        user.setPasswordHash("$2a$10$hashDePrueba");
        return user;
    }
}
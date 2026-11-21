package com.crowdfunding.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.DuplicatedEmailException;
import com.crowdfunding.backend.persistence.UserRepository;
import com.crowdfunding.backend.register.RegisterRequest;
import com.crowdfunding.backend.register.RegisterResponse;

/**
 * UT-15 a UT-17 · Registro de usuarios.
 * Componente bajo prueba: RegisterService.register(RegisterRequest).
 * Trazabilidad: HU-1.1.1 (CA1, CA2, CA6).
 *
 * Nota: el stub de repository.save(...) asigna id y createdAt porque en producción
 * JPA los pobla durante el INSERT. Sin esa simulación, la respuesta tendría null
 * y la prueba no representaría el comportamiento real del servicio.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UT-15..UT-17 · RegisterService")
class RegisterServiceTest {

    private static final LocalDateTime FECHA_CREACION = LocalDateTime.of(2026, 11, 21, 10, 0);

    @Mock
    private UserRepository repository;

    // Encoder real para verificar que el hash es BCrypt y que matches() funciona.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private RegisterService service;

    @BeforeEach
    void setUp() {
        service = new RegisterService(repository, passwordEncoder);
    }

    // ---------- UT-15 ----------
    @Test
    @DisplayName("UT-15 · CA2 · Un correo ya registrado se rechaza y no se persiste nada")
    void rechazaCorreoDuplicado() {
        User existente = new User();
        existente.setId(1L);
        existente.setFullName("Camila Torres");
        existente.setEmail("camila@example.com");
        existente.setPasswordHash("$2a$10$hashExistente");
        when(repository.findByEmail("camila@example.com")).thenReturn(Optional.of(existente));

        RegisterRequest request = new RegisterRequest(
                "Nuevo Usuario", "camila@example.com", "Password123");

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(DuplicatedEmailException.class)
                .hasMessage("Este correo ya está registrado");

        verify(repository, never()).save(any(User.class));
    }

    // ---------- UT-16 ----------
    @Test
    @DisplayName("UT-16 · CA1/CA6 · La contraseña se guarda como hash BCrypt y la respuesta no expone credenciales")
    void registraConHashBcryptYRespuestaSegura() {
        RegisterRequest request = new RegisterRequest(
                "Ana Gómez", "ana.nueva@example.com", "Password123");
        when(repository.findByEmail("ana.nueva@example.com")).thenReturn(Optional.empty());
        simularGuardadoConId(2L);

        RegisterResponse response = service.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User guardado = captor.getValue();

        assertThat(guardado.getFullName()).isEqualTo("Ana Gómez");
        assertThat(guardado.getEmail()).isEqualTo("ana.nueva@example.com");
        assertThat(guardado.getPasswordHash())
                .isNotEqualTo("Password123")
                .startsWith("$2a$10$")
                .hasSize(60);
        assertThat(passwordEncoder.matches("Password123", guardado.getPasswordHash())).isTrue();

        // RegisterResponse es un record: sus componentes son solo id, fullName, email y createdAt.
        assertThat(response).isEqualTo(new RegisterResponse(
                2L, "Ana Gómez", "ana.nueva@example.com", FECHA_CREACION));
    }

    // ---------- UT-17 · comportamiento actual, no conforme ----------
    @Test
    @DisplayName("UT-17 · D-10 · El correo no se normaliza: CAMILA@example.com crea una cuenta distinta")
    void noNormalizaElCorreo() {
        RegisterRequest request = new RegisterRequest(
                "Camila Mayúsculas", "CAMILA@example.com", "Password123");
        when(repository.findByEmail("CAMILA@example.com")).thenReturn(Optional.empty());
        simularGuardadoConId(3L);

        RegisterResponse response = service.register(request);

        // El servicio consulta el correo literal, sin trim() ni toLowerCase().
        verify(repository).findByEmail("CAMILA@example.com");
        verify(repository, never()).findByEmail("camila@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("CAMILA@example.com");
        assertThat(response.email()).isEqualTo("CAMILA@example.com");
        // Especificación esperada: si ya existe camila@example.com, debería rechazarse.
        // Comportamiento actual: se crea una segunda cuenta con el correo en mayúsculas.
    }

    // ---------- auxiliar ----------
    private void simularGuardadoConId(Long id) {
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(id);
            user.setCreatedAt(FECHA_CREACION);
            return user;
        });
    }
}
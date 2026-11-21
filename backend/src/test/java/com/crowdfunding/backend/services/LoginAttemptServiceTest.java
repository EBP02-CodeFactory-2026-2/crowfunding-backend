package com.crowdfunding.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * UT-09 y UT-10 · Control de intentos fallidos de inicio de sesión.
 * Componente bajo prueba: LoginAttemptService.
 * Trazabilidad: HU-1.1.2 (CA4 y CA5) y §5.3.3 "Error 1: condición de carrera".
 *
 * UT-10 materializa la estrategia 4 declarada en §5.3.3: pruebas de concurrencia
 * con ExecutorService y CountDownLatch que verifican el bloqueo exacto al quinto intento.
 */
@DisplayName("UT-09..UT-10 · LoginAttemptService")
class LoginAttemptServiceTest {

    private static final String EMAIL = "ana.gomez@example.com";
    private static final String OTRO_EMAIL = "carlos@example.com";

    // Espejo de LOCK_MINUTES = 15 del servicio (la constante es privada y no es accesible).
    private static final long LOCK_SECONDS = 15 * 60;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        // Sin mocks: cada prueba parte de una caché vacía.
        service = new LoginAttemptService();
    }

    // ---------- UT-09 · umbral de bloqueo ----------
    @ParameterizedTest(name = "UT-09 · con {0} intento(s) fallido(s) la cuenta NO se bloquea")
    @ValueSource(ints = {1, 2, 3, 4})
    void noBloqueaAntesDelQuintoIntento(int intentos) {
        fallar(EMAIL, intentos);

        assertThat(service.isLocked(EMAIL)).isFalse();
        assertThat(service.getRemainingSeconds(EMAIL)).isZero();
    }

    @Test
    @DisplayName("UT-09 · CA4 · El quinto intento bloquea la cuenta y reporta ~15 minutos restantes")
    void bloqueaEnElQuintoIntento() {
        fallar(EMAIL, 5);

        assertThat(service.isLocked(EMAIL)).isTrue();
        // Duration.getSeconds() trunca: 900 s menos unos milisegundos devuelve 899.
        assertThat(service.getRemainingSeconds(EMAIL)).isBetween(LOCK_SECONDS - 5, LOCK_SECONDS);
    }

    @Test
    @DisplayName("UT-09 · El tiempo restante es 0 si no hay registro o si no hay bloqueo activo")
    void tiempoRestanteCeroSinBloqueo() {
        assertThat(service.getRemainingSeconds(EMAIL)).isZero();   // rama info == null
        assertThat(service.isLocked(EMAIL)).isFalse();

        fallar(EMAIL, 3);
        assertThat(service.getRemainingSeconds(EMAIL)).isZero();   // rama lockedUntil == null
    }

    @Test
    @DisplayName("UT-09 · CA5 · resetAttempts limpia el bloqueo y reinicia el contador")
    void resetLimpiaElEstado() {
        fallar(EMAIL, 5);
        assertThat(service.isLocked(EMAIL)).isTrue();

        service.resetAttempts(EMAIL);

        assertThat(service.isLocked(EMAIL)).isFalse();
        assertThat(service.getRemainingSeconds(EMAIL)).isZero();

        // El contador volvió a cero: un nuevo fallo no puede reactivar el bloqueo.
        fallar(EMAIL, 1);
        assertThat(service.isLocked(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("UT-09 · El bloqueo es por correo: no se propaga a otras cuentas")
    void bloqueoIndependientePorCorreo() {
        fallar(EMAIL, 5);
        fallar(OTRO_EMAIL, 4);

        assertThat(service.isLocked(EMAIL)).isTrue();
        assertThat(service.isLocked(OTRO_EMAIL)).isFalse();
    }

    @Test
    @DisplayName("UT-09 · Comportamiento actual · Fallos adicionales no levantan el bloqueo")
    void fallosAdicionalesNoLevantanElBloqueo() {
        fallar(EMAIL, 5);
        fallar(EMAIL, 1); // sexto fallo

        assertThat(service.isLocked(EMAIL)).isTrue();
        assertThat(service.getRemainingSeconds(EMAIL)).isBetween(LOCK_SECONDS - 5, LOCK_SECONDS);
        // Nota: el servicio re-arma lockedUntil = now + 15 min en cada fallo posterior.
        // En producción esto no ocurre porque AuthService.login consulta isLocked() antes
        // de autenticar; la guarda vive en la capa superior, no en este servicio.
    }

    // ---------- UT-10 · atomicidad bajo concurrencia ----------
    @RepeatedTest(value = 10, name = "UT-10 · ronda {currentRepetition}/{totalRepetitions}")
    @DisplayName("UT-10 · Cinco fallos simultáneos producen exactamente un bloqueo")
    void cincoHilosSimultaneosBloquean() throws InterruptedException {
        fallosConcurrentes(EMAIL, 5);

        // Si un incremento se perdiera (el get-then-put del diseño anterior),
        // el contador quedaría por debajo de 5 y la cuenta NO estaría bloqueada.
        assertThat(service.isLocked(EMAIL)).isTrue();
        assertThat(service.getRemainingSeconds(EMAIL)).isBetween(LOCK_SECONDS - 5, LOCK_SECONDS);
    }

    @Test
    @DisplayName("UT-10 · Veinte fallos simultáneos no extienden el bloqueo más allá de 15 minutos")
    void veinteHilosNoExcedenLaDuracionMaxima() throws InterruptedException {
        fallosConcurrentes(EMAIL, 20);

        assertThat(service.isLocked(EMAIL)).isTrue();
        assertThat(service.getRemainingSeconds(EMAIL))
                .isBetween(LOCK_SECONDS - 5, LOCK_SECONDS);
    }

    // ---------- auxiliares ----------
    private void fallar(String email, int veces) {
        for (int i = 0; i < veces; i++) {
            service.registerFailedAttempt(email);
        }
    }

    /**
     * Lanza N fallos simultáneos contra el mismo correo. La barrera de inicio
     * maximiza la solapación de los hilos, que es donde se manifestaría la carrera.
     */
    private void fallosConcurrentes(String email, int hilos) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(hilos);
        try {
            for (int i = 0; i < hilos; i++) {
                pool.submit(() -> {
                    try {
                        inicio.await();
                        service.registerFailedAttempt(email);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        fin.countDown();
                    }
                });
            }
            inicio.countDown();
            assertThat(fin.await(10, TimeUnit.SECONDS))
                    .as("los %d hilos deben terminar", hilos)
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
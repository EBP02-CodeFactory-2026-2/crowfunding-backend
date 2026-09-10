package services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    // Constantes
    private static final int MAX_ATTEMPTS = 5; // Número máximo de intentos
    private static final long WINDOW_MINUTES = 10; // Ventana de tiempo donde deben suceder los 5 intentos
    private static final long LOCK_MINUTES = 15; // Tiempo que tarda el bloqueo

    // Record anidado para agrupar el estado de un email en un solo objeto inmutable
    private record AttemptInfo(int count, LocalDateTime firstAttempt, LocalDateTime lockedUntil) {
    }

    private final ConcurrentHashMap<String, AttemptInfo> cache = new ConcurrentHashMap<>();

    /**
     * Se llama cada vez que un login falla para un email dado.
     * Atómico: seguro incluso si varios hilos llaman esto al mismo tiempo
     * para el mismo email.
     */
    public void registerFailedAttempt(String email) {
        cache.compute(email, (key, info) -> {
            LocalDateTime now = LocalDateTime.now();

            // Caso 1: primer intento o ya expiró la ventana de 10 minutos, reiniciamos
            boolean expired = info == null || Duration.between(info.firstAttempt(), now).toMinutes() >= WINDOW_MINUTES;

            if (expired) {
                return new AttemptInfo(1, now, null);
            }

            // Caso 2: seguimos dentro de la ventana de 10 minutos, incrementamos el
            // contador
            int newCount = info.count() + 1;
            LocalDateTime lockedUntil = (newCount >= MAX_ATTEMPTS) ? now.plusMinutes(LOCK_MINUTES) : null;

            return new AttemptInfo(newCount, info.firstAttempt(), lockedUntil);
        });
    }

    /**
     * Solo lectura, no necesita ser atómico frente a otras escrituras.
     */
    public boolean isLocked(String email) {
        AttemptInfo info = cache.get(email);
        return info != null
                && info.lockedUntil() != null
                && info.lockedUntil().isAfter(LocalDateTime.now());
    }

    /**
     * Tiempo restante de bloqueo en segundos. Devuelve 0 si no está bloqueado.
     */
    public long getRemainingSeconds(String email) {
        AttemptInfo info = cache.get(email);
        if (info == null || info.lockedUntil() == null) {
            return 0;
        }
        long seconds = Duration.between(LocalDateTime.now(), info.lockedUntil()).getSeconds();
        return Math.max(seconds, 0);
    }

    /**
     * Se llama tras un login exitoso, para limpiar el historial de fallos.
     */
    public void resetAttempts(String email) {
        cache.remove(email);
    }
}

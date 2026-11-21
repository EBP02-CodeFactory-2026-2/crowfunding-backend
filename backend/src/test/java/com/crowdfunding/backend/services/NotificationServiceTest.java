package com.crowdfunding.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.Notification;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.ProjectAccessDeniedException;
import com.crowdfunding.backend.exception.ResourceNotFoundException;
import com.crowdfunding.backend.notification.NotificationResponse;
import com.crowdfunding.backend.persistence.NotificationRepository;
import com.crowdfunding.backend.persistence.UserRepository;

/**
 * UT-29, UT-30 y UT-34 · Notificaciones.
 * Componente bajo prueba: NotificationService.
 * Trazabilidad: HU-4.1.1 (CA1, CA3), HU-4.1.2 (CA3, CA4), HU-4.1.4 (CA2, CA4)
 * y ciclo de vida de NOTIFICATIONS del modelo conceptual.
 *
 * El servicio recibe el correo por parámetro (no lee SecurityContextHolder),
 * por lo que estas pruebas no requieren poblar el contexto de seguridad.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UT-29, UT-30, UT-34 · NotificationService")
class NotificationServiceTest {

    private static final String EMAIL = "ana.gomez@example.com";
    private static final Long USER_ID = 2L;
    private static final Long OTRO_USER_ID = 1L;
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OTRO_PROJECT_ID = UUID.randomUUID();
    private static final UUID NOTIFICATION_ID = UUID.randomUUID();
    private static final LocalDateTime CIERRE = LocalDateTime.of(2026, 11, 21, 10, 0);
    private static final String REFUND_PROCESSED = "REFUND_PROCESSED";
    private static final String PROJECT_FUNDED = "PROJECT_FUNDED";
    private static final String PROJECT_FAILED = "PROJECT_FAILED";

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService service;

    // ==================== UT-29 · marcado como leída ====================

    @Test
    @DisplayName("UT-29 · El titular marca su notificación como leída")
    void marcaComoLeidaNotificacionPropia() {
        Notification notificacion = notificacion(usuario(USER_ID, EMAIL), false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario(USER_ID, EMAIL)));
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notificacion));

        service.markAsRead(NOTIFICATION_ID, EMAIL);

        assertThat(notificacion.getIsRead()).isTrue();
        verify(notificationRepository).saveAndFlush(notificacion);
    }

    @Test
    @DisplayName("UT-29 · Marcar una notificación ya leída vuelve a completar sin error (idempotente)")
    void marcarComoLeidaEsIdempotente() {
        Notification notificacion = notificacion(usuario(USER_ID, EMAIL), true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario(USER_ID, EMAIL)));
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notificacion));

        service.markAsRead(NOTIFICATION_ID, EMAIL);

        assertThat(notificacion.getIsRead()).isTrue();
        // Obs: no hay cortocircuito; cada repetición emite un UPDATE aunque nada cambie.
        verify(notificationRepository).saveAndFlush(notificacion);
    }

    @Test
    @DisplayName("UT-29 · Una notificación de otro usuario se rechaza sin modificarla")
    void notificacionDeOtroUsuarioRechaza() {
        Notification ajena = notificacion(usuario(OTRO_USER_ID, "camila@example.com"), false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario(USER_ID, EMAIL)));
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.markAsRead(NOTIFICATION_ID, EMAIL))
                .isInstanceOf(ProjectAccessDeniedException.class)
                .hasMessage("Esta notificación pertenece a otro usuario.");

        assertThat(ajena.getIsRead()).isFalse();
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UT-29 · Una notificación inexistente responde 404")
    void notificacionInexistenteRechaza() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario(USER_ID, EMAIL)));
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(NOTIFICATION_ID, EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("La notificación no fue encontrada.");

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UT-29 · El usuario autenticado que ya no existe se rechaza antes de buscar la notificación")
    void markAsReadDeUsuarioInexistenteRechaza() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(NOTIFICATION_ID, EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuario autenticado no encontrado: " + EMAIL);

        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("UT-29 · Un identificador nulo se rechaza por guarda de entrada")
    void idNuloRechaza() {
        assertThatThrownBy(() -> service.markAsRead(null, EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El ID de notificación y el email de usuario son obligatorios.");

        verifyNoInteractions(userRepository, notificationRepository);
    }

    @ParameterizedTest(name = "UT-29 · email=[{0}] se rechaza por guarda de entrada")
    @CsvSource(value = {"null", "''", "'   '"}, nullValues = "null")
    void emailInvalidoEnMarkAsReadRechaza(String email) {
        assertThatThrownBy(() -> service.markAsRead(NOTIFICATION_ID, email))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(userRepository, notificationRepository);
    }

    // ==================== UT-30 · idempotencia del reembolso (D-07) ====================

    @Test
    @DisplayName("UT-30 · CA2 · El reembolso genera una notificación con el monto y el proyecto")
    void notificaReembolsoDelAporte() {
        Contribution aporte = aporte(UUID.randomUUID(), usuario(USER_ID, EMAIL),
                proyecto(PROJECT_ID, "Agua para Todos", "1000000", "10000"), "10000");
        when(notificationRepository.existsByUserIdAndTypeAndProjectId(USER_ID, REFUND_PROCESSED, PROJECT_ID))
                .thenReturn(false);

        service.notifyRefundProcessed(aporte);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        Notification guardada = captor.getValue();
        assertThat(guardada.getType()).isEqualTo(REFUND_PROCESSED);
        assertThat(guardada.getUser()).isSameAs(aporte.getBacker());
        assertThat(guardada.getProject()).isSameAs(aporte.getProject());
        assertThat(guardada.getMessage())
                .contains("tu aporte de $10,000")
                .contains("Agua para Todos")
                .contains("ha sido reembolsado");
        assertThat(guardada.getIsRead()).isFalse();
    }

    @Test
    @DisplayName("UT-30 · D-07 · Un segundo aporte del mismo usuario al mismo proyecto NO genera notificación")
    void segundoAporteDelMismoProyectoNoNotifica() {
        User backer = usuario(USER_ID, EMAIL);
        Project proyecto = proyecto(PROJECT_ID, "Agua para Todos", "1000000", "30000");
        Contribution primero = aporte(UUID.randomUUID(), backer, proyecto, "10000");
        Contribution segundo = aporte(UUID.randomUUID(), backer, proyecto, "20000");
        // La clave de idempotencia no incluye el aporte: la segunda consulta ya encuentra registro.
        when(notificationRepository.existsByUserIdAndTypeAndProjectId(USER_ID, REFUND_PROCESSED, PROJECT_ID))
                .thenReturn(false, true);

        service.notifyRefundProcessed(primero);
        service.notifyRefundProcessed(segundo);

        // HU-4.1.4 CA2 y el modelo conceptual exigen una notificación por cada aporte reembolsado.
        verify(notificationRepository, times(1)).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("UT-30 · Reembolsos en proyectos distintos sí generan una notificación cada uno")
    void reembolsoDeOtroProyectoSiNotifica() {
        User backer = usuario(USER_ID, EMAIL);
        Contribution primero = aporte(UUID.randomUUID(), backer,
                proyecto(PROJECT_ID, "Agua para Todos", "1000000", "10000"), "10000");
        Contribution segundo = aporte(UUID.randomUUID(), backer,
                proyecto(OTRO_PROJECT_ID, "EcoBici", "2000000", "20000"), "20000");
        when(notificationRepository.existsByUserIdAndTypeAndProjectId(USER_ID, REFUND_PROCESSED, PROJECT_ID))
                .thenReturn(false);
        when(notificationRepository.existsByUserIdAndTypeAndProjectId(USER_ID, REFUND_PROCESSED, OTRO_PROJECT_ID))
                .thenReturn(false);

        service.notifyRefundProcessed(primero);
        service.notifyRefundProcessed(segundo);

        // La clave discrimina por proyecto, pero no por aporte: ese es el alcance exacto del defecto.
        verify(notificationRepository, times(2)).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("UT-30 · Un aporte sin monto se rechaza por guarda de entrada")
    void aporteInvalidoRechaza() {
        Contribution sinMonto = aporte(UUID.randomUUID(), usuario(USER_ID, EMAIL),
                proyecto(PROJECT_ID, "Agua para Todos", "1000000", "0"), null);

        assertThatThrownBy(() -> service.notifyRefundProcessed(sinMonto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El aporte debe incluir un usuario, un proyecto y un monto válidos.");

        verifyNoInteractions(notificationRepository);
    }

    // ==================== UT-34 · listado y alertas de cierre ====================

    @Test
    @DisplayName("UT-34 · El listado mapea id, tipo, mensaje, lectura y fecha, conservando el orden")
    void listaNotificacionesDelUsuario() {
        Notification reciente = notificacion(usuario(USER_ID, EMAIL), false);
        reciente.setType(PROJECT_FAILED);
        reciente.setMessage("Cierre por plazo vencido");
        Notification anterior = notificacion(usuario(USER_ID, EMAIL), true);
        anterior.setId(UUID.randomUUID());
        anterior.setType(REFUND_PROCESSED);
        anterior.setMessage("Reembolso procesado");
        anterior.setCreatedAt(CIERRE.minusDays(2));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario(USER_ID, EMAIL)));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(reciente, anterior));

        List<NotificationResponse> respuesta = service.getMyNotifications(EMAIL);

        assertThat(respuesta).hasSize(2);
        assertThat(respuesta.get(0).id()).isEqualTo(NOTIFICATION_ID);
        assertThat(respuesta.get(0).type()).isEqualTo(PROJECT_FAILED);
        assertThat(respuesta.get(0).message()).isEqualTo("Cierre por plazo vencido");
        assertThat(respuesta.get(0).isRead()).isFalse();
        assertThat(respuesta.get(0).createdAt()).isEqualTo(CIERRE);
        assertThat(respuesta.get(1).isRead()).isTrue();
    }

    @Test
    @DisplayName("UT-34 · El listado de un usuario inexistente responde 404")
    void listadoDeUsuarioInexistenteRechaza() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyNotifications(EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuario autenticado no encontrado: " + EMAIL);

        verifyNoInteractions(notificationRepository);
    }

    @ParameterizedTest(name = "UT-34 · email=[{0}] se rechaza por guarda de entrada")
    @CsvSource(value = {"null", "''", "'   '"}, nullValues = "null")
    void listadoConEmailInvalidoRechaza(String email) {
        assertThatThrownBy(() -> service.getMyNotifications(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El email del usuario no puede ser nulo ni vacío.");

        verifyNoInteractions(userRepository, notificationRepository);
    }

    @Test
    @DisplayName("UT-34 · HU-4.1.1 CA1/CA3 · La alerta de éxito lleva solo agregados, sin datos de terceros")
    void alertaDeCierreExitoso() {
        User creador = usuario(OTRO_USER_ID, "camila@example.com");
        Project proyecto = proyecto(PROJECT_ID, "HoneyMoney", "25000000", "25000000");
        when(notificationRepository.existsByUserIdAndTypeAndProjectId(OTRO_USER_ID, PROJECT_FUNDED, PROJECT_ID))
                .thenReturn(false);

        service.notifyProjectFunded(creador, proyecto, 3L, CIERRE);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        String mensaje = captor.getValue().getMessage();
        assertThat(captor.getValue().getType()).isEqualTo(PROJECT_FUNDED);
        assertThat(mensaje)
                .contains("HoneyMoney")
                .contains("$25,000,000")
                .contains("Patrocinadores: 3")
                .contains("2026-11-21T10:00");
        assertThat(mensaje)
                .doesNotContain("Ana Gómez")
                .doesNotContain(EMAIL);
    }

    @Test
    @DisplayName("UT-34 · HU-4.1.2 CA4 · La alerta de éxito no se duplica para el mismo usuario y proyecto")
    void alertaDeCierreExitosoNoSeDuplica() {
        when(notificationRepository.existsByUserIdAndTypeAndProjectId(OTRO_USER_ID, PROJECT_FUNDED, PROJECT_ID))
                .thenReturn(true);

        service.notifyProjectFunded(usuario(OTRO_USER_ID, "camila@example.com"),
                proyecto(PROJECT_ID, "HoneyMoney", "25000000", "25000000"), 3L, CIERRE);

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UT-34 · HU-4.1.2 CA3 · La alerta de caducidad informa el porcentaje alcanzado")
    void alertaDeCaducidadIncluyePorcentaje() {
        Project proyecto = proyecto(PROJECT_ID, "Agua para Todos", "5000", "1250");
        when(notificationRepository.existsByUserIdAndTypeAndProjectId(USER_ID, PROJECT_FAILED, PROJECT_ID))
                .thenReturn(false);

        service.notifyProjectFailed(usuario(USER_ID, EMAIL), proyecto, 1L, CIERRE);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        // 1250 * 100 / 5000 = 25.00 -> stripTrailingZeros -> "25"
        assertThat(captor.getValue().getMessage())
                .contains("(25% alcanzado)")
                .contains("Los aportes serán reembolsados")
                // Obs: Locale.US usa coma de miles, contrario a la Guía de Estilos (§3.4.2).
                .contains("$1,250")
                .contains("$5,000");
    }

    @Test
    @DisplayName("UT-34 · Una meta de cero se rechaza antes de dividir (guarda de signum)")
    void alertaDeCaducidadConMetaCeroRechaza() {
        Project sinMeta = proyecto(PROJECT_ID, "Agua para Todos", "0", "0");

        assertThatThrownBy(() -> service.notifyProjectFailed(usuario(USER_ID, EMAIL), sinMeta, 0L, CIERRE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El usuario y el proyecto deben estar registrados.");

        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("UT-34 · La alerta de caducidad no se duplica para el mismo usuario y proyecto")
    void alertaDeCaducidadNoSeDuplica() {
        when(notificationRepository.existsByUserIdAndTypeAndProjectId(USER_ID, PROJECT_FAILED, PROJECT_ID))
                .thenReturn(true);

        service.notifyProjectFailed(usuario(USER_ID, EMAIL),
                proyecto(PROJECT_ID, "Agua para Todos", "5000", "1250"), 1L, CIERRE);

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    // ==================== fixtures ====================

    private User usuario(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setFullName("Ana Gómez");
        user.setEmail(email);
        user.setPasswordHash("$2a$10$hashDePrueba");
        user.setCreatedAt(LocalDateTime.now().minusDays(30));
        return user;
    }

    private Project proyecto(UUID id, String titulo, String meta, String recaudado) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(titulo);
        project.setDescription("a".repeat(200));
        project.setFundingGoal(new BigDecimal(meta));
        project.setCurrentAmount(new BigDecimal(recaudado));
        project.setDeadline(CIERRE);
        return project;
    }

    private Notification notificacion(User dueño, boolean leida) {
        Notification notification = new Notification();
        notification.setId(NOTIFICATION_ID);
        notification.setUser(dueño);
        notification.setProject(proyecto(PROJECT_ID, "HoneyMoney", "25000000", "25000000"));
        notification.setType(PROJECT_FUNDED);
        notification.setMessage("Alerta de prueba");
        notification.setIsRead(leida);
        notification.setCreatedAt(CIERRE);
        return notification;
    }

    private Contribution aporte(UUID id, User backer, Project project, String monto) {
        Contribution contribution = new Contribution();
        contribution.setId(id);
        contribution.setBacker(backer);
        contribution.setProject(project);
        contribution.setAmount(monto == null ? null : new BigDecimal(monto));
        contribution.setCreatedAt(CIERRE);
        return contribution;
    }
}
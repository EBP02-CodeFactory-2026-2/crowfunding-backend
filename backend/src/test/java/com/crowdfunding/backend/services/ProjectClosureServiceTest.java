package com.crowdfunding.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.ContributionStatus;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.ProjectStatus;
import com.crowdfunding.backend.entity.Transaction;
import com.crowdfunding.backend.entity.TransactionStatus;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.persistence.ContributionRepository;
import com.crowdfunding.backend.persistence.ProjectRepository;

/**
 * UT-31 y UT-32 · Cierre automático y reembolsos.
 * Componente bajo prueba: ProjectClosureService.
 * Trazabilidad: HU-4.1.3 (CA1, CA2), HU-4.1.4 (CA1, CA2, CA4),
 * HU-4.1.1/4.1.2 y modelo conceptual §2.1 (ledger mutable).
 *
 * evaluateAndClose recibe el instante por parámetro, por lo que las fronteras
 * temporales son deterministas: no se necesita inyectar Clock ni dormir hilos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UT-31..UT-32 · ProjectClosureService")
class ProjectClosureServiceTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 12, 0);
    private static final LocalDateTime FECHA_CREACION = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ContributionRepository contributionRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ProjectClosureService service;

    // ==================== UT-31 · matriz de decisión ====================

    @ParameterizedTest(name = "UT-31 · Un proyecto en {0} no se cierra")
    @EnumSource(value = ProjectStatus.class, names = {"DRAFT", "FUNDED", "FAILED", "DELETED"})
    void proyectoNoActivoNoSeCierra(ProjectStatus status) {
        // Aunque la meta esté superada y el plazo vencido, el estado manda.
        Project project = proyecto(status, "1000000", "2000000", NOW.minusDays(1));

        boolean cerrado = service.evaluateAndClose(project, NOW);

        assertThat(cerrado).isFalse();
        assertThat(project.getStatus()).isEqualTo(status);
        verify(projectRepository, never()).save(any());
        verify(contributionRepository, never()).findByProjectIdAndStatus(any(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("UT-31 · CA1 · Meta alcanzada cierra como FUNDED, incluso con plazo futuro")
    void metaAlcanzadaCierraComoFundedAunqueElPlazoNoHayaVencido() {
        Project project = proyecto(ProjectStatus.ACTIVE, "1000000", "1000000", NOW.plusDays(30));
        Contribution c1 = aporte(usuario(2L, "ana.gomez@example.com"), ContributionStatus.COMPLETED);
        Contribution c2 = aporte(usuario(3L, "carlos@example.com"), ContributionStatus.COMPLETED);
        when(contributionRepository.findByProjectIdAndStatus(PROJECT_ID, ContributionStatus.COMPLETED))
                .thenReturn(List.of(c1, c2));

        boolean cerrado = service.evaluateAndClose(project, NOW);

        assertThat(cerrado).isTrue();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.FUNDED);
        verify(projectRepository).save(project);
        // backerCount = 2 patrocinadores distintos; se notifica al creador y a cada uno.
        verify(notificationService).notifyProjectFunded(project.getCreator(), project, 2L, NOW);
        verify(notificationService).notifyProjectFunded(c1.getBacker(), project, 2L, NOW);
        verify(notificationService).notifyProjectFunded(c2.getBacker(), project, 2L, NOW);
        verify(notificationService, times(3)).notifyProjectFunded(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("UT-31 · CA2 · Plazo vencido sin meta cierra como FAILED y procesa reembolsos")
    void plazoVencidoSinMetaCierraComoFailedYProcesaReembolsos() {
        Project project = proyecto(ProjectStatus.ACTIVE, "1000000", "100000", NOW.minusDays(1));
        Contribution c1 = aporte(usuario(2L, "ana.gomez@example.com"), ContributionStatus.COMPLETED);
        when(contributionRepository.findByProjectIdAndStatus(PROJECT_ID, ContributionStatus.COMPLETED))
                .thenReturn(List.of(c1));
        when(contributionRepository.findByProjectIdAndStatus(PROJECT_ID, ContributionStatus.REFUNDED))
                .thenReturn(List.of(c1));

        boolean cerrado = service.evaluateAndClose(project, NOW);

        assertThat(cerrado).isTrue();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.FAILED);
        verify(projectRepository).save(project);
        assertThat(c1.getStatus()).isEqualTo(ContributionStatus.REFUNDED);
        verify(contributionRepository).saveAndFlush(c1);
        verify(notificationService).notifyRefundProcessed(c1);
        verify(notificationService).notifyProjectFailed(project.getCreator(), project, 1L, NOW);
        verify(notificationService).notifyProjectFailed(c1.getBacker(), project, 1L, NOW);
    }

    @Test
    @DisplayName("UT-31 · Sin meta y con plazo futuro, el proyecto permanece ACTIVE")
    void activoSinMetaYConPlazoFuturoPermaneceActivo() {
        Project project = proyecto(ProjectStatus.ACTIVE, "1000000", "100000", NOW.plusDays(30));

        boolean cerrado = service.evaluateAndClose(project, NOW);

        assertThat(cerrado).isFalse();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        verify(projectRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("UT-31 · Borde: plazo exactamente igual al instante actual NO se cierra")
    void plazoIgualAlInstanteActualNoSeCierra() {
        Project project = proyecto(ProjectStatus.ACTIVE, "1000000", "100000", NOW);

        boolean cerrado = service.evaluateAndClose(project, NOW);

        // deadline.isBefore(now) es falso para la igualdad, aunque findExpiredActiveProjects
        // lo seleccione con 'deadline <= :now'. El cierre se retrasa un ciclo completo.
        assertThat(cerrado).isFalse();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-31 · El creador que también aportó no recibe una segunda notificación")
    void creadorQueTambienAportoNoSeNotificaDosVeces() {
        Project project = proyecto(ProjectStatus.ACTIVE, "1000000", "1000000", NOW.plusDays(30));
        User creador = project.getCreator(); // id 1
        Contribution c1 = aporte(creador, ContributionStatus.COMPLETED); // el creador aporta
        Contribution c2 = aporte(usuario(2L, "ana.gomez@example.com"), ContributionStatus.COMPLETED);
        when(contributionRepository.findByProjectIdAndStatus(PROJECT_ID, ContributionStatus.COMPLETED))
                .thenReturn(List.of(c1, c2));

        service.evaluateAndClose(project, NOW);

        // backerCount = 2; el creador se notifica una sola vez, no como creador y como backer.
        verify(notificationService, times(1)).notifyProjectFunded(creador, project, 2L, NOW);
        verify(notificationService, times(1)).notifyProjectFunded(c2.getBacker(), project, 2L, NOW);
        verify(notificationService, times(2)).notifyProjectFunded(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("UT-31 · Dos aportes del mismo patrocinador cuentan como un solo backer")
    void dosAportesDelMismoBackerCuentanComoUnPatrocinador() {
        Project project = proyecto(ProjectStatus.ACTIVE, "1000000", "1000000", NOW.plusDays(30));
        User backer = usuario(2L, "ana.gomez@example.com");
        Contribution c1 = aporte(backer, ContributionStatus.COMPLETED);
        Contribution c2 = aporte(backer, ContributionStatus.COMPLETED);
        when(contributionRepository.findByProjectIdAndStatus(PROJECT_ID, ContributionStatus.COMPLETED))
                .thenReturn(List.of(c1, c2));

        service.evaluateAndClose(project, NOW);

        verify(notificationService).notifyProjectFunded(project.getCreator(), project, 1L, NOW);
        verify(notificationService, times(1)).notifyProjectFunded(backer, project, 1L, NOW);
        verify(notificationService, times(2)).notifyProjectFunded(any(), any(), anyLong(), any());
    }

    // ==================== UT-32 · integridad del ledger ====================

    @Test
    @DisplayName("UT-32 · CA1 · processRefunds reembolsa cada aporte COMPLETED")
    void processRefundsReembolsaCadaAporteCompletado() {
        Project project = proyecto(ProjectStatus.FAILED, "1000000", "100000", NOW.minusDays(1));
        Contribution c1 = aporte(usuario(2L, "ana.gomez@example.com"), ContributionStatus.COMPLETED);
        Contribution c2 = aporte(usuario(3L, "carlos@example.com"), ContributionStatus.COMPLETED);
        when(contributionRepository.findByProjectIdAndStatus(PROJECT_ID, ContributionStatus.COMPLETED))
                .thenReturn(List.of(c1, c2));

        service.processRefunds(project);

        assertThat(c1.getStatus()).isEqualTo(ContributionStatus.REFUNDED);
        assertThat(c2.getStatus()).isEqualTo(ContributionStatus.REFUNDED);
        verify(contributionRepository).saveAndFlush(c1);
        verify(contributionRepository).saveAndFlush(c2);
        verify(notificationService).notifyRefundProcessed(c1);
        verify(notificationService).notifyRefundProcessed(c2);
    }

    @Test
    @DisplayName("UT-32 · D-06 · El reembolso NO revierte la transacción: el ledger queda inconsistente")
    void processRefundsNoRevocaLaTransaccionAsociada() {
        Project project = proyecto(ProjectStatus.FAILED, "1000000", "100000", NOW.minusDays(1));
        Contribution c1 = aporte(usuario(2L, "ana.gomez@example.com"), ContributionStatus.COMPLETED);
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setContribution(c1);
        tx.setAmount(new BigDecimal("10000"));
        tx.setStatus(TransactionStatus.SUCCESS);
        when(contributionRepository.findByProjectIdAndStatus(PROJECT_ID, ContributionStatus.COMPLETED))
                .thenReturn(List.of(c1));

        service.processRefunds(project);

        // El aporte pasa a REFUNDED...
        assertThat(c1.getStatus()).isEqualTo(ContributionStatus.REFUNDED);
        // ...pero la transacción financiera permanece en SUCCESS. ProjectClosureService
        // ni siquiera tiene un TransactionRepository inyectado, así que no puede revertirla.
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    // ==================== fixtures ====================

    private User usuario(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setFullName("Usuario " + id);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$hashDePrueba");
        user.setCreatedAt(FECHA_CREACION); // fija, para que equals sea determinista
        return user;
    }

    private Project proyecto(ProjectStatus status, String meta, String recaudado, LocalDateTime deadline) {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setCreator(usuario(1L, "camila@example.com"));
        project.setTitle("HoneyMoney");
        project.setStatus(status);
        project.setFundingGoal(new BigDecimal(meta));
        project.setCurrentAmount(new BigDecimal(recaudado));
        project.setDeadline(deadline);
        return project;
    }

    private Contribution aporte(User backer, ContributionStatus status) {
        Contribution contribution = new Contribution();
        contribution.setId(UUID.randomUUID());
        contribution.setBacker(backer);
        contribution.setAmount(new BigDecimal("10000"));
        contribution.setStatus(status);
        return contribution;
    }
}
package com.crowdfunding.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.crowdfunding.backend.contribution.ContributionListResponse;
import com.crowdfunding.backend.contribution.ContributionReceipt;
import com.crowdfunding.backend.contribution.ContributionRequest;
import com.crowdfunding.backend.contribution.ContributionResponse;
import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.ContributionStatus;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.ProjectStatus;
import com.crowdfunding.backend.entity.Transaction;
import com.crowdfunding.backend.entity.TransactionStatus;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.InvalidContributionAmountException;
import com.crowdfunding.backend.exception.ProjectAccessDeniedException;
import com.crowdfunding.backend.exception.ReplayAttackException;
import com.crowdfunding.backend.exception.ResourceNotFoundException;
import com.crowdfunding.backend.persistence.ContributionRepository;
import com.crowdfunding.backend.persistence.ProjectRepository;
import com.crowdfunding.backend.persistence.TransactionRepository;
import com.crowdfunding.backend.persistence.UserRepository;

/**
 * UT-24 a UT-28 · Aportes, historial y comprobante.
 * Componente bajo prueba: ContributionService.
 * Trazabilidad: HU-2.2.1 (CA1, CA3, CA7), HU-2.2.2 (CA1, CA3, CA6),
 * HU-2.2.3 (CA2, CA5), HU-3.1.2 (CA1, CA3, CA4) y secuencia SEQ-03.
 *
 * El constructor de ContributionService es package-private: esta prueba debe
 * residir en el mismo paquete. El mínimo de $10.000 no se cubre aquí porque
 * vive en las anotaciones de ContributionRequest (se valida en PF-15).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UT-24..UT-28 · ContributionService")
class ContributionServiceTest {

    private static final String BACKER_EMAIL = "ana.gomez@example.com";
    private static final Long BACKER_ID = 2L;
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID CONTRIBUTION_ID = UUID.randomUUID();
    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 21, 10, 0);
    private static final String NONCE = "nonce-001";

    @Mock
    private ContributionRepository contributionRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ContributionService service;

    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(BACKER_EMAIL, null, List.of()));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    // ==================== UT-24 · guardas del aporte ====================

    @Test
    @DisplayName("UT-24 · Aportar a un proyecto inexistente responde 404 y no registra nada")
    void proyectoInexistente() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());
        ContributionRequest request = solicitud(NONCE, null);

        assertThatThrownBy(() -> service.supportProject(PROJECT_ID, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Proyecto no encontrado con el ID: " + PROJECT_ID);

        verificarQueNoSeRegistraAporte();
    }

    @ParameterizedTest(name = "UT-24 · CA3 · Proyecto en {0} no admite aportes")
    @EnumSource(value = ProjectStatus.class, names = {"DRAFT", "FUNDED", "FAILED", "DELETED"})
    void proyectoNoActivo(ProjectStatus estado) {
        Project project = proyectoActivo(5);
        project.setStatus(estado);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        ContributionRequest request = solicitud(NONCE, null);

        assertThatThrownBy(() -> service.supportProject(PROJECT_ID, request))
                .isInstanceOf(ProjectAccessDeniedException.class)
                .hasMessage("El proyecto no se encuentra en estado activo y no puede recibir aportes");

        verificarQueNoSeRegistraAporte();
    }

    @ParameterizedTest(name = "UT-24 · HU-2.2.3 CA5 · Riesgo Alto con confirmación={0} se rechaza")
    @CsvSource(value = {"null", "false"}, nullValues = "null")
    void riesgoAltoSinConfirmacion(Boolean aceptoRiesgo) {
        Project project = proyectoActivo(-5); // riskTag = "Riesgo Alto"
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        ContributionRequest request = solicitud(NONCE, aceptoRiesgo);

        assertThat(project.getRiskTag()).isEqualTo("Riesgo Alto");
        assertThatThrownBy(() -> service.supportProject(PROJECT_ID, request))
                .isInstanceOf(InvalidContributionAmountException.class)
                .hasMessage("Debes confirmar la advertencia de riesgo antes de aportar");

        verificarQueNoSeRegistraAporte();
    }

    @Test
    @DisplayName("UT-24 · CA7 · Un nonce ya utilizado se rechaza antes de crear el aporte")
    void nonceReutilizado() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(proyectoActivo(5)));
        when(transactionRepository.existsByNonce(NONCE)).thenReturn(true);
        ContributionRequest request = solicitud(NONCE, null);

        assertThatThrownBy(() -> service.supportProject(PROJECT_ID, request))
                .isInstanceOf(ReplayAttackException.class)
                .hasMessage("El token de seguridad (nonce) ya ha sido utilizado en una transacción anterior");

        verificarQueNoSeRegistraAporte();
    }

    @Test
    @DisplayName("UT-24 · El usuario autenticado que ya no existe se rechaza sin registrar el aporte")
    void usuarioAutenticadoInexistente() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(proyectoActivo(5)));
        when(transactionRepository.existsByNonce(NONCE)).thenReturn(false);
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.empty());
        ContributionRequest request = solicitud(NONCE, null);

        assertThatThrownBy(() -> service.supportProject(PROJECT_ID, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuario autenticado no encontrado: " + BACKER_EMAIL);

        verificarQueNoSeRegistraAporte();
    }

    // ==================== UT-25 · aporte exitoso ====================

    @Test
    @DisplayName("UT-25 · CA1 · Aporte válido: COMPLETED + SUCCESS + referencia MOCK- + incremento único")
    void aporteExitoso() {
        Project project = proyectoActivo(5); // Riesgo Medio, currentAmount 0
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(transactionRepository.existsByNonce(NONCE)).thenReturn(false);
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));
        alGuardarAporteAsignarIdYFecha();

        ContributionResponse response = service.supportProject(PROJECT_ID, solicitud(NONCE, false));

        assertThat(response.contributionId()).isEqualTo(CONTRIBUTION_ID);
        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.amount()).isEqualByComparingTo("50000");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.projectNewAmount()).isEqualByComparingTo("50000");
        assertThat(response.createdAt()).isEqualTo(FECHA);
        assertThat(response.transaction().referenceCode()).startsWith("MOCK-");
        assertThat(response.transaction().paymentMethod()).isEqualTo("SIMULATED_CARD");
        assertThat(response.transaction().processedAt()).isNotNull();

        ArgumentCaptor<Contribution> aporte = ArgumentCaptor.forClass(Contribution.class);
        verify(contributionRepository).save(aporte.capture());
        assertThat(aporte.getValue().getStatus()).isEqualTo(ContributionStatus.COMPLETED);
        assertThat(aporte.getValue().getProject()).isSameAs(project);
        assertThat(aporte.getValue().getBacker().getEmail()).isEqualTo(BACKER_EMAIL);

        ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(tx.capture());
        assertThat(tx.getValue().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(tx.getValue().getNonce()).isEqualTo(NONCE);
        assertThat(tx.getValue().getAmount()).isEqualByComparingTo("50000");
        assertThat(tx.getValue().getContribution()).isSameAs(aporte.getValue());

        // El flush fuerza la violación de UNIQUE dentro de la transacción (SEQ-03).
        verify(transactionRepository).flush();
        // El total se incrementa una sola vez, con el UPDATE atómico del repositorio.
        verify(projectRepository, times(1))
                .incrementCurrentAmount(PROJECT_ID, new BigDecimal("50000"));
        assertThat(project.getCurrentAmount()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("UT-25 · HU-2.2.3 CA2 · Riesgo Alto con confirmación explícita sí se acepta")
    void riesgoAltoConConfirmacion() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(proyectoActivo(-5)));
        when(transactionRepository.existsByNonce(NONCE)).thenReturn(false);
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));
        alGuardarAporteAsignarIdYFecha();

        ContributionResponse response = service.supportProject(PROJECT_ID, solicitud(NONCE, true));

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(projectRepository).incrementCurrentAmount(PROJECT_ID, new BigDecimal("50000"));
    }

    // ==================== UT-26 · plazo vencido (D-05) ====================

    @Test
    @DisplayName("UT-26 · D-05 · Con el plazo ya vencido y estado ACTIVE el aporte SE ACEPTA")
    void plazoVencidoAceptaAporte() {
        Project project = proyectoActivo(5);
        project.setDeadline(LocalDateTime.now().minusMinutes(1));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(transactionRepository.existsByNonce(NONCE)).thenReturn(false);
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));
        alGuardarAporteAsignarIdYFecha();

        ContributionResponse response = service.supportProject(PROJECT_ID, solicitud(NONCE, null));

        // HU-2.2.1 CA3 esperaría un rechazo: "Esta campaña ya no admite aportes".
        // supportProject solo evalúa ProjectStatus.ACTIVE y nunca compara el deadline,
        // así que el aporte pasa durante toda la ventana hasta el siguiente ciclo del job.
        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(contributionRepository).save(any(Contribution.class));
        verify(projectRepository).incrementCurrentAmount(PROJECT_ID, new BigDecimal("50000"));
    }

    // ==================== UT-27 · historial y filtro ====================

    @ParameterizedTest(name = "UT-27 · status={0} usa la consulta sin filtro")
    @CsvSource(value = {"null", "''"}, nullValues = "null")
    void sinFiltroUsaConsultaCompleta(String status) {
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));
        when(contributionRepository.findByBackerIdOrderByCreatedAtDesc(BACKER_ID)).thenReturn(List.of());

        assertThat(service.contributionHistory(status)).isEmpty();

        verify(contributionRepository).findByBackerIdOrderByCreatedAtDesc(BACKER_ID);
        verify(contributionRepository, never())
                .findByBackerIdAndStatusOrderByCreatedAtDesc(any(), any());
    }

    @ParameterizedTest(name = "UT-27 · CA3 · status={0} se normaliza a {1}")
    @CsvSource({"refunded,REFUNDED", "' COMPLETED ',COMPLETED"})
    void filtroNormalizaMayusculasYEspacios(String status, ContributionStatus esperado) {
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));
        when(contributionRepository.findByBackerIdAndStatusOrderByCreatedAtDesc(BACKER_ID, esperado))
                .thenReturn(List.of());

        service.contributionHistory(status);

        verify(contributionRepository).findByBackerIdAndStatusOrderByCreatedAtDesc(BACKER_ID, esperado);
        verify(contributionRepository, never()).findByBackerIdOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("UT-27 · Un estado inválido produce 400 con la lista de valores permitidos")
    void estadoInvalidoResponde400() {
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.contributionHistory("INVALIDO"));

        assertThat(ex.getStatusCode().value()).isEqualTo(400);
        assertThat(ex.getReason()).isEqualTo(
                "Estado inválido: INVALIDO. Valores permitidos: [PENDING, COMPLETED, REFUNDED, CANCELLED]");
        verify(contributionRepository, never()).findByBackerIdOrderByCreatedAtDesc(any());
        verify(contributionRepository, never())
                .findByBackerIdAndStatusOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("UT-27 · CA1 · El historial mapea proyecto, monto, estado y fecha, conservando el orden")
    void mapeaCamposDelHistorial() {
        Project proyecto = proyectoActivo(5);
        Contribution reciente = aporte(ContributionStatus.COMPLETED, proyecto);
        Contribution anterior = aporte(ContributionStatus.REFUNDED, proyecto);
        anterior.setId(UUID.randomUUID());
        anterior.setCreatedAt(FECHA.minusDays(2));
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));
        when(contributionRepository.findByBackerIdOrderByCreatedAtDesc(BACKER_ID))
                .thenReturn(List.of(reciente, anterior));

        List<ContributionListResponse> respuesta = service.contributionHistory(null);

        assertThat(respuesta).hasSize(2);
        assertThat(respuesta.get(0).id()).isEqualTo(CONTRIBUTION_ID);
        assertThat(respuesta.get(0).projectName()).isEqualTo("HoneyMoney");
        assertThat(respuesta.get(0).projectId()).isEqualTo(PROJECT_ID);
        assertThat(respuesta.get(0).amount()).isEqualByComparingTo("50000");
        assertThat(respuesta.get(0).status()).isEqualTo("COMPLETED");
        assertThat(respuesta.get(0).createdAt()).isEqualTo(FECHA);
        assertThat(respuesta.get(1).status()).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("UT-27 · El historial de un usuario inexistente responde 404")
    void historialDeUsuarioInexistente() {
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.contributionHistory(null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuario autenticado no encontrado: " + BACKER_EMAIL);
    }

    // ==================== UT-28 · comprobante ====================

    @Test
    @DisplayName("UT-28 · CA1/CA4 · Comprobante del titular con medio de pago enmascarado")
    void comprobanteDelTitular() {
        Contribution aporte = aporte(ContributionStatus.COMPLETED, proyectoActivo(5));
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));
        when(contributionRepository.findByIdAndBackerId(CONTRIBUTION_ID, BACKER_ID))
                .thenReturn(Optional.of(aporte));
        when(transactionRepository.findByContributionId(CONTRIBUTION_ID))
                .thenReturn(Optional.of(transaccion(aporte)));

        ContributionReceipt recibo = service.getReceipt(CONTRIBUTION_ID);

        assertThat(recibo.receiptId()).isEqualTo("RCP-MOCK-REF-001");
        assertThat(recibo.contributionId()).isEqualTo(CONTRIBUTION_ID);
        assertThat(recibo.projectName()).isEqualTo("HoneyMoney");
        assertThat(recibo.amount()).isEqualByComparingTo("50000");
        assertThat(recibo.status()).isEqualTo("COMPLETED");
        assertThat(recibo.generatedAt()).isNotNull();
        assertThat(recibo.transactionDetails().referenceCode()).isEqualTo("MOCK-REF-001");
        assertThat(recibo.transactionDetails().paymentMethod())
                .matches("^SIMULATED_CARD \\*\\*\\* \\d{4}$");
    }

    @Test
    @DisplayName("UT-28 · CA3 · Un aporte ajeno responde igual que uno inexistente (IDOR)")
    void comprobanteDeAporteAjeno() {
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));
        when(contributionRepository.findByIdAndBackerId(CONTRIBUTION_ID, BACKER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReceipt(CONTRIBUTION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Aporte no encontrado con el ID: " + CONTRIBUTION_ID);

        // La consulta siempre acota por el titular: nunca se usa findById a secas.
        verify(contributionRepository, never()).findById(any());
        verify(transactionRepository, never()).findByContributionId(any());
    }

    @Test
    @DisplayName("UT-28 · D-08 · Un aporte sin transacción lanza IllegalStateException (500 sin formato)")
    void aporteSinTransaccion() {
        Contribution aporte = aporte(ContributionStatus.PENDING, proyectoActivo(5));
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));
        when(contributionRepository.findByIdAndBackerId(CONTRIBUTION_ID, BACKER_ID))
                .thenReturn(Optional.of(aporte));
        when(transactionRepository.findByContributionId(CONTRIBUTION_ID)).thenReturn(Optional.empty());

        // HU-3.1.2 CA2 esperaría "No hay comprobante disponible para este aporte" con un 4xx.
        // IllegalStateException no tiene @ExceptionHandler: Spring responde 500 con su
        // cuerpo por defecto y expone el mensaje interno.
        assertThatThrownBy(() -> service.getReceipt(CONTRIBUTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El aporte " + CONTRIBUTION_ID + " no tiene una transacción asociada");
    }

    @Test
    @DisplayName("UT-28 · Los últimos 4 dígitos son deterministas para la misma transacción")
    void mascaraDeterminista() {
        Contribution aporte = aporte(ContributionStatus.COMPLETED, proyectoActivo(5));
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));
        when(contributionRepository.findByIdAndBackerId(CONTRIBUTION_ID, BACKER_ID))
                .thenReturn(Optional.of(aporte));
        when(transactionRepository.findByContributionId(CONTRIBUTION_ID))
                .thenReturn(Optional.of(transaccion(aporte)));

        String primera = service.getReceipt(CONTRIBUTION_ID).transactionDetails().paymentMethod();
        String segunda = service.getReceipt(CONTRIBUTION_ID).transactionDetails().paymentMethod();

        // Se derivan de Math.floorMod(transaction.getId().hashCode(), 10_000):
        // no son dígitos reales de tarjeta, pero son estables entre consultas.
        assertThat(primera).isEqualTo(segunda);
    }

    @Test
    @DisplayName("UT-28 · El comprobante también se genera para un aporte REFUNDED")
    void comprobanteDeAporteReembolsado() {
        Contribution aporte = aporte(ContributionStatus.REFUNDED, proyectoActivo(5));
        when(userRepository.findByEmail(BACKER_EMAIL)).thenReturn(Optional.of(patrocinador()));
        when(contributionRepository.findByIdAndBackerId(CONTRIBUTION_ID, BACKER_ID))
                .thenReturn(Optional.of(aporte));
        when(transactionRepository.findByContributionId(CONTRIBUTION_ID))
                .thenReturn(Optional.of(transaccion(aporte)));

        assertThat(service.getReceipt(CONTRIBUTION_ID).status()).isEqualTo("REFUNDED");
    }

    // ==================== fixtures y auxiliares ====================

    private User patrocinador() {
        User user = new User();
        user.setId(BACKER_ID);
        user.setFullName("Ana Gómez");
        user.setEmail(BACKER_EMAIL);
        user.setPasswordHash("$2a$10$hashDePrueba");
        user.setCreatedAt(LocalDateTime.now().minusDays(30));
        return user;
    }

    private Project proyectoActivo(int riskScore) {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setTitle("HoneyMoney");
        project.setDescription("a".repeat(200));
        project.setFundingGoal(new BigDecimal("1000000"));
        project.setCurrentAmount(BigDecimal.ZERO);
        project.setDeadline(LocalDateTime.now().plusDays(30));
        project.setStatus(ProjectStatus.ACTIVE);
        project.setRiskScore(riskScore);
        return project;
    }

    private ContributionRequest solicitud(String nonce, Boolean aceptoRiesgo) {
        return new ContributionRequest(new BigDecimal("50000"), "SIMULATED_CARD", nonce, aceptoRiesgo);
    }

    private Contribution aporte(ContributionStatus estado, Project proyecto) {
        Contribution contribution = new Contribution();
        contribution.setId(CONTRIBUTION_ID);
        contribution.setBacker(patrocinador());
        contribution.setProject(proyecto);
        contribution.setAmount(new BigDecimal("50000"));
        contribution.setStatus(estado);
        contribution.setCreatedAt(FECHA);
        return contribution;
    }

    private Transaction transaccion(Contribution aporte) {
        Transaction transaction = new Transaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setContribution(aporte);
        transaction.setReferenceCode("MOCK-REF-001");
        transaction.setPaymentMethod("SIMULATED_CARD");
        transaction.setAmount(new BigDecimal("50000"));
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setNonce(NONCE);
        transaction.setProcessedAt(FECHA);
        return transaction;
    }

    /** En producción JPA asigna id y createdAt durante el INSERT. */
    private void alGuardarAporteAsignarIdYFecha() {
        when(contributionRepository.save(any(Contribution.class))).thenAnswer(invocation -> {
            Contribution c = invocation.getArgument(0);
            c.setId(CONTRIBUTION_ID);
            c.setCreatedAt(FECHA);
            return c;
        });
    }

    private void verificarQueNoSeRegistraAporte() {
        verify(contributionRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(projectRepository, never()).incrementCurrentAmount(any(), any());
    }
}
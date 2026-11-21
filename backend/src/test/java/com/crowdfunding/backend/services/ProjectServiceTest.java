package com.crowdfunding.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.ProjectStatus;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.MissingProjectFieldsException;
import com.crowdfunding.backend.exception.ProjectAccessDeniedException;
import com.crowdfunding.backend.exception.ProjectLockedException;
import com.crowdfunding.backend.exception.ResourceNotFoundException;
import com.crowdfunding.backend.interfaces.ScoringEngine;
import com.crowdfunding.backend.persistence.ContributionRepository;
import com.crowdfunding.backend.persistence.ProjectRepository;
import com.crowdfunding.backend.persistence.UserRepository;
import com.crowdfunding.backend.project.CreateProjectRequest;
import com.crowdfunding.backend.project.ProjectResponse;
import com.crowdfunding.backend.project.PublishProjectResponse;
import com.crowdfunding.backend.entity.ContributionStatus;
import com.crowdfunding.backend.exception.DeleteConflictException;
import com.crowdfunding.backend.exception.RiskScoreCalculationException;
import com.crowdfunding.backend.project.EditProjectRequest;
import com.crowdfunding.backend.project.EditProjectResponse;

/**
 * UT-18 a UT-20 · Creación de borrador y publicación de proyectos.
 * Componente bajo prueba: ProjectService.createProject / publishProject.
 * Trazabilidad: HU-2.1.1 (CA7), HU-2.1.6 (CA1, CA2, CA4), HU-2.1.8 (CA1, CA2, CA5)
 * y diagramas de secuencia SEQ-02A y SEQ-02B.
 *
 * El scoring se mockea mediante la interfaz ScoringEngine: estas pruebas verifican
 * la orquestación de ProjectService, no el algoritmo (cubierto en UT-02..UT-08).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UT-18..UT-20 · ProjectService (crear y publicar)")
class ProjectServiceTest {

    private static final String EMAIL = "camila@example.com";
    private static final Long CREATOR_ID = 1L;
    private static final Long OTRO_USUARIO_ID = 2L;
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 21, 10, 0);
    private static final String DESCRIPCION_LARGA = "a".repeat(200);

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContributionRepository contributionRepository;

    @Mock
    private ScoringEngine scoringEngine;

    @InjectMocks
    private ProjectService service;

    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null, List.of()));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    // ---------- UT-18 · creación de borrador ----------
    @Test
    @DisplayName("UT-18 · HU-2.1.6 CA1/CA2 · El borrador nace en DRAFT con monto 0 y creador de la sesión")
    void creaBorradorConCreadorDeLaSesion() {
        User creator = creador();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creator));
        alGuardarAsignarIdYFecha();
        CreateProjectRequest request = new CreateProjectRequest(
                "EcoBici Comunitaria", DESCRIPCION_LARGA, null, null, null);

        ProjectResponse response = service.createProject(request);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project guardado = captor.getValue();

        // HU-2.1.1 CA7: el creador sale del contexto de seguridad, nunca de la petición.
        assertThat(guardado.getCreator()).isSameAs(creator);
        assertThat(guardado.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(guardado.getCurrentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(guardado.getRiskScore()).isNull();
        assertThat(guardado.getFundingGoal()).isNull();
        assertThat(guardado.getDeadline()).isNull();
        assertThat(guardado.getTitle()).isEqualTo("EcoBici Comunitaria");

        assertThat(response.id()).isEqualTo(PROJECT_ID);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.currentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.riskTag()).isNull();
        assertThat(response.createdAt()).isEqualTo(FECHA);
    }

    @Test
    @DisplayName("UT-18 · El usuario autenticado que ya no existe se rechaza sin persistir")
    void creadorInexistenteRechaza() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        CreateProjectRequest request = new CreateProjectRequest(
                "EcoBici", DESCRIPCION_LARGA, null, null, null);

        assertThatThrownBy(() -> service.createProject(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuario autenticado no encontrado: " + EMAIL);

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-18 · D-12 · Una meta negativa se persiste en el borrador (comportamiento actual)")
    void aceptaMetaNegativa() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        alGuardarAsignarIdYFecha();
        CreateProjectRequest request = new CreateProjectRequest(
                "EcoBici", DESCRIPCION_LARGA, new BigDecimal("-25000"), null, null);

        ProjectResponse response = service.createProject(request);

        // HU-2.1.1 CA3 esperaría un 400 "La meta debe ser mayor a $0".
        // CreateProjectRequest solo declara @NotBlank en title y description, y el
        // servicio no valida formatos: el valor inválido llega a la base de datos.
        assertThat(response.fundingGoal()).isEqualByComparingTo("-25000");
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    @DisplayName("UT-18 · D-12 · Una fecha límite pasada se persiste en el borrador (comportamiento actual)")
    void aceptaFechaPasada() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        alGuardarAsignarIdYFecha();
        LocalDateTime ayer = LocalDateTime.now().minusDays(1);
        CreateProjectRequest request = new CreateProjectRequest(
                "EcoBici", DESCRIPCION_LARGA, new BigDecimal("1000000"), null, ayer);

        ProjectResponse response = service.createProject(request);

        // El error solo se detecta al publicar, vía RiskScoringService.checkConsistency (422).
        assertThat(response.deadline()).isEqualTo(ayer);
        verify(projectRepository).save(any(Project.class));
    }

    // ---------- UT-19 · guardas de publicación ----------
    @Test
    @DisplayName("UT-19 · Publicar un proyecto inexistente responde 404 y no calcula riesgo")
    void proyectoInexistente() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publishProject(PROJECT_ID, CREATOR_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Proyecto no encontrado con el ID: " + PROJECT_ID);

        verificarQueNoSePublica();
    }

    @Test
    @DisplayName("UT-19 · Un usuario que no es el creador no puede publicar el proyecto")
    void usuarioNoEsCreador() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(borradorCompleto()));

        assertThatThrownBy(() -> service.publishProject(PROJECT_ID, OTRO_USUARIO_ID))
                .isInstanceOf(ProjectAccessDeniedException.class)
                .hasMessage("No tienes permiso para publicar este proyecto");

        verificarQueNoSePublica();
    }

    @Test
    @DisplayName("UT-19 · Publicar un proyecto ya ACTIVE responde 409")
    void proyectoYaPublicado() {
        Project project = borradorCompleto();
        project.setStatus(ProjectStatus.ACTIVE);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.publishProject(PROJECT_ID, CREATOR_ID))
                .isInstanceOf(ProjectLockedException.class)
                .hasMessage("El proyecto ya fue publicado y no está en estado borrador");

        verificarQueNoSePublica();
    }

    @Test
    @DisplayName("UT-19 · HU-2.1.8 CA1 · Sin meta ni plazo se listan los campos faltantes y sigue DRAFT")
    void camposFaltantesMetaYPlazo() {
        Project project = borradorCompleto();
        project.setFundingGoal(null);
        project.setDeadline(null);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        MissingProjectFieldsException ex = assertThrows(MissingProjectFieldsException.class,
                () -> service.publishProject(PROJECT_ID, CREATOR_ID));

        assertThat(ex.getMessage()).isEqualTo("Faltan campos obligatorios para publicar el proyecto");
        assertThat(ex.getMissingFields()).containsExactly("fundingGoal", "deadline");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        verificarQueNoSePublica();
    }

    @Test
    @DisplayName("UT-19 · Un título en blanco también se reporta como campo faltante")
    void tituloEnBlanco() {
        Project project = borradorCompleto();
        project.setTitle("   ");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        MissingProjectFieldsException ex = assertThrows(MissingProjectFieldsException.class,
                () -> service.publishProject(PROJECT_ID, CREATOR_ID));

        assertThat(ex.getMissingFields()).containsExactly("title");
        verificarQueNoSePublica();
    }

    // ---------- UT-20 · publicación exitosa ----------
    @Test
    @DisplayName("UT-20 · HU-2.1.8 CA2/CA5 · Publica delegando el puntaje al motor y persiste ACTIVE")
    void publicaCalculandoRiesgo() {
        Project project = borradorCompleto();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(scoringEngine.calculateRiskScore(any(Project.class), any(User.class))).thenReturn(5);
        alGuardarYFlushAsignarFechaActualizacion();

        PublishProjectResponse response = service.publishProject(PROJECT_ID, CREATOR_ID);

        assertThat(response.id()).isEqualTo(PROJECT_ID);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.riskScore()).isEqualTo(5);
        assertThat(response.riskTag()).isEqualTo("Riesgo Medio");
        assertThat(response.fundingGoal()).isEqualByComparingTo("25000000");
        assertThat(response.updatedAt()).isEqualTo(FECHA);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(project.getRiskScore()).isEqualTo(5);

        // SEQ-02B: el puntaje se calcula antes de persistir el cambio de estado.
        InOrder orden = inOrder(scoringEngine, projectRepository);
        orden.verify(scoringEngine).calculateRiskScore(project, project.getCreator());
        orden.verify(projectRepository).saveAndFlush(project);
    }

    @Test
    @DisplayName("UT-20 · El overload de un argumento resuelve el usuario desde el contexto de seguridad")
    void publicaResolviendoUsuarioDesdeElContexto() {
        Project project = borradorCompleto();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(scoringEngine.calculateRiskScore(any(Project.class), any(User.class))).thenReturn(6);
        alGuardarYFlushAsignarFechaActualizacion();

        PublishProjectResponse response = service.publishProject(PROJECT_ID);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.riskScore()).isEqualTo(6);
        assertThat(response.riskTag()).isEqualTo("Riesgo Bajo");
        verify(userRepository).findByEmail(EMAIL);
    }

    // ---------- fixtures y auxiliares ----------
    private User creador() {
        User user = new User();
        user.setId(CREATOR_ID);
        user.setFullName("Camila Torres");
        user.setEmail(EMAIL);
        user.setPasswordHash("$2a$10$hashDePrueba");
        user.setCreatedAt(LocalDateTime.now().minusDays(30));
        return user;
    }

    private Project borradorCompleto() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setCreator(creador());
        project.setTitle("HoneyMoney");
        project.setDescription(DESCRIPCION_LARGA);
        project.setImageUrl("https://placehold.co/600x400/png");
        project.setFundingGoal(new BigDecimal("25000000"));
        project.setDeadline(LocalDateTime.now().plusDays(60));
        return project;
    }

    /** En producción JPA asigna id y createdAt durante el INSERT. */
    private void alGuardarAsignarIdYFecha() {
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            p.setId(PROJECT_ID);
            p.setCreatedAt(FECHA);
            return p;
        });
    }

    /** En producción @UpdateTimestamp asigna updatedAt al hacer flush. */
    private void alGuardarYFlushAsignarFechaActualizacion() {
        when(projectRepository.saveAndFlush(any(Project.class))).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            p.setUpdatedAt(FECHA);
            return p;
        });
    }

    private void verificarQueNoSePublica() {
        verify(scoringEngine, never()).calculateRiskScore(any(), any());
        verify(projectRepository, never()).saveAndFlush(any());
    }

        // ==================== UT-21 · inmutabilidad de meta y plazo ====================

    @Test
    @DisplayName("UT-21 · D-01 · En ACTIVE se aplica una meta DISTINTA si el plazo también viene con valor")
    void enActiveAplicaUnaMetaDistinta() {
        Project project = proyectoActivo(); // meta 25.000.000, recaudado 1.000.000
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(scoringEngine.calculateRiskScore(any(Project.class), any(User.class))).thenReturn(5);
        alGuardarYFlushAsignarFechaActualizacion();

        EditProjectRequest request = new EditProjectRequest(
                "Título nuevo", null, new BigDecimal("1000000"), null, project.getDeadline());

        EditProjectResponse response = service.editProject(PROJECT_ID, request);

        // HU-2.1.7 CA5 esperaría un rechazo sin modificar ningún campo.
        assertThat(response.fundingGoal()).isEqualByComparingTo("1000000");
        assertThat(project.getFundingGoal()).isEqualByComparingTo("1000000");
        assertThat(response.status()).isEqualTo("ACTIVE");
        // Con la meta reducida, lo ya recaudado pasa a representar el 100 % de la meta:
        // el siguiente ciclo del job declararía FUNDED el proyecto.
        assertThat(response.currentAmount()).isEqualByComparingTo(response.fundingGoal());
        verify(projectRepository).saveAndFlush(project);
    }

    @Test
    @DisplayName("UT-21 · D-01 · En ACTIVE también se aplica un plazo DISTINTO si la meta viene con valor")
    void enActiveAplicaUnPlazoDistinto() {
        Project project = proyectoActivo();
        LocalDateTime nuevoPlazo = LocalDateTime.now().plusDays(2);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(scoringEngine.calculateRiskScore(any(Project.class), any(User.class))).thenReturn(5);
        alGuardarYFlushAsignarFechaActualizacion();

        EditProjectRequest request = new EditProjectRequest(
                "Título nuevo", null, project.getFundingGoal(), null, nuevoPlazo);

        EditProjectResponse response = service.editProject(PROJECT_ID, request);

        assertThat(response.deadline()).isEqualTo(nuevoPlazo);
        assertThat(project.getDeadline()).isEqualTo(nuevoPlazo);
    }

    // ==================== UT-22 · guardas y recálculo ====================

    @Test
    @DisplayName("UT-22 · En ACTIVE, editar sin enviar meta ni plazo se rechaza y no modifica nada")
    void enActiveSinMetaNiPlazoRechaza() {
        Project project = proyectoActivo();
        String tituloOriginal = project.getTitle();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        EditProjectRequest request = new EditProjectRequest("Solo título", null, null, null, null);

        assertThatThrownBy(() -> service.editProject(PROJECT_ID, request))
                .isInstanceOf(ProjectLockedException.class)
                .hasMessage("La meta y el plazo no pueden modificarse una vez el proyecto está activo");

        assertThat(project.getTitle()).isEqualTo(tituloOriginal);
        verify(scoringEngine, never()).calculateRiskScore(any(), any());
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UT-22 · CA6 · Un usuario que no es el creador no puede editar (mensaje copiado de publicar)")
    void editarComoOtroUsuarioRechaza() {
        Project project = proyectoActivo();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioConId(OTRO_USUARIO_ID)));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        EditProjectRequest request = new EditProjectRequest("Intento ajeno", null, null, null, null);

        assertThatThrownBy(() -> service.editProject(PROJECT_ID, request))
                .isInstanceOf(ProjectAccessDeniedException.class)
                // El texto dice "publicar" en lugar de "editar": copia del mensaje de publishProject.
                .hasMessage("No tienes permiso para publicar este proyecto");

        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UT-22 · Editar un proyecto inexistente responde 404")
    void editarProyectoInexistente() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());
        EditProjectRequest request = new EditProjectRequest("X", null, null, null, null);

        assertThatThrownBy(() -> service.editProject(PROJECT_ID, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Proyecto no encontrado con el ID: " + PROJECT_ID);
    }

        @Test
    @DisplayName("UT-22 · CA3 · Al cambiar el contenido de un ACTIVE se recalcula el riesgo")
    void enActiveConContenidoRecalcula() {
        Project project = proyectoActivo();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(scoringEngine.calculateRiskScore(any(Project.class), any(User.class))).thenReturn(8);
        alGuardarYFlushAsignarFechaActualizacion();

        // Orden del record: (title, description, fundingGoal, imageUrl, deadline)
        EditProjectRequest request = new EditProjectRequest(
                "Título enriquecido",
                "b".repeat(200),
                project.getFundingGoal(),
                null,
                project.getDeadline());

        EditProjectResponse response = service.editProject(PROJECT_ID, request);

        assertThat(response.riskTag()).isEqualTo("Riesgo Bajo");
        assertThat(project.getRiskScore()).isEqualTo(8);
        verify(scoringEngine).calculateRiskScore(project, project.getCreator());
    }

    @Test
    @DisplayName("UT-22 · D-13 · Cambiar solo meta y plazo NO recalcula: el puntaje queda obsoleto")
    void enActiveSoloMetaYPlazoNoRecalcula() {
        Project project = proyectoActivo(); // riskScore = 5
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        alGuardarYFlushAsignarFechaActualizacion();

        // Meta fuera del rango razonable y plazo de 3 días: ambos factores puntúan a la baja.
        EditProjectRequest request = new EditProjectRequest(
                null, null, new BigDecimal("100000"), null, LocalDateTime.now().plusDays(3));

        EditProjectResponse response = service.editProject(PROJECT_ID, request);

        // contentChanged solo se activa con title, description o imageUrl.
        verify(scoringEngine, never()).calculateRiskScore(any(), any());
        assertThat(project.getRiskScore()).isEqualTo(5);
        assertThat(response.riskTag()).isEqualTo("Riesgo Medio");
        assertThat(response.fundingGoal()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("UT-22 · CA1 · En DRAFT la edición es parcial y no calcula riesgo")
    void enDraftEdicionParcialSinRecalculo() {
        Project project = borradorCompleto();
        BigDecimal metaOriginal = project.getFundingGoal();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        alGuardarYFlushAsignarFechaActualizacion();

        EditProjectRequest request = new EditProjectRequest("Borrador editado", null, null, null, null);

        EditProjectResponse response = service.editProject(PROJECT_ID, request);

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.title()).isEqualTo("Borrador editado");
        assertThat(response.fundingGoal()).isEqualByComparingTo(metaOriginal); // los null no se modifican
        assertThat(response.riskTag()).isEqualTo("Sin evaluar");
        verify(scoringEngine, never()).calculateRiskScore(any(), any());
    }

        @Test
    @DisplayName("UT-22 · CA4 · Si el recálculo falla no se persiste, pero se pierde el detalle por campo")
    void falloDelRecalculoRechazaSinPersistir() {
        Project project = proyectoActivo();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(scoringEngine.calculateRiskScore(any(Project.class), any(User.class)))
                .thenThrow(new RiskScoreCalculationException(
                        "Fallo interno del motor",
                        List.of(new RiskScoreCalculationException.Issue("deadline", "Detalle original"))));
        // No se prepara saveAndFlush: el recálculo falla antes de llegar a la escritura.

        EditProjectRequest request = new EditProjectRequest(
                "Título nuevo", null, project.getFundingGoal(), null, project.getDeadline());

        RiskScoreCalculationException ex = assertThrows(RiskScoreCalculationException.class,
                () -> service.editProject(PROJECT_ID, request));

        // CA4 se cumple en el efecto: no hay escritura, los datos persistidos se conservan.
        verify(projectRepository, never()).saveAndFlush(any());
        // Pero el catch (Exception e) reenvuelve con lista vacía y concatena el mensaje interno.
        assertThat(ex.getMessage())
                .isEqualTo("No fue posible recalcular el riesgo del proyecto: Fallo interno del motor");
        assertThat(ex.getIssuesList()).isEmpty();
    }

    @Test
    @DisplayName("UT-22 · D-14 · Un título en blanco se aplica sobre un proyecto ACTIVE")
    void tituloEnBlancoSeAplicaEnActive() {
        Project project = proyectoActivo();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(scoringEngine.calculateRiskScore(any(Project.class), any(User.class))).thenReturn(5);
        alGuardarYFlushAsignarFechaActualizacion();

        EditProjectRequest request = new EditProjectRequest(
                "   ", null, project.getFundingGoal(), null, project.getDeadline());

        EditProjectResponse response = service.editProject(PROJECT_ID, request);

        // EditProjectRequest no declara ninguna anotación de validación: @Valid no tiene nada que comprobar.
        assertThat(response.title()).isEqualTo("   ");
        verify(projectRepository).saveAndFlush(project);
    }

    // ==================== UT-23 · eliminación lógica ====================

    @Test
    @DisplayName("UT-23 · CA4 · Con un aporte COMPLETED la eliminación se bloquea")
    void eliminarConAporteCompletedBloquea() {
        Project project = borradorCompleto();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(contributionRepository.countByProjectIdAndStatus(PROJECT_ID, ContributionStatus.COMPLETED))
                .thenReturn(1);

        assertThatThrownBy(() -> service.deleteProject(PROJECT_ID))
                .isInstanceOf(DeleteConflictException.class)
                .hasMessage("No se puede eliminar un proyecto con contribuciones");

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        verify(projectRepository, never()).save(any());
    }

    @ParameterizedTest(name = "UT-23 · D-09 · Con aportes {0} la eliminación PROCEDE")
    @ValueSource(strings = {"REFUNDED", "PENDING"})
    @DisplayName("UT-23 · D-09 · Solo se cuentan los aportes COMPLETED (comportamiento actual)")
    void eliminarConAportesNoCompletedProcede(ContributionStatus estado) {
        Project project = proyectoActivo();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(contributionRepository.countByProjectIdAndStatus(PROJECT_ID, ContributionStatus.COMPLETED))
                .thenReturn(0);

        service.deleteProject(PROJECT_ID);

        // HU-2.1.2 CA4 habla de "al menos un aporte", sin distinguir estado.
        // El código consulta únicamente COMPLETED, así que un proyecto que administró
        // dinero de terceros (reembolsado o pendiente) puede pasar a DELETED.
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DELETED);
        verify(projectRepository).save(project);
    }

    @Test
    @DisplayName("UT-23 · CA2 · Sin aportes el proyecto pasa a DELETED (baja lógica, no borrado físico)")
    void eliminarSinAportesMarcaDeleted() {
        Project project = borradorCompleto();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(contributionRepository.countByProjectIdAndStatus(PROJECT_ID, ContributionStatus.COMPLETED))
                .thenReturn(0);

        service.deleteProject(PROJECT_ID);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DELETED);
        verify(projectRepository).save(project);
        verify(projectRepository, never()).delete(any());
        verify(projectRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("UT-23 · CA5 · Un usuario que no es el creador no puede eliminar")
    void eliminarComoOtroUsuarioRechaza() {
        Project project = borradorCompleto();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioConId(OTRO_USUARIO_ID)));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.deleteProject(PROJECT_ID))
                .isInstanceOf(ProjectAccessDeniedException.class)
                .hasMessage("No tienes permiso para eliminar este proyecto");

        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-23 · Eliminar un proyecto inexistente responde 404")
    void eliminarProyectoInexistente() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProject(PROJECT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Proyecto no encontrado con el ID: " + PROJECT_ID);
    }

    @Test
    @DisplayName("UT-23 · Eliminar un proyecto ya DELETED vuelve a responder sin error (idempotente)")
    void eliminarProyectoYaDeletedEsIdempotente() {
        Project project = borradorCompleto();
        project.setStatus(ProjectStatus.DELETED);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(creador()));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(contributionRepository.countByProjectIdAndStatus(PROJECT_ID, ContributionStatus.COMPLETED))
                .thenReturn(0);

        service.deleteProject(PROJECT_ID);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DELETED);
        verify(projectRepository).save(project);
    }

    // ---------- fixtures adicionales del Lote 7b ----------

    /** Proyecto ACTIVE con meta 25.000.000, recaudado 1.000.000 y riskScore 5. */
    private Project proyectoActivo() {
        Project project = borradorCompleto();
        project.setStatus(ProjectStatus.ACTIVE);
        project.setRiskScore(5);
        project.setCurrentAmount(new BigDecimal("1000000"));
        return project;
    }

    private User usuarioConId(Long id) {
        User user = creador();
        user.setId(id);
        return user;
    }
}
package com.crowdfunding.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.ProjectStatus;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.RiskScoreCalculationException;
import com.crowdfunding.backend.persistence.ProjectRepository;
import com.crowdfunding.backend.utils.RiskUtils;

/**
 * UT-02 a UT-08 · Motor de scoring de riesgo.
 * Componente bajo prueba: RiskScoringService.calculateRiskScore(Project, User).
 * Trazabilidad: HU-2.1.8 (CA3, CA4, CA5) y HU-2.3.3 (CA1).
 *
 * Fixture base (puntaje 7): cuenta de 30 días (+2), sin historial (0),
 * descripción de 200 caracteres (+2), imagen (+1), meta 25.000.000 (+1), plazo 60 días (+1).
 * Cada prueba varía un único factor sobre esa base.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UT-02..UT-08 · RiskScoringService")
class RiskScoringServiceTest {

    private static final String MENSAJE_CONSISTENCIA =
            "No se pudo calcular el riesgo del proyecto, revisa la información e inténtalo de nuevo";
    private static final Long CREATOR_ID = 1L;

    @Mock
    private ProjectRepository repository;

    @InjectMocks
    private RiskScoringService service;

    // ---------- UT-02 ----------
    @Test
    @DisplayName("UT-02 · Creador consolidado con proyecto completo alcanza el techo de 9 puntos")
    void alcanzaPuntajeMaximo() {
        User creator = creador(30);
        Project project = proyectoBase();
        when(repository.existsByCreatorIdAndStatus(CREATOR_ID, ProjectStatus.FUNDED)).thenReturn(true);
        when(repository.existsByCreatorIdAndStatus(CREATOR_ID, ProjectStatus.FAILED)).thenReturn(false);

        int score = service.calculateRiskScore(project, creator);

        assertThat(score).isEqualTo(9);
        assertThat(RiskUtils.calculateRiskTag(score)).isEqualTo("Riesgo Bajo");
    }

    // ---------- UT-03 ----------
    @Test
    @DisplayName("UT-03 · Una cuenta nueva no puede superar 5 puntos: queda en Riesgo Medio")
    void cuentaNuevaTieneTechoEstructural() {
        User creator = creador(1);
        Project project = proyectoBase();
        sinHistorial();

        int score = service.calculateRiskScore(project, creator);

        assertThat(score).isEqualTo(5);
        assertThat(RiskUtils.calculateRiskTag(score)).isEqualTo("Riesgo Medio");
    }

    // ---------- UT-04 ----------
    @Test
    @DisplayName("UT-04 · La suma bruta de -6 se acota al mínimo de -5")
    void acotaElPuntajeAlMinimo() {
        User creator = creador(1);
        Project project = new Project();
        project.setTitle("Propuesta débil");
        project.setDescription("Corta");
        project.setImageUrl(null);
        project.setFundingGoal(new BigDecimal("100000"));
        project.setDeadline(LocalDateTime.now().plusDays(3));
        when(repository.existsByCreatorIdAndStatus(CREATOR_ID, ProjectStatus.FUNDED)).thenReturn(false);
        when(repository.existsByCreatorIdAndStatus(CREATOR_ID, ProjectStatus.FAILED)).thenReturn(true);

        int score = service.calculateRiskScore(project, creator);

        // 0 (cuenta nueva) -1 (FAILED) -1 (descripción) -1 (sin imagen) -2 (meta) -1 (plazo) = -6
        assertThat(score).isEqualTo(-5);
        assertThat(RiskUtils.calculateRiskTag(score)).isEqualTo("Riesgo Alto");
    }

    // ---------- UT-05 · bordes de descripción ----------
    @ParameterizedTest(name = "UT-05 · descripción de {0} caracteres -> puntaje {1}")
    @CsvSource({"49,4", "50,5", "149,5", "150,7"})
    void bordesDeLongitudDeDescripcion(int longitud, int esperado) {
        User creator = creador(30);
        Project project = proyectoBase();
        project.setDescription("a".repeat(longitud));
        sinHistorial();

        assertThat(service.calculateRiskScore(project, creator)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("UT-05 · La descripción se mide después de trim(): los espacios no suman")
    void descripcionAplicaTrim() {
        User creator = creador(30);
        Project project = proyectoBase();
        project.setDescription("     " + "a".repeat(150) + "     ");
        sinHistorial();

        assertThat(service.calculateRiskScore(project, creator)).isEqualTo(7);
    }

    // ---------- UT-05 · bordes de meta ----------
    @ParameterizedTest(name = "UT-05 · meta {0} -> puntaje {1}")
    @CsvSource({"499999.99,4", "500000,7", "50000000,7", "50000000.01,4"})
    void bordesDelRangoDeMeta(String meta, int esperado) {
        User creator = creador(30);
        Project project = proyectoBase();
        project.setFundingGoal(new BigDecimal(meta));
        sinHistorial();

        assertThat(service.calculateRiskScore(project, creator)).isEqualTo(esperado);
    }

    // ---------- UT-05 · bordes de plazo ----------
    // El servicio calcula Duration.between(instantTime, deadline).toDays(), que TRUNCA.
    // Como instantTime ocurre unos instantes después del now() de la prueba, un plazo de
    // exactamente N días se mide como N-1 y cae en el bucket anterior. Por eso cada fila
    // añade +2 h de margen: el conteo de días queda en N con certeza y la prueba es estable.
    // Base sin el componente de plazo = 6 (cuenta 30 días +2, sin historial 0,
    // descripción 200 caracteres +2, imagen +1, meta 25.000.000 +1).
    @ParameterizedTest(name = "UT-05 · plazo de {0} días + {1} h -> puntaje {2}")
    @CsvSource({
            "6,2,5",     // 6 días: menos de 7 -> -1
            "7,2,6",     // 7 días: límite inferior aceptable -> 0
            "14,2,6",    // 14 días: justo antes del rango ideal -> 0
            "15,2,7",    // 15 días: límite inferior ideal -> +1
            "90,2,7",    // 90 días: límite superior ideal -> +1
            "91,2,6",    // 91 días: justo después del ideal -> 0
            "120,2,6",   // 120 días: límite superior aceptable -> 0
            "121,2,5"    // 121 días: más de 120 -> -1
    })
    void bordesDelPlazo(long dias, long horas, int esperado) {
        User creator = creador(30);
        Project project = proyectoBase();
        project.setDeadline(LocalDateTime.now().plusDays(dias).plusHours(horas));
        sinHistorial();

        assertThat(service.calculateRiskScore(project, creator)).isEqualTo(esperado);
    }

    // ---------- UT-06 ----------
    @Test
    @DisplayName("UT-06 · Historial mixto: FUNDED (+2) y FAILED (-1) se acumulan, no se excluyen")
    void historialMixtoSumaAmbosComponentes() {
        User creator = creador(30);
        Project project = proyectoBase();
        when(repository.existsByCreatorIdAndStatus(CREATOR_ID, ProjectStatus.FUNDED)).thenReturn(true);
        when(repository.existsByCreatorIdAndStatus(CREATOR_ID, ProjectStatus.FAILED)).thenReturn(true);

        int score = service.calculateRiskScore(project, creator);

        assertThat(score).isEqualTo(8);
        verify(repository).existsByCreatorIdAndStatus(CREATOR_ID, ProjectStatus.FUNDED);
        verify(repository).existsByCreatorIdAndStatus(CREATOR_ID, ProjectStatus.FAILED);
    }

    // ---------- UT-07 ----------
    @Test
    @DisplayName("UT-07 · checkConsistency acumula las tres inconsistencias y no inicia el cálculo")
    void consistenciaAcumulaTodasLasInconsistencias() {
        User creator = creador(30);
        creator.setCreatedAt(null);
        Project project = proyectoBase();
        project.setFundingGoal(BigDecimal.ZERO);
        project.setDeadline(LocalDateTime.now().minusSeconds(1));

        RiskScoreCalculationException ex = assertThrows(RiskScoreCalculationException.class,
                () -> service.calculateRiskScore(project, creator));

        assertThat(ex.getMessage()).isEqualTo(MENSAJE_CONSISTENCIA);
        assertThat(ex.getIssuesList()).hasSize(3);
        assertThat(ex.getIssuesList())
                .extracting(RiskScoreCalculationException.Issue::field)
                .containsExactly("fundingGoal", "deadline", "creator.createdAt");
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("UT-07 · Una fecha de creación futura también se rechaza")
    void consistenciaRechazaCreadorConFechaFutura() {
        User creator = creador(30);
        creator.setCreatedAt(LocalDateTime.now().plusDays(1));
        Project project = proyectoBase();

        RiskScoreCalculationException ex = assertThrows(RiskScoreCalculationException.class,
                () -> service.calculateRiskScore(project, creator));

        assertThat(ex.getIssuesList()).hasSize(1);
        assertThat(ex.getIssuesList().get(0).field()).isEqualTo("creator.createdAt");
        verifyNoInteractions(repository);
    }

    // ---------- UT-08 · comportamiento actual, no conforme ----------
    @Test
    @DisplayName("UT-08 · D-11 · Meta nula produce NullPointerException en lugar de la excepción de negocio")
    void metaNulaProduceNullPointer() {
        User creator = creador(30);
        Project project = proyectoBase();
        project.setFundingGoal(null);

        assertThatThrownBy(() -> service.calculateRiskScore(project, creator))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("UT-08 · D-11 · Plazo nulo produce NullPointerException en lugar de la excepción de negocio")
    void plazoNuloProduceNullPointer() {
        User creator = creador(30);
        Project project = proyectoBase();
        project.setDeadline(null);

        assertThatThrownBy(() -> service.calculateRiskScore(project, creator))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(repository);
    }

    // ---------- fixtures ----------
    private User creador(int diasDeAntiguedad) {
        User user = new User();
        user.setId(CREATOR_ID);
        user.setFullName("Camila Torres");
        user.setEmail("camila@example.com");
        user.setCreatedAt(LocalDateTime.now().minusDays(diasDeAntiguedad));
        return user;
    }

    private Project proyectoBase() {
        Project project = new Project();
        project.setTitle("HoneyMoney");
        project.setDescription("a".repeat(200));
        project.setImageUrl("https://placehold.co/600x400/png");
        project.setFundingGoal(new BigDecimal("25000000"));
        project.setDeadline(LocalDateTime.now().plusDays(60));
        return project;
    }

    private void sinHistorial() {
        when(repository.existsByCreatorIdAndStatus(CREATOR_ID, ProjectStatus.FUNDED)).thenReturn(false);
        when(repository.existsByCreatorIdAndStatus(CREATOR_ID, ProjectStatus.FAILED)).thenReturn(false);
    }
}
package com.crowdfunding.backend.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * UT-01 · Clasificación de la etiqueta de riesgo.
 * Componente bajo prueba: RiskUtils.calculateRiskTag(Integer).
 * Trazabilidad: HU-2.3.3 CA1 (tabla de puntaje -> etiqueta) y HU-2.3.3 CA4 (borrador sin etiqueta).
 */
@DisplayName("UT-01 · RiskUtils.calculateRiskTag")
class RiskUtilsTest {

    @ParameterizedTest(name = "score={0} -> {1}")
    @CsvSource({
            "-5,Riesgo Alto",
            "1,Riesgo Alto",
            "2,Riesgo Medio",
            "5,Riesgo Medio",
            "6,Riesgo Bajo",
            "9,Riesgo Bajo"
    })
    @DisplayName("CA1 · Clasifica el puntaje en las fronteras exactas de la tabla de HU-2.3.3")
    void clasificaPuntajeEnLasFronteras(Integer score, String etiquetaEsperada) {
        assertThat(RiskUtils.calculateRiskTag(score)).isEqualTo(etiquetaEsperada);
    }

    @Test
    @DisplayName("CA4 · Un proyecto sin puntaje (DRAFT) devuelve 'Sin evaluar'")
    void devuelveSinEvaluarCuandoElPuntajeEsNulo() {
        assertThat(RiskUtils.calculateRiskTag(null)).isEqualTo("Sin evaluar");
    }

    @ParameterizedTest(name = "score fuera de rango={0} -> {1}")
    @CsvSource({
            "-100,Riesgo Alto",
            "10,Riesgo Bajo",
            "100,Riesgo Bajo"   
    })
    @DisplayName("Comportamiento actual · El método no valida el rango [-5, 9]: clasifica cualquier entero")
    void noValidaElRangoDelPuntaje(Integer score, String etiquetaEsperada) {
        // La acotación a [-5, 9] es responsabilidad exclusiva de RiskScoringService (se verifica en UT-04).
        // RiskUtils solo traduce el número a texto; documentarlo evita atribuirle una validación que no tiene.
        assertThat(RiskUtils.calculateRiskTag(score)).isEqualTo(etiquetaEsperada);
    }
}
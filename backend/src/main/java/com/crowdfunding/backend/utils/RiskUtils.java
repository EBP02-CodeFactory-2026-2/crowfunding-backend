package com.crowdfunding.backend.utils;

/**
 * Utilidades para el cálculo y clasificación del riesgo de un proyecto.
 * Centraliza la lógica de mapeo risk_score -> riskTag para evitar duplicación.
 */
public class RiskUtils {
    private RiskUtils(){}

    /**
     * Convierte un puntaje de riesgo numérico en su etiqueta legible.
     * Reglas:
     *   - score <= 1  -> "Riesgo Alto"
     *   - 2 <= score <= 5 -> "Riesgo Medio"
     *   - score >= 6  -> "Riesgo Bajo"
     *   - score == null -> "Sin evaluar"
     */

    public static String calculateRiskTag(Integer score) {
        if (score == null) return "Sin evaluar";
        if (score <= 1) return "Riesgo Alto";
        if (score <= 5) return "Riesgo Medio";
        return "Riesgo Bajo";
    }
}

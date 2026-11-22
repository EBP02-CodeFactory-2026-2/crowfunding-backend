package com.crowdfunding.backend.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.ProjectStatus;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.GlobalExceptionHandler;
import com.crowdfunding.backend.persistence.ContributionRepository;
import com.crowdfunding.backend.persistence.ProjectRepository;
import com.crowdfunding.backend.persistence.TransactionRepository;
import com.crowdfunding.backend.persistence.UserRepository;
import com.crowdfunding.backend.services.ContributionService;

/**
 * HU-2.2.3 — Validar confirmación de advertencia de riesgo alto antes de aportar.
 * Ejercita POST /api/v1/projects/{id}/contributions con el servicio real y el
 * GlobalExceptionHandler real; solo los repositorios están simulados.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContributionRiskWarningTest {

    private static final String RISK_MESSAGE = "Debes confirmar la advertencia de riesgo antes de aportar";

    @Mock ContributionRepository contributionRepository;
    @Mock ProjectRepository projectRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock UserRepository userRepository;

    @InjectMocks ContributionService contributionService;

    MockMvc mockMvc;
    UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ContributionController(contributionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("backer@test.com", null, List.of()));

        User backer = new User();
        backer.setEmail("backer@test.com");
        when(userRepository.findByEmail("backer@test.com")).thenReturn(Optional.of(backer));
        when(transactionRepository.existsByNonce(anyString())).thenReturn(false);
        when(contributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void givenActiveProjectWithRiskScore(Integer riskScore) {
        Project project = new Project();
        project.setId(projectId);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setRiskScore(riskScore);
        project.setCurrentAmount(BigDecimal.ZERO);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    }

    private String body(String riskWarningField) {
        return """
                {
                  "amount": 50000,
                  "paymentMethod": "SIMULATED_CARD",
                  "nonce": "nonce-%s"%s
                }
                """.formatted(UUID.randomUUID(), riskWarningField);
    }

    private org.springframework.test.web.servlet.ResultActions contribute(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/{id}/contributions", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // ---------- Proyecto con risk_score = 0 (Riesgo Alto) ----------

    @Test
    @DisplayName("Riesgo Alto + riskWarningAccepted ausente/null -> 400")
    void highRisk_missingFlag_returns400() throws Exception {
        givenActiveProjectWithRiskScore(0);

        contribute(body("")) // el campo no viene en el JSON
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(RISK_MESSAGE))
                .andExpect(jsonPath("$.path").value("/api/v1/projects/" + projectId + "/contributions"));

        verify(contributionRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Riesgo Alto + riskWarningAccepted = null explícito -> 400")
    void highRisk_explicitNull_returns400() throws Exception {
        givenActiveProjectWithRiskScore(0);

        contribute(body(", \"riskWarningAccepted\": null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(RISK_MESSAGE));

        verify(contributionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Riesgo Alto + riskWarningAccepted = false -> 400")
    void highRisk_false_returns400() throws Exception {
        givenActiveProjectWithRiskScore(0);

        contribute(body(", \"riskWarningAccepted\": false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(RISK_MESSAGE));

        verify(contributionRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(projectRepository, never()).incrementCurrentAmount(any(), any());
    }

    @Test
    @DisplayName("Riesgo Alto + riskWarningAccepted = true -> 201")
    void highRisk_true_returns201() throws Exception {
        givenActiveProjectWithRiskScore(0);

        contribute(body(", \"riskWarningAccepted\": true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()));

        verify(contributionRepository).save(any());
        verify(projectRepository).incrementCurrentAmount(any(), any());
    }

    @Test
    @DisplayName("Límite: risk_score = 1 sigue siendo Riesgo Alto -> 400 sin confirmar")
    void highRisk_boundaryScore1_returns400() throws Exception {
        givenActiveProjectWithRiskScore(1);

        contribute(body(", \"riskWarningAccepted\": false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(RISK_MESSAGE));
    }

    // ---------- Proyecto con risk_score = 7 (Riesgo Bajo) ----------

    @Test
    @DisplayName("Riesgo Bajo + riskWarningAccepted ausente -> 201")
    void lowRisk_missingFlag_returns201() throws Exception {
        givenActiveProjectWithRiskScore(7);

        contribute(body(""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Riesgo Bajo + riskWarningAccepted = false -> 201")
    void lowRisk_false_returns201() throws Exception {
        givenActiveProjectWithRiskScore(7);

        contribute(body(", \"riskWarningAccepted\": false"))
                .andExpect(status().isCreated());
    }

    // ---------- Riesgo Medio (CA4) ----------

    @Test
    @DisplayName("Riesgo Medio (score 3) + riskWarningAccepted ausente -> 201")
    void mediumRisk_missingFlag_returns201() throws Exception {
        givenActiveProjectWithRiskScore(3);

        contribute(body(""))
                .andExpect(status().isCreated());
    }
}
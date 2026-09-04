package org.example.kalkulationsprogramm.controller;

import java.time.LocalDateTime;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.example.kalkulationsprogramm.dto.DatensatzLockDto;
import org.example.kalkulationsprogramm.service.DatensatzLockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-Tests fuer {@link DatensatzLockController}. Auth-Mapping laeuft
 * analog zu den anderen Controller-Tests: {@code addFilters = false} +
 * Auth-Token als Request-Principal, damit Spring den
 * {@link FrontendUserPrincipal} aufloesen kann.
 */
@WebMvcTest(DatensatzLockController.class)
@AutoConfigureMockMvc(addFilters = false)
class DatensatzLockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DatensatzLockService service;

    private UsernamePasswordAuthenticationToken testAuth() {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(
                42L, "max.mustermann", "Max Mustermann", "hash", true,
                Set.of(FrontendUserRole.ADMIN));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("POST /api/datensatz-locks/{typ}/{id}/acquire")
    class Acquire {

        @Test
        @DisplayName("Happy-Path: Lock erworben -> 200 mit ACQUIRED")
        void erwirbtLock_liefert200() throws Exception {
            LocalDateTime now = LocalDateTime.of(2026, 4, 3, 10, 0);
            given(service.acquire(eq(SperrbarerTyp.AUSGANG), eq(5L), eq(42L), eq("Max Mustermann")))
                    .willReturn(new DatensatzLockDto(DatensatzLockDto.ACQUIRED, 42L, "Max Mustermann", now, now));

            mockMvc.perform(post("/api/datensatz-locks/ausgang/5/acquire")
                            .principal(testAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACQUIRED"))
                    .andExpect(jsonPath("$.holderUserId").value(42));
        }

        @Test
        @DisplayName("Fremdes Lock aktiv -> 409 mit LOCKED_BY_OTHER")
        void fremdesLock_liefert409() throws Exception {
            LocalDateTime now = LocalDateTime.of(2026, 4, 3, 10, 0);
            given(service.acquire(eq(SperrbarerTyp.AUSGANG), eq(5L), eq(42L), eq("Max Mustermann")))
                    .willReturn(new DatensatzLockDto(DatensatzLockDto.LOCKED_BY_OTHER, 7L, "Erika Musterfrau", now, now));

            mockMvc.perform(post("/api/datensatz-locks/ausgang/5/acquire")
                            .principal(testAuth()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("LOCKED_BY_OTHER"))
                    .andExpect(jsonPath("$.holderUserId").value(7));
        }

        @Test
        @DisplayName("Unbekannter Typ -> 400")
        void unbekannterTyp_liefert400() throws Exception {
            mockMvc.perform(post("/api/datensatz-locks/foo/5/acquire")
                            .principal(testAuth()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Kein Principal -> 401")
        void keinPrincipal_liefert401() throws Exception {
            mockMvc.perform(post("/api/datensatz-locks/ausgang/5/acquire"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/datensatz-locks/{typ}/{id}")
    class Release {

        @Test
        @DisplayName("Happy-Path: Lock freigegeben -> 204")
        void gibtLockFrei_liefert204() throws Exception {
            mockMvc.perform(delete("/api/datensatz-locks/eingang/9")
                            .principal(testAuth()))
                    .andExpect(status().isNoContent());
        }
    }
}

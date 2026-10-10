package org.example.kalkulationsprogramm.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prüft den Health-Check durch die ECHTE Security-Filter-Chain (nicht nur die
 * Pfadliste wie {@link OeffentlichePfadeTest}): Das Nachtupdate auf den
 * Kundenservern fragt {@code /actuator/health} ohne Login ab und entscheidet
 * daran über Erfolg oder Rollback. Alles andere vom Actuator bleibt zu.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "file.mail-attachment-dir=attachments" })
@AutoConfigureMockMvc
class ActuatorHealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/health ohne Login: 200 und nur der Status, keine Details")
    void healthOhneLoginNurStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    @Test
    @DisplayName("Andere Actuator-Pfade ohne Login: 401")
    void andereActuatorPfadeOhneLoginGesperrt() throws Exception {
        for (String pfad : new String[] { "/actuator", "/actuator/env", "/actuator/beans",
                "/actuator/health/db", "/actuator/heapdump" }) {
            mockMvc.perform(get(pfad)).andExpect(status().isUnauthorized());
        }
    }
}

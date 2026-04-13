package org.example.kalkulationsprogramm.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeatureFlagController.class)
@AutoConfigureMockMvc(addFilters = false)
class FeatureFlagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getFeatures_gibtMapZurueck() throws Exception {
        mockMvc.perform(get("/api/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.en1090").isBoolean())
                .andExpect(jsonPath("$.echeck").isBoolean())
                .andExpect(jsonPath("$.email").isBoolean())
                .andExpect(jsonPath("$.rag").isBoolean());
    }

    @Test
    void getFeatures_defaultValues() throws Exception {
        // Default values from @Value annotations
        mockMvc.perform(get("/api/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.en1090").value(false))
                .andExpect(jsonPath("$.echeck").value(false))
                .andExpect(jsonPath("$.email").value(true))
                .andExpect(jsonPath("$.rag").value(false));
    }
}

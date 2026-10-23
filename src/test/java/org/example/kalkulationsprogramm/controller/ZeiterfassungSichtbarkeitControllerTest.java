package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.service.ZeiterfassungSichtbarkeitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ZeiterfassungSichtbarkeitController.class)
@AutoConfigureMockMvc(addFilters = false)
class ZeiterfassungSichtbarkeitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZeiterfassungSichtbarkeitService service;

    @Test
    void liefertSichtbarkeitDesAngemeldetenBenutzers() throws Exception {
        when(service.darfZeiterfassungSehen(any())).thenReturn(true, false);

        mockMvc.perform(get("/api/zeitverwaltung/sichtbarkeit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.darfZeiterfassungSehen").value(true));
        mockMvc.perform(get("/api/zeitverwaltung/sichtbarkeit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.darfZeiterfassungSehen").value(false));
    }
}

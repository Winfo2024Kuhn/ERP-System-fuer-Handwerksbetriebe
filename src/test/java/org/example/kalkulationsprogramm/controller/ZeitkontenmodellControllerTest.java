package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.ZeitkontenmodellDto;
import org.example.kalkulationsprogramm.service.ZeitkontenmodellService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ZeitkontenmodellController.class)
@AutoConfigureMockMvc(addFilters = false)
class ZeitkontenmodellControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ZeitkontenmodellService service;
    private static final String URL = "/api/zeitverwaltung/zeitkontenmodelle";
    String json(String name, String stunden, String version) {
        return "{\"bezeichnung\":\"" + name + "\",\"arbeitszeit\":{\"montagStunden\":" + stunden
                + ",\"dienstagStunden\":0,\"mittwochStunden\":0,\"donnerstagStunden\":0,\"freitagStunden\":0,"
                + "\"samstagStunden\":0,\"sonntagStunden\":0}" + version + "}";
    }
    ZeitkontenmodellDto dto(String name) {
        return new ZeitkontenmodellDto(1L, 0L, name, new ZeitkontenmodellDto.Arbeitszeit(BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null));
    }
    @Test void alleCrudRoutenVerwendenDtoVertrag() throws Exception {
        when(service.alle()).thenReturn(List.of(dto("Werkstatt")));
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$[0].bezeichnung").value("Werkstatt"));
        when(service.erstellen(any())).thenReturn(dto("Werkstatt"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("Werkstatt", "0", "")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0));
        when(service.aktualisieren(eq(1L), any())).thenReturn(dto("Teilzeit"));
        mvc.perform(put(URL + "/1").contentType(MediaType.APPLICATION_JSON).content(json("Teilzeit", "7", ",\"expectedVersion\":0")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bezeichnung").value("Teilzeit"));
        mvc.perform(delete(URL + "/1").param("expectedVersion", "0")).andExpect(status().isNoContent());
        verify(service).loeschen(1L, 0L);
    }
    @Test void updateUndDeleteErfordernVersionsstand() throws Exception {
        mvc.perform(put(URL + "/1").contentType(MediaType.APPLICATION_JSON).content(json("Teilzeit", "7", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(URL + "/1")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void namenUndStundenWerdenVorServiceValidiert() throws Exception {
        for (String name : new String[]{"", " ", "x".repeat(10001)}) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(name, "7", ""))).andExpect(status().isBadRequest());
        }
        for (String value : new String[]{"-1", "24.01", "1.001", "null"}) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("Werkstatt", value, ""))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void scriptUndSqlTextWerdenAlsJsonUndNichtAlsHtmlAusgegeben() throws Exception {
        for (String text : new String[]{"'; DROP TABLE x; --", "<script>alert(1)</script>"}) {
            when(service.erstellen(any())).thenReturn(dto(text));
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(text, "0", "")))
                    .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.bezeichnung").value(text));
        }
    }
}

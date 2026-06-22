package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatGrund;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatTrefferDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeListItemDto;
import org.example.kalkulationsprogramm.mapper.KundeMapper;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KundeController.class)
@AutoConfigureMockMvc(addFilters = false)
class KundeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KundeRepository kundeRepository;

    @MockBean
    private KundeMapper kundeMapper;

    @MockBean
    private org.example.kalkulationsprogramm.service.KundenDetailService kundenDetailService;

    @MockBean
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @MockBean
    private org.example.kalkulationsprogramm.service.KundennummerService kundennummerService;

    @MockBean
    private org.example.kalkulationsprogramm.service.KundeDuplikatService kundeDuplikatService;

    @BeforeEach
    void setupDuplikatService() {
        // Default: keine Duplikate, damit der "Happy Path" funktioniert.
        given(kundeDuplikatService.findeDuplikate(any(), any(), any(), any(), any(), any()))
                .willReturn(new KundeDuplikatResponseDto(List.of(), false));
    }

    @Test
    @DisplayName("Suchanfragen überschreiten nie das Limit von 50 Einträgen")
    void searchLimitsPageSizeTo50() throws Exception {
        Page<Kunde> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 1), 0);
        given(kundeRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(emptyPage);

        mockMvc.perform(get("/api/kunden").param("size", "500"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(kundeRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("Neue Kunden können angelegt werden")
    void createKundePersistsSanitizedData() throws Exception {
        given(kundeRepository.findByKundennummerIgnoreCase(eq("K-1"))).willReturn(Optional.empty());
        ArgumentCaptor<Kunde> kundeCaptor = ArgumentCaptor.forClass(Kunde.class);
        given(kundeRepository.save(any(Kunde.class))).willAnswer(invocation -> {
            Kunde entity = invocation.getArgument(0);
            entity.setId(11L);
            return entity;
        });
        KundeListItemDto dto = new KundeListItemDto();
        dto.setId(11L);
        dto.setKundennummer("K-1");
        dto.setName("Muster GmbH");
        given(kundeMapper.toListItem(any(Kunde.class))).willReturn(dto);

        var payload = new java.util.HashMap<String, Object>();
        payload.put("kundennummer", "  K-1  ");
        payload.put("name", "  Muster GmbH  ");
        payload.put("ansprechspartner", " Max Mustermann ");
        payload.put("strasse", " Hauptstr. 1 ");
        payload.put("plz", "12345");
        payload.put("ort", "  Berlin ");
        payload.put("telefon", "030123");
        payload.put("mobiltelefon", "0170123");
        payload.put("kundenEmails", List.of(" INFO@example.com ", "info@example.com", ""));

        mockMvc.perform(post("/api/kunden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kundennummer").value("K-1"))
                .andExpect(jsonPath("$.name").value("Muster GmbH"));

        verify(kundeRepository).save(kundeCaptor.capture());
        Kunde saved = kundeCaptor.getValue();
        assertThat(saved.getKundennummer()).isEqualTo("K-1");
        assertThat(saved.getName()).isEqualTo("Muster GmbH");
        assertThat(saved.getAnsprechspartner()).isEqualTo("Max Mustermann");
        assertThat(saved.getOrt()).isEqualTo("Berlin");
        assertThat(saved.getKundenEmails()).containsExactly("info@example.com");
    }

    @Test
    @DisplayName("Konflikt bei bestehender Kundennummer wird sauber zurückgegeben")
    void createKundeFailsOnDuplicateNumber() throws Exception {
        given(kundeRepository.findByKundennummerIgnoreCase(eq("K-99"))).willReturn(Optional.of(new Kunde()));

        var payload = new java.util.HashMap<String, Object>();
        payload.put("kundennummer", "K-99");
        payload.put("name", "Bestehender Kunde");

        mockMvc.perform(post("/api/kunden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Duplikat-Verdacht liefert 409 mit Treffer-Liste im Body")
    void createKundeReturnsConflictWithDuplikate() throws Exception {
        KundeDuplikatTrefferDto treffer = new KundeDuplikatTrefferDto();
        treffer.setId(42L);
        treffer.setName("Müller GmbH");
        treffer.setKundennummer("K-42");
        treffer.setGruende(List.of(KundeDuplikatGrund.EMAIL_GLEICH));
        treffer.setScore(100);
        given(kundeDuplikatService.findeDuplikate(any(), any(), any(), any(), any(), any()))
                .willReturn(new KundeDuplikatResponseDto(List.of(treffer), true));

        var payload = new java.util.HashMap<String, Object>();
        payload.put("name", "Müller GmbH");
        payload.put("kundenEmails", List.of("info@mueller.de"));

        mockMvc.perform(post("/api/kunden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.harterTreffer").value(true))
                .andExpect(jsonPath("$.duplikate[0].id").value(42))
                .andExpect(jsonPath("$.duplikate[0].name").value("Müller GmbH"))
                .andExpect(jsonPath("$.duplikate[0].gruende[0]").value("EMAIL_GLEICH"));
    }

    @Test
    @DisplayName("Freitext-Suche nach Telefonnummer liefert 200 und ruft Repository auf")
    void sucheKundenMitTelefonnummerLiefert200() throws Exception {
        Page<Kunde> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 12), 0);
        given(kundeRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(emptyPage);

        mockMvc.perform(get("/api/kunden").param("q", "0931"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kunden").isArray())
                .andExpect(jsonPath("$.gesamt").value(0));

        verify(kundeRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Header X-Duplikat-Bestaetigt umgeht den Duplikat-Check")
    void createKundeBypassesDuplikatCheckWithHeader() throws Exception {
        // Service würde Treffer melden – wird aber dank Header nicht aufgerufen.
        KundeDuplikatTrefferDto treffer = new KundeDuplikatTrefferDto();
        treffer.setId(1L);
        treffer.setName("Konflikt");
        given(kundeDuplikatService.findeDuplikate(any(), any(), any(), any(), any(), any()))
                .willReturn(new KundeDuplikatResponseDto(List.of(treffer), true));

        given(kundeRepository.save(any(Kunde.class))).willAnswer(inv -> {
            Kunde k = inv.getArgument(0);
            k.setId(99L);
            return k;
        });
        given(kundennummerService.reserviereNaechsteKundennummer()).willReturn("K-100");
        KundeListItemDto dto = new KundeListItemDto();
        dto.setId(99L);
        dto.setName("Neuer Kunde");
        given(kundeMapper.toListItem(any(Kunde.class))).willReturn(dto);

        var payload = new java.util.HashMap<String, Object>();
        payload.put("name", "Neuer Kunde");

        mockMvc.perform(post("/api/kunden")
                        .header("X-Duplikat-Bestaetigt", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        // Service darf bei aktivem Header nicht angefragt worden sein.
        verify(kundeDuplikatService, org.mockito.Mockito.never())
                .findeDuplikate(any(), any(), any(), any(), any(), any());
    }
}

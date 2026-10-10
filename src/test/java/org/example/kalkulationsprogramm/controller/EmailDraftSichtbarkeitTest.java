package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.dto.Email.EmailDraftDto;
import org.example.kalkulationsprogramm.service.EmailDraftService;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeit;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Entwürfe zu einer Antwort oder Weiterleitung enthalten den zitierten Text der Mail. Wer die Mail
 * nicht öffnen darf (fremdes Postfach, keinem Projekt zugeordnet), sieht auch den Entwurf nicht.
 */
class EmailDraftSichtbarkeitTest {

    private static final PostfachSichtbarkeit NUR_INFO = new PostfachSichtbarkeit(false, Set.of(1L));
    private static final long FREMDE_MAIL = 20L;

    private final EmailDraftService service = mock(EmailDraftService.class);
    private final PostfachSichtbarkeitService sichtbarkeit = mock(PostfachSichtbarkeitService.class);
    private MockMvc mvc;

    private final EmailDraftDto ohneBezug = entwurf(1L, null, null);
    private final EmailDraftDto antwortAufEigene = entwurf(2L, 10L, null);
    private final EmailDraftDto antwortAufFremde = entwurf(3L, FREMDE_MAIL, null);
    private final EmailDraftDto weiterleitungFremder = entwurf(4L, null, FREMDE_MAIL);
    /** Neue Mail des Chefs aus chef@ – Erika sieht chef@ nicht. */
    private final EmailDraftDto ausFremdemPostfach = neueMail(5L, 8L);
    /** Neue Mail aus einem Postfach, das es nicht mehr gibt – sperrt nicht. */
    private final EmailDraftDto ausGeloeschtemPostfach = neueMail(6L, 99L);

    private static EmailDraftDto neueMail(Long id, Long postfachId) {
        return new EmailDraftDto(id, "kunde@example.org", null, "Neu " + id, "<p>Text</p>", null, null,
                null, null, false, postfachId, false, null, null, null, List.of());
    }

    private static EmailDraftDto entwurf(Long id, Long replyEmailId, Long weitergeleitetVon) {
        return new EmailDraftDto(id, "kunde@example.org", null, "Betreff " + id, "<p>Zitat</p>", null, replyEmailId,
                null, null, false, null, false, weitergeleitetVon, null, null, List.of());
    }

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new EmailDraftController(service, sichtbarkeit)).build();
        when(sichtbarkeit.fuer(any())).thenReturn(NUR_INFO);
        when(sichtbarkeit.nichtLesbareEmailIds(anyCollection(), eq(NUR_INFO))).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            return ids.contains(FREMDE_MAIL) ? Set.of(FREMDE_MAIL) : Set.of();
        });
        when(sichtbarkeit.nichtSichtbarePostfachIds(anyCollection(), eq(NUR_INFO))).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            return ids.contains(8L) ? Set.of(8L) : Set.of();
        });
        when(service.list()).thenReturn(List.of(ohneBezug, antwortAufEigene, antwortAufFremde, weiterleitungFremder,
                ausFremdemPostfach, ausGeloeschtemPostfach));
        for (EmailDraftDto d : List.of(ohneBezug, antwortAufEigene, antwortAufFremde, weiterleitungFremder,
                ausFremdemPostfach, ausGeloeschtemPostfach)) {
            when(service.get(d.id())).thenReturn(d);
        }
    }

    @Test
    void listeUndZaehlerOhneEntwuerfeZuFremdenMails() throws Exception {
        mvc.perform(get("/api/emails/drafts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[2].id").value(6));
        mvc.perform(get("/api/emails/drafts/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void ohneBezuegeKeinePruefung() throws Exception {
        when(service.list()).thenReturn(List.of(ohneBezug));

        mvc.perform(get("/api/emails/drafts")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/emails/drafts/count")).andExpect(jsonPath("$.count").value(1));
        verify(sichtbarkeit, never()).nichtLesbareEmailIds(anyCollection(), any());
        verify(sichtbarkeit, never()).nichtSichtbarePostfachIds(anyCollection(), any());
        verify(sichtbarkeit, never()).fuer(any());
    }

    @Test
    void adminSiehtAlle() throws Exception {
        when(sichtbarkeit.nichtLesbareEmailIds(anyCollection(), eq(NUR_INFO))).thenReturn(null);
        when(sichtbarkeit.nichtSichtbarePostfachIds(anyCollection(), eq(NUR_INFO))).thenReturn(null);

        mvc.perform(get("/api/emails/drafts")).andExpect(jsonPath("$.length()").value(6));
        mvc.perform(get("/api/emails/drafts/count")).andExpect(jsonPath("$.count").value(6));
    }

    @Test
    void entwurfZuFremderMail404() throws Exception {
        for (long id : List.of(3L, 4L, 5L)) {
            mvc.perform(get("/api/emails/drafts/" + id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Entwurf nicht gefunden."));
            mvc.perform(get("/api/emails/drafts/" + id + "/attachments/7")).andExpect(status().isNotFound());
            mvc.perform(delete("/api/emails/drafts/" + id)).andExpect(status().isNotFound());
            mvc.perform(put("/api/emails/drafts/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"subject\":\"überschrieben\"}")).andExpect(status().isNotFound());
            mvc.perform(multipart("/api/emails/drafts/" + id)
                            .file(new MockMultipartFile("dto", "", "application/json", "{\"subject\":\"x\"}".getBytes()))
                            .with(r -> { r.setMethod("PUT"); return r; }))
                    .andExpect(status().isNotFound());
        }
        verify(service, never()).download(any(), any());
        verify(service, never()).delete(any());
        verify(service, never()).save(any(), any(), any());
    }

    @Test
    void eigeneEntwuerfeBleibenNutzbar() throws Exception {
        when(service.download(2L, 7L)).thenReturn(new EmailDraftService.Download("plan.pdf", "application/pdf", new byte[] { 1 }));
        when(service.save(eq(2L), any(), any())).thenReturn(antwortAufEigene);

        mvc.perform(get("/api/emails/drafts/2")).andExpect(status().isOk()).andExpect(jsonPath("$.replyEmailId").value(10));
        mvc.perform(get("/api/emails/drafts/1")).andExpect(status().isOk());
        mvc.perform(get("/api/emails/drafts/6")).andExpect(status().isOk());
        mvc.perform(get("/api/emails/drafts/2/attachments/7")).andExpect(status().isOk());
        mvc.perform(put("/api/emails/drafts/2").contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"neu\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/emails/drafts/2")).andExpect(status().isOk());
        verify(service).delete(2L);
        verify(sichtbarkeit, org.mockito.Mockito.atLeastOnce())
                .nichtLesbareEmailIds(argThat(ids -> ids.contains(10L)), eq(NUR_INFO));
    }
}

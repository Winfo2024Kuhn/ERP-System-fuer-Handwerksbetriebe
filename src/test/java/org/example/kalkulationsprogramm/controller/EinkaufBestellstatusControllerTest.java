package org.example.kalkulationsprogramm.controller;

import java.util.*;
import org.example.kalkulationsprogramm.config.*;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto;
import org.example.kalkulationsprogramm.service.einkauf.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {EinkaufBestellstatusController.class, EinkaufBestellfreigabeController.class, EinkaufBelegController.class, EinkaufAngebotController.class, EinkaufStornoanfrageController.class})
@Import({SecurityConfig.class, EinkaufBestellstatusControllerTest.FilterBeans.class})
class EinkaufBestellstatusControllerTest {
    @TestConfiguration static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }
    @Autowired MockMvc mvc;
    @MockBean EinkaufBestellstatusService reads;
    @MockBean EinkaufBelegService belege;
    @MockBean EinkaufAngebotService angebote;
    @MockBean EinkaufStornoanfrageService stornos;
    @MockBean EinkaufBestellfreigabeService writes;
    @MockBean EinkaufBerechtigungService rights;
    @MockBean FrontendUserDetailsService users;
    private static final String BASE = "/api/einkauf/bestellungen/12";

    @ParameterizedTest @ValueSource(strings = {"/mengen", "/lieferungen", "/bestaetigungen", "/revisionen/9/versandstatus"})
    void lesenBrauchtAnmeldung(String path) throws Exception {
        mvc.perform(get(BASE + path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reads, rights);
    }
    @ParameterizedTest @ValueSource(strings = {"/mengen", "/lieferungen", "/bestaetigungen", "/revisionen/9/versandstatus"})
    void lesenBrauchtEinkaufsrecht(String path) throws Exception {
        when(rights.verlange(any(), eq(EinkaufBerechtigung.LESEN))).thenThrow(new AccessDeniedException("Nicht erlaubt"));
        mvc.perform(get(BASE + path).with(auth())).andExpect(status().isForbidden());
        verifyNoInteractions(reads);
    }
    @ParameterizedTest @ValueSource(strings = {"/mengen", "/lieferungen", "/bestaetigungen", "/revisionen/9/versandstatus"})
    void listenSindMitLeserechtErreichbar(String path) throws Exception {
        when(rights.verlange(any(), eq(EinkaufBerechtigung.LESEN))).thenReturn(4L);
        mvc.perform(get(BASE + path).with(auth())).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        verify(rights).verlange(any(), eq(EinkaufBerechtigung.LESEN));
    }
    @ParameterizedTest @ValueSource(strings = {"erneut", "klaeren"})
    void versandAendernBrauchtAnmeldungUndCsrf(String action) throws Exception {
        mvc.perform(post(BASE + "/versand/7/" + action).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content(body(action))).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/versand/7/" + action).with(auth())
                .contentType("application/json").content(body(action))).andExpect(status().isForbidden());
        verifyNoInteractions(writes);
    }
    @ParameterizedTest @ValueSource(strings = {"erneut", "klaeren"})
    void versandAendernBrauchtBestellfreigabe(String action) throws Exception {
        when(rights.verlange(any(), eq(EinkaufBerechtigung.BESTELLUNG_FREIGEBEN))).thenThrow(new AccessDeniedException("Nicht erlaubt"));
        mvc.perform(post(BASE + "/versand/7/" + action).with(auth()).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content(body(action))).andExpect(status().isForbidden());
        verifyNoInteractions(writes);
    }
    @Test void wiederholungVerwendetVersionUndGeschuetztenVorgang() throws Exception {
        when(rights.verlange(any(), eq(EinkaufBerechtigung.BESTELLUNG_FREIGEBEN))).thenReturn(4L);
        when(writes.erneutSenden(12L, 7L, 3L, 4L)).thenReturn(new VersandDto(7L,4,"BESTELLUNG",12L,9L,"VORBEREITET",null,null,null,false,"dummy"));
        mvc.perform(post(BASE + "/versand/7/erneut").with(auth()).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content(body("erneut"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VORBEREITET"));
        verify(writes).erneutSenden(12L,7L,3L,4L);
    }
    @Test void klaerungVerwendetBelegteEntscheidungUndGeschuetztenVorgang() throws Exception {
        when(rights.verlange(any(), eq(EinkaufBerechtigung.BESTELLUNG_FREIGEBEN))).thenReturn(4L);
        mvc.perform(post(BASE + "/versand/7/klaeren").with(auth()).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content(body("klaeren"))).andExpect(status().isNoContent());
        verify(writes).versandKlaeren(eq(12L),eq(7L),argThat(r -> r.version()==3 && "Dummy-Nachweis".equals(r.beleg())),eq(4L));
    }
    @Test void versionskonfliktBleibt409MitVerstaendlicherMeldung() throws Exception {
        when(rights.verlange(any(), eq(EinkaufBerechtigung.BESTELLUNG_FREIGEBEN))).thenReturn(4L);
        when(writes.erneutSenden(any(),any(),anyLong(),any())).thenThrow(new IllegalStateException("Versand wurde geändert."));
        mvc.perform(post(BASE + "/versand/7/erneut").with(auth()).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content(body("erneut"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Versand wurde geändert."));
    }
    @Test void fremdeFassungBleibt404() throws Exception {
        when(reads.versandstatus(12L,9L)).thenThrow(new org.example.kalkulationsprogramm.exception.NotFoundException("Fremde Fassung"));
        mvc.perform(get(BASE + "/revisionen/9/versandstatus").with(auth())).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings = {"/api/einkauf/anfragen/12/angebote", BASE + "/belege", BASE + "/storno-anfragen"})
    void neueListenSindAuthentifiziertUndRechtegeschuetzt(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        when(rights.verlange(any(), eq(EinkaufBerechtigung.LESEN))).thenThrow(new AccessDeniedException("Nicht erlaubt"));
        mvc.perform(get(path).with(auth())).andExpect(status().isForbidden());
        verifyNoInteractions(belege, angebote, stornos);
        when(rights.verlange(any(), eq(EinkaufBerechtigung.LESEN))).thenReturn(4L);
        mvc.perform(get(path).with(auth())).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }
    @ParameterizedTest @ValueSource(strings = {"/storno-vorschau", "/storno-anfragen", "/storno-anfragen/7/erneut", "/storno-anfragen/7/klaeren", "/belege/6/datei"})
    void neueMutationenBrauchenCsrfUndPassendesFachrecht(String path) throws Exception {
        var right=path.startsWith("/belege") ? EinkaufBerechtigung.BEARBEITEN : EinkaufBerechtigung.BESTELLUNG_FREIGEBEN;
        mvc.perform(post(BASE+path).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE+path).with(auth()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        when(rights.verlange(any(),eq(right))).thenThrow(new AccessDeniedException("Nicht erlaubt"));
        mvc.perform(post(BASE+path).with(auth()).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(belege,stornos);
        when(rights.verlange(any(),eq(right))).thenReturn(4L);
        mvc.perform(post(BASE+path).with(auth()).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{}")).andExpect(path.endsWith("/klaeren") ? status().isNoContent() : status().isOk());
    }
    @Test void uploadBleibtMultipartMitFachrechtUndCsrf() throws Exception {
        var file=new org.springframework.mock.web.MockMultipartFile("datei","dummy.pdf","application/pdf","%PDF-1.7 Dummy".getBytes());
        mvc.perform(multipart(BASE+"/belege").file(file).param("typ","SONSTIG").with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(BASE+"/belege").file(file).param("typ","SONSTIG").with(auth())).andExpect(status().isForbidden());
        when(rights.verlange(any(),eq(EinkaufBerechtigung.BEARBEITEN))).thenThrow(new AccessDeniedException("Nicht erlaubt"));
        mvc.perform(multipart(BASE+"/belege").file(file).param("typ","SONSTIG").with(auth()).with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(belege);
        when(rights.verlange(any(),eq(EinkaufBerechtigung.BEARBEITEN))).thenReturn(4L);
        mvc.perform(multipart(BASE+"/belege").file(file).param("typ","SONSTIG").with(auth()).with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isCreated());
        verify(belege).hochladen(eq(12L),eq(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.SONSTIG),any(),eq(4L));
    }

    private static String body(String action) {
        return "erneut".equals(action) ? "{\"version\":3}" : "{\"version\":3,\"entscheidung\":\"BEREITS_ANGENOMMEN\",\"beleg\":\"Dummy-Nachweis\"}";
    }
    private static RequestPostProcessor auth() {
        var p = new FrontendUserPrincipal(4L,"test@example.com","Max Mustermann","{noop}dummy",true,Set.of(FrontendUserRole.USER));
        return SecurityMockMvcRequestPostProcessors.authentication(new UsernamePasswordAuthenticationToken(p,p.getPassword(),p.getAuthorities()));
    }
}

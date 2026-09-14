package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.*;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.*;
import org.example.kalkulationsprogramm.dto.DatevDto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DatevExportController.class)
@Import({SecurityConfig.class,DatevExportService.class,MonatsabschlussBerechtigungService.class,DatevExportSecurityTest.Filters.class})
class DatevExportSecurityTest {
 @org.springframework.boot.test.context.TestConfiguration static class Filters {
  @org.springframework.context.annotation.Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter(){return new CloudflareAccessJwtFilter();}
 }
 @Autowired MockMvc mvc;
 @MockBean FrontendUserDetailsService users;
 @MockBean FrontendUserProfileRepository profiles;
 @MockBean DatevExportRepository repo;
 @MockBean LodasDateiWriter writer;
 @MockBean PlatformTransactionManager tm;
 static final String BASE="/api/zeitverwaltung/monatsabschluesse/datev/";
 static final String BODY="{\"auswahl\":[{\"mitarbeiterId\":1,\"jahr\":2026,\"monat\":1,\"version\":0}],\"konfigurationVersion\":0}";
 FrontendUserPrincipal principal(){
  var m=new Mitarbeiter();m.setId(9L);m.setArt(MitarbeiterArt.MENSCH);m.setAktiv(true);
  var a=new Abteilung();a.setDarfMonatAbschliessen(true);m.setAbteilungen(Set.of(a));
  var p=new FrontendUserProfile();p.setMitarbeiter(m);p.setActive(true);when(profiles.findById(70L)).thenReturn(Optional.of(p));
  return new FrontendUserPrincipal(70L,"test@example.com","Max Mustermann","",true,Set.of());
 }
 @Test void anonymUndGefaelschteIdentitaetAbgewiesen() throws Exception {
  for(String action:List.of("vorpruefung","export")) mvc.perform(post(BASE+action).with(csrf()).header("X-Mitarbeiter-Id","9").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
  verifyNoInteractions(repo);
 }
 @Test @WithMockUser(roles="ADMIN") void rechtUndCsrfErforderlich() throws Exception {
  for(String action:List.of("vorpruefung","export")) {
   mvc.perform(post(BASE+action).with(csrf()).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
   mvc.perform(post(BASE+action).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
  }
  verifyNoInteractions(repo);
 }
 @Test void falscheIdsMonateUndInjektionenAbgewiesen() throws Exception {
  var auth=principal();
  for(String id:List.of("0","-1","\"../etc/passwd\"","\"'; DROP TABLE x; --\"","\"<script>alert(1)</script>\"","\""+"1".repeat(10001)+"\""))
   mvc.perform(post(BASE+"export").with(user(auth)).with(csrf()).contentType("application/json").content(BODY.replace("\"mitarbeiterId\":1","\"mitarbeiterId\":"+id))).andExpect(status().isBadRequest());
  verifyNoInteractions(repo);
 }
 @Test void downloadNurMitNoStoreUndServerDateiname() throws Exception {
  var auth=principal();when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
  var config=new DatevKonfiguration();config.setBeraterNr("1234");config.setMandantenNr("1");
  config.setZuordnungenJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(DatevKonfigurationService.KATEGORIEN.stream().map(k->new Zuordnung(k,!k.equals("ARBEIT"),k.equals("ARBEIT")?"200":"")).toList()));
  when(repo.konfiguration()).thenReturn(config);
  var s=new MonatsSaldo();var m=new Mitarbeiter();m.setId(1L);s.setMitarbeiter(m);s.setJahr(2026);s.setMonat(1);s.setVersion(0L);s.setFestgeschrieben(true);s.setIstStunden(java.math.BigDecimal.ONE);
  when(repo.salden(any(),any())).thenReturn(List.of(s));var n=new DatevPersonalnummer();n.setMitarbeiterId(1L);n.setPersonalnummer("1");when(repo.personalnummern(any())).thenReturn(List.of(n));
  when(writer.schreiben(any(),anyInt(),anyInt(),any())).thenReturn("DATEV".getBytes());
  mvc.perform(post(BASE+"vorpruefung").with(user(auth)).with(csrf()).contentType("application/json").content(BODY)).andExpect(status().isOk())
   .andExpect(jsonPath("$.gueltig").value(true)).andExpect(jsonPath("$.auswahl[0].mitarbeiterId").value(1))
   .andExpect(jsonPath("$.konfigurationVersion").value(0)).andExpect(jsonPath("$.ausschluesse[0].kategorie").value("ABWESENHEIT_UNGEGLIEDERT"));
  mvc.perform(post(BASE+"export").with(user(auth)).with(csrf()).contentType("application/json").content(BODY)).andExpect(status().isOk())
   .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Disposition","attachment; filename=\"lodas-2026-01.txt\""))
   .andExpect(content().bytes("DATEV".getBytes()));
 }
}

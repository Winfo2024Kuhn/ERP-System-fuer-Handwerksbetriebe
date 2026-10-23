package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.*;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import org.example.kalkulationsprogramm.testsupport.SitzungsRechteImTest;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The month-end closing may only read the DATEV setup; changes go through /api/settings/datev (admins). */
@WebMvcTest(DatevKonfigurationController.class)
@Import({SecurityConfig.class, DatevKonfigurationService.class, MonatsabschlussBerechtigungService.class, DatevKonfigurationSecurityTest.Filters.class})
class DatevKonfigurationSecurityTest {
 @org.springframework.boot.test.context.TestConfiguration
 static class Filters {
  @org.springframework.context.annotation.Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
 }
 @Autowired MockMvc mvc;
 @MockitoBean FrontendUserDetailsService users;
 @MockitoBean FrontendUserProfileRepository profiles;
 @MockitoBean DatevKonfigurationRepository configs;
 @MockitoBean DatevPersonalnummerRepository nummern;
 static final String URL="/api/zeitverwaltung/monatsabschluesse/datev/konfiguration";
 static final String BODY="{\"version\":0,\"ziel\":\"LODAS\",\"beraterNr\":\"\",\"mandantenNr\":\"\",\"stundenart\":\"SOLL\",\"zuordnungen\":[],\"personalnummern\":[]}";
 FrontendUserPrincipal principal() {
  var m=new Mitarbeiter();m.setId(9L);m.setArt(MitarbeiterArt.MENSCH);m.setAktiv(true);
  var a=new Abteilung();a.setDarfMonatAbschliessen(true);m.setAbteilungen(Set.of(a));
  var p=new FrontendUserProfile();p.setMitarbeiter(m);p.setActive(true);
  when(profiles.findById(70L)).thenReturn(Optional.of(p));
  return new FrontendUserPrincipal(70L,"test@example.com","Max Mustermann","",true,Set.of());
 }
 @BeforeEach
 void sitzungsRechteUnveraendert() {
     SitzungsRechteImTest.rechteUnveraendert(users);
 }

 @Test void anonymUndGefälschteIdentitaetAbgewiesen() throws Exception {
  mvc.perform(get(URL).header("X-Mitarbeiter-Id","9")).andExpect(status().isUnauthorized());
  mvc.perform(put(URL).with(csrf()).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
  verifyNoInteractions(configs,nummern);
 }
 @Test @WithMockUser(roles="ADMIN") void adminOhneAbschlussrechtUndCsrfAbgewiesen() throws Exception {
  mvc.perform(get(URL)).andExpect(status().isForbidden());
  mvc.perform(put(URL).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
  verifyNoInteractions(configs,nummern);
 }
 @Test void berechtigtePersonKannNurLesen() throws Exception {
  var auth=principal();var c=new DatevKonfiguration();
  when(configs.findById(1L)).thenReturn(Optional.of(c));
  mvc.perform(get(URL).with(user(auth))).andExpect(status().isOk())
   .andExpect(jsonPath("$.ziel").value("LODAS")).andExpect(jsonPath("$.stundenart").value("SOLL"));
  mvc.perform(put(URL).with(user(auth)).with(csrf()).contentType("application/json").content(BODY))
   .andExpect(status().isMethodNotAllowed());
  verify(configs,never()).sperren();
  verify(nummern,never()).saveAll(anyList());
 }
}

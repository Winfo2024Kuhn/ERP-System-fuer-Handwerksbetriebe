package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.*;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DatevKonfigurationController.class)
@Import({SecurityConfig.class, DatevKonfigurationService.class, MonatsabschlussBerechtigungService.class, DatevKonfigurationSecurityTest.Filters.class})
class DatevKonfigurationSecurityTest {
 @org.springframework.boot.test.context.TestConfiguration
 static class Filters {
  @org.springframework.context.annotation.Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
 }
 @Autowired MockMvc mvc;
 @MockBean FrontendUserDetailsService users;
 @MockBean FrontendUserProfileRepository profiles;
 @MockBean DatevKonfigurationRepository configs;
 @MockBean DatevPersonalnummerRepository nummern;
 static final String URL="/api/zeitverwaltung/monatsabschluesse/datev/konfiguration";
 static final String BODY="{\"version\":0,\"ziel\":\"LODAS\",\"beraterNr\":\"\",\"mandantenNr\":\"\",\"zuordnungen\":[],\"personalnummern\":[]}";
 FrontendUserPrincipal principal() {
  var m=new Mitarbeiter();m.setId(9L);m.setArt(MitarbeiterArt.MENSCH);m.setAktiv(true);
  var a=new Abteilung();a.setDarfMonatAbschliessen(true);m.setAbteilungen(Set.of(a));
  var p=new FrontendUserProfile();p.setMitarbeiter(m);p.setActive(true);
  when(profiles.findById(70L)).thenReturn(Optional.of(p));
  return new FrontendUserPrincipal(70L,"test@example.com","Max Mustermann","",true,Set.of());
 }
 @Test void anonymUndGefälschteIdentitaetAbgewiesen() throws Exception {
  mvc.perform(get(URL).header("X-Mitarbeiter-Id","9")).andExpect(status().isUnauthorized());
  mvc.perform(put(URL).with(csrf()).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
  verifyNoInteractions(configs,nummern);
 }
 @Test @WithMockUser(roles="ADMIN") void adminOhneAbschlussrechtUndCsrfAbgewiesen() throws Exception {
  mvc.perform(get(URL)).andExpect(status().isForbidden());
  mvc.perform(put(URL).with(csrf()).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
  mvc.perform(put(URL).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
  verifyNoInteractions(configs,nummern);
 }
 @Test void berechtigtePersonKannLesenUndSpeichern() throws Exception {
  var auth=principal();var c=new DatevKonfiguration();
  when(configs.findById(1L)).thenReturn(Optional.of(c));when(configs.sperren()).thenReturn(Optional.of(c));
  when(configs.saveAndFlush(c)).thenAnswer(i->{c.setVersion(1L);return c;});
  mvc.perform(get(URL).with(user(auth))).andExpect(status().isOk()).andExpect(jsonPath("$.ziel").value("LODAS"));
  mvc.perform(put(URL).with(user(auth)).with(csrf()).contentType("application/json").content(BODY))
   .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
 }
 @Test void manipulierterTextUndVersionskonflikt() throws Exception {
  var auth=principal();
  for(String value:List.of("'; DROP TABLE x; --","<script>alert(1)</script>","1".repeat(10001))) {
   String body=BODY.replace("\"beraterNr\":\"\"","\"beraterNr\":\""+value+"\"");
   mvc.perform(put(URL).with(user(auth)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  }
  var c=new DatevKonfiguration();c.setVersion(1L);when(configs.sperren()).thenReturn(Optional.of(c));
  mvc.perform(put(URL).with(user(auth)).with(csrf()).contentType("application/json").content(BODY)).andExpect(status().isConflict());
  verifyNoInteractions(nummern);
 }
}

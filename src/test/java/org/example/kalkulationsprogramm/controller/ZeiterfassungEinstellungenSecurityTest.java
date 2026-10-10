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
import java.time.LocalDate;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Einstellungen → Zeiterfassung: nur Admins, mit CSRF, verständliche 400-Meldungen. */
@WebMvcTest(ZeiterfassungEinstellungenController.class)
@Import({SecurityConfig.class, ZeiterfassungEinstellungService.class, DatevKonfigurationService.class,
  ZeiterfassungEinstellungenSecurityTest.Filters.class})
class ZeiterfassungEinstellungenSecurityTest {
 @org.springframework.boot.test.context.TestConfiguration
 static class Filters {
  @org.springframework.context.annotation.Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
 }
 @Autowired MockMvc mvc;
 @MockBean FrontendUserDetailsService users;
 @MockBean FrontendUserProfileRepository profiles;
 @MockBean SystemSettingsService settings;
 @MockBean DatevKonfigurationRepository configs;
 @MockBean DatevPersonalnummerRepository nummern;
 @MockBean MonatsabschlussBerechtigungService rechte;
 static final String START="/api/settings/zeiterfassung";
 static final String DATEV="/api/settings/datev";
 static final String DATEV_BODY="{\"version\":0,\"ziel\":\"LODAS\",\"beraterNr\":\"\",\"mandantenNr\":\"\",\"stundenart\":\"IST\",\"zuordnungen\":[],\"personalnummern\":[]}";
 static String start(String datum) { return "{\"eingefuehrtAm\":" + (datum == null ? "null" : "\"" + datum + "\"") + "}"; }

 @Test void anonymWirdAbgewiesen() throws Exception {
  mvc.perform(get(START)).andExpect(status().isUnauthorized());
  mvc.perform(put(START).with(csrf()).contentType("application/json").content(start("2025-03-01"))).andExpect(status().isUnauthorized());
  mvc.perform(get(DATEV)).andExpect(status().isUnauthorized());
  mvc.perform(put(DATEV).with(csrf()).contentType("application/json").content(DATEV_BODY)).andExpect(status().isUnauthorized());
  verifyNoInteractions(settings,configs,nummern);
 }
 @Test @WithMockUser(roles="USER") void ohneAdminrechtVerboten() throws Exception {
  mvc.perform(get(START)).andExpect(status().isForbidden());
  mvc.perform(put(START).with(csrf()).contentType("application/json").content(start("2025-03-01"))).andExpect(status().isForbidden());
  mvc.perform(get(DATEV)).andExpect(status().isForbidden());
  mvc.perform(put(DATEV).with(csrf()).contentType("application/json").content(DATEV_BODY)).andExpect(status().isForbidden());
  verifyNoInteractions(settings,configs,nummern);
 }
 @Test @WithMockUser(roles="ADMIN") void ohneCsrfVerboten() throws Exception {
  mvc.perform(put(START).contentType("application/json").content(start("2025-03-01"))).andExpect(status().isForbidden());
  mvc.perform(put(DATEV).contentType("application/json").content(DATEV_BODY)).andExpect(status().isForbidden());
  verifyNoInteractions(settings,configs,nummern);
 }
 @Test @WithMockUser(roles="ADMIN") void startDerZeiterfassungLesenUndSpeichern() throws Exception {
  when(settings.get("zeiterfassung.eingefuehrt-am","")).thenReturn("");
  mvc.perform(get(START)).andExpect(status().isOk()).andExpect(jsonPath("$.eingefuehrtAm").isEmpty());
  when(settings.get("zeiterfassung.eingefuehrt-am","")).thenReturn("2025-03-01");
  mvc.perform(get(START)).andExpect(status().isOk()).andExpect(jsonPath("$.eingefuehrtAm").value("2025-03-01"));
  mvc.perform(put(START).with(csrf()).contentType("application/json").content(start("2025-03-01")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.eingefuehrtAm").value("2025-03-01"));
  verify(settings).save(eq("zeiterfassung.eingefuehrt-am"),eq("2025-03-01"),anyString());
  mvc.perform(put(START).with(csrf()).contentType("application/json").content(start(null)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.eingefuehrtAm").isEmpty());
  verify(settings).save(eq("zeiterfassung.eingefuehrt-am"),eq(""),anyString());
 }
 @Test @WithMockUser(roles="ADMIN") void ungueltigeDatenWerdenMitMeldungAbgelehnt() throws Exception {
  for(String value:List.of("'; DROP TABLE x; --","<script>alert(1)</script>","1".repeat(10001),"2025-02-30","01.03.2025","1899-12-31")) {
   mvc.perform(put(START).with(csrf()).contentType("application/json").content(start(value)))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Bitte ein gültiges Datum eingeben."));
  }
  mvc.perform(put(START).with(csrf()).contentType("application/json").content(start(LocalDate.now().plusDays(1).toString())))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Das Datum darf nicht in der Zukunft liegen."));
  verify(settings,never()).save(anyString(),anyString(),anyString());
 }
 @Test @WithMockUser(roles="ADMIN") void adminRichtetDatevOhneAbschlussrechtEin() throws Exception {
  var c=new DatevKonfiguration();
  when(configs.findById(1L)).thenReturn(Optional.of(c));when(configs.sperren()).thenReturn(Optional.of(c));
  when(configs.saveAndFlush(c)).thenAnswer(i->{c.setVersion(1L);return c;});
  mvc.perform(get(DATEV)).andExpect(status().isOk()).andExpect(jsonPath("$.stundenart").value("SOLL"));
  mvc.perform(put(DATEV).with(csrf()).contentType("application/json").content(DATEV_BODY))
   .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.stundenart").value("IST"));
  verifyNoInteractions(rechte);
 }
 @Test @WithMockUser(roles="ADMIN") void datevManipulierteTexteUndVersionskonflikt() throws Exception {
  for(String value:List.of("'; DROP TABLE x; --","<script>alert(1)</script>","1".repeat(10001))) {
   mvc.perform(put(DATEV).with(csrf()).contentType("application/json").content(DATEV_BODY.replace("\"beraterNr\":\"\"","\"beraterNr\":\""+value+"\"")))
    .andExpect(status().isBadRequest());
   mvc.perform(put(DATEV).with(csrf()).contentType("application/json").content(DATEV_BODY.replace("\"stundenart\":\"IST\"","\"stundenart\":\""+value+"\"")))
    .andExpect(status().isBadRequest());
  }
  var c=new DatevKonfiguration();c.setVersion(1L);when(configs.sperren()).thenReturn(Optional.of(c));
  mvc.perform(put(DATEV).with(csrf()).contentType("application/json").content(DATEV_BODY)).andExpect(status().isConflict());
  verifyNoInteractions(nummern);
 }
}

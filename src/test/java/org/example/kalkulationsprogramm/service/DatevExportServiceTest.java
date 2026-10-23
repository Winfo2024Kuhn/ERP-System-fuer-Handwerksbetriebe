package org.example.kalkulationsprogramm.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.DatevDto.*;
import org.example.kalkulationsprogramm.repository.DatevExportRepository;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import org.springframework.transaction.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatevExportServiceTest {
 DatevExportRepository repo=mock(DatevExportRepository.class);
 MonatsabschlussBerechtigungService rights=mock(MonatsabschlussBerechtigungService.class);
 PlatformTransactionManager tm=mock(PlatformTransactionManager.class);
 DatevExportService service;
 DatevKonfiguration config;
 MonatsSaldo saldo;
 ExportRequest request=new ExportRequest(List.of(new Stand(1L,2026,1,3L)),0L);
 @BeforeEach void setup() throws Exception {
  when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
  service=new DatevExportService(repo,rights,new LodasDateiWriter(),tm);
  // The existing cases check the worked hours; SOLL has its own tests below.
  config=new DatevKonfiguration();config.setBeraterNr("1234");config.setMandantenNr("123");config.setStundenart("IST");mapping(false);
  saldo=saldo(1);when(repo.konfiguration()).thenReturn(config);
  when(repo.salden(any(),any())).thenAnswer(i->List.of(saldo));
  var n=new DatevPersonalnummer();n.setMitarbeiterId(1L);n.setPersonalnummer("00014");n.setNormalisiert("14");
  when(repo.personalnummern(any())).thenReturn(List.of(n));
 }
 MonatsSaldo saldo(int month) {
  var s=new MonatsSaldo();var m=new Mitarbeiter();m.setId(1L);m.setArt(MitarbeiterArt.MENSCH);
  s.setMitarbeiter(m);s.setJahr(2026);s.setMonat(month);s.setVersion(3L);s.setFestgeschrieben(true);
  s.setIstStunden(new BigDecimal("100.00"));s.setAbwesenheitsStunden(new BigDecimal("8"));s.setKorrekturStunden(new BigDecimal("-2"));return s;
 }
 void mapping(boolean absence) throws Exception {
  var mappings=DatevKonfigurationService.KATEGORIEN.stream().sorted().map(k->new Zuordnung(k,!k.equals("ARBEIT")&&!(absence&&k.equals("URLAUB")),k.equals("ARBEIT")||absence&&k.equals("URLAUB")?"200":"")).toList();
  config.setZuordnungenJson(JsonMapper.builderWithJackson2Defaults().build().writeValueAsString(mappings));
 }
 @Test void alteAbwesenheitNurExplizitAusschliessenKorrekturNiemalsAuszahlen() {
  var result=service.pruefen(request,null);
  assertThat(result.gueltig()).isTrue();
  assertThat(result.ausschluesse()).extracting(Hinweis::kategorie).containsExactlyInAnyOrder("ABWESENHEIT_UNGEGLIEDERT","KORREKTUR");
  assertThat(new String(service.exportieren(request,null).inhalt(),java.nio.charset.StandardCharsets.US_ASCII)).contains(";100,00;01;200;14;").doesNotContain(";108,00;");
 }
 @Test void alteAbwesenheitNichtMitHeutigenWertenErgaenzen() throws Exception {
  mapping(true);var result=service.pruefen(request,null);
  assertThat(result.gueltig()).isFalse();assertThat(result.fehler()).extracting(Hinweis::kategorie).contains("ABWESENHEIT_UNGEGLIEDERT");
  assertThatThrownBy(()->service.exportieren(request,null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
 }
 @Test void offeneFehlendeUndVeralteteStaendeLiefernKeineDatei() {
  saldo.setFestgeschrieben(false);
  assertThat(service.pruefen(request,null).gueltig()).isFalse();
  assertThatThrownBy(()->service.exportieren(request,null)).hasMessageContaining("409");
  saldo.setFestgeschrieben(true);saldo.setVersion(4L);
  assertThatThrownBy(()->service.exportieren(request,null)).hasMessageContaining("409");
  when(repo.salden(any(),any())).thenReturn(List.of());
  assertThat(service.pruefen(request,null).gueltig()).isFalse();
 }
 @Test void konfigurationsversionWirdBeimDownloadErneutGeprueft() {
  assertThat(service.pruefen(request,null).gueltig()).isTrue();config.setVersion(1L);
  assertThatThrownBy(()->service.exportieren(request,null)).hasMessageContaining("409");
 }
 @Test void fehlendeEntscheidungIstKeinAusschluss() {
  config.setZuordnungenJson("[]");
  assertThat(service.pruefen(request,null).fehler()).hasSizeGreaterThanOrEqualTo(8);
 }
 @Test void pflichtnummernUndUngueltigeStundenBlockieren() {
  config.setBeraterNr("");saldo.setIstStunden(new BigDecimal("-1"));
  var result=service.pruefen(request,null);assertThat(result.gueltig()).isFalse();
  assertThat(result.fehler()).extracting(Hinweis::kategorie).contains("KONFIGURATION","ARBEIT");
 }
 @Test void doppeltePersonalnummernBlockieren() {
  var n=new DatevPersonalnummer();n.setMitarbeiterId(2L);n.setPersonalnummer("14");
  var original=repo.personalnummern(Set.of(1L)).getFirst();
  when(repo.personalnummern(any())).thenReturn(List.of(original,n));
  var r=new ExportRequest(List.of(new Stand(1L,2026,1,3L),new Stand(2L,2026,1,3L)),0L);
  assertThat(service.pruefen(r,null).fehler()).extracting(Hinweis::kategorie).contains("PERSONALNUMMER");
 }
 @Test void mehrMonateZipEnthaeltJedenMonatGenauEinmal() throws Exception {
  when(repo.salden(any(),any())).thenReturn(List.of(saldo(2),saldo(1)));
  var file=service.exportieren(new ExportRequest(List.of(new Stand(1L,2026,2,3L),new Stand(1L,2026,1,3L)),0L),null);
  assertThat(file.contentType()).isEqualTo("application/zip");
  try(var zip=new ZipInputStream(new ByteArrayInputStream(file.inhalt()))) {
   assertThat(zip.getNextEntry().getName()).isEqualTo("lodas-2026-01.txt");assertThat(new String(zip.readAllBytes())).contains("3;01/01/2026;100,00;");
   assertThat(zip.getNextEntry().getName()).isEqualTo("lodas-2026-02.txt");assertThat(new String(zip.readAllBytes())).contains("3;01/02/2026;100,00;");
   assertThat(zip.getNextEntry()).isNull();
  }
 }
 @Test void requestGrenzenUndDuplikate() {
  for(var selection:List.of(List.<Stand>of(),List.of(new Stand(0L,2026,1,0L)),List.of(new Stand(1L,2026,13,0L)),List.of(request.auswahl().getFirst(),request.auswahl().getFirst())))
   assertThatThrownBy(()->service.pruefen(new ExportRequest(selection,0L),null)).hasMessageContaining("400");
 }
 @Test void vollstaendigeHistorischeDetailsWerdenExaktZusammengefasst() throws Exception {
  mapping(true);saldo.setUrlaubStunden(new BigDecimal("8"));saldo.setKrankheitStunden(BigDecimal.ZERO);
  saldo.setFortbildungStunden(BigDecimal.ZERO);saldo.setZeitausgleichStunden(BigDecimal.ZERO);
  saldo.setKrankengeldStunden(BigDecimal.ZERO);saldo.setWiedereingliederungStunden(BigDecimal.ZERO);
  assertThat(new String(service.exportieren(request,null).inhalt())).contains(";108,00;01;200;14;");
  saldo.setUrlaubStunden(new BigDecimal("9"));
  assertThat(service.pruefen(request,null).fehler()).extracting(Hinweis::kategorie).contains("ABWESENHEIT");
 }
 @Test void nullStundenSindKeineDateiUndAggregateWerdenGeprueft() throws Exception {
  saldo.setIstStunden(BigDecimal.ZERO);assertThat(service.pruefen(request,null).gueltig()).isFalse();
  saldo.setIstStunden(new BigDecimal("1000000000"));assertThat(service.pruefen(request,null).gueltig()).isFalse();
  saldo.setIstStunden(new BigDecimal("1.001"));assertThat(service.pruefen(request,null).gueltig()).isFalse();
 }
 @Test void zuVieleMonateUndStaendeWerdenNichtStillGekuerzt() {
  var many=java.util.stream.IntStream.rangeClosed(1,501).mapToObj(i->new Stand((long)i,2026,1,0L)).toList();
  assertThatThrownBy(()->service.pruefen(new ExportRequest(many,0L),null)).hasMessageContaining("400");
  var months=java.util.stream.IntStream.range(0,13).mapToObj(i->{var m=java.time.YearMonth.of(2024,1).plusMonths(i);return new Stand(1L,m.getYear(),m.getMonthValue(),0L);}).toList();
  assertThatThrownBy(()->service.pruefen(new ExportRequest(months,0L),null)).hasMessageContaining("400");
 }
 @Test void sollArbeitIstSollOhneFeiertageUndAbwesenheiten() {
  // 160 h Soll, 8 h Abwesenheit (setup) → 152 h Arbeit; the worked 100 h do not matter.
  config.setStundenart("SOLL");saldo.setSollStunden(new BigDecimal("160.00"));
  assertThat(new String(service.exportieren(request,null).inhalt(),java.nio.charset.StandardCharsets.US_ASCII))
   .contains(";152,00;01;200;14;").doesNotContain(";100,00;").doesNotContain(";160,00;");
 }
 @Test void gemischterBetriebZaehltUrlaubBeiSollNichtDoppelt() throws Exception {
  // Company IST with vacation exported, this employee SOLL: 168 h Soll, 8 h holiday, 16 h vacation.
  mapping(true);repo.personalnummern(Set.of(1L)).getFirst().setStundenart("SOLL");
  saldo.setSollStunden(new BigDecimal("168.00"));saldo.setFeiertagsStunden(new BigDecimal("8.00"));
  saldo.setAbwesenheitsStunden(new BigDecimal("16.00"));saldo.setUrlaubStunden(new BigDecimal("16.00"));
  saldo.setKrankheitStunden(BigDecimal.ZERO);saldo.setFortbildungStunden(BigDecimal.ZERO);saldo.setZeitausgleichStunden(BigDecimal.ZERO);
  saldo.setKrankengeldStunden(BigDecimal.ZERO);saldo.setWiedereingliederungStunden(BigDecimal.ZERO);
  // ARBEIT 144 h + URLAUB 16 h share wage type 200 in this mapping → 160 h; double counting would give 184 h.
  assertThat(new String(service.exportieren(request,null).inhalt(),java.nio.charset.StandardCharsets.US_ASCII))
   .contains(";160,00;01;200;14;").doesNotContain(";184,00;");
 }
 @Test void mitarbeiterKannVomBetriebAbweichen() {
  config.setStundenart("SOLL");saldo.setSollStunden(new BigDecimal("160.00"));
  repo.personalnummern(Set.of(1L)).getFirst().setStundenart("IST");
  assertThat(new String(service.exportieren(request,null).inhalt(),java.nio.charset.StandardCharsets.US_ASCII))
   .contains(";100,00;01;200;14;").doesNotContain(";152,00;");
 }
 @Test void ohneSollWerdenGearbeiteteStundenSichtbarAusgelassen() {
  config.setStundenart("SOLL");saldo.setSollStunden(BigDecimal.ZERO);saldo.setAbwesenheitsStunden(BigDecimal.ZERO);
  var result=service.pruefen(request,null);
  assertThat(result.ausschluesse()).filteredOn(h->h.kategorie().equals("ARBEIT")).singleElement()
   .satisfies(h->assertThat(h.stunden()).isEqualByComparingTo("100.00"));
 }
 @Test void ohneSollAberMitAbwesenheitRaetZuIstStunden() {
  // A temp without contractual hours but with 8 h absence (setup): the advice is IST, not "check the calendar".
  config.setStundenart("SOLL");saldo.setSollStunden(BigDecimal.ZERO);
  assertThat(service.pruefen(request,null).fehler()).filteredOn(h->h.kategorie().equals("ARBEIT")).singleElement()
   .satisfies(h->assertThat(h.meldung()).contains("Ist-Stunden wählen"));
 }
 @Test void abwesenheitenUeberDemSollBlockierenStattNegativerStunden() {
  config.setStundenart("SOLL");saldo.setSollStunden(new BigDecimal("4.00"));
  var fehler=service.pruefen(request,null).fehler();
  assertThat(fehler).filteredOn(h->h.kategorie().equals("ARBEIT")).singleElement()
   .satisfies(h->assertThat(h.stunden()).isEqualByComparingTo("-4.00"));
 }

}

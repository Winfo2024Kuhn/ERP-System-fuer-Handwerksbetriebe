package org.example.kalkulationsprogramm.service;

import jakarta.persistence.*;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.DatevDto.*;
import org.example.kalkulationsprogramm.repository.DatevExportRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit opt-in; own EMPTY disposable MySQL DB, never a production schema. */
@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect","spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({DatevExportService.class,DatevExportRepository.class,LodasDateiWriter.class,DatevKonfigurationService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named="datev.mysql.url",matches="jdbc:mysql:.*")
class DatevExportMysqlTest {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
  r.add("spring.datasource.url",()->System.getProperty("datev.mysql.url"));r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
  r.add("spring.datasource.username",()->"root");r.add("spring.datasource.password",()->"");
 }
 @Autowired DatevExportService service;
 @Autowired DatevKonfigurationService configurations;
 @Autowired EntityManager em;
 @Autowired PlatformTransactionManager tm;
 @Autowired JdbcTemplate jdbc;
 @MockBean MonatsabschlussBerechtigungService rights;
 @SpyBean LodasDateiWriter writer;
 TransactionTemplate tx;
 long saldoId;
 ExportRequest request;
 @BeforeEach void setup() throws Exception {
  reset(writer);tx=new TransactionTemplate(tm);
  jdbc.update("DELETE FROM monats_saldo");jdbc.update("DELETE FROM datev_personalnummer");jdbc.update("DELETE FROM datev_konfiguration");jdbc.update("DELETE FROM mitarbeiter");
  String mapping=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(DatevKonfigurationService.KATEGORIEN.stream().map(k->new Zuordnung(k,!k.equals("ARBEIT"),k.equals("ARBEIT")?"200":"")).toList());
  request=tx.execute(status->{
   var m=new Mitarbeiter();m.setVorname("Max");m.setNachname("Mustermann");em.persist(m);
   var c=new DatevKonfiguration();c.setBeraterNr("1234");c.setMandantenNr("123");c.setZuordnungenJson(mapping);em.persist(c);
   var n=new DatevPersonalnummer();n.setMitarbeiterId(m.getId());n.setPersonalnummer("14");n.setNormalisiert("14");em.persist(n);
   var s=new MonatsSaldo();s.setMitarbeiter(m);s.setJahr(2026);s.setMonat(1);s.setFestgeschrieben(true);s.setIstStunden(new BigDecimal("100"));em.persist(s);em.flush();saldoId=s.getId();
   return new ExportRequest(List.of(new Stand(m.getId(),2026,1,s.getVersion())),c.getVersion());
  });
 }
 @Test void echterSetQueryUndErfolgreicherDownload() {
  assertThat(service.pruefen(request,null).gueltig()).isTrue();
  assertThat(new String(service.exportieren(request,null).inhalt())).contains("3;01/01/2026;100,00;01;200;14;");
 }
 @Test void wiedereroeffnungNachVorschauVerhindertDatei() {
  assertThat(service.pruefen(request,null).gueltig()).isTrue();
  tx.executeWithoutResult(status->em.find(MonatsSaldo.class,saldoId).setFestgeschrieben(false));
  assertThatThrownBy(()->service.exportieren(request,null)).hasMessageContaining("409");
 }
 @ParameterizedTest @ValueSource(strings={"MONAT","KONFIGURATION","PERSONALNUMMER"})
 void aenderungWaehrendByteerzeugungLiefertKeineDatei(String change) throws Exception {
  var before=configurations.laden(null); // Separate GET transaction, as in the HTTP configuration flow.
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  doAnswer(inv->{entered.countDown();await(release);return inv.callRealMethod();}).when(writer).schreiben(any(),anyInt(),anyInt(),any());
  try(var pool=Executors.newFixedThreadPool(1)) {
   var download=pool.submit(()->service.exportieren(request,null));
   try {
    await(entered);
    tx.executeWithoutResult(status->{
     if(change.equals("PERSONALNUMMER")) {
      var c=before;
      configurations.speichern(new Konfiguration(c.version(),c.ziel(),c.beraterNr(),c.mandantenNr(),c.zuordnungen(),List.of(new Personalnummer(request.auswahl().getFirst().mitarbeiterId(),"15"))),null);
     } else if(change.equals("KONFIGURATION")) em.find(DatevKonfiguration.class,1L).setMandantenNr("999");
     else em.find(MonatsSaldo.class,saldoId).setFestgeschrieben(false);
    });
   } finally { release.countDown(); }
   assertThatThrownBy(()->download.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasRootCauseMessage("409 CONFLICT \"Ein Monatsstand oder die DATEV-Einstellungen wurden geändert. Bitte neu laden und erneut prüfen.\"");
  } finally { release.countDown(); }
 }
 @Test void vorherGeladenerPersistenceContextKannAltenStandNichtExportieren() {
  tx.executeWithoutResult(status->{
   em.find(MonatsSaldo.class,saldoId);
   var independent=new TransactionTemplate(tm);independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
   independent.executeWithoutResult(other->em.find(MonatsSaldo.class,saldoId).setFestgeschrieben(false));
   assertThatThrownBy(()->service.exportieren(request,null)).hasMessageContaining("409");
  });
 }
 private static void await(CountDownLatch latch) {
  try { assertThat(latch.await(15,TimeUnit.SECONDS)).isTrue(); }
  catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}
 }
}

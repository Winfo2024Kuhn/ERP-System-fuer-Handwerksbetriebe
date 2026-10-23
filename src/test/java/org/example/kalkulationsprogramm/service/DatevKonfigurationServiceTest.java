package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.DatevDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatevKonfigurationServiceTest {
 @Mock DatevKonfigurationRepository configs;
 @Mock DatevPersonalnummerRepository nummern;
 @Mock MonatsabschlussBerechtigungService rechte;
 @InjectMocks DatevKonfigurationService service;
 DatevKonfiguration entity;
 @BeforeEach void setup() { entity = new DatevKonfiguration(); entity.setId(1L); entity.setVersion(0L); }
 Konfiguration dto(String berater, List<Zuordnung> mapping, List<Personalnummer> personal) {
  return new Konfiguration(0L,"LODAS",berater,"","SOLL",mapping,personal);
 }
 @Test void leereVorgabeOhneGerateneNummern() {
  when(configs.findById(1L)).thenReturn(Optional.of(entity));
  var result=service.laden(null);
  assertEquals("LODAS",result.ziel()); assertEquals("",result.beraterNr()); assertTrue(result.zuordnungen().isEmpty());
  verify(rechte).verlangeAkteur(null);
 }
 @Test void unvollstaendigeEinrichtungSpeicherbarUndVersionWirdErhoeht() {
  when(configs.sperren()).thenReturn(Optional.of(entity));
  when(configs.saveAndFlush(entity)).thenAnswer(i->{entity.setVersion(1L);return entity;});
  var result=service.speichern(dto("",List.of(new Zuordnung("ARBEIT",false,"")),List.of()));
  assertEquals(1L,result.version()); assertEquals("",result.zuordnungen().getFirst().lohnart());
  assertEquals(1L,entity.getAenderungszaehler());
 }
 @Test void veralteteVersionIstKonflikt() {
  entity.setVersion(1L); when(configs.sperren()).thenReturn(Optional.of(entity));
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.speichern(dto("",List.of(),List.of()))).getStatusCode().value());
  verifyNoInteractions(nummern);
 }
 @Test void ungueltigeNummernUndAngriffstexteWerdenAbgelehnt() {
  for(String value:List.of("123","12345678","<script>alert(1)</script>","'; DROP TABLE x; --","1".repeat(10001),"12,5")) {
   assertEquals(400,assertThrows(ResponseStatusException.class,()->service.speichern(dto(value,List.of(),List.of()))).getStatusCode().value());
  }
 }
 @Test void unbekannteZieleKategorienUndDoppelteEntscheidungenAbgelehnt() {
  assertThrows(ResponseStatusException.class,()->service.speichern(new Konfiguration(0L,"OTHER","","","SOLL",List.of(),List.of())));
  for(var mappings:List.of(List.of(new Zuordnung("KORREKTUR",false,"1")), List.of(new Zuordnung("ARBEIT",true,"1")),List.of(new Zuordnung("ARBEIT",false,"12345")),List.of(new Zuordnung("ARBEIT",true,""),new Zuordnung("ARBEIT",false,"2"))))
   assertThrows(ResponseStatusException.class,()->service.speichern(dto("",mappings,List.of())));
 }
 @Test void normalisiertePersonalnummernSindEindeutig() {
  assertThrows(ResponseStatusException.class,()->service.speichern(dto("",List.of(),List.of(new Personalnummer(1L,"00012",null),new Personalnummer(2L,"12",null)))));
 }
 @Test void nummernBleibenStringsUndMenschenWerdenGeprueft() {
  when(configs.sperren()).thenReturn(Optional.of(entity)); when(nummern.findMenschenIds(Set.of(1L))).thenReturn(List.of(1L));
  when(configs.saveAndFlush(entity)).thenReturn(entity);
  var result=service.speichern(dto("0123",List.of(new Zuordnung("ARBEIT",false,"0200")),List.of(new Personalnummer(1L,"00012",null))));
  assertEquals("00012",result.personalnummern().getFirst().personalnummer());
  verify(nummern).findMenschenIds(Set.of(1L));
 }
 @Test void einstellungenLadenBrauchtKeinAbschlussrechtUndLiefertStundenart() {
  entity.setStundenart("IST"); when(configs.findById(1L)).thenReturn(Optional.of(entity));
  var n=new DatevPersonalnummer(); n.setMitarbeiterId(1L); n.setPersonalnummer("00012"); n.setStundenart("SOLL");
  when(nummern.findAll()).thenReturn(List.of(n));
  var result=service.einstellungenLaden();
  assertEquals("IST",result.stundenart()); assertEquals("SOLL",result.personalnummern().getFirst().stundenart());
  verifyNoInteractions(rechte);
 }
 @Test void stundenartFuerBetriebUndJeMitarbeiterWirdGespeichert() {
  when(configs.sperren()).thenReturn(Optional.of(entity)); when(nummern.findMenschenIds(Set.of(1L,2L))).thenReturn(List.of(1L,2L));
  when(configs.saveAndFlush(entity)).thenReturn(entity);
  var input=new Konfiguration(0L,"LODAS","","","IST",List.of(),List.of(new Personalnummer(1L,"11","SOLL"),new Personalnummer(2L,"12",null)));
  var result=service.speichern(input);
  assertEquals("IST",entity.getStundenart()); assertEquals("IST",result.stundenart());
  @SuppressWarnings("unchecked") ArgumentCaptor<List<DatevPersonalnummer>> rows=ArgumentCaptor.forClass(List.class);
  verify(nummern).saveAll(rows.capture());
  assertEquals(Arrays.asList("SOLL",null),rows.getValue().stream().map(DatevPersonalnummer::getStundenart).toList());
 }
 @Test void ungueltigeOderFehlendeStundenartWirdAbgelehnt() {
  for(String art:Arrays.asList(null,"","soll","'; DROP TABLE x; --","<script>alert(1)</script>","S".repeat(10001)))
   assertEquals(400,assertThrows(ResponseStatusException.class,()->service.speichern(new Konfiguration(0L,"LODAS","","",art,List.of(),List.of()))).getStatusCode().value());
  assertThrows(ResponseStatusException.class,()->service.speichern(dto("",List.of(),List.of(new Personalnummer(1L,"12","HALB")))));
  var ohneNummer=assertThrows(ResponseStatusException.class,()->service.speichern(dto("",List.of(),List.of(new Personalnummer(1L,"","IST")))));
  assertEquals("Eine eigene Stunden-Auswahl braucht eine Personalnummer.",ohneNummer.getReason());
  verifyNoInteractions(configs,nummern);
 }
 @Test void unbekannterMitarbeiterUndUngueltigeIdsWerdenAbgelehnt() {
  assertThrows(ResponseStatusException.class,()->service.speichern(dto("",List.of(),List.of(new Personalnummer(0L,"12",null)))));
  when(configs.sperren()).thenReturn(Optional.of(entity)); when(nummern.findMenschenIds(Set.of(99L))).thenReturn(List.of());
  assertThrows(ResponseStatusException.class,()->service.speichern(dto("",List.of(),List.of(new Personalnummer(99L,"12",null)))));
 }
 @Test void echtePersistenzErhoehtVersionBeiReinerPersonalnummerAenderungUndSchuetztDubletten() {
  var cfg = new org.hibernate.cfg.Configuration()
   .addAnnotatedClass(DatevKonfiguration.class).addAnnotatedClass(DatevPersonalnummer.class)
   .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
   .setProperty("hibernate.connection.url", "jdbc:h2:mem:datev_config_" + UUID.randomUUID())
   .setProperty("hibernate.hbm2ddl.auto", "create-drop");
  try(var factory=cfg.buildSessionFactory(); var session=factory.openSession()) {
   session.beginTransaction(); session.persist(new DatevKonfiguration()); session.getTransaction().commit(); session.clear();
   session.beginTransaction();
   var stored=session.find(DatevKonfiguration.class,1L);
   when(configs.sperren()).thenReturn(Optional.of(stored));
   when(nummern.findMenschenIds(Set.of(1L))).thenReturn(List.of(1L));
   when(nummern.saveAll(anyList())).thenAnswer(i->{
    List<DatevPersonalnummer> rows=i.getArgument(0); rows.forEach(session::persist); return rows;
   });
   when(configs.saveAndFlush(stored)).thenAnswer(i->{session.flush(); return stored;});
   var result=service.speichern(dto("",List.of(),List.of(new Personalnummer(1L,"00012",null))));
   session.getTransaction().commit(); session.clear();
   assertEquals(1L,result.version());
   assertEquals(1L,session.find(DatevKonfiguration.class,1L).getVersion());
   assertEquals("00012",session.find(DatevPersonalnummer.class,1L).getPersonalnummer());
   session.beginTransaction(); var duplicate=new DatevPersonalnummer(); duplicate.setMitarbeiterId(2L);
   duplicate.setPersonalnummer("12"); duplicate.setNormalisiert("12"); session.persist(duplicate);
   assertThrows(org.hibernate.exception.ConstraintViolationException.class,session::flush);
   session.getTransaction().rollback();
  }
 }
}

package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellungDto.Herkunft;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBestellfreigabeDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLieferungDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.einkauf.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=EinkaufBestellWorkflowRegressionTest.Config.class)
class EinkaufBestellWorkflowRegressionTest {
 @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.44")
  .withDatabaseName("bestell_workflow_dummy").withUsername("test").withPassword("test");
 @Autowired PlatformTransactionManager transactionManager;
 @jakarta.persistence.PersistenceContext EntityManager em;
 @Autowired EinkaufBestellungRepository orders;
 @Autowired BestellungRevisionRepository revisions;
 @Autowired EinkaufBedarfRepository needs;
 @Autowired EinkaufMengenbuchungRepository bookings;
 @Autowired EinkaufAuditRepository audits;
 @Autowired EinkaufVersandauftragRepository dispatches;
 @Autowired EinkaufVersandAnnahmeereignisRepository acceptances;
 @Autowired EinkaufKommunikationVorschauRepository previews;
 @Autowired AngebotVersionRepository offers;
 @Autowired LieferantDokumentRepository documents;
 @Autowired EinkaufLieferungRepository deliveries;
 @Autowired EinkaufZeugnisRepository certificates;
 @Autowired EinkaufAnforderungsVorlageRepository certificateTemplates;
 @Autowired org.example.kalkulationsprogramm.repository.EinkaufDateiRepository purchaseFiles;
 private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 private EinkaufBestellungService ordering;
 private EinkaufBestellfreigabeService approval;
 private EinkaufZeugnisService certificateService;
 private EinkaufLieferungService delivery;
 private EinkaufMengenService amounts;
 private EinkaufDateiService files;
 private EinkaufPdfService pdf;
 private EinkaufOutboxService outbox;
 private EinkaufStornoanfrageService stornoRequests;
 @Autowired LieferantenRepository suppliers;
 @Autowired EinkaufAngebotRepository supplierOffers;
 @Autowired AnfrageRevisionRepository requestRevisions;
 @Autowired AnfrageLieferantRepository participations;
 @Autowired EinkaufAnlageVersionRepository attachmentVersions;
 @Autowired EmailAttachmentRepository emailAttachments;
 @org.junit.jupiter.api.io.TempDir java.nio.file.Path uploadRoot;
 private Long supplierId, needId;
 private org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot recipient;

 @BeforeEach void setup() throws Exception {
  try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())) {
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
    new org.springframework.core.io.ClassPathResource("db/migration/V387__einkauf_versand_outbox.sql"));
  }
  tx(()->{
   var supplier=new Lieferanten();supplier.setLieferantenname("Dummy "+UUID.randomUUID());em.persist(supplier);supplierId=supplier.getId();
   var basis=new Mengenbasis(new BigDecimal("30"),Einheit.STUECK,new BigDecimal("30"),null,null,null);
   var snapshot=new PositionSnapshot(Positionsart.ARTIKEL,21L,"DUMMY",null,null,"Profil","S235",null,basis,null,null,null,null,null,
    List.of(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.DokumentSoll(Dokumentart.ZEUGNIS_3_1,"EN 10204 für S235","DUMMY-SPEC-1",true)),List.of());
   var need=new EinkaufBedarf(snapshot,new Liefergruppe("Musterstraße 1",null,null,"Werkstatt"),null,null,false);
   em.persist(need);em.flush();needId=need.getId();return null;
  });
  recipient=new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot(supplierId,8L,"Dummy","test@example.com","Max Mustermann",null,null);
  amounts=new EinkaufMengenService(needs,bookings);
  var audit=new EinkaufAuditService(audits);
  var numbers=mock(DokumentnummerService.class);
  var sequence=new AtomicInteger();String prefix="B-"+UUID.randomUUID().toString().substring(0,8);
  when(numbers.naechsteEinkaufsnummer(any(),any())).thenAnswer(i->prefix+"-"+sequence.incrementAndGet());
  ordering=new EinkaufBestellungService(orders,revisions,offers,mock(EinkaufVergleichService.class),needs,amounts,numbers,audit,json,mock(LieferantenArtikelPreiseRepository.class));
  files=mock(EinkaufDateiService.class);pdf=mock(EinkaufPdfService.class);
  byte[] bytes="%PDF-DUMMY".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  when(pdf.erzeugen(any())).thenReturn(bytes);
  when(files.speicherePdfSnapshot(any(),any())).thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto.PdfSnapshotDto(55L,"dummy",bytes.length));
  when(files.ladePdfSnapshotBytes(55L)).thenReturn(bytes);
  var templates=mock(EinkaufVorlagenService.class);
  when(templates.rendern(any(),any())).thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.Gerendert(4L,1,"Dummy Bestellung","<p>Dummy</p>","hash"));
  var transport=mock(org.example.kalkulationsprogramm.service.mail.KontoMailTransport.class);
  when(transport.vorbereiten(any(),any())).thenReturn(bytes);
  outbox=new EinkaufOutboxService(dispatches,acceptances,mock(org.example.kalkulationsprogramm.service.mail.MailkontoService.class),
   mock(org.example.kalkulationsprogramm.config.LocalTestMailPolicy.class),transport,json,transactionManager);
  certificateService=new EinkaufZeugnisService(revisions,certificates,certificateTemplates,purchaseFiles,em);
  approval=new EinkaufBestellfreigabeService(orders,revisions,offers,previews,templates,pdf,files,outbox,
   mock(EinkaufVersandWorker.class),dispatches,amounts,needs,documents,audit,json,certificateService);
  stornoRequests=new EinkaufStornoanfrageService(orders,revisions,needs,amounts,audits,dispatches,outbox,mock(EinkaufVersandWorker.class),json,new EinkaufStornoanfrageAnnahmeListener(dispatches));
  delivery=new EinkaufLieferungService(orders,revisions,deliveries,needs,amounts,documents,em,audit,json);
 }

 @Test void angebotFuerReineFreitextpositionKannBestelltWerden() {
  var ids=tx(()->{
   var need=needs.findById(needId).orElseThrow();
   var basis=new Mengenbasis(new BigDecimal("4"),Einheit.KILOGRAMM,null,null,null,null);
   var position=new PositionSnapshot(Positionsart.FREITEXT,null,null,null,null,"Schweißdraht",null,null,basis,null,null,null,null,null,List.of(),List.of());
   need.setPosition(position);need.setBedarfMenge(new BigDecimal("4"));em.flush();
   var request=new Einkaufsanfrage("PA-"+UUID.randomUUID().toString().substring(0,8),9L,UUID.randomUUID(),"f".repeat(64));em.persist(request);
   var revision=new AnfrageRevision(request,1,null,LocalDate.now().plusDays(7),UUID.randomUUID(),"a".repeat(64));em.persist(revision);request.setAktuelleRevision(revision);
   var requestLine=new AnfragePosition(revision,position,new BigDecimal("4"));
   requestLine.addHerkunft(new AnfrageHerkunft(requestLine,need,need.getVersion(),new BigDecimal("4")));revision.addPosition(requestLine);em.persist(requestLine);
   var participation=new AnfrageLieferant(revision,recipient);em.persist(participation);
   var offer=new EinkaufAngebot(participation);em.persist(offer);
   var offerVersion=new AngebotVersion(offer,revision,1,"DUMMY-ANGEBOT",LocalDate.now(),LocalDate.now().plusDays(7),"EUR",null,null,null,null,null);
   var line=new AngebotPosition(offerVersion,requestLine,"1","Draht",basis,null,null,LocalDate.now().plusDays(3),List.of(),List.of());
   offerVersion.addPosition(line);offerVersion.addKosten(new AngebotKostenbestandteil(offerVersion,line,"material","MATERIAL",new BigDecimal("10"),"KG",BigDecimal.ONE,null,false,false,"Dummy Angebot"));
   offerVersion.bestaetigen(9L);offer.addVersion(offerVersion);em.persist(offerVersion);em.flush();
   return List.of(offerVersion.getId(),request.getId(),need.getVersion());
  });
  var comparison=mock(EinkaufVergleichService.class);
  when(comparison.vergleiche(eq(ids.get(1)),any())).thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVergleichDto.Vergleich(ids.get(1),LocalDate.now(),List.of(
   new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVergleichDto.AngebotSumme(ids.getFirst(),new BigDecimal("40"),true,true,true,List.of(),List.of())),ids.getFirst()));
  var numbers=mock(DokumentnummerService.class);when(numbers.naechsteEinkaufsnummer(any(),any())).thenReturn("B-FREITEXT-"+needId);
  var service=new EinkaufBestellungService(orders,revisions,offers,comparison,needs,amounts,numbers,new EinkaufAuditService(audits),json,mock(LieferantenArtikelPreiseRepository.class));
  var result=tx(()->service.ausAngebot(new AusAngebot(ids.getFirst(),List.of(new Herkunft(needId,ids.get(2),new BigDecimal("4"))),"Dummy Angebot geprüft",UUID.randomUUID()),9L));
  var line=result.revisionen().getFirst().positionen().getFirst();
  assertEquals(Positionsart.FREITEXT,line.snapshot().art());
  assertEquals(0,line.nettoEinzelpreis().compareTo(new BigDecimal("10")));
  accept(result.id());assertAmount(result.id(),"4","0","0");
 }

 @Test void freierBedarfWerkstattUndVersandOhnePreisBewahrenMengenUndNull() {
  tx(() -> {
   var need=needs.findById(needId).orElseThrow();
   var position=new PositionSnapshot(Positionsart.FREITEXT,null,null,null,null,"Schweißdraht",null,null,
    new Mengenbasis(new BigDecimal("10"),Einheit.KILOGRAMM,null,null,null,null),null,null,null,null,null,List.of(),List.of());
   need.setPosition(position);need.setBedarfMenge(new BigDecimal("10"));return null;
  });
  var workshop=new EinkaufWerkstattService(needs,new EinkaufAuditService(audits),json);
  long before=needs.findById(needId).orElseThrow().getVersion();
  tx(()->workshop.pruefen(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto.Werkstattpruefung(List.of(
   new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto.Werkstattposition(needId,before,new BigDecimal("4")))),9L));
  var need=needs.findById(needId).orElseThrow();assertTrue(need.getVersion()>before);
  var request=new Direkt(supplierId,recipient,List.of(new Herkunft(needId,need.getVersion(),new BigDecimal("6"))),List.of(),null,null,null,UUID.randomUUID());
  Long id=tx(()->ordering.direkt(request,9L).id());
  assertNull(tx(()->ordering.lade(id)).revisionen().getFirst().positionen().getFirst().nettoEinzelpreis());
  assertEquals(0,needs.findById(needId).orElseThrow().disponierbar().signum());
  accept(id);
  assertAmount(id,"6","0","0");
  var captor=org.mockito.ArgumentCaptor.forClass(org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.Beleg.class);
  verify(pdf).erzeugen(captor.capture());
  assertNull(captor.getValue().nettoSumme());
  assertNull(captor.getValue().positionen().getFirst().nettoSumme());
  assertEquals(0,captor.getValue().positionen().getFirst().technik().basis().menge().compareTo(new BigDecimal("6")));
  var current=needs.findById(needId).orElseThrow();
  var amendment=new Direkt(supplierId,recipient,List.of(new Herkunft(needId,current.getVersion(),new BigDecimal("6"))),
   List.of(),LocalDate.now().plusDays(3),null,"Neuer Liefertermin",UUID.randomUUID());
  var amended=tx(()->ordering.aendern(id,new Aenderung(version(id),amendment,"Liefertermin angepasst"),9L));
  assertNull(amended.revisionen().getLast().positionen().getFirst().nettoEinzelpreis());
  accept(id);
  assertAmount(id,"6","0","0");
 }

 @Test void revisionTeilstornoUndLieferungNutzenNurEigeneMengenUndBewahrenSnapshots() {
  Long a=tx(()->ordering.direkt(content("10"),9L).id());
  var draft=tx(()->ordering.lade(a));
  assertEquals("DIREKT",draft.revisionen().getFirst().snapshot().get("typ"));
  assertEquals("frei Haus",draft.revisionen().getFirst().snapshot().get("bedingungen"));
  accept(a);
  var firstAccepted=tx(()->revisions.findByBestellung_IdOrderByNummerAsc(a).stream().filter(BestellungRevision::istAngenommen).findFirst().orElseThrow());
  var firstCertificates=tx(()->certificates.findByRevision_IdOrderByIdAsc(firstAccepted.getId()));
  assertEquals(1,firstCertificates.size());
  assertEquals(LocalDate.now().plusDays(7),firstCertificates.getFirst().getFrist());
  assertEquals(EinkaufZeugnisErwartung.Status.ANGEFORDERT,firstCertificates.getFirst().getStatus());
  var rendered=org.mockito.ArgumentCaptor.forClass(org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.Beleg.class);
  verify(pdf).erzeugen(rendered.capture());assertEquals("frei Haus",rendered.getValue().bedingungen());
  Long b=tx(()->ordering.direkt(content("10"),9L).id());accept(b);
  tx(()->ordering.aendern(a,new Aenderung(version(a),content("12"),"Zwei zusätzliche Profile"),9L));
  assertAmount(a,"10","2","0");accept(a);assertAmount(a,"12","0","0");
  var revisionsAfter=tx(()->ordering.lade(a).revisionen());
  assertEquals(new BigDecimal("10.000000"),revisionsAfter.getFirst().positionen().getFirst().menge());
  assertEquals("AENDERUNG",revisionsAfter.getLast().snapshot().get("typ"));
  Long slip=proof(LieferantDokumentTyp.LIEFERSCHEIN);
  deliver(a,slip,"4");assertAmount(a,"12","0","4");
  tx(()->ordering.aendern(a,new Aenderung(version(a),content("10"),"Zwei entfallen nach Bestätigung"),9L));
  accept(a);assertAmount(a,"12","0","4"); // Reductions wait for supplier evidence.
  Long credit=proof(LieferantDokumentTyp.GUTSCHRIFT);
  Storno partial=tx(()->new Storno(version(a),List.of(new Herkunft(needId,needs.findById(needId).orElseThrow().getVersion(),new BigDecimal("2"))),credit,"Teilmenge bestätigt",UUID.randomUUID()));
  tx(()->approval.stornoBestaetigen(a,partial,9L));
  assertAmount(a,"10","0","4");assertAmount(b,"10","0","0");
  assertEquals(BestellungStatus.TEILGELIEFERT,tx(()->ordering.lade(a).status()));
  tx(()->approval.stornoBestaetigen(a,partial,9L));assertAmount(a,"10","0","4");
  Long overCredit=proof(LieferantDokumentTyp.GUTSCHRIFT);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->approval.stornoBestaetigen(a,
   new Storno(version(a),List.of(new Herkunft(needId,0,new BigDecimal("7"))),overCredit,"Fremde Menge",UUID.randomUUID()),9L)));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->deliver(b,slip,"1"));
  deliver(a,proof(LieferantDokumentTyp.LIEFERSCHEIN),"6");
  assertEquals(BestellungStatus.GELIEFERT,tx(()->ordering.lade(a).status()));
  assertAmount(b,"10","0","0");
  Long confirmation=proof(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
  tx(()->delivery.bestaetigungErfassen(b,new Bestaetigung(confirmation,LocalDate.now(),null,List.of()),9L));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->delivery.bestaetigungErfassen(b,
   new Bestaetigung(confirmation,LocalDate.now(),null,List.of()),9L)));
 }

 @Test void vollstaendigeLieferungDerAltenFassungBlockiertOffeneAenderungNicht() {
  Long id=tx(()->ordering.direkt(content("10"),9L).id());accept(id);
  tx(()->ordering.aendern(id,new Aenderung(version(id),content("12"),"Zusatzbedarf"),9L));
  deliver(id,proof(LieferantDokumentTyp.LIEFERSCHEIN),"10");
  assertAmount(id,"10","2","10");
  assertEquals(BestellungStatus.TEILGELIEFERT,tx(()->ordering.lade(id).status()));
  accept(id);assertAmount(id,"12","0","10");
  deliver(id,proof(LieferantDokumentTyp.LIEFERSCHEIN),"2");
  assertEquals(BestellungStatus.GELIEFERT,tx(()->ordering.lade(id).status()));
 }

 @Test void unveroeffentlichteAenderungKannNachLieferungVerworfenWerden() {
  Long id=tx(()->ordering.direkt(content("10"),9L).id());accept(id);
  tx(()->ordering.aendern(id,new Aenderung(version(id),content("12"),"Zusatzbedarf"),9L));
  var stalePreview=tx(()->approval.vorschau(id,4L));
  deliver(id,proof(LieferantDokumentTyp.LIEFERSCHEIN),"10");
  tx(()->{ordering.verwerfen(id,version(id),9L);return null;});
  assertAmount(id,"10","0","10");
  assertEquals(BestellungStatus.GELIEFERT,tx(()->ordering.lade(id).status()));
  assertTrue(tx(()->ordering.lade(id).revisionen().getLast().verworfen()));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->approval.freigeben(id,
   new Freigabe(version(id),stalePreview.vorschauHash(),UUID.randomUUID()),9L)));
 }

 @Test void verwerfenBewahrtAlteBestellungUndVerhindertAbbruchNachMailfreigabe() {
  Long id=tx(()->ordering.direkt(content("10"),9L).id());accept(id);
  tx(()->ordering.aendern(id,new Aenderung(version(id),content("12"),"Zusatzbedarf"),9L));
  tx(()->{ordering.verwerfen(id,version(id),9L);return null;});
  assertAmount(id,"10","0","0");
  tx(()->ordering.aendern(id,new Aenderung(version(id),content("11"),"Neuer Zusatzbedarf"),9L));
  var preview=tx(()->approval.vorschau(id,4L));
  tx(()->approval.freigeben(id,new Freigabe(version(id),preview.vorschauHash(),UUID.randomUUID()),9L));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->{ordering.verwerfen(id,version(id),9L);return null;}));
  assertAmount(id,"10","1","0");
  Long draft=tx(()->ordering.direkt(content("2"),9L).id());
  tx(()->{ordering.verwerfen(draft,version(draft),9L);return null;});
  assertAmount(draft,"0","0","0");
  assertEquals(BestellungStatus.STORNIERT,tx(()->ordering.lade(draft).status()));
 }

 @Test void dokumentierteStornobestaetigungBrauchtKeineGutschrift() {
  Long id=tx(()->ordering.direkt(content("10"),9L).id());accept(id);
  Long wrong=proof(LieferantDokumentTyp.RECHNUNG);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->approval.stornoBestaetigen(id,
   new Storno(version(id),List.of(new Herkunft(needId,0,BigDecimal.ONE)),wrong,"Nicht bestätigt",UUID.randomUUID()),9L)));
  Long confirmation=proof(LieferantDokumentTyp.SONSTIG);
  var request=tx(()->new Storno(version(id),List.of(new Herkunft(needId,0,BigDecimal.TEN)),confirmation,
   "Lieferant bestätigt vollständigen Storno per E-Mail vor Rechnungsstellung",UUID.randomUUID()));
  tx(()->approval.stornoBestaetigen(id,request,9L));
  tx(()->approval.stornoBestaetigen(id,request,9L));
  assertAmount(id,"0","0","0");
  assertEquals(BestellungStatus.STORNIERT,tx(()->ordering.lade(id).status()));
  Long other=tx(()->ordering.direkt(content("10"),9L).id());accept(other);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->approval.stornoBestaetigen(other,
   new Storno(version(other),List.of(new Herkunft(needId,0,BigDecimal.ONE)),confirmation,"Wiederverwendung",UUID.randomUUID()),9L)));
  assertAmount(other,"10","0","0");
 }

 @Test void externerVersandSpeichertEigenenNachweisStattEinerFalschenOutboxId() {
  Long id=tx(()->ordering.direkt(content("2"),9L).id());
  when(files.pruefeExternenVersandbeleg(999L,supplierId,id)).thenThrow(new IllegalArgumentException("Beleg fehlt"));
  assertThrows(IllegalArgumentException.class,()->tx(()->{
   approval.externGesendet(id,new ExternerNachweis(version(id),Instant.now(),999L,"Dummy",UUID.randomUUID()),9L);return null;
  }));
  assertAmount(id,"0","2","0");
  when(files.pruefeExternenVersandbeleg(123L,supplierId,id)).thenReturn(Map.of("dateiId",123L,"lieferantDokumentId",456L,"sha256","a".repeat(64)));
  var evidence=new ExternerNachweis(tx(()->version(id)),Instant.now().minusSeconds(10),123L,"Übermittlung mit Beleg geprüft",UUID.randomUUID());
  tx(()->{approval.externGesendet(id,evidence,9L);return null;});
  tx(()->{approval.externGesendet(id,evidence,9L);return null;});
  assertAmount(id,"2","0","0");
  tx(()->{var revision=revisions.findFirstByBestellung_IdOrderByNummerDesc(id).orElseThrow();
   assertNull(revision.getVersandId());assertTrue(revision.istAngenommen());
  assertEquals(123,((Number)revision.getExternerNachweis().get("dateiId")).intValue());return null;});
  var expected=tx(()->certificates.findByRevision_IdOrderByIdAsc(revisions.findFirstByBestellung_IdOrderByNummerDesc(id).orElseThrow().getId()));
  assertEquals(1,expected.size());
 }

 @Test void angenommeneBestellungOhneLieferterminErzeugtKlaerungsfallMitIdempotentemErwartungseintrag(){
  var withNoDeliveryDate=content("2");
  var request=new Direkt(withNoDeliveryDate.lieferantId(),withNoDeliveryDate.empfaenger(),withNoDeliveryDate.paket(),withNoDeliveryDate.preise(),
    null,withNoDeliveryDate.bestaetigungsfrist(),withNoDeliveryDate.bedingungen(),UUID.randomUUID());
  Long id=tx(()->ordering.direkt(request,9L).id());
  when(files.pruefeExternenVersandbeleg(231L,supplierId,id)).thenReturn(Map.of("dateiId",231L,"lieferantDokumentId",456L,"sha256","b".repeat(64)));
  var evidence=new ExternerNachweis(tx(()->version(id)),Instant.now().minusSeconds(5),231L,"Dummy Versandbeleg",UUID.randomUUID());
  tx(()->{approval.externGesendet(id,evidence,9L);return null;});
  tx(()->{approval.externGesendet(id,evidence,9L);return null;});
  var accepted=tx(()->revisions.findFirstByBestellung_IdOrderByNummerDesc(id).orElseThrow());
  var expected=tx(()->certificates.findByRevision_IdOrderByIdAsc(accepted.getId()));
  assertEquals(1,expected.size());
  assertNull(expected.getFirst().getFrist());
  assertEquals(EinkaufZeugnisErwartung.Status.KLAERUNG_NOETIG,expected.getFirst().getStatus());
 }

 @Test void zeugnisWorkflowOrdnetLieferpositionChargeAppendOnlyPruefungUndMaterialfreigabeZu(){
  Long id=tx(()->ordering.direkt(content("2"),9L).id());accept(id);
  var expectation=tx(()->certificates.findByRevision_IdOrderByIdAsc(revisions.findFirstByBestellung_IdOrderByNummerDesc(id).orElseThrow().getId()).getFirst());
  Long deliveryDocument=proof(LieferantDokumentTyp.LIEFERSCHEIN);deliver(id,deliveryDocument,"2");
  var deliveryPosition=tx(()->deliveries.findByBestellung_IdOrderByEingangAsc(id).getFirst().getPositionen().getFirst());
  var charge=tx(()->em.createQuery("select c from EinkaufCharge c where c.position.id=:id",EinkaufCharge.class).setParameter("id",deliveryPosition.getId()).getSingleResult());
  Long certificateDocument=tx(()->{var document=new LieferantDokument();document.setLieferant(em.find(Lieferanten.class,supplierId));document.setTyp(LieferantDokumentTyp.SONSTIG);document.setOriginalDateiname("zeugnis.pdf");em.persist(document);em.flush();
   var file=new EinkaufDatei("c".repeat(64),null,"zeugnis.pdf","application/pdf",12,null,document.getId());em.persist(file);em.flush();return file.getId();});
  var arrived=tx(()->certificateService.eingang(expectation.getId(),certificateDocument));
  assertEquals(EinkaufZeugnisErwartung.Status.EINGEGANGEN,arrived.status());
  var assignment=tx(()->certificateService.zuordnen(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufZeugnisDto.Zuordnung(
    certificateDocument,List.of(expectation.getId()),List.of(deliveryPosition.getId()),List.of(charge.getId()),null),9L));
  assertFalse(assignment.klaerungNoetig());
  assertEquals(EinkaufZeugnisErwartung.Status.ZUGEORDNET,assignment.erwartungen().getFirst().status());
  var reload = tx(()->certificateService.liste(id)).getFirst();
  var reloadJson = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(reload);
  assertEquals(assignment.chargen().getFirst().zuordnungId().longValue(), reloadJson.path("zuordnungen").path(0).path("zuordnungId").asLong());
  assertEquals(assignment.chargen().getFirst().version(), reloadJson.path("zuordnungen").path(0).path("version").asLong());
  var reviewed=tx(()->certificateService.pruefen(assignment.chargen().getFirst().zuordnungId(),new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufZeugnisDto.Pruefung(
    assignment.chargen().getFirst().version(),"BESTANDEN","Dummy-Prüfung bestätigt","DUMMY-SPEC-1"),9L));
  assertTrue(reviewed.materialFreigegeben());
  var persisted=tx(()->certificates.findById(expectation.getId()).orElseThrow());
  assertEquals(EinkaufZeugnisErwartung.Status.GEPRUEFT,persisted.getStatus());
  assertTrue(persisted.isMaterialFreigegeben());
  assertEquals(1,tx(()->certificates.findById(expectation.getId()).orElseThrow().getPruefungen().size()));
  assertEquals(1,tx(()->em.createQuery("select count(z) from EinkaufZeugnisZuordnung z where z.zeugnis.id=:id",Long.class).setParameter("id",expectation.getId()).getSingleResult()));
 }

 @Test void separateTeilzeugnisseGebenNurIhreChargeFreiUndSpaetererEingangBleibtMoeglich(){
  Long id=tx(()->ordering.direkt(content("2"),9L).id());accept(id);
  var expectation=tx(()->certificates.findByRevision_IdOrderByIdAsc(revisions.findFirstByBestellung_IdOrderByNummerDesc(id).orElseThrow().getId()).getFirst());
  Long slipA=proof(LieferantDokumentTyp.LIEFERSCHEIN);deliver(id,slipA,"1","DUMMY-CHARGE-A");
  var deliveryA=tx(()->deliveries.findByBestellung_IdOrderByEingangAsc(id).getFirst().getPositionen().getFirst());
  var chargeA=tx(()->em.createQuery("select c from EinkaufCharge c where c.position.id=:id",EinkaufCharge.class).setParameter("id",deliveryA.getId()).getSingleResult());
  Long pdfA=certificateFile("zeugnis-a.pdf");
  tx(()->certificateService.eingang(expectation.getId(),pdfA));
  var assignedA=tx(()->certificateService.zuordnen(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufZeugnisDto.Zuordnung(pdfA,List.of(expectation.getId()),List.of(deliveryA.getId()),List.of(chargeA.getId()),null),9L));
  assertFalse(assignedA.klaerungNoetig());
  assertTrue(tx(()->em.createQuery("select count(z) from EinkaufZeugnisZuordnung z where z.zeugnis.id=:id and z.klaerungNoetig=true",Long.class).setParameter("id",expectation.getId()).getSingleResult()==0));
  var reviewedA=tx(()->certificateService.pruefen(assignedA.chargen().getFirst().zuordnungId(),new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufZeugnisDto.Pruefung(assignedA.chargen().getFirst().version(),"BESTANDEN","Charge A geprüft","DUMMY-SPEC-1"),9L));
  assertTrue(reviewedA.materialFreigegeben());
  assertEquals(1,tx(()->em.createQuery("select count(s) from EinkaufZeugnisChargeStatus s where s.zeugnis.id=:id and s.charge.id=:charge and s.status=:done and s.materialFreigegeben=true",Long.class)
    .setParameter("id",expectation.getId()).setParameter("charge",chargeA.getId()).setParameter("done",EinkaufZeugnisErwartung.Status.GEPRUEFT).getSingleResult()));

  Long slipB=proof(LieferantDokumentTyp.LIEFERSCHEIN);deliver(id,slipB,"1","DUMMY-CHARGE-B");
  var deliveryB=tx(()->deliveries.findByBestellung_IdOrderByEingangAsc(id).getLast().getPositionen().getFirst());
  var chargeB=tx(()->em.createQuery("select c from EinkaufCharge c where c.position.id=:id",EinkaufCharge.class).setParameter("id",deliveryB.getId()).getSingleResult());
  // A newly delivered charge must be actionable without a certificate GET materializing its status.
  var deadlineClock=Clock.fixed(LocalDate.now().plusDays(8).atStartOfDay(ZoneOffset.UTC).toInstant(),ZoneOffset.UTC);
  var reminderTemplates=mock(EinkaufVorlagenService.class);
  when(reminderTemplates.rendern(any(),any())).thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.Gerendert(4L,1,"Dummy Nachfrage","<p>Dummy</p>","hash"));
  tx(()->{var template=new EmailTextTemplate();template.setDokumentTyp("EINKAUF_ZEUGNIS_NACHFORDERUNG");template.setName("Dummy Zeugnisnachfrage");template.setSubjectTemplate("Dummy");template.setHtmlBody("<p>Dummy</p>");template.setStandard(true);em.persist(template);return null;});
  var deadlines=new EinkaufFaelligkeitService(em,reminderTemplates,deadlineClock);
  assertEquals(EinkaufZeugnisErwartung.Status.GEPRUEFT,tx(()->certificates.findById(expectation.getId()).orElseThrow().getStatus()));
  assertTrue(tx(()->deadlines.liste(LocalDate.now(deadlineClock),9L,org.springframework.data.domain.PageRequest.of(0,200)))
    .stream().anyMatch(row->row.typ().equals("ZEUGNIS")&&row.vorgangId().equals(id)));
  var reminder=tx(()->deadlines.nachfrage("ZEUGNIS",id,null));
  assertEquals(1,reminder.fehlendeNachweise().size());
  assertTrue(reminder.fehlendeNachweise().getFirst().contains("DUMMY-CHARGE-B"));
  assertFalse(reminder.fehlendeNachweise().getFirst().contains("DUMMY-CHARGE-A"));
  var afterDeliveryB=tx(()->certificateService.liste(id).getFirst());
  assertEquals(EinkaufZeugnisErwartung.Status.EINGEGANGEN,afterDeliveryB.status());
  assertFalse(afterDeliveryB.materialFreigegeben());
  assertEquals(EinkaufZeugnisErwartung.Status.ERWARTET,afterDeliveryB.chargeStaende().stream().filter(s->s.chargeId().equals(chargeB.getId())).findFirst().orElseThrow().status());
  assertTrue(afterDeliveryB.chargeStaende().stream().filter(s->s.chargeId().equals(chargeA.getId())).findFirst().orElseThrow().materialFreigegeben());
  assertEquals(1,tx(()->em.createQuery("select count(s) from EinkaufZeugnisChargeStatus s where s.zeugnis.id=:id and s.charge.id=:charge and s.status=:open and s.materialFreigegeben=false",Long.class)
    .setParameter("id",expectation.getId()).setParameter("charge",chargeB.getId()).setParameter("open",EinkaufZeugnisErwartung.Status.ERWARTET).getSingleResult()));
  Long pdfB=certificateFile("zeugnis-b.pdf");
  var arrivedB=tx(()->certificateService.eingang(expectation.getId(),pdfB));
  assertEquals(EinkaufZeugnisErwartung.Status.EINGEGANGEN,arrivedB.status());
  var assignedB=tx(()->certificateService.zuordnen(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufZeugnisDto.Zuordnung(pdfB,List.of(expectation.getId()),List.of(deliveryB.getId()),List.of(chargeB.getId()),null),9L));
  assertFalse(assignedB.klaerungNoetig());
  var reviewedB=tx(()->certificateService.pruefen(assignedB.chargen().getFirst().zuordnungId(),new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufZeugnisDto.Pruefung(assignedB.chargen().getFirst().version(),"BESTANDEN","Charge B geprüft","DUMMY-SPEC-1"),9L));
  assertTrue(reviewedB.materialFreigegeben());
  long currentAVersion=tx(()->em.createQuery("select z.chargeStatus.version from EinkaufZeugnisZuordnung z where z.id=:id",Long.class).setParameter("id",assignedA.chargen().getFirst().zuordnungId()).getSingleResult());
  var reviewedAgainA=tx(()->certificateService.pruefen(assignedA.chargen().getFirst().zuordnungId(),new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufZeugnisDto.Pruefung(currentAVersion,"BESTANDEN","Charge A erneut geprüft","DUMMY-SPEC-1"),9L));
  assertTrue(reviewedAgainA.materialFreigegeben());
  assertEquals(2,tx(()->em.createQuery("select count(z) from EinkaufZeugnisZuordnung z where z.zeugnis.id=:id",Long.class).setParameter("id",expectation.getId()).getSingleResult()));
  assertEquals(3,tx(()->certificates.findById(expectation.getId()).orElseThrow().getPruefungen().size()));
  assertEquals(2,tx(()->em.createQuery("select count(s) from EinkaufZeugnisChargeStatus s where s.zeugnis.id=:id and s.status=:done and s.materialFreigegeben=true",Long.class).setParameter("id",expectation.getId()).setParameter("done",EinkaufZeugnisErwartung.Status.GEPRUEFT).getSingleResult()));
 }

 @Test void zeugnisAusAndererBestellungDesselbenLieferantenWirdAbgelehnt(){
  Long first=tx(()->ordering.direkt(content("2"),9L).id());accept(first);
  Long second=tx(()->ordering.direkt(content("2"),9L).id());accept(second);
  var expected=tx(()->certificates.findByRevision_IdOrderByIdAsc(revisions.findFirstByBestellung_IdOrderByNummerDesc(second).orElseThrow().getId()).getFirst());
  Long file=certificateFile("fremde-bestellung.pdf");
  tx(()->{em.find(LieferantDokument.class,purchaseFiles.findById(file).orElseThrow().getLieferantDokumentId()).setEinkaufBestellungId(first);return null;});
  assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->certificateService.eingang(expected.getId(),file))).getStatusCode().value());
  // Existing associations must also be checked, including ones imported before the guard existed.
  tx(()->{certificates.findById(expected.getId()).orElseThrow().eingegangen(purchaseFiles.findById(file).orElseThrow(),Instant.now());return null;});
  deliver(second,proof(LieferantDokumentTyp.LIEFERSCHEIN),"1");
  var line=tx(()->deliveries.findByBestellung_IdOrderByEingangAsc(second).getFirst().getPositionen().getFirst());
  var charge=tx(()->em.createQuery("select c from EinkaufCharge c where c.position.id=:id",EinkaufCharge.class).setParameter("id",line.getId()).getSingleResult());
  assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->certificateService.zuordnen(
    new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufZeugnisDto.Zuordnung(file,List.of(expected.getId()),List.of(line.getId()),List.of(charge.getId()),null),9L))).getStatusCode().value());
  assertEquals(0L,tx(()->em.createQuery("select count(z) from EinkaufZeugnisZuordnung z where z.zeugnis.id=:id",Long.class).setParameter("id",expected.getId()).getSingleResult()));
  Long unbound=certificateFile("neuer-beleg.pdf");
  tx(()->certificateService.eingang(expected.getId(),unbound));
  assertEquals(second,tx(()->em.find(LieferantDokument.class,purchaseFiles.findById(unbound).orElseThrow().getLieferantDokumentId()).getEinkaufBestellungId()));
  var firstExpected=tx(()->certificates.findByRevision_IdOrderByIdAsc(revisions.findFirstByBestellung_IdOrderByNummerDesc(first).orElseThrow().getId()).getFirst());
  assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->certificateService.eingang(firstExpected.getId(),unbound))).getStatusCode().value());
  Long foreign=certificateFile("fremder-lieferant.pdf");
  tx(()->{var other=new Lieferanten();other.setLieferantenname("Dummy Fremdlieferant "+UUID.randomUUID());em.persist(other);em.find(LieferantDokument.class,purchaseFiles.findById(foreign).orElseThrow().getLieferantDokumentId()).setLieferant(other);return null;});
  assertEquals(400,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->certificateService.eingang(expected.getId(),foreign))).getStatusCode().value());
 }

 @Test void gleichzeitigeAenderungUndLieferungSperrenBedarfVorBestellkopf() throws Exception {
  Long id=tx(()->ordering.direkt(content("10"),9L).id());accept(id);
  Long slip=proof(LieferantDokumentTyp.LIEFERSCHEIN);
  var amendment=tx(()->new Aenderung(version(id),content("12"),"Zusatzbedarf"));
  var receipt=tx(()->new Annahme(version(id),slip,Instant.now(),List.of(new Lieferanteil(
   ordering.lade(id).revisionen().getLast().positionen().getFirst().id(),BigDecimal.ONE,"DUMMY",null,
   List.of(new Herkunft(needId,needs.findById(needId).orElseThrow().getVersion(),BigDecimal.ONE)))),UUID.randomUUID()));
  var start=new java.util.concurrent.CyclicBarrier(2);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
   var jobs=new ArrayList<java.util.concurrent.Future<Boolean>>();
   for(boolean change:List.of(true,false))jobs.add(pool.submit(()->{
    try{return tx(()->{
     try{start.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception error){throw new IllegalStateException(error);}
     if(change)ordering.aendern(id,amendment,9L);else delivery.annehmen(id,receipt,9L);
     return true;
    });}catch(org.springframework.web.server.ResponseStatusException conflict){assertEquals(409,conflict.getStatusCode().value());return false;}
   }));
   int winners=0;for(var job:jobs)if(job.get(20,java.util.concurrent.TimeUnit.SECONDS))winners++;
   assertEquals(1,winners);
  }
  var balance=tx(()->amounts.standFuerVorgang("BESTELLUNG:"+id).get(needId));
  assertEquals(0,BigDecimal.TEN.compareTo(balance.bestellt()));
  assertTrue(balance.reserviert().compareTo(new BigDecimal("2"))<=0);
  assertTrue(balance.geliefert().compareTo(BigDecimal.ONE)<=0);
 }

 @Test void leseseitenZeigenPersistierteLieferungenChargenBestaetigungenUndEigeneMengen() {
  Long id=tx(()->ordering.direkt(content("4"),9L).id());
  assertNull(tx(()->ordering.lade(id).revisionen().getLast().angenommenAm()));
  var preview=tx(()->approval.vorschau(id,4L));
  assertEquals(1,preview.revisionsNummer());
  assertEquals(LocalDate.now().plusDays(7),preview.liefertermin());
  accept(id);
  Long other=tx(()->ordering.direkt(content("2"),9L).id());accept(other);
  deliver(id,proof(LieferantDokumentTyp.LIEFERSCHEIN),"2","CHARGE-A");
  deliver(id,proof(LieferantDokumentTyp.LIEFERSCHEIN),"2","CHARGE-B");
  var reader=new EinkaufBestellstatusService(orders,revisions,deliveries,amounts,dispatches,em,json);
  var rows=tx(()->reader.lieferungen(id));
  assertEquals(2,rows.size());
  assertEquals(List.of("CHARGE-A","CHARGE-B"),rows.stream().map(r->r.positionen().getFirst().charge()).toList());
  for(var row:rows){assertEquals(id,row.bestellungId());assertNotNull(row.positionen().getFirst().id());
   var part=row.positionen().getFirst();assertEquals(1,part.chargen().size());assertNotNull(part.chargen().getFirst().id());assertEquals(part.charge(),part.chargen().getFirst().kennung());}
  assertTrue(tx(()->reader.lieferungen(other)).isEmpty());
  var balance=tx(()->reader.mengen(id)).getFirst();
  assertEquals(needId,balance.bedarfId());assertEquals(0,new BigDecimal("4").compareTo(balance.bestellt()));
  assertEquals(0,new BigDecimal("4").compareTo(balance.geliefert()));assertEquals(0,balance.offen().signum());
  Long line=tx(()->ordering.lade(id).revisionen().getLast().positionen().getFirst().id());
  var ab=new Bestaetigung(proof(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG),LocalDate.now(),LocalDate.now().plusDays(3),List.of(new BestaetigtePosition(line,new BigDecimal("4"),BigDecimal.ONE,null)));
  var saved=tx(()->delivery.bestaetigungErfassen(id,ab,9L));
  var loaded=tx(()->reader.bestaetigungen(id));assertEquals(1,loaded.size());assertEquals(saved.id(),loaded.getFirst().id());
  assertEquals(ab.datum(),loaded.getFirst().datum());assertEquals(ab.liefertermin(),loaded.getFirst().liefertermin());assertEquals(ab.positionen(),loaded.getFirst().positionen());
  assertTrue(tx(()->reader.bestaetigungen(other)).isEmpty());
  var accepted=tx(()->ordering.lade(id).revisionen().getLast());assertNotNull(accepted.angenommenAm());
  assertEquals("ANGENOMMEN",tx(()->reader.versandstatus(id,accepted.id())).getFirst().status());
  assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class,()->tx(()->reader.versandstatus(other,accepted.id())));
 }

 @Test void leseseitenUnterscheidenFehlendeBestellungVonLeeremVerlauf() {
  var reader=new EinkaufBestellstatusService(orders,revisions,deliveries,amounts,dispatches,em,json);
  assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class,()->tx(()->reader.lieferungen(Long.MAX_VALUE)));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->reader.mengen(0L)));
  Long id=tx(()->ordering.direkt(content("1"),9L).id());
  assertTrue(tx(()->reader.bestaetigungen(id)).isEmpty());
  assertTrue(tx(()->reader.versandstatus(id,ordering.lade(id).revisionen().getLast().id())).isEmpty());
 }

 @Test void unklarerBestellversandWirdBelegtUndOhneZweitenVersandAktiviert() {
  Long id=tx(()->ordering.direkt(content("4"),9L).id());
  var queued=prepareUnknown(id);
  Long other=tx(()->ordering.direkt(content("2"),9L).id());
  var request=new Klaerung(dispatches.findById(queued.id()).orElseThrow().getVersion(),Entscheidung.BEREITS_ANGENOMMEN,"Lieferant hat den Eingang bestätigt");
  assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class,()->tx(()->{approval.versandKlaeren(other,queued.id(),request,9L);return null;}));
  assertAmount(id,"0","4","0");
  tx(()->{approval.versandKlaeren(id,queued.id(),request,9L);return null;});
  assertAmount(id,"4","0","0");
  var event=acceptances.findAll().stream().filter(e->queued.id().equals(e.getVersandauftragId())).findFirst().orElseThrow();
  assertNotNull(event.getVerarbeitetAm());
  assertThrows(IllegalStateException.class,()->tx(()->{approval.versandKlaeren(id,queued.id(),request,9L);return null;}));
  assertAmount(id,"4","0","0");
 }

 @Test void nurSicherFehlgeschlagenerEigenerBestellversandKannErneutVorbereitetWerden() {
  Long id=tx(()->ordering.direkt(content("4"),9L).id());var queued=prepareUnknown(id);
  long unknownVersion=dispatches.findById(queued.id()).orElseThrow().getVersion();
  assertThrows(IllegalStateException.class,()->tx(()->approval.erneutSenden(id,queued.id(),unknownVersion,9L)));
  tx(()->{approval.versandKlaeren(id,queued.id(),new Klaerung(unknownVersion,Entscheidung.NACHWEISLICH_NICHT_GESENDET,"Eingang beim Lieferanten nachweislich ausgeschlossen"),9L);return null;});
  assertAmount(id,"0","4","0");
  long failedVersion=dispatches.findById(queued.id()).orElseThrow().getVersion();
  assertThrows(IllegalStateException.class,()->tx(()->approval.erneutSenden(id,queued.id(),unknownVersion,9L)));
  var retried=tx(()->approval.erneutSenden(id,queued.id(),failedVersion,9L));
  assertEquals(queued.id(),retried.id());assertEquals("VORBEREITET",retried.status());assertAmount(id,"0","4","0");
 }

 @Test void fehlgeschlageneFachlicheKlaerungRolltVersandannahmeUndEreignisZurueck() {
  Long id=tx(()->ordering.direkt(content("4"),9L).id());var queued=prepareUnknown(id);
  long unknownVersion=dispatches.findById(queued.id()).orElseThrow().getVersion();
  var failing=spy(approval);doThrow(new IllegalStateException("DUMMY-DATENBANKFEHLER")).when(failing).versandAngenommen(any());
  assertThrows(IllegalStateException.class,()->tx(()->{failing.versandKlaeren(id,queued.id(),new Klaerung(unknownVersion,Entscheidung.BEREITS_ANGENOMMEN,"Bestätigter Eingang"),9L);return null;}));
  assertEquals(EinkaufVersandauftrag.Status.UNKLAR,dispatches.findById(queued.id()).orElseThrow().getStatus());
  assertTrue(acceptances.findAll().stream().noneMatch(e->queued.id().equals(e.getVersandauftragId())));
  assertAmount(id,"0","4","0");
 }

 @Test void stornoanfrageVersendetGeprueftenInhaltOhneMengenfreigabeUndOhneDoppelsendung() {
  Long id=tx(()->ordering.direkt(content("4"),9L).id());accept(id);
  var request=new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufStornoanfrageDto.Entwurf(version(id),List.of(new Herkunft(needId,0,new BigDecimal("2"))),"Dummy <script>alert(1)</script>");
  var preview=tx(()->stornoRequests.vorschau(id,request,9L));
  assertTrue(preview.htmlBody().contains("&lt;script&gt;"));assertFalse(preview.htmlBody().contains("<script>"));
  assertAmount(id,"4","0","0");
  var approved=new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufStornoanfrageDto.Freigabe(preview.version(),preview.vorschauId(),preview.vorschauHash(),UUID.randomUUID());
  assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class,()->tx(()->stornoRequests.freigeben(id,approved,8L)));
  var sent=tx(()->stornoRequests.freigeben(id,approved,9L));assertEquals("STORNO_ANFRAGE",sent.typ());
  assertEquals(sent.id(),tx(()->stornoRequests.freigeben(id,approved,9L)).id());
  assertThrows(IllegalStateException.class,()->tx(()->stornoRequests.freigeben(id,new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufStornoanfrageDto.Freigabe(preview.version(),preview.vorschauId(),preview.vorschauHash(),UUID.randomUUID()),9L)));
  assertNotNull(outbox.beanspruche(sent.id()));
  outbox.abgeschlossen(sent.id(),new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Versandergebnis(org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Status.ANGENOMMEN,null,null,"%PDF-DUMMY".getBytes()));
  outbox.verarbeiteOffeneAnnahmeereignisse(new EinkaufStornoanfrageAnnahmeListener(dispatches),100);
  assertAmount(id,"4","0","0");
  assertEquals(1,tx(()->stornoRequests.status(id)).size());
  assertEquals("ANGENOMMEN",tx(()->stornoRequests.status(id)).getFirst().status());
  assertTrue(acceptances.findAll().stream().filter(e->sent.id().equals(e.getVersandauftragId())).allMatch(e->e.getVerarbeitetAm()!=null));
 }

 @Test void lieferungNachStornovorschauVerhindertUeberhoehtenStornoversand() {
  Long id=tx(()->ordering.direkt(content("4"),9L).id());accept(id);
  var preview=tx(()->stornoRequests.vorschau(id,new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufStornoanfrageDto.Entwurf(version(id),List.of(new Herkunft(needId,0,new BigDecimal("4"))),"Nicht mehr benötigt"),9L));
  deliver(id,proof(LieferantDokumentTyp.LIEFERSCHEIN),"2");
  assertThrows(IllegalStateException.class,()->tx(()->stornoRequests.freigeben(id,new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufStornoanfrageDto.Freigabe(preview.version(),preview.vorschauId(),preview.vorschauHash(),UUID.randomUUID()),9L)));
  assertAmount(id,"4","0","2");assertTrue(tx(()->stornoRequests.status(id)).isEmpty());
 }

 @Test void belegUploadIstOhneBedarfMoeglichUndFehlendeOderFremdeQuellenBleibenGesperrt() throws Exception {
  Long id=tx(()->ordering.direkt(content("4"),9L).id());
  var realFiles=new EinkaufDateiService(purchaseFiles,attachmentVersions,needs,emailAttachments,documents,uploadRoot.toString(),uploadRoot.resolve("email").toString());
  var service=new EinkaufBelegService(orders,suppliers,documents,realFiles,new EinkaufAuditService(audits),json);
  var upload=new org.springframework.mock.web.MockMultipartFile("datei","zeugnis.pdf","application/pdf","%PDF-1.7 Dummyzeugnis".getBytes());
  long before=attachmentVersions.count();
  var saved=tx(()->service.hochladen(id,LieferantDokumentTyp.SONSTIG,upload,9L));
  assertNotNull(saved.dateiId());assertNotNull(saved.lieferantDokumentId());assertTrue(saved.verfuegbar());
  assertEquals(before,attachmentVersions.count());
  assertEquals(saved.dateiId(),tx(()->service.registrieren(id,saved.lieferantDokumentId(),9L)).dateiId());
  assertEquals(saved.lieferantDokumentId(),purchaseFiles.findById(saved.dateiId()).orElseThrow().getLieferantDokumentId());
  assertArrayEquals(upload.getBytes(),tx(()->realFiles.ladePdfSnapshotBytes(saved.dateiId())));
  Long fremd=tx(()->{var d=new LieferantDokument();var supplier=new Lieferanten();supplier.setLieferantenname("Fremder Dummy");em.persist(supplier);d.setLieferant(supplier);d.setTyp(LieferantDokumentTyp.SONSTIG);em.persist(d);em.flush();return d.getId();});
  assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class,()->tx(()->service.registrieren(id,fremd,9L)));
  Long other=tx(()->ordering.direkt(content("1"),9L).id());
  tx(()->{documents.findById(saved.lieferantDokumentId()).orElseThrow().setEinkaufBestellungId(other);return null;});
  assertTrue(tx(()->service.auflisten(id)).isEmpty());
  assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class,()->tx(()->service.registrieren(id,saved.lieferantDokumentId(),9L)));
  tx(()->{documents.findById(saved.lieferantDokumentId()).orElseThrow().setEinkaufBestellungId(null);return null;});
  String stored=documents.findById(saved.lieferantDokumentId()).orElseThrow().getGespeicherterDateiname();
  java.nio.file.Files.delete(uploadRoot.resolve("lieferanten").resolve(supplierId.toString()).resolve(stored));
  assertFalse(tx(()->service.auflisten(id)).getFirst().verfuegbar());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx(()->service.registrieren(id,saved.lieferantDokumentId(),9L)));
 }

 @Test void belegDateiWirdBeiTransaktionsrollbackEntfernt() throws Exception {
  Long id=tx(()->ordering.direkt(content("4"),9L).id());
  var realFiles=new EinkaufDateiService(purchaseFiles,attachmentVersions,needs,emailAttachments,documents,uploadRoot.toString(),uploadRoot.resolve("email").toString());
  var service=new EinkaufBelegService(orders,suppliers,documents,realFiles,new EinkaufAuditService(audits),json);
  long before=documents.count();
  assertThrows(IllegalStateException.class,()->tx(()->{service.hochladen(id,LieferantDokumentTyp.SONSTIG,new org.springframework.mock.web.MockMultipartFile("datei","rollback.pdf","application/pdf","%PDF-1.7 rollback".getBytes()),9L);throw new IllegalStateException("Dummyrollback");}));
  assertEquals(before,documents.count());
  try(var paths=java.nio.file.Files.walk(uploadRoot)){assertEquals(0,paths.filter(java.nio.file.Files::isRegularFile).count());}
 }

 @Test void angebotslisteEnthaeltAlleVersionenMitOriginalkostenUndNurEigenerAnfrage() {
  Long requestId=tx(()->{
   var request=new Einkaufsanfrage("PA-"+UUID.randomUUID().toString().substring(0,8),9L,UUID.randomUUID(),"d".repeat(64));em.persist(request);
   var revision=new AnfrageRevision(request,1,null,null,UUID.randomUUID(),"e".repeat(64));em.persist(revision);
   var position=new AnfragePosition(revision,needs.findById(needId).orElseThrow().getPosition(),new BigDecimal("4"));em.persist(position);
   var participation=new AnfrageLieferant(revision,recipient);em.persist(participation);
   var offer=new EinkaufAngebot(participation);em.persist(offer);
   for(int number=1;number<=2;number++){
    var v=new AngebotVersion(offer,revision,number,"DUMMY-"+number,null,null,"EUR",null,null,null,null,null);
    var line=new AngebotPosition(v,position,"1","Dummyprofil",position.getSnapshot().basis(),null,null,null,List.of(),List.of());
    v.addPosition(line);v.addKosten(new AngebotKostenbestandteil(v,line,"profil","MATERIAL",BigDecimal.valueOf(8+number),"STUECK",BigDecimal.ONE,null,false,false,"manuell"));
    v.addKosten(new AngebotKostenbestandteil(v,null,"fracht","FRACHT",BigDecimal.valueOf(number),"PAUSCHAL",null,null,false,false,"manuell"));
    if(number==1)v.abloesen();offer.addVersion(v);em.persist(v);
   }
   em.flush();return request.getId();
  });
  var service=new EinkaufAngebotService(supplierOffers,offers,participations,requestRevisions,mock(EmailRepository.class),purchaseFiles,emailAttachments,documents,mock(EinkaufMailZuordnungRepository.class));
  var results=tx(()->service.auflisten(requestId));
  assertEquals(1,results.size());assertEquals(supplierId,results.getFirst().lieferantId());
  var versions=results.getFirst().angebot().versionen();assertEquals(2,versions.size());
  assertEquals("ABGELOEST",versions.getFirst().status());assertEquals(1,versions.getFirst().nummer());assertEquals(2,versions.getLast().nummer());
  var actualPosition=versions.getLast().positionen().getFirst();
  assertNotNull(actualPosition.id());assertNotEquals(versions.getFirst().positionen().getFirst().id(),actualPosition.id());
  assertEquals(versions.getLast().id(),tx(()->em.createQuery("select p.version.id from AngebotPosition p where p.id = :id",Long.class).setParameter("id",actualPosition.id()).getSingleResult()));
  assertEquals(0,new BigDecimal("10").compareTo(actualPosition.kosten().getFirst().betrag()));
  assertEquals(0,new BigDecimal("2").compareTo(versions.getLast().kosten().getFirst().betrag()));
  Long empty=tx(()->{var request=new Einkaufsanfrage("PA-"+UUID.randomUUID().toString().substring(0,8),9L,UUID.randomUUID(),"d".repeat(64));em.persist(request);em.persist(new AnfrageRevision(request,1,null,null,UUID.randomUUID(),"e".repeat(64)));em.flush();return request.getId();});
  assertTrue(tx(()->service.auflisten(empty)).isEmpty());
  assertThrows(NoSuchElementException.class,()->tx(()->service.auflisten(Long.MAX_VALUE)));
  assertThrows(IllegalArgumentException.class,()->tx(()->service.auflisten(0L)));
 }

 @Test void unklareStornoanfrageBleibtBisZurBelegtenKlaerungGesperrtUndAendertNieMengen() {
  Long id=tx(()->ordering.direkt(content("4"),9L).id());accept(id);
  var preview=tx(()->stornoRequests.vorschau(id,new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufStornoanfrageDto.Entwurf(version(id),List.of(new Herkunft(needId,0,new BigDecimal("2"))),"Nicht mehr benötigt"),9L));
  var sent=tx(()->stornoRequests.freigeben(id,new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufStornoanfrageDto.Freigabe(preview.version(),preview.vorschauId(),preview.vorschauHash(),UUID.randomUUID()),9L));
  outbox.beanspruche(sent.id());outbox.abgeschlossen(sent.id(),new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Versandergebnis(org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Status.UNKLAR,null,"DUMMY_TIMEOUT",null));
  long uncertain=dispatches.findById(sent.id()).orElseThrow().getVersion();
  assertThrows(IllegalStateException.class,()->tx(()->stornoRequests.erneutSenden(id,sent.id(),uncertain,9L)));
  Long other=tx(()->ordering.direkt(content("1"),9L).id());
  assertThrows(org.example.kalkulationsprogramm.exception.NotFoundException.class,()->tx(()->{stornoRequests.klaeren(other,sent.id(),new Klaerung(uncertain,Entscheidung.BEREITS_ANGENOMMEN,"Dummy-Nachweis"),9L);return null;}));
  tx(()->{stornoRequests.klaeren(id,sent.id(),new Klaerung(uncertain,Entscheidung.NACHWEISLICH_NICHT_GESENDET,"Lieferant bestätigt fehlenden Eingang"),9L);return null;});
  long failed=dispatches.findById(sent.id()).orElseThrow().getVersion();
  assertEquals(sent.id(),tx(()->stornoRequests.erneutSenden(id,sent.id(),failed,9L)).id());
  outbox.beanspruche(sent.id());outbox.abgeschlossen(sent.id(),new org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Versandergebnis(org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Status.UNKLAR,null,"DUMMY_TIMEOUT",null));
  long again=dispatches.findById(sent.id()).orElseThrow().getVersion();
  tx(()->{stornoRequests.klaeren(id,sent.id(),new Klaerung(again,Entscheidung.BEREITS_ANGENOMMEN,"Lieferant bestätigt Eingang der Anfrage"),9L);return null;});
  assertEquals("ANGENOMMEN",tx(()->stornoRequests.status(id)).getFirst().status());
  assertAmount(id,"4","0","0");
 }

 @Test void gleichzeitigeBelegregistrierungDarfBestehendeDateiNichtUmbinden() throws Exception {
  Long id=tx(()->ordering.direkt(content("4"),9L).id());
  byte[] bytes="%PDF-1.7 gleichzeitiger Dummybeleg".getBytes();
  var directory=uploadRoot.resolve("lieferanten").resolve(supplierId.toString());java.nio.file.Files.createDirectories(directory);
  java.nio.file.Files.write(directory.resolve("a.pdf"),bytes);java.nio.file.Files.write(directory.resolve("b.pdf"),bytes);
  String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
  var ids=tx(()->{
   List<Long> result=new ArrayList<>();
   for(String name:List.of("a.pdf","b.pdf")){var doc=new LieferantDokument();doc.setLieferant(em.find(Lieferanten.class,supplierId));doc.setTyp(LieferantDokumentTyp.SONSTIG);doc.setOriginalDateiname(name);doc.setGespeicherterDateiname(name);em.persist(doc);em.flush();result.add(doc.getId());}
   em.persist(new EinkaufDatei(hash,null,"technical.pdf","application/pdf",bytes.length));em.flush();return result;
  });
  var realFiles=new EinkaufDateiService(purchaseFiles,attachmentVersions,needs,emailAttachments,documents,uploadRoot.toString(),uploadRoot.resolve("email").toString());
  var service=new EinkaufBelegService(orders,suppliers,documents,realFiles,new EinkaufAuditService(audits),json);
  var gate=new java.util.concurrent.CountDownLatch(1);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var tasks=ids.stream().map(doc->pool.submit(()->{gate.await();try{tx(()->service.registrieren(id,doc,9L));return true;}catch(org.springframework.web.server.ResponseStatusException conflict){assertEquals(409,conflict.getStatusCode().value());return false;}})).toList();
   gate.countDown();int success=0;for(var task:tasks)if(task.get(10,java.util.concurrent.TimeUnit.SECONDS))success++;
   assertEquals(1,success);
  }
  assertTrue(ids.contains(purchaseFiles.findBySha256(hash).orElseThrow().getLieferantDokumentId()));
 }

 private VersandDto prepareUnknown(Long id) {
  var preview=tx(()->approval.vorschau(id,4L));
  var queued=tx(()->approval.freigeben(id,new Freigabe(version(id),preview.vorschauHash(),UUID.randomUUID()),9L));
  tx(()->{dispatches.findById(queued.id()).orElseThrow().unklar("DUMMY-DATA-TIMEOUT");return null;});
  return queued;
 }

 private Direkt content(String quantity) {
  var need=needs.findById(needId).orElseThrow();
  return new Direkt(supplierId,recipient,List.of(new Herkunft(needId,need.getVersion(),new BigDecimal(quantity))),
   List.of(new Direktpreis(needId,BigDecimal.ONE,Einheit.STUECK,BigDecimal.ONE,null,LocalDate.now(),LocalDate.now().plusDays(30),"Dummy-Preisbestätigung")),
   LocalDate.now().plusDays(7),LocalDate.now().plusDays(2),"frei Haus",UUID.randomUUID());
 }
 private void accept(Long id) {
  var preview=tx(()->approval.vorschau(id,4L));
  var queued=tx(()->approval.freigeben(id,new Freigabe(version(id),preview.vorschauHash(),UUID.randomUUID()),9L));
  UUID eventKey=UUID.randomUUID();
  var event=tx(()->{var revision=revisions.findFirstByBestellung_IdOrderByNummerDesc(id).orElseThrow();
   dispatches.findById(queued.id()).orElseThrow().angenommen(Instant.now());
   var accepted=new EinkaufVersandAngenommen(eventKey,queued.id(),"BESTELLUNG",id,revision.getId(),dispatches.findById(queued.id()).orElseThrow().getBeteiligungId(),Instant.now());
   approval.versandAngenommen(accepted);return accepted;});
  tx(()->{approval.versandAngenommen(event);return null;});
 }
 private void deliver(Long id,Long proof,String quantity) {
  deliver(id,proof,quantity,"DUMMY-CHARGE");
 }
 private void deliver(Long id,Long proof,String quantity,String chargeNumber) {
  var request=tx(()->{var order=ordering.lade(id);var line=revisions.findByBestellung_IdOrderByNummerAsc(id).stream().filter(BestellungRevision::istAngenommen)
    .max(Comparator.comparingInt(BestellungRevision::getNummer)).orElseThrow().getPositionen().getFirst();
   return new Annahme(order.version(),proof,Instant.now(),List.of(new Lieferanteil(line.getId(),new BigDecimal(quantity),
    chargeNumber,null,List.of(new Herkunft(needId,needs.findById(needId).orElseThrow().getVersion(),new BigDecimal(quantity))))),UUID.randomUUID());});
  Long first=tx(()->delivery.annehmen(id,request,9L).id());
  assertEquals(first,tx(()->delivery.annehmen(id,request,9L).id()));
 }
 private Long proof(LieferantDokumentTyp type) {
  return tx(()->{var doc=new LieferantDokument();doc.setLieferant(em.find(Lieferanten.class,supplierId));doc.setTyp(type);em.persist(doc);em.flush();return doc.getId();});
 }
 private Long certificateFile(String name){return tx(()->{var doc=new LieferantDokument();doc.setLieferant(em.find(Lieferanten.class,supplierId));doc.setTyp(LieferantDokumentTyp.SONSTIG);doc.setOriginalDateiname(name);em.persist(doc);em.flush();var file=new EinkaufDatei(UUID.randomUUID().toString().replace("-","").repeat(2),null,name,"application/pdf",12,null,doc.getId());em.persist(file);em.flush();return file.getId();});}
 private long version(Long id){return orders.findById(id).orElseThrow().getVersion();}
 private void assertAmount(Long id,String ordered,String reserved,String delivered) {
  var amount=tx(()->amounts.standFuerVorgang("BESTELLUNG:"+id).get(needId));
  assertEquals(0,amount.bestellt().compareTo(new BigDecimal(ordered)));assertEquals(0,amount.reserviert().compareTo(new BigDecimal(reserved)));
  assertEquals(0,amount.geliefert().compareTo(new BigDecimal(delivered)));
 }
 private <T> T tx(Supplier<T> action){var tx=new TransactionTemplate(transactionManager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return tx.execute(status->action.get());}

 @Configuration @EnableJpaRepositories(basePackages="org.example.kalkulationsprogramm.repository")
 static class Config {
  @Bean DriverManagerDataSource dataSource(){return new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());}
  @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource dataSource){
   var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(dataSource);factory.setPackagesToScan("org.example.kalkulationsprogramm.domain");
   factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.dialect","org.hibernate.dialect.MySQLDialect"));return factory;
  }
  @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory emf){return new JpaTransactionManager(emf);}
 }
}

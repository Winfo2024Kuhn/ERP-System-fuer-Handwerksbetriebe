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
  var outbox=new EinkaufOutboxService(dispatches,acceptances,mock(org.example.kalkulationsprogramm.service.mail.MailkontoService.class),
   mock(org.example.kalkulationsprogramm.config.LocalTestMailPolicy.class),transport,json,transactionManager);
  certificateService=new EinkaufZeugnisService(revisions,certificates,certificateTemplates,purchaseFiles,em);
  approval=new EinkaufBestellfreigabeService(orders,revisions,offers,previews,templates,pdf,files,outbox,
   mock(EinkaufVersandWorker.class),dispatches,amounts,needs,documents,audit,json,certificateService);
  delivery=new EinkaufLieferungService(orders,revisions,deliveries,needs,amounts,documents,em,audit,json);
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
   var accepted=new EinkaufVersandAngenommen(eventKey,queued.id(),"BESTELLUNG",id,revision.getId(),null,Instant.now());
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
 private Long certificateFile(String name){return tx(()->{var doc=new LieferantDokument();doc.setLieferant(em.find(Lieferanten.class,supplierId));doc.setTyp(LieferantDokumentTyp.SONSTIG);doc.setOriginalDateiname(name);em.persist(doc);em.flush();var file=new EinkaufDatei(name.equals("zeugnis-a.pdf")?"a".repeat(64):"b".repeat(64),null,name,"application/pdf",12,null,doc.getId());em.persist(file);em.flush();return file.getId();});}
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

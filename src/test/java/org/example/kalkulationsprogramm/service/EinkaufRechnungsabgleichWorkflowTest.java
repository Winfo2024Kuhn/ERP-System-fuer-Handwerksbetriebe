package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.example.kalkulationsprogramm.controller.EinkaufRechnungsabgleichController;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers @ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=EinkaufRechnungsabgleichWorkflowTest.Config.class)
class EinkaufRechnungsabgleichWorkflowTest {
 @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.44")
  .withDatabaseName("rechnungs_workflow_dummy").withUsername("test").withPassword("test");
 @Autowired PlatformTransactionManager transactionManager;
 @jakarta.persistence.PersistenceContext EntityManager em;
 @Autowired EinkaufRechnungsabgleichService service;
 @Autowired EinkaufBestellungRepository orders;
 @Autowired EinkaufBedarfRepository needs;
 @Autowired EinkaufMengenService amounts;
 @Autowired EinkaufBelegPositionRepository positions;
 @Autowired LieferantDokumentRepository documents;
 @Autowired ObjectMapper json;
 private MockMvc mvc;
 private Long supplierId,orderId,lineId,needId;
 @BeforeEach void setup() {
  var rights=mock(EinkaufBerechtigungService.class);when(rights.verlange(any(),any())).thenReturn(9L);
  mvc=MockMvcBuilders.standaloneSetup(new EinkaufRechnungsabgleichController(service,rights)).build();
  tx(()->{
   var supplier=new Lieferanten();supplier.setLieferantenname("Dummy "+UUID.randomUUID());em.persist(supplier);supplierId=supplier.getId();
   var order=new EinkaufBestellung("B-"+UUID.randomUUID().toString().substring(0,12),supplierId,null,null,
    new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Snapshot(supplierId,8L,"Dummy","test@example.com","Max Mustermann",null,null),UUID.randomUUID(),"a".repeat(64),9L);
   var revision=new BestellungRevision(order,1,Map.of("typ","DIREKT"),"b".repeat(64),9L);revision.angenommen(Instant.now());
   var snapshot=new PositionSnapshot(Positionsart.ARTIKEL,21L,"DUMMY",null,null,"Profil",null,null,
    new Mengenbasis(new BigDecimal("4"),Einheit.STUECK,new BigDecimal("4"),null,null,null),null,null,null,null,null,List.of(),List.of());
   var need=new EinkaufBedarf(snapshot,new Liefergruppe("Musterstraße 1",null,null,"Dummy"),null,null,false);
   em.persist(need);em.flush();needId=need.getId();
   var line=new BestellungPosition(revision,snapshot,new BigDecimal("4"),BigDecimal.TEN,"EUR",List.of(freight()),Map.of("lieferadresse","Musterstraße 1"));
   line.addHerkunft(new BestellungHerkunft(line,needId,0,new BigDecimal("4")));revision.addPosition(line);order.addRevision(revision);order.setStatus(BestellungStatus.BESTELLT);orders.saveAndFlush(order);
   orderId=order.getId();lineId=line.getId();
   amounts.buche(List.of(new Herkunft(needId,need.getVersion(),new BigDecimal("4"))),EinkaufMengenService.Mengenaktion.RESERVIEREN,"BESTELLUNG:"+orderId,UUID.randomUUID(),9L);needs.flush();
   amounts.buche(List.of(new Herkunft(needId,need.getVersion(),new BigDecimal("4"))),EinkaufMengenService.Mengenaktion.BESTELLEN,"BESTELLUNG:"+orderId,UUID.randomUUID(),9L);return null;
  });
 }
 @Test void echterHttpWorkflowOrdnetTeilrechnungenGutschriftUndPreisnachberechnungZu() throws Exception {
  Long first=doc(LieferantDokumentTyp.RECHNUNG,supplierId);
  var firstRequest=request("RECHNUNG",null,"2","10",List.of(freight()),false);
  postBill(first,firstRequest,200);postBill(first,firstRequest,200);
  assertEquals(orderId,documents.findById(first).orElseThrow().getEinkaufBestellungId());
  assertRow("2","2",null);assertEquals(1,positions.findByDokumentIdOrderByIdAsc(first).size());
  postBill(first,request("RECHNUNG",null,"2","10",List.of(),false),409);
  Long second=doc(LieferantDokumentTyp.RECHNUNG,supplierId);
  postBill(second,request("RECHNUNG",null,"2","10",List.of(),false),200);assertRow("4","0",null);
  Long credit=doc(LieferantDokumentTyp.GUTSCHRIFT,supplierId);
  postBill(credit,request("GUTSCHRIFT",first,"1","10",List.of(),false),200);assertRow("3","1",null);
  Long surcharge=doc(LieferantDokumentTyp.RECHNUNG,supplierId);
  postBill(surcharge,request("NACHBERECHNUNG",first,"2","40",List.of(),true),200);assertRow("3","1","80");
 }
 @Test void doppelteFrachtUndEinzelpreisabweichungSindSichtbar() throws Exception {
  postBill(doc(LieferantDokumentTyp.RECHNUNG,supplierId),request("RECHNUNG",null,"2","10",List.of(freight()),false),200);
  postBill(doc(LieferantDokumentTyp.RECHNUNG,supplierId),request("RECHNUNG",null,"2","15",List.of(freight()),false),200);
  assertRow("4","0","90");
 }
 @Test void falscherBelegtypUndFremderLieferantWerdenVorBindungAbgewiesen() throws Exception {
  Long wrong=doc(LieferantDokumentTyp.SONSTIG,supplierId);
  postBill(wrong,request("RECHNUNG",null,"1","10",List.of(),false),400);
  assertNull(documents.findById(wrong).orElseThrow().getEinkaufBestellungId());
  Long invoice=doc(LieferantDokumentTyp.RECHNUNG,supplierId);
  postBill(invoice,request("GUTSCHRIFT",null,"1","10",List.of(),false),400);
  Long foreign=tx(()->{var s=new Lieferanten();s.setLieferantenname("Anderer Dummy");em.persist(s);em.flush();return s.getId();});
  Long foreignDoc=doc(LieferantDokumentTyp.RECHNUNG,foreign);
  postBill(foreignDoc,request("RECHNUNG",null,"1","10",List.of(),false),400);
  assertNull(documents.findById(foreignDoc).orElseThrow().getEinkaufBestellungId());
 }
 @Test void preisJeHundertUndBestaetigtesStornoBleibenMengenrichtig() throws Exception {
  var request=request("RECHNUNG",null,"2","1000",List.of(),false);
  @SuppressWarnings("unchecked") var line=(Map<String,Object>)((List<?>)request.get("positionen")).getFirst();
  line.put("preisBasisMenge","100");
  postBill(doc(LieferantDokumentTyp.RECHNUNG,supplierId),request,200);assertRow("2","2",null);
  tx(()->{amounts.buche(List.of(new Herkunft(needId,needs.findById(needId).orElseThrow().getVersion(),new BigDecimal("2"))),
   EinkaufMengenService.Mengenaktion.STORNO_BESTAETIGEN,"BESTELLUNG:"+orderId,UUID.randomUUID(),9L);return null;});
  assertRow("2","0",null);
 }
 @Test void unbekannteEinheitBleibtZurPruefungOffen() throws Exception {
  var request=request("RECHNUNG",null,"2","10",List.of(),false);
  @SuppressWarnings("unchecked") var line=(Map<String,Object>)((List<?>)request.get("positionen")).getFirst();line.put("einheit","KILOGRAMM");
  Long document=doc(LieferantDokumentTyp.RECHNUNG,supplierId);postBill(document,request,200);
  var result=service.vergleichen(orderId);assertTrue(result.positionen().getFirst().pruefen());assertTrue(result.unbelegteDokumentIds().contains(document));
  assertEquals(0,BigDecimal.ZERO.compareTo(result.positionen().getFirst().kumuliertAbgerechnet()));
 }
 private void assertRow(String billed,String open,String costDelta) {
  var row=service.vergleichen(orderId).positionen().getFirst();
  assertEquals(0,new BigDecimal(billed).compareTo(row.kumuliertAbgerechnet()));
  assertEquals(0,new BigDecimal(open).compareTo(row.offen()));assertFalse(row.pruefen());
  assertTrue(row.abweichungen().stream().noneMatch(a->a.feld().equals("menge")));
  if(costDelta==null)assertTrue(row.abweichungen().isEmpty(),row.abweichungen().toString());
  else assertEquals(0,new BigDecimal(costDelta).compareTo(row.abweichungen().stream().filter(a->a.feld().equals("kosten")).findFirst().orElseThrow().differenz()));
 }
 private Map<String,Object> request(String type,Long reference,String quantity,String price,List<Map<String,Object>> costs,boolean priceOnly) {
  var line=new LinkedHashMap<String,Object>();line.put("originalPositionsnummer","1");line.put("bestellPositionId",lineId);
  line.put("menge",quantity);line.put("einheit","STUECK");line.put("nettoEinzelpreis",price);line.put("preisBasisMenge","1");line.put("nurPreisKorrektur",priceOnly);
  line.put("kosten",costs);line.put("quellen",List.of());
  var request=new LinkedHashMap<String,Object>();request.put("bestellungId",orderId);request.put("version",orders.findById(orderId).orElseThrow().getVersion());
  request.put("art",type);request.put("bezugsDokumentId",reference);request.put("positionen",List.of(line));request.put("idempotenzKey",UUID.randomUUID());return request;
 }
 private static Map<String,Object> freight(){return Map.of("schluessel","fracht","art","FRACHT","betrag",new BigDecimal("80"),"basis","PAUSCHAL","basisMenge",BigDecimal.ONE,"enthalten",false,"quelle","Dummy Vereinbarung");}
 private Long doc(LieferantDokumentTyp type,Long supplier){return tx(()->{var d=new LieferantDokument();d.setLieferant(em.find(Lieferanten.class,supplier));d.setTyp(type);em.persist(d);em.flush();return d.getId();});}
 private void postBill(Long document,Map<String,Object> request,int expected) throws Exception {mvc.perform(post("/api/einkauf/belege/{id}/zuordnung",document).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request))).andExpect(status().is(expected));}
 private <T> T tx(Supplier<T> work){return new TransactionTemplate(transactionManager).execute(s->work.get());}
 @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackages="org.example.kalkulationsprogramm.repository")
 static class Config {
  @Bean DriverManagerDataSource dataSource(){return new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());}
  @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource ds){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("org.example.kalkulationsprogramm.domain");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop"));return f;}
  @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory emf){return new JpaTransactionManager(emf);}
  @Bean EinkaufMengenService amounts(EinkaufBedarfRepository n,EinkaufMengenbuchungRepository b){return new EinkaufMengenService(n,b);}
  @Bean ObjectMapper json(){return new ObjectMapper().findAndRegisterModules();}
  @Bean EinkaufRechnungsabgleichService service(EinkaufBestellungRepository b,BestellungRevisionRepository r,LieferantDokumentRepository d,EinkaufBelegPositionRepository p,EinkaufLieferungRepository l,EntityManager em,ObjectMapper j,EinkaufMengenService m){return new EinkaufRechnungsabgleichService(b,r,d,p,l,em,j,m);}
 }
}

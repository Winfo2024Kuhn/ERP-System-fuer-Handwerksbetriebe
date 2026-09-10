package org.example.kalkulationsprogramm.service;

import jakarta.persistence.*;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Run only against an EMPTY disposable database: -Dzeitkonto.mysql.url=jdbc:mysql://.../testdb. */
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MonatsSaldoService.class, MonatsabschlussSammelService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "zeitkonto.mysql.url", matches = "jdbc:mysql:.*")
class MonatsabschlussSammelMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getProperty("zeitkonto.mysql.url"));
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        r.add("spring.datasource.username", () -> "root"); r.add("spring.datasource.password", () -> "");
    }
    @Autowired MonatsSaldoService service;
    @Autowired MonatsSaldoRepository salden;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager tm;
    @Autowired JdbcTemplate jdbc;
    @MockBean ZeitbuchungRepository zeitbuchungRepository;
    @MockBean AbwesenheitRepository abwesenheitRepository;
    @MockBean ZeitkontoKorrekturRepository korrekturRepository;
    @MockBean MitarbeiterRepository mitarbeiterRepository;
    @MockBean ZeitkontoService zeitkontoService;
    @MockBean TagesSollService tagesSollService;
    @MockBean MonatsabschlussBerechtigungService berechtigungService;
    TransactionTemplate tx;
    Mitarbeiter person;
    YearMonth month = YearMonth.now().minusMonths(1);
    @BeforeEach void setup() {
        tx = new TransactionTemplate(tm);
        jdbc.update("DELETE FROM monatsabschluss_audit"); jdbc.update("DELETE FROM monats_saldo");
        jdbc.update("DELETE FROM mitarbeiter_abteilung"); jdbc.update("DELETE FROM abteilung"); jdbc.update("DELETE FROM zeitkonto_version"); jdbc.update("DELETE FROM mitarbeiter");
        person = tx.execute(status -> {
            var m = new Mitarbeiter(); m.setVorname("Max"); m.setNachname("Mustermann");
            m.setEintrittsdatum(month.atDay(1)); em.persist(m); em.flush(); return m;
        });
        when(berechtigungService.verlangeAkteur(null)).thenReturn(person);
        when(mitarbeiterRepository.existsById(person.getId())).thenReturn(true);
        when(zeitkontoService.berechneSollstundenFuerMonat(anyLong(), anyInt(), anyInt())).thenReturn(new BigDecimal("80"));
        when(tagesSollService.feiertagsGutschriftSumme(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
    }
    MonatsSaldo cache(String hours) { var s = new MonatsSaldo(); s.setIstStunden(new BigDecimal(hours)); return s; }
    void seed() { service.saveMonatsSaldoCache(person.getId(), month.getYear(), month.getMonthValue(), cache("12")); }
    void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    @Autowired MonatsabschlussSammelService sammel;
    @Autowired MonatsabschlussUebersichtRepository uebersicht;
    @Test void parallelEinzelUndSammelGenauEinAuditUndTeilerfolgTrotzRollback() throws Exception {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(zeitkontoService.berechneSollstundenFuerMonat(anyLong(),anyInt(),anyInt())).thenAnswer(i->{ locked.countDown();await(release);return new BigDecimal("80"); });
        Long other=tx.execute(s->{var m=new Mitarbeiter();m.setVorname("Max");m.setNachname("Mustermann");em.persist(m);em.flush();return m.getId();});
        doThrow(new IllegalStateException("Testfehler")).when(zeitkontoService).berechneSollstundenFuerMonat(eq(other),anyInt(),anyInt());
        var ref=new org.example.kalkulationsprogramm.dto.MonatsabschlussUebersichtDto.Referenz(person.getId(),month.getYear(),month.getMonthValue());
        var failure=new org.example.kalkulationsprogramm.dto.MonatsabschlussUebersichtDto.Referenz(other,month.getYear(),month.getMonthValue());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var single=pool.submit(()->service.abschliessen(person.getId(),month.getYear(),month.getMonthValue(),null));await(locked);
            var batch=pool.submit(()->sammel.abschliessen(new org.example.kalkulationsprogramm.dto.MonatsabschlussUebersichtDto.SammelRequest(java.util.List.of(ref,failure)),null));
            try {assertThatThrownBy(()->batch.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);} finally {release.countDown();}
            single.get(10,TimeUnit.SECONDS);assertThat(batch.get(10,TimeUnit.SECONDS).ergebnisse()).extracting(x->x.status()).containsExactly("BEREITS_ABGESCHLOSSEN","FEHLGESCHLAGEN");
        } finally {release.countDown();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monatsabschluss_audit",Integer.class)).isEqualTo(1);
        assertThat(salden.findByMitarbeiterIdAndJahrAndMonat(person.getId(),month.getYear(),month.getMonthValue()).orElseThrow().getFestgeschrieben()).isTrue();
        assertThat(salden.findByMitarbeiterIdAndJahrAndMonat(other,month.getYear(),month.getMonthValue())).isEmpty();
    }
    @Test void neuerSammelErfolgBleibtNachFehltransaktionDauerhaftUndWiederholungIdempotent() {
        Long other=tx.execute(s->{var m=new Mitarbeiter();m.setVorname("Max");m.setNachname("Mustermann");em.persist(m);em.flush();return m.getId();});
        doThrow(new IllegalStateException("Testfehler")).when(zeitkontoService).berechneSollstundenFuerMonat(eq(other),anyInt(),anyInt());
        var success=new org.example.kalkulationsprogramm.dto.MonatsabschlussUebersichtDto.Referenz(person.getId(),month.getYear(),month.getMonthValue());
        var failure=new org.example.kalkulationsprogramm.dto.MonatsabschlussUebersichtDto.Referenz(other,month.getYear(),month.getMonthValue());
        var request=new org.example.kalkulationsprogramm.dto.MonatsabschlussUebersichtDto.SammelRequest(java.util.List.of(success,failure));
        assertThat(sammel.abschliessen(request,null).ergebnisse()).extracting(x->x.status()).containsExactly("ABGESCHLOSSEN","FEHLGESCHLAGEN");
        assertThat(sammel.abschliessen(request,null).ergebnisse()).extracting(x->x.status()).containsExactly("BEREITS_ABGESCHLOSSEN","FEHLGESCHLAGEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monatsabschluss_audit",Integer.class)).isEqualTo(1);
        assertThat(salden.findByMitarbeiterIdAndJahrAndMonat(person.getId(),month.getYear(),month.getMonthValue()).orElseThrow().getFestgeschrieben()).isTrue();
        assertThat(salden.findByMitarbeiterIdAndJahrAndMonat(other,month.getYear(),month.getMonthValue())).isEmpty();
    }
    @Test void echteProjektionenLadenInaktiveMenschenUndAbteilungenAberKeineSysteme() {
        tx.executeWithoutResult(s->{person=em.find(Mitarbeiter.class,person.getId());person.setAktiv(false);person.setFuehrtZeitkonto(false);var a=new Abteilung();a.setName("Test");em.persist(a);person.setAbteilungen(java.util.Set.of(a));var sys=new Mitarbeiter();sys.setArt(MitarbeiterArt.SYSTEM);sys.setVorname("System");sys.setNachname("Test");em.persist(sys);});
        seed();
        tx.executeWithoutResult(s->{
            var ms = salden.findByMitarbeiterIdAndJahrAndMonat(person.getId(), month.getYear(), month.getMonthValue()).orElseThrow();
            ms.setFestgeschrieben(true);
            em.merge(ms);
        });
        var von = month.atDay(1);
        var bis = month.atEndOfMonth();
        var vonDt = von.atStartOfDay();
        var bisDt = bis.atTime(23, 59, 59, 999_999_999);
        int ym = month.getYear() * 12 + month.getMonthValue();
        var people=uebersicht.personen(null,null,von,bis,vonDt,bisDt,ym,ym,org.springframework.data.domain.PageRequest.of(0,501));assertThat(people).hasSize(1);assertThat(people.getFirst().getId()).isEqualTo(person.getId());
        var departments=uebersicht.abteilungen(java.util.List.of(person.getId()));assertThat(departments).hasSize(1);assertThat(uebersicht.personen(null,departments.getFirst().getAbteilungId(),von,bis,vonDt,bisDt,ym,ym,org.springframework.data.domain.PageRequest.of(0,501))).hasSize(1);
        assertThat(uebersicht.salden(java.util.List.of(person.getId()),month.getYear()*12+month.getMonthValue(),month.getYear()*12+month.getMonthValue())).hasSize(1);
    }
}

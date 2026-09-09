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
@Import(MonatsSaldoService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "zeitkonto.mysql.url", matches = "jdbc:mysql:.*")
class MonatsabschlussMysqlTest {
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
        jdbc.update("DELETE FROM zeitkonto_version"); jdbc.update("DELETE FROM mitarbeiter");
        person = tx.execute(status -> {
            var m = new Mitarbeiter(); m.setVorname("Max"); m.setNachname("Mustermann");
            m.setEintrittsdatum(month.atDay(1)); em.persist(m); em.flush(); return m;
        });
        when(berechtigungService.verlangeAkteur(null)).thenReturn(person);
        when(mitarbeiterRepository.existsById(person.getId())).thenReturn(true);
        when(zeitkontoService.berechneSollstundenFuerMonat(anyLong(), anyInt(), anyInt())).thenReturn(new BigDecimal("80"));
        when(zeitkontoService.getOrCreateZeitkonto(anyLong())).thenReturn(new Zeitkonto(person));
        when(tagesSollService.feiertagsGutschriftSumme(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }
    MonatsSaldo cache(String hours) { var s = new MonatsSaldo(); s.setIstStunden(new BigDecimal(hours)); return s; }
    void seed() { service.saveMonatsSaldoCache(person.getId(), month.getYear(), month.getMonthValue(), cache("12")); }
    void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void abschlussSperrtParallelenCacheWriteAuchBeiErstanlage(boolean vorhanden) throws Exception {
        if (vorhanden) seed();
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(zeitkontoService.berechneSollstundenFuerMonat(anyLong(), anyInt(), anyInt())).thenAnswer(i -> {
            locked.countDown(); await(release); return new BigDecimal("80");
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var close = pool.submit(() -> service.abschliessen(person.getId(), month.getYear(), month.getMonthValue(), null));
            await(locked);
            var started = new CountDownLatch(1);
            var write = pool.submit(() -> { started.countDown(); return service.saveMonatsSaldoCache(person.getId(), month.getYear(), month.getMonthValue(), cache("999")); });
            await(started);
            try { assertThatThrownBy(() -> write.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            assertThat(close.get(10, TimeUnit.SECONDS).festgeschrieben()).isTrue();
            assertThat(write.get(10, TimeUnit.SECONDS).getIstStunden()).isEqualByComparingTo("0");
        } finally { release.countDown(); }
        assertThat(salden.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monatsabschluss_audit", Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cacheTransactionVorAbschlussErzeugtKeinenDeadlock(boolean vorhanden) throws Exception {
        if (vorhanden) seed();
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var write = pool.submit(() -> tx.execute(status -> {
                service.saveMonatsSaldoCache(person.getId(), month.getYear(), month.getMonthValue(), cache("999"));
                locked.countDown(); await(release); return true;
            }));
            await(locked);
            var close = pool.submit(() -> service.abschliessen(person.getId(), month.getYear(), month.getMonthValue(), null));
            try { assertThatThrownBy(() -> close.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            write.get(10, TimeUnit.SECONDS);
            var result = close.get(10, TimeUnit.SECONDS);
            assertThat(result.festgeschrieben()).isTrue(); assertThat(result.istStunden()).isEqualByComparingTo("0");
        } finally { release.countDown(); }
        assertThat(salden.count()).isEqualTo(1);
    }
    @Test void alleInvalidierungenSchuetzenAbschlussUndErhoehenOffeneVersion() {
        service.abschliessen(person.getId(), month.getYear(), month.getMonthValue(), null);
        var older = month.minusMonths(1);
        service.saveMonatsSaldoCache(person.getId(), older.getYear(), older.getMonthValue(), cache("10"));
        long closedVersion = service.status(person.getId(), month.getYear(), month.getMonthValue()).version();
        long before = salden.findByMitarbeiterIdAndJahrAndMonat(person.getId(), older.getYear(), older.getMonthValue()).orElseThrow().getVersion();
        service.invalidiereMonat(person.getId(), month.getYear(), month.getMonthValue());
        service.invalidiereJahr(person.getId(), month.getYear()); service.invalidiereAlle(person.getId());
        var closed = salden.findByMitarbeiterIdAndJahrAndMonat(person.getId(), month.getYear(), month.getMonthValue()).orElseThrow();
        assertThat(closed.getGueltig()).isTrue(); assertThat(closed.getVersion()).isEqualTo(closedVersion);
        var open = salden.findByMitarbeiterIdAndJahrAndMonat(person.getId(), older.getYear(), older.getMonthValue()).orElseThrow();
        assertThat(open.getGueltig()).isFalse(); assertThat(open.getVersion()).isGreaterThan(before);
    }
    @Test void bulkInvalidierungVerhindertStalenEntityWrite() {
        seed();
        assertThatThrownBy(() -> tx.execute(status -> {
            var stale = salden.findByMitarbeiterIdAndJahrAndMonat(person.getId(), month.getYear(), month.getMonthValue()).orElseThrow();
            service.invalidiereAlle(person.getId()); stale.setIstStunden(new BigDecimal("999")); em.flush(); return null;
        })).isInstanceOf(OptimisticLockException.class);
    }
    @Test void statusSchliesstNichtImplizitUndReopenErhaeltAuditUndNeueSummen() {
        assertThat(service.status(person.getId(), month.getYear(), month.getMonthValue()).festgeschrieben()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monatsabschluss_audit", Integer.class)).isZero();
        var fixed = service.abschliessen(person.getId(), month.getYear(), month.getMonthValue(), null);
        when(zeitkontoService.berechneSollstundenFuerMonat(anyLong(), anyInt(), anyInt())).thenReturn(new BigDecimal("100"));
        assertThat(service.status(person.getId(), month.getYear(), month.getMonthValue()).sollStunden()).isEqualByComparingTo("80");
        var open = service.oeffnen(person.getId(), month.getYear(), month.getMonthValue(), null);
        assertThat(open.sollStunden()).isEqualByComparingTo("100"); assertThat(open.version()).isGreaterThan(fixed.version());
        assertThat(open.audit()).extracting(a -> a.aktion()).containsExactly("ABSCHLIESSEN", "OEFFNEN");
        assertThat(open.audit()).allSatisfy(a -> assertThat(a.akteurMitarbeiterId()).isEqualTo(person.getId()));
    }
    @Test void echteSqlErfasstFehlendeCachezeilenHistorieUndIgnoriertFallbackUndKontolose() {
        var from = month.minusMonths(2);
        jdbc.update("UPDATE mitarbeiter SET eintrittsdatum=?, fuehrt_zeitkonto=FALSE, aktiv=FALSE WHERE id=?", from.atDay(1), person.getId());
        version(person.getId(), from.atDay(1), month.minusMonths(1).atEndOfMonth());
        var fallback = employee(null, "MENSCH"); version(fallback, LocalDate.of(1000,1,1), null);
        employee(from.atDay(1), "MENSCH");
        var system = employee(from.atDay(1), "SYSTEM"); version(system, from.atDay(1), null);
        var current = employee(YearMonth.now().atDay(1), "MENSCH"); version(current, YearMonth.now().atDay(1), null);
        var result = salden.findOffeneAbschlussMonate(YearMonth.now().atDay(1));
        assertThat(result).hasSize(2); assertThat(result).allSatisfy(r -> assertThat(r.getAnzahl()).isEqualTo(1));
        assertThat(result.getFirst().getMonat()).isEqualTo(from.getMonthValue());
        service.abschliessen(person.getId(), from.getYear(), from.getMonthValue(), null);
        assertThat(salden.findOffeneAbschlussMonate(YearMonth.now().atDay(1))).hasSize(1);
        service.saveMonatsSaldoCache(fallback, month.getYear(), month.getMonthValue(), cache("5"));
        assertThat(salden.findOffeneAbschlussMonate(YearMonth.now().atDay(1))).hasSize(2);
    }
    long employee(LocalDate entry, String art) {
        return tx.execute(status -> {
            var m = new Mitarbeiter(); m.setVorname("Max"); m.setNachname("Mustermann");
            m.setArt(MitarbeiterArt.valueOf(art)); m.setEintrittsdatum(entry); m.setFuehrtZeitkonto(false);
            em.persist(m); em.flush(); return m.getId();
        });
    }
    void version(long id, LocalDate from, LocalDate to) {
        tx.executeWithoutResult(status -> {
            var v = new ZeitkontoVersion(); v.setMitarbeiter(em.getReference(Mitarbeiter.class, id));
            v.setGueltigVon(from); v.setGueltigBis(to); em.persist(v);
        });
    }
}

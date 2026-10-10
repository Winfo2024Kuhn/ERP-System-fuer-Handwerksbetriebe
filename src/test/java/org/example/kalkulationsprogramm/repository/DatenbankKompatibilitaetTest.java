package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.example.kalkulationsprogramm.domain.ZeitkontoVersion;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;

/**
 * Laeuft der Datenbankzugriff auf MySQL (eigener Server) UND PostgreSQL (Kunden)?
 *
 * <p>Fuehrt jede {@code @Query}-Methode aller Repositories gegen eine echte
 * Datenbank aus - einmal mit Beispielwerten, einmal mit {@code null} fuer alle
 * Filter-Parameter (typischer PostgreSQL-Stolperstein: {@code :param IS NULL}).
 * Dazu die handgeschriebenen Varianten-Abfragen (Upsert, Datumsrechnung) mit
 * echten Daten und erwarteten Ergebnissen.
 *
 * <p>Das Schema ist das echte der Kunden: Basis-Schema + Flyway-Migrationen der
 * jeweiligen Datenbank (FlywayStartSetupConfig), danach {@code ddl-auto=validate}.
 * Abweichungen zwischen Basis und Entities fallen damit hier auf.
 *
 * <p>Nur gegen eine LEERE Wegwerf-Datenbank laufen lassen (die Testdaten bleiben
 * darin), z. B.:
 * <pre>
 * ./mvnw test -Dtest=DatenbankKompatibilitaetTest -Dkompat.db.url=jdbc:postgresql://localhost:5432/kompat \
 *     -Dkompat.db.user=kompat -Dkompat.db.pass=kompat
 * </pre>
 * In der CI laeuft er gegen beide Datenbanken (pr-ci.yml, Job "Datenbank-Kompatibilitaet").
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FlywayStartSetupConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:(mysql|postgresql):.*")
class DatenbankKompatibilitaetTest {

    private static final Logger LOG = LoggerFactory.getLogger(DatenbankKompatibilitaetTest.class);

    @DynamicPropertySource
    static void datenbank(DynamicPropertyRegistry r) {
        String url = System.getProperty("kompat.db.url");
        boolean postgres = url.startsWith("jdbc:postgresql:");
        r.add("spring.datasource.url", () -> url);
        // Test-application.properties stellt fest auf H2 - hier die echte Datenbank
        r.add("spring.datasource.driver-class-name", () -> postgres ? "org.postgresql.Driver" : "com.mysql.cj.jdbc.Driver");
        r.add("spring.jpa.database-platform", () -> postgres
                ? "org.hibernate.dialect.PostgreSQLDialect" : "org.hibernate.dialect.MySQLDialect");
        // Je Datenbank die eigene Migrationslinie (wie application-postgres.properties)
        r.add("spring.flyway.locations", () -> postgres ? "classpath:db/postgresql/migration" : "classpath:db/migration");
        r.add("spring.datasource.username", () -> System.getProperty("kompat.db.user", "root"));
        r.add("spring.datasource.password", () -> System.getProperty("kompat.db.pass", ""));
        // "SET NAMES utf8mb4" aus application.properties ist MySQL-Syntax
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SELECT 1");
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    PlatformTransactionManager transaktionen;
    @Autowired
    EntityManager em;
    @Autowired
    SpamTokenCountRepository spamTokens;
    @Autowired
    SeenSenderDomainRepository absenderDomains;
    @Autowired
    LieferantGeschaeftsdokumentRepository geschaeftsdokumente;
    @Autowired
    KundeRepository kunden;
    @Autowired
    MonatsSaldoRepository monatsSalden;
    @Autowired
    ArtikelRepository artikelRepository;
    @Autowired
    DatensatzLockRepository datensatzLocks;
    @Autowired
    WerkstoffRepository werkstoffe;

    TransactionTemplate tx;

    @BeforeEach
    void vorbereiten() {
        tx = new TransactionTemplate(transaktionen);
    }

    // ------------------------------------------------------------------
    // Alle @Query-Methoden
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Jede @Query-Methode laeuft - mit Beispielwerten und mit null-Filtern")
    void alleQueryMethodenLaufen() {
        List<String> fehler = new ArrayList<>();
        List<String> uebersprungen = new ArrayList<>();
        int geprueft = 0;

        for (Object repository : context.getBeansOfType(Repository.class).values()) {
            for (Class<?> schnittstelle : repository.getClass().getInterfaces()) {
                if (!schnittstelle.getPackageName().startsWith("org.example")) {
                    continue;
                }
                for (Method methode : schnittstelle.getMethods()) {
                    if (AnnotatedElementUtils.findMergedAnnotation(methode, Query.class) == null) {
                        continue;
                    }
                    String name = schnittstelle.getSimpleName() + "." + methode.getName();
                    Object[] beispiel = argumente(methode, false);
                    if (beispiel == null) {
                        uebersprungen.add(name);
                        continue;
                    }
                    ausfuehren(repository, methode, beispiel, name, fehler);
                    Object[] mitNull = argumente(methode, true);
                    if (!java.util.Arrays.equals(beispiel, mitNull)) {
                        ausfuehren(repository, methode, mitNull, name + " (null-Filter)", fehler);
                    }
                    geprueft++;
                }
            }
        }

        LOG.info("[kompat] {} @Query-Methoden geprueft, uebersprungen: {}", geprueft, uebersprungen);
        assertThat(fehler).as("Abfragen, die auf dieser Datenbank scheitern").isEmpty();
        assertThat(geprueft).as("Repositories nicht gefunden - Test waere wertlos").isGreaterThan(200);
    }

    /** Fuehrt die Methode in einer eigenen, zurueckgerollten Transaktion aus. */
    private void ausfuehren(Object repository, Method methode, Object[] argumente, String name, List<String> fehler) {
        try {
            tx.executeWithoutResult(status -> {
                status.setRollbackOnly();
                try {
                    Object ergebnis = methode.invoke(repository, argumente);
                    if (ergebnis instanceof Stream<?> strom) {
                        try (strom) {
                            strom.forEach(zeile -> { });
                        }
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                } catch (InvocationTargetException e) {
                    throw e.getCause() instanceof RuntimeException laufzeit ? laufzeit : new IllegalStateException(e.getCause());
                }
            });
        } catch (RuntimeException e) {
            fehler.add(name + ": " + ursache(e));
        }
    }

    private static String ursache(Throwable fehler) {
        Throwable aktuell = fehler;
        while (aktuell.getCause() != null && aktuell.getCause() != aktuell) {
            aktuell = aktuell.getCause();
        }
        return aktuell.getClass().getSimpleName() + ": " + aktuell.getMessage();
    }

    /** Beispielwerte je Parametertyp; {@code null}, wenn ein Typ unbekannt ist. */
    private static Object[] argumente(Method methode, boolean filterAlsNull) {
        Type[] typen = methode.getGenericParameterTypes();
        Object[] werte = new Object[typen.length];
        for (int i = 0; i < typen.length; i++) {
            Class<?> klasse = methode.getParameterTypes()[i];
            if (filterAlsNull && !klasse.isPrimitive() && !Collection.class.isAssignableFrom(klasse)
                    && klasse != Pageable.class && klasse != Sort.class) {
                werte[i] = null;
                continue;
            }
            if (Collection.class.isAssignableFrom(klasse)) {
                Object element = typen[i] instanceof ParameterizedType p && p.getActualTypeArguments()[0] instanceof Class<?> e
                        ? beispiel(e) : null;
                if (element == null) {
                    return null;
                }
                werte[i] = Set.class.isAssignableFrom(klasse) ? Set.of(element) : List.of(element);
                continue;
            }
            Object wert = beispiel(klasse);
            if (wert == null && !klasse.isAnnotationPresent(Entity.class)) {
                return null;
            }
            werte[i] = wert;
        }
        return werte;
    }

    private static Object beispiel(Class<?> klasse) {
        if (klasse == Long.class || klasse == long.class) return 1L;
        if (klasse == Integer.class || klasse == int.class) return 1;
        if (klasse == Short.class || klasse == short.class) return (short) 1;
        if (klasse == Double.class || klasse == double.class) return 1.0;
        if (klasse == Boolean.class || klasse == boolean.class) return true;
        if (klasse == BigDecimal.class) return BigDecimal.ONE;
        if (klasse == String.class) return "Mustermann";
        if (klasse == LocalDate.class) return LocalDate.of(2026, 3, 15);
        if (klasse == LocalDateTime.class) return LocalDateTime.of(2026, 3, 15, 12, 0);
        if (klasse == LocalTime.class) return LocalTime.NOON;
        if (klasse == Instant.class) return Instant.parse("2026-03-15T12:00:00Z");
        if (klasse == OffsetDateTime.class) return OffsetDateTime.parse("2026-03-15T12:00:00Z");
        if (klasse == Date.class) return new Date(1_773_576_000_000L);
        if (klasse == byte[].class) return new byte[] { 1 };
        if (klasse == Pageable.class) return PageRequest.of(0, 5);
        if (klasse == Sort.class) return Sort.unsorted();
        if (klasse.isEnum()) return klasse.getEnumConstants()[0];
        return null; // Entities: null, alles andere: Methode ueberspringen
    }

    // ------------------------------------------------------------------
    // Handgeschriebene Varianten (Repository-Fragmente) mit echten Daten
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Spam-Token-Upsert: legt an und zaehlt danach hoch")
    void spamTokenUpsert() {
        tx.executeWithoutResult(s -> em.createQuery("DELETE FROM SpamTokenCount").executeUpdate());
        tx.executeWithoutResult(s -> spamTokens.upsertToken("rabatt", 1, 0));
        tx.executeWithoutResult(s -> spamTokens.upsertToken("rabatt", 2, 1));

        var token = spamTokens.findByToken("rabatt").orElseThrow();
        assertThat(token.getSpamCount()).isEqualTo(3);
        assertThat(token.getHamCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Absender-Domain-Upsert: legt an und zaehlt danach hoch")
    void absenderDomainUpsert() {
        tx.executeWithoutResult(s -> em.createQuery("DELETE FROM SeenSenderDomain").executeUpdate());
        LocalDateTime zeitpunkt = LocalDateTime.of(2026, 3, 15, 12, 0);
        absenderDomains.upsertSeen("example.com", zeitpunkt);
        // Innerhalb einer laufenden Transaktion (so ruft der Mail-Import auf): darf
        // sie weder abbrechen noch auf "nur noch Rollback" setzen
        tx.executeWithoutResult(s -> {
            absenderDomains.upsertSeen("example.com", zeitpunkt.plusDays(1));
            assertThat(s.isRollbackOnly()).isFalse();
        });

        assertThat(absenderDomains.existsByDomain("example.com")).isTrue();
        var domain = absenderDomains.findById("example.com").orElseThrow();
        assertThat(domain.getEmailCount()).isEqualTo(2);
        assertThat(domain.getFirstSeen()).isEqualTo(zeitpunkt);
    }

    @Test
    @DisplayName("Lieferzeit-Durchschnitt und Lieferanten-Auswertungen rechnen mit Datum richtig")
    void lieferantenAuswertungen() {
        Long lieferantId = tx.execute(s -> {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Mustermann Stahlhandel " + System.nanoTime());
            em.persist(lieferant);
            dokument(lieferant, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 11), null);
            dokument(lieferant, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 25), null);
            dokument(lieferant, LieferantDokumentTyp.RECHNUNG, LocalDate.of(2026, 3, 20), null, new BigDecimal("100.50"));
            dokument(lieferant, LieferantDokumentTyp.RECHNUNG, LocalDate.of(2026, 4, 2), null, new BigDecimal("50.00"));
            em.flush();
            return lieferant.getId();
        });

        assertThat(geschaeftsdokumente.calculateAverageLieferzeitByLieferantId(lieferantId)).isEqualTo(15.0);
        assertThat(geschaeftsdokumente.calculateAverageLieferzeitByLieferantId(-1L)).isNull();
        assertThat(geschaeftsdokumente.sumGesamtkostenByLieferantId(lieferantId)).isEqualTo(150.5);

        assertThat(zeileFuer(geschaeftsdokumente.getLieferantenPerformanceFiltered(2026, null), lieferantId)).isEqualByComparingTo("150.50");
        assertThat(zeileFuer(geschaeftsdokumente.getLieferantenPerformanceFiltered(2026, 3), lieferantId)).isEqualByComparingTo("100.50");
        assertThat(geschaeftsdokumente.getLieferantenkostenProJahr())
                .anySatisfy(zeile -> assertThat(((Number) zeile[0]).intValue()).isEqualTo(2026));
    }

    private BigDecimal zeileFuer(List<Object[]> zeilen, Long lieferantId) {
        String name = tx.execute(s -> em.find(Lieferanten.class, lieferantId).getLieferantenname());
        return zeilen.stream().filter(z -> name.equals(z[0]))
                .map(z -> new BigDecimal(z[2].toString())).findFirst().orElseThrow();
    }

    private void dokument(Lieferanten lieferant, LieferantDokumentTyp typ, LocalDate datum, LocalDate liefertermin, BigDecimal netto) {
        LieferantDokument dokument = new LieferantDokument();
        dokument.setLieferant(lieferant);
        dokument.setTyp(typ);
        dokument.setUploadDatum(LocalDateTime.of(2026, 3, 1, 8, 0));
        em.persist(dokument);
        LieferantGeschaeftsdokument daten = new LieferantGeschaeftsdokument();
        daten.setDokument(dokument);
        daten.setDokumentDatum(datum);
        daten.setLiefertermin(liefertermin);
        daten.setBetragNetto(netto);
        em.persist(daten);
    }

    /**
     * Fuehrt {@code parallel} in einem eigenen Thread (= eigene Transaktion) aus
     * und wartet, bis sie committet ist - wie ein zweiter Nutzer, der genau
     * zwischen Pruefen und Anlegen dazwischenkommt.
     */
    private void inAndererTransaktion(Runnable parallel) {
        var thread = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            thread.submit(() -> tx.executeWithoutResult(s -> parallel.run())).get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            thread.shutdownNow();
        }
    }

    @Test
    @DisplayName("Lock-Rennen: anderer Nutzer legt das Lock gleichzeitig an - kein Fehler, Transaktion bleibt nutzbar")
    void lockRennen() {
        long entitaetId = System.nanoTime();
        tx.executeWithoutResult(s -> {
            // Wie DatensatzLockService.acquire: erst nachsehen (leer) ...
            assertThat(datensatzLocks.findByEntitaetTypAndEntitaetId(SperrbarerTyp.AUSGANG, entitaetId)).isEmpty();
            // ... dann kommt Erika zuvor ...
            inAndererTransaktion(() -> assertThat(datensatzLocks.legeAnFallsFrei(SperrbarerTyp.AUSGANG, entitaetId, 2L,
                    "Erika Mustermann", LocalDateTime.of(2026, 3, 15, 12, 0))).isTrue());
            // ... Max' Anlegen tut nichts, und er sieht Erikas Lock (MySQL: trotz Lesestand vom Transaktionsbeginn)
            assertThat(datensatzLocks.legeAnFallsFrei(SperrbarerTyp.AUSGANG, entitaetId, 1L, "Max Mustermann",
                    LocalDateTime.of(2026, 3, 15, 12, 0))).isFalse();
            assertThat(datensatzLocks.findGesperrt(SperrbarerTyp.AUSGANG, entitaetId))
                    .hasValueSatisfying(lock -> assertThat(lock.getUserId()).isEqualTo(2L));
            assertThat(s.isRollbackOnly()).isFalse();
        });
    }

    @Test
    @DisplayName("Werkstoff-Rennen: paralleler Import legt denselben Werkstoff an - kein Fehler, Import laeuft weiter")
    void werkstoffRennen() {
        String name = "Mustermann-Stahl " + System.nanoTime();
        tx.executeWithoutResult(s -> {
            assertThat(werkstoffe.findByNameIgnoreCase(name)).isEmpty();
            inAndererTransaktion(() -> werkstoffe.legeAnFallsNeu(name));
            werkstoffe.legeAnFallsNeu(name);
            assertThat(werkstoffe.findByNameGesperrt(name)).isPresent();
            assertThat(s.isRollbackOnly()).isFalse();
        });
        Long anzahl = tx.execute(s -> werkstoffe.findAll().stream().filter(w -> name.equals(w.getName())).count());
        assertThat(anzahl).as("genau ein Werkstoff, keine Dublette").isEqualTo(1L);
    }

    @Test
    @DisplayName("Stammdaten-Artikel aus der Basis lassen sich sperren und aendern")
    void stammdatenArtikelBearbeitbar() {
        Long artikelId = tx.execute(s -> em.createQuery("SELECT MIN(a.id) FROM Artikel a", Long.class).getSingleResult());
        assertThat(artikelId).as("Basis ohne Stammdaten-Artikel - Test waere wertlos").isNotNull();

        tx.executeWithoutResult(s -> {
            s.setRollbackOnly();
            var artikel = artikelRepository.findByIdForUpdate(artikelId).orElseThrow();
            artikel.setBeschreibung("Geaendert im Kompatibilitaetstest");
            em.flush();
        });
    }

    @Test
    @DisplayName("Hoechste Kundennummer: nur rein numerische zaehlen, numerisch verglichen")
    void hoechsteKundennummer() {
        tx.executeWithoutResult(s -> {
            em.createNativeQuery("DELETE FROM kunden_emails").executeUpdate();
            em.createQuery("DELETE FROM Kunde").executeUpdate();
            for (String nummer : List.of("998", "1009", "K-5000")) {
                Kunde kunde = new Kunde();
                kunde.setKundennummer(nummer);
                kunde.setName("Max Mustermann");
                em.persist(kunde);
            }
        });
        assertThat(kunden.findMaxKundennummer()).contains("1009");
    }

    @Test
    @DisplayName("Offene Abschlussmonate: Monatsrechnung liefert jeden vergangenen Monat genau einmal")
    void offeneAbschlussmonate() {
        YearMonth start = YearMonth.now().minusMonths(3);
        tx.executeWithoutResult(s -> {
            em.createQuery("DELETE FROM MonatsSaldo").executeUpdate();
            em.createQuery("DELETE FROM ZeitkontoVersion").executeUpdate();
            Mitarbeiter person = new Mitarbeiter();
            person.setVorname("Max");
            person.setNachname("Mustermann");
            person.setEintrittsdatum(start.atDay(10));
            em.persist(person);
            ZeitkontoVersion version = new ZeitkontoVersion();
            version.setMitarbeiter(person);
            version.setGueltigVon(start.atDay(10));
            em.persist(version);
        });

        var offen = monatsSalden.findOffeneAbschlussMonate(YearMonth.now().atDay(1));

        assertThat(offen).extracting(m -> YearMonth.of(m.getJahr(), m.getMonat()))
                .containsExactly(start, start.plusMonths(1), start.plusMonths(2));
        assertThat(offen).allSatisfy(m -> assertThat(m.getAnzahl()).isEqualTo(1));
    }
}

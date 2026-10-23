package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.util.ConstraintErrorDetail;
import org.example.kalkulationsprogramm.util.ConstraintMessageResolver;
import org.example.kalkulationsprogramm.util.DatabaseConstraintMetadataService;
import org.example.kalkulationsprogramm.util.FieldErrorDetail;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.Assigned;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.BasicEntityIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.NonAggregatedIdentifierMapping;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.mapping.internal.BasicAttributeMapping;
import org.hibernate.metamodel.mapping.internal.EmbeddedAttributeMapping;
import org.hibernate.metamodel.mapping.internal.EmbeddedIdentifierMappingImpl;
import org.hibernate.metamodel.mapping.internal.ToOneAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

/**
 * Laesst sich JEDE Entitaet auf PostgreSQL speichern, wenn alle
 * freiwilligen Felder leer sind - und kommt sie unveraendert zurueck?
 *
 * <p>{@link DatenbankKompatibilitaetTest} prueft die Abfragen, dieser Test das
 * Schreiben: Fuer jede Entitaet wird ein Datensatz angelegt, in dem nur die
 * Pflichtspalten (NOT NULL in der Datenbank oder im Mapping) einen Beispielwert
 * bekommen; alle anderen Felder bleiben {@code null}. Pflicht-Verweise auf
 * andere Entitaeten werden genauso angelegt. Gespeichert wird in einer
 * zurueckgerollten Transaktion, danach frisch geladen und verglichen.
 *
 * <p>So fallen ganze Fehlerklassen auf, ohne dass jemand an die einzelne Entitaet
 * denken muss:
 * <ul>
 * <li>{@code null} in einem Spaltentyp, den der Treiber nicht binden kann
 * (PostgreSQL: {@code LONG32VARCHAR}/{@code LONG32VARBINARY} -> "Unknown Types
 * value", SQLState 07006 - Mail-Import im Produktivbetrieb),</li>
 * <li>Java-Typ und Spaltentyp passen nicht zusammen (boolean/Zahl, Text/Zahl),</li>
 * <li>Werte kommen anders zurueck (Zeitzone, Nachkommastellen, Laenge).</li>
 * </ul>
 * Scheitert ein Datensatz nur an den Beispieldaten (z. B. einer CHECK-Regel),
 * wird die Entitaet als "nicht pruefbar" protokolliert statt als Fehler - Entitaeten
 * mit Langtext-Feldern muessen aber gespeichert werden.
 *
 * <p>Wie {@link DatenbankKompatibilitaetTest} nur gegen eine Wegwerf-Datenbank:
 * <pre>
 * ./mvnw test -Dtest=DatenbankKompatibilitaetEntitaetenTest -Dkompat.db.url=jdbc:postgresql://localhost:5432/kompat \
 *     -Dkompat.db.user=kompat -Dkompat.db.pass=kompat
 * </pre>
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false",
        // Geprueft wird die Datenbank, nicht die Bean-Validation der Eingabemasken
        "spring.jpa.properties.jakarta.persistence.validation.mode=none" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FlywayStartSetupConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class DatenbankKompatibilitaetEntitaetenTest {

    private static final Logger LOG = LoggerFactory.getLogger(DatenbankKompatibilitaetEntitaetenTest.class);
    private static final int MAX_TIEFE = 6;

    @DynamicPropertySource
    static void datenbank(DynamicPropertyRegistry r) {
        KompatibilitaetsDatenbank.eintragen(r);
    }

    @Autowired
    EntityManager em;
    @Autowired
    PlatformTransactionManager transaktionen;

    /** Eindeutige Beispielwerte (Unique-Spalten), auch ueber mehrere Laeufe. */
    private final AtomicLong zaehler = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);
    /** "tabelle.spalte" aller NOT-NULL-Spalten. */
    private Set<String> pflichtspalten;
    /** "tabelle.spalte" -> hoechstens erlaubte Zeichen laut Datenbank. */
    private Map<String, Long> textlaengen;
    /** "tabelle.spalte" aller Spalten mit fester Laenge (CHAR(n)). */
    private Set<String> festeLaengen;
    /** "tabelle.spalte" -> Zieltabelle aller Fremdschluessel. */
    private Map<String, String> fremdschluessel;
    private Map<String, EntityPersister> persisterJeTabelle;
    /** Nach einer verletzten CHECK-Regel: ab dieser Verweistiefe auch freiwillige einfache Felder fuellen. */
    private int alleFelderAbTiefe = Integer.MAX_VALUE;

    @Test
    @DisplayName("Jede Entitaet: mit leeren freiwilligen Feldern speichern und unveraendert wieder laden")
    void jedeEntitaetMitLeerenFeldernSpeichern() {
        ladeSchema();
        SessionFactoryImplementor fabrik = em.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class);
        List<EntityPersister> alle = new ArrayList<>();
        fabrik.getMappingMetamodel().forEachEntityDescriptor(alle::add);
        alle.sort(Comparator.comparing(EntityPersister::getEntityName));

        List<String> fehler = new ArrayList<>();
        Map<String, String> nichtPruefbar = new LinkedHashMap<>();
        Set<String> langtextFelder = new TreeSet<>();
        Set<String> langtextLeerGespeichert = new TreeSet<>();
        int gespeichert = 0;

        for (EntityPersister persister : alle) {
            Class<?> klasse = persister.getMappedClass();
            if (persister.isAbstract() || Modifier.isAbstract(klasse.getModifiers())) {
                continue;
            }
            String name = klasse.getSimpleName();
            Set<String> leereLangtexte = new TreeSet<>();
            langtextFelder.addAll(freiwilligeLangtextFelder(persister));
            TransactionTemplate tx = new TransactionTemplate(transaktionen);
            try {
                List<String> abweichungen = null;
                // CHECK-Regeln wie "Datei ODER Umbuchung" verlangen mehr als die Pflichtspalten:
                // dann zuerst die verwiesenen Datensaetze, danach alles mit einfachen Feldern
                // befuellen - die freiwilligen Langtexte bleiben in jedem Versuch leer
                for (int ab : new int[] { Integer.MAX_VALUE, 1, 0 }) {
                    alleFelderAbTiefe = ab;
                    leereLangtexte.clear();
                    try {
                        abweichungen = tx.execute(status -> {
                            status.setRollbackOnly();
                            return speichernUndVergleichen(persister, leereLangtexte);
                        });
                        break;
                    } catch (RuntimeException e) {
                        SQLException sql = sqlFehler(e);
                        if (ab == 0 || !istCheckVerletzung(sql)) {
                            throw e;
                        }
                    }
                }
                gespeichert++;
                langtextLeerGespeichert.addAll(leereLangtexte);
                abweichungen.forEach(a -> fehler.add(name + ": " + a));
            } catch (RuntimeException e) {
                SQLException sql = sqlFehler(e);
                String ursache = ursache(e);
                if (sql == null || nurBeispieldaten(sql)) {
                    // Nur die Beispieldaten passen nicht (CHECK, Verweis, Dublette, Pruefung in der
                    // Entitaet ...) - kein Datenbankfehler. Die kommen immer mit einer SQLException.
                    nichtPruefbar.put(name, ursache);
                } else {
                    fehler.add(name + ": " + ursache);
                }
            }
        }

        LOG.info("[kompat] {} Entitaeten gespeichert, nicht pruefbar: {}", gespeichert, nichtPruefbar);
        assertThat(fehler).as("Entitaeten, die sich auf dieser Datenbank nicht speichern/laden lassen").isEmpty();
        assertThat(langtextLeerGespeichert)
                .as("Langtext-Felder (LONG32VARCHAR/LONG32VARBINARY), die leer gespeichert wurden - nicht pruefbar: "
                        + nichtPruefbar)
                .containsAll(langtextFelder);
        assertThat(langtextFelder).as("keine Langtext-Felder gefunden - Test waere wertlos").hasSizeGreaterThan(10);
        assertThat(gespeichert).as("zu wenige Entitaeten gespeichert - Test waere wertlos").isGreaterThan(80);
    }

    @Autowired
    KundeRepository kunden;

    @Test
    @DisplayName("Zu langer Text: die Oberflaeche nennt die Hoechstlaenge statt einer allgemeinen Meldung")
    void zuLangerTextVerstaendlich() {
        DatabaseConstraintMetadataService metadaten = mock(DatabaseConstraintMetadataService.class);
        when(metadaten.findColumnByName(anyString())).thenReturn(Optional.empty());
        ConstraintMessageResolver meldungen = new ConstraintMessageResolver(metadaten);
        Kunde kunde = new Kunde();
        kunde.setKundennummer("K-" + zaehler.incrementAndGet());
        kunde.setName("Max Mustermann ".repeat(30));

        DataIntegrityViolationException fehler = catchThrowableOfType(() -> new TransactionTemplate(transaktionen)
                .executeWithoutResult(status -> {
                    status.setRollbackOnly();
                    kunden.saveAndFlush(kunde);
                }), DataIntegrityViolationException.class);

        assertThat(fehler).as("zu langer Name muss als DataIntegrityViolationException ankommen").isNotNull();
        // PostgreSQL: "Ein eingegebener Text ist zu lang. ..."
        assertThat(meldungen.resolve(fehler).userMessage()).contains("zu lang");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Verweis: echte Metadaten nennen beim Loeschen die verweisende, beim Einfuegen die Zieltabelle")
    void verweisMeldungMitEchtenMetadaten() {
        // Eigene kleine Tabellen statt kunde/projekt: die haben zu viele Pflichtspalten
        jdbc.execute("DROP TABLE IF EXISTS kompat_fk_projekt, kompat_fk_kunde");
        jdbc.execute("CREATE TABLE kompat_fk_kunde (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE kompat_fk_projekt (id BIGINT PRIMARY KEY, kunde_id BIGINT, "
                + "CONSTRAINT fk_kompat_fk_projekt_kunde FOREIGN KEY (kunde_id) REFERENCES kompat_fk_kunde (id))");
        try {
            jdbc.update("INSERT INTO kompat_fk_kunde (id) VALUES (7)");
            jdbc.update("INSERT INTO kompat_fk_projekt (id, kunde_id) VALUES (1, 7)");
            DatabaseConstraintMetadataService metadaten = new DatabaseConstraintMetadataService(jdbc);
            ConstraintMessageResolver meldungen = new ConstraintMessageResolver(metadaten);

            DataIntegrityViolationException loeschen = catchThrowableOfType(
                    () -> jdbc.update("DELETE FROM kompat_fk_kunde WHERE id = 7"), DataIntegrityViolationException.class);
            DataIntegrityViolationException einfuegen = catchThrowableOfType(
                    () -> jdbc.update("INSERT INTO kompat_fk_projekt (id, kunde_id) VALUES (2, 99)"),
                    DataIntegrityViolationException.class);

            assertThat(metadaten.findConstraint("fk_kompat_fk_projekt_kunde")).get()
                    .satisfies(fk -> {
                        assertThat(fk.tableName()).as("verweisende Tabelle").isEqualTo("kompat_fk_projekt");
                        assertThat(fk.referencedTableName()).as("Zieltabelle").isEqualTo("kompat_fk_kunde");
                    });
            assertThat(loeschen).as("Loeschen des noch gebrauchten Kunden").isNotNull();
            assertThat(meldungen.resolve(loeschen).userMessage()).isEqualTo(
                    "Der Datensatz kann nicht gelöscht werden, da noch Einträge in 'Kompat Fk Projekt' darauf verweisen.");
            assertThat(einfuegen).as("Verweis auf einen nicht vorhandenen Kunden").isNotNull();
            ConstraintErrorDetail ungueltig = meldungen.resolve(einfuegen);
            assertThat(ungueltig.userMessage()).isEqualTo(
                    "Der angegebene Wert für Kunde Id ist ungültig – es existiert kein passender Eintrag in 'Kompat Fk Kunde'.");
            assertThat(ungueltig.fieldErrors()).extracting(FieldErrorDetail::field).containsExactly("kunde_id");
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS kompat_fk_projekt, kompat_fk_kunde");
        }
    }

    // ------------------------------------------------------------------
    // Speichern, frisch laden, vergleichen
    // ------------------------------------------------------------------

    private List<String> speichernUndVergleichen(EntityPersister persister, Set<String> leereLangtexte) {
        SharedSessionContractImplementor sitzung = em.unwrap(SharedSessionContractImplementor.class);
        Object entitaet = neu(persister, 0, leereLangtexte);
        em.persist(entitaet);
        em.flush();
        Object id = persister.getIdentifier(entitaet, sitzung);
        Map<String, Object> vorher = einfacheWerte(persister, entitaet);
        em.clear();
        Object geladen = em.find(persister.getMappedClass(), id);
        if (geladen == null) {
            return List.of("nach dem Speichern nicht wieder auffindbar (id " + id + ")");
        }
        Map<String, Object> nachher = einfacheWerte(persister, geladen);
        List<String> abweichungen = new ArrayList<>();
        vorher.forEach((feld, wert) -> {
            Object neuGeladen = nachher.get(feld);
            if (!gleich(wert, neuGeladen)) {
                abweichungen.add(feld + " gespeichert als <" + wert + ">, gelesen als <" + neuGeladen + ">");
            }
        });
        return abweichungen;
    }

    /** Werte aller einfachen Felder, die die Anwendung schreibt. */
    private static Map<String, Object> einfacheWerte(EntityPersister persister, Object entitaet) {
        Map<String, Object> werte = new HashMap<>();
        persister.forEachAttributeMapping(am -> {
            if (am instanceof BasicAttributeMapping b && b.isInsertable() && !b.isFormula()) {
                werte.put(am.getAttributeName(), am.getValue(entitaet));
            }
        });
        return werte;
    }

    private static boolean gleich(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        // Zeitstempel: Java liefert mehr Stellen (now()) als die Spalte haelt - PostgreSQL
        // speichert Mikrosekunden.
        // Gerundet wird dabei; Zeitzonen-Fehler waeren ganze Stunden.
        if (a instanceof LocalDateTime x && b instanceof LocalDateTime y) {
            return fastGleich(Duration.between(x, y));
        }
        if (a instanceof LocalTime x && b instanceof LocalTime y) {
            return fastGleich(Duration.between(x, y));
        }
        if (a instanceof Instant x && b instanceof Instant y) {
            return fastGleich(Duration.between(x, y));
        }
        if (a instanceof OffsetDateTime x && b instanceof OffsetDateTime y) {
            return fastGleich(Duration.between(x, y));
        }
        if (a instanceof ZonedDateTime x && b instanceof ZonedDateTime y) {
            return fastGleich(Duration.between(x, y));
        }
        if (a instanceof Date x && b instanceof Date y) {
            return Math.abs(x.getTime() - y.getTime()) < 1_000;
        }
        if (a instanceof byte[] x && b instanceof byte[] y) {
            return Arrays.equals(x, y);
        }
        return Objects.equals(a, b);
    }

    private static boolean fastGleich(Duration abstand) {
        return abstand.abs().compareTo(Duration.ofSeconds(1)) < 0;
    }

    // ------------------------------------------------------------------
    // Datensatz mit nur den Pflichtfeldern bauen
    // ------------------------------------------------------------------

    private Object neu(EntityPersister persister, int tiefe, Set<String> leereLangtexte) {
        if (tiefe > MAX_TIEFE) {
            throw new Befuellungsfehler("Pflicht-Verweise zu tief verschachtelt bei " + persister.getEntityName());
        }
        Object entitaet = instanziieren(persister.getMappedClass());
        SharedSessionContractImplementor sitzung = em.unwrap(SharedSessionContractImplementor.class);
        persister.forEachAttributeMapping(am -> {
            if (persister.getVersionMapping() != null && persister.getVersionMapping().getVersionAttribute() == am) {
                return; // setzt Hibernate
            }
            befuellen(persister.getEntityName(), entitaet, am, tiefe, tiefe == 0 ? leereLangtexte : new HashSet<>());
        });
        EntityIdentifierMapping id = persister.getIdentifierMapping();
        if (id instanceof EmbeddedIdentifierMappingImpl eingebettet) {
            Object wert = instanziieren(eingebettet.getEmbeddableTypeDescriptor().getJavaType().getJavaTypeClass());
            eingebettet.getEmbeddableTypeDescriptor().forEachAttributeMapping(
                    am -> befuellenPflicht(persister.getEntityName(), wert, am, tiefe));
            id.setIdentifier(entitaet, wert, sitzung);
        } else if (id instanceof NonAggregatedIdentifierMapping virtuell) {
            virtuell.getVirtualIdEmbeddable().forEachAttributeMapping(
                    am -> befuellenPflicht(persister.getEntityName(), entitaet, am, tiefe));
        } else if (persister.getGenerator() instanceof Assigned && id.getIdentifier(entitaet) == null
                && id instanceof BasicEntityIdentifierMapping einfach) {
            // Vergebene Schluessel koennen zugleich Fremdschluessel sein (1:1 ueber die ID)
            id.setIdentifier(entitaet, pflichtwert(einfach.getContainingTableExpression(),
                    einfach.getSelectionExpression(), einfach.getJavaType().getJavaTypeClass(), 64L, false, tiefe), sitzung);
        }
        return entitaet;
    }

    private void befuellen(String entitaet, Object ziel, AttributeMapping am, int tiefe, Set<String> leereLangtexte) {
        if (am instanceof PluralAttributeMapping) {
            return; // Sammlungen bleiben leer
        }
        if (am instanceof BasicAttributeMapping b) {
            if (!b.isInsertable() || b.isFormula()) {
                return;
            }
            boolean pflicht = !b.isNullable() || istPflicht(b.getContainingTableExpression(), b.getSelectionExpression());
            if (istLangtext(ziel.getClass(), am.getAttributeName()) && !pflicht) {
                am.setValue(ziel, null);
                leereLangtexte.add(entitaet + "." + am.getAttributeName());
                return;
            }
            if ((pflicht || tiefe >= alleFelderAbTiefe) && am.getValue(ziel) == null) {
                am.setValue(ziel, pflichtwert(b, tiefe));
            }
            return;
        }
        befuellenPflicht(entitaet, ziel, am, tiefe);
    }

    /** Verweise und eingebettete Werte - nur, wenn die Datenbank sie verlangt. */
    private void befuellenPflicht(String entitaet, Object ziel, AttributeMapping am, int tiefe) {
        if (am instanceof BasicAttributeMapping b) {
            if (am.getValue(ziel) == null) {
                am.setValue(ziel, pflichtwert(b, tiefe));
            }
        } else if (am instanceof ToOneAttributeMapping verweis) {
            if (verweis.getSideNature() != ForeignKeyDescriptor.Nature.KEY
                    || am.getValue(ziel) != null) {
                return; // Gegenseite einer 1:1-Beziehung oder schon gesetzt
            }
            boolean[] pflicht = { !verweis.isNullable() };
            verweis.getForeignKeyDescriptor().getKeyPart().forEachSelectable((i, spalte) -> {
                if (istPflicht(spalte.getContainingTableExpression(), spalte.getSelectionExpression())) {
                    pflicht[0] = true;
                }
            });
            if (pflicht[0]) {
                EntityPersister zielTyp = verweis.getEntityMappingType().getEntityPersister();
                Object referenz = neu(zielTyp, tiefe + 1, new HashSet<>());
                em.persist(referenz);
                am.setValue(ziel, referenz);
            }
        } else if (am instanceof EmbeddedAttributeMapping eingebettet) {
            Object wert = am.getValue(ziel);
            if (wert == null) {
                wert = instanziieren(eingebettet.getEmbeddableTypeDescriptor().getJavaType().getJavaTypeClass());
            }
            Object fertig = wert;
            eingebettet.getEmbeddableTypeDescriptor().forEachAttributeMapping(
                    teil -> befuellen(entitaet, fertig, teil, tiefe, new HashSet<>()));
            am.setValue(ziel, fertig);
        }
    }

    /** Pflichtwert einer einfachen Spalte; Fremdschluessel ohne Mapping zeigen auf einen echten Datensatz. */
    private Object pflichtwert(BasicAttributeMapping b, int tiefe) {
        int code = b.getJdbcMapping().getJdbcType().getDefaultSqlTypeCode();
        return pflichtwert(b.getContainingTableExpression(), b.getSelectionExpression(), b.getJavaType().getJavaTypeClass(),
                b.getLength(), code == SqlTypes.CHAR || code == SqlTypes.NCHAR, tiefe);
    }

    private Object pflichtwert(String tabelle, String spalte, Class<?> typ, Long laenge, boolean festeLaenge, int tiefe) {
        String ziel = fremdschluessel.get(schluessel(tabelle, spalte));
        // Die Datenbank hat das letzte Wort ueber die Laenge (columnDefinition = "CHAR(64)" ...)
        Long erlaubt = textlaengen.get(schluessel(tabelle, spalte));
        if (erlaubt != null && (laenge == null || erlaubt < laenge)) {
            laenge = erlaubt;
        }
        festeLaenge = festeLaenge || festeLaengen.contains(schluessel(tabelle, spalte));
        if (ziel != null && persisterJeTabelle.containsKey(ziel)) {
            EntityPersister zielTyp = persisterJeTabelle.get(ziel);
            Object referenz = neu(zielTyp, tiefe + 1, new HashSet<>());
            em.persist(referenz);
            em.flush();
            return zielTyp.getIdentifier(referenz, em.unwrap(SharedSessionContractImplementor.class));
        }
        return beispiel(typ, laenge, festeLaenge);
    }

    private Object beispiel(Class<?> typ, Long laenge, boolean festeLaenge) {
        long n = zaehler.incrementAndGet();
        if (typ == String.class) {
            int max = laenge == null || laenge <= 0 || laenge > 255 ? 255 : laenge.intValue();
            String wert = "Mustermann-" + Long.toString(n, 36);
            if (festeLaenge) {
                wert = (wert + "0".repeat(max)).substring(0, max);
            }
            return wert.length() <= max ? wert : wert.substring(wert.length() - max);
        }
        if (typ == Long.class || typ == long.class) return 900_000_000L + n;
        if (typ == Integer.class || typ == int.class) return 1;
        if (typ == Short.class || typ == short.class) return (short) 1;
        if (typ == Byte.class || typ == byte.class) return (byte) 1;
        if (typ == Double.class || typ == double.class) return 1.5;
        if (typ == Float.class || typ == float.class) return 1.5f;
        if (typ == BigDecimal.class) return new BigDecimal("1.50");
        if (typ == BigInteger.class) return BigInteger.ONE;
        if (typ == Boolean.class || typ == boolean.class) return Boolean.FALSE;
        if (typ == Character.class || typ == char.class) return 'M';
        if (typ == LocalDate.class) return LocalDate.of(2026, 3, 15);
        if (typ == LocalDateTime.class) return LocalDateTime.of(2026, 3, 15, 12, 30, 15);
        if (typ == LocalTime.class) return LocalTime.of(12, 30, 15);
        if (typ == Instant.class) return Instant.parse("2026-03-15T11:30:15Z");
        // Bewusst nicht UTC: Zeitzonen-Fehler fallen nur so auf
        if (typ == OffsetDateTime.class) return OffsetDateTime.of(2026, 3, 15, 12, 30, 15, 0, ZoneOffset.ofHours(2));
        if (typ == ZonedDateTime.class) return ZonedDateTime.of(2026, 3, 15, 12, 30, 15, 0, ZoneOffset.ofHours(2));
        if (typ == java.sql.Timestamp.class) return java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 3, 15, 12, 30, 15));
        if (typ == java.sql.Date.class) return java.sql.Date.valueOf(LocalDate.of(2026, 3, 15));
        if (typ == Date.class) return Date.from(Instant.parse("2026-03-15T11:30:15Z"));
        if (typ == YearMonth.class) return YearMonth.of(2026, 3);
        if (typ == Year.class) return Year.of(2026);
        if (typ == Duration.class) return Duration.ofMinutes(90);
        if (typ == UUID.class) return UUID.randomUUID();
        if (typ == byte[].class) return new byte[] { 1, 2, 3 };
        if (typ.isEnum()) return typ.getEnumConstants()[0];
        throw new Befuellungsfehler("kein Beispielwert fuer " + typ.getName());
    }

    private static Object instanziieren(Class<?> klasse) {
        try {
            Constructor<?> konstruktor = klasse.getDeclaredConstructor();
            konstruktor.setAccessible(true);
            return konstruktor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new Befuellungsfehler("nicht instanziierbar: " + klasse.getName());
        }
    }

    // ------------------------------------------------------------------
    // Schema und Mapping
    // ------------------------------------------------------------------

    private void ladeSchema() {
        String schema = "current_schema()";
        pflichtspalten = new HashSet<>();
        fremdschluessel = new HashMap<>();
        textlaengen = new HashMap<>();
        festeLaengen = new HashSet<>();
        @SuppressWarnings("unchecked")
        List<Object[]> spalten = em.createNativeQuery("SELECT table_name, column_name, is_nullable, "
                + "character_maximum_length, data_type FROM information_schema.columns WHERE table_schema = " + schema)
                .getResultList();
        for (Object[] z : spalten) {
            String spalte = schluessel(z[0].toString(), z[1].toString());
            if ("NO".equals(z[2].toString())) {
                pflichtspalten.add(spalte);
            }
            if (z[3] != null) {
                textlaengen.put(spalte, ((Number) z[3]).longValue());
            }
            String typ = z[4].toString().toLowerCase(Locale.ROOT);
            if (typ.equals("char") || typ.equals("character") || typ.equals("bpchar")) {
                festeLaengen.add(spalte);
            }
        }
        String verweise = "SELECT k.table_name, k.column_name, z.table_name FROM information_schema.key_column_usage k "
                        + "JOIN information_schema.referential_constraints r ON r.constraint_schema = k.constraint_schema "
                        + "AND r.constraint_name = k.constraint_name "
                        + "JOIN information_schema.constraint_column_usage z ON z.constraint_schema = r.unique_constraint_schema "
                        + "AND z.constraint_name = r.unique_constraint_name WHERE k.table_schema = current_schema()";
        @SuppressWarnings("unchecked")
        List<Object[]> fks = em.createNativeQuery(verweise).getResultList();
        fks.forEach(z -> fremdschluessel.put(schluessel(z[0].toString(), z[1].toString()),
                z[2].toString().toLowerCase(Locale.ROOT)));

        persisterJeTabelle = new HashMap<>();
        SessionFactoryImplementor fabrik = em.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class);
        fabrik.getMappingMetamodel().forEachEntityDescriptor(p -> {
            if (!p.isAbstract() && !Modifier.isAbstract(p.getMappedClass().getModifiers())
                    && p.getIdentifierMapping() instanceof BasicEntityIdentifierMapping id) {
                persisterJeTabelle.putIfAbsent(tabelle(id.getContainingTableExpression()), p);
            }
        });
    }

    private boolean istPflicht(String tabelle, String spalte) {
        return pflichtspalten.contains(schluessel(tabelle, spalte));
    }

    private static String schluessel(String tabelle, String spalte) {
        return tabelle(tabelle) + "." + spalte.replace("\"", "").replace("`", "").toLowerCase(Locale.ROOT);
    }

    private static String tabelle(String ausdruck) {
        String ohne = ausdruck.replace("\"", "").replace("`", "");
        return ohne.substring(ohne.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /** Freiwillige Langtext-Felder ("Entitaet.feld") einer Entitaet, ueber die Annotation am Feld. */
    private Set<String> freiwilligeLangtextFelder(EntityPersister persister) {
        Set<String> felder = new TreeSet<>();
        persister.forEachAttributeMapping(am -> {
            if (am instanceof BasicAttributeMapping b && b.isInsertable()
                    && istLangtext(persister.getMappedClass(), am.getAttributeName())
                    && b.isNullable() && !istPflicht(b.getContainingTableExpression(), b.getSelectionExpression())) {
                felder.add(persister.getEntityName() + "." + am.getAttributeName());
            }
        });
        return felder;
    }

    private static boolean istLangtext(Class<?> klasse, String feldname) {
        for (Class<?> k = klasse; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                Field feld = k.getDeclaredField(feldname);
                JdbcTypeCode code = feld.getAnnotation(JdbcTypeCode.class);
                return code != null && (code.value() == SqlTypes.LONG32VARCHAR || code.value() == SqlTypes.LONG32VARBINARY);
            } catch (NoSuchFieldException e) {
                // in der Oberklasse weitersuchen
            }
        }
        return false;
    }

    private static SQLException sqlFehler(Throwable fehler) {
        for (Throwable t = fehler; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql;
            }
        }
        return null;
    }

    /**
     * Verletzt nur eine Regel, die die Beispieldaten nicht kennen: Verweis (23503),
     * Dublette (23505) oder CHECK (23514). NICHT dazu gehoert NOT NULL (23502): alle
     * NOT-NULL-Spalten werden befuellt - fehlt trotzdem ein Wert, weicht das Schema einer Datenbank
     * vom Mapping ab, und genau das soll der Test finden.
     */
    private static boolean nurBeispieldaten(SQLException sql) {
        String zustand = sql.getSQLState() == null ? "" : sql.getSQLState();
        return istCheckVerletzung(sql) || zustand.equals("23503") || zustand.equals("23505");
    }

    /** CHECK-Verletzung: SQLState 23514. */
    private static boolean istCheckVerletzung(SQLException sql) {
        return sql != null && "23514".equals(sql.getSQLState());
    }

    private static String ursache(Throwable fehler) {
        Throwable aktuell = fehler;
        while (aktuell.getCause() != null && aktuell.getCause() != aktuell) {
            aktuell = aktuell.getCause();
        }
        SQLException sql = sqlFehler(fehler);
        return aktuell.getClass().getSimpleName() + (sql != null ? " [" + sql.getSQLState() + "]" : "") + ": "
                + aktuell.getMessage();
    }

    /** Die Beispieldaten reichen nicht aus - kein Fehler der Datenbank. */
    private static final class Befuellungsfehler extends RuntimeException {
        Befuellungsfehler(String meldung) {
            super(meldung);
        }
    }
}

package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.internal.BasicAttributeMapping;
import org.hibernate.metamodel.mapping.internal.ToOneAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import jakarta.persistence.criteria.Nulls;
import org.hibernate.query.sqm.tree.SqmStatement;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.query.sqm.tree.select.SqmQuerySpec;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;
import org.hibernate.query.sqm.tree.select.SqmSortSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * Sortieren Abfragen nach einer Spalte, die leer sein darf, ohne festzulegen, wohin
 * die leeren Werte gehoeren?
 *
 * <p>PostgreSQL sortiert {@code NULL} als groessten Wert - genau umgekehrt wie die
 * Nutzer es von frueher (MySQL) kennen: leer aufsteigend oben, absteigend unten.
 * "Neueste zuerst" zeigte sonst zuerst alle Eintraege ohne Datum, und wer "den ersten
 * Treffer" nimmt, bekommt einen anderen. Jede {@code @Query} (JPQL) und jede abgeleitete
 * Abfrage ({@code ...OrderBy...}) wird hier gegen das echte Schema geprueft: Sortiert
 * sie nach einer Spalte ohne {@code NOT NULL}, muss sie {@code NULLS FIRST} bzw.
 * {@code NULLS LAST} angeben - wie gewohnt also {@code ASC NULLS FIRST} und
 * {@code DESC NULLS LAST}.
 *
 * <p>Ausnahmen stehen mit Begruendung in {@link #NIE_LEER} und {@link #OHNE_LEERE_WERTE}. Native Abfragen prueft
 * der Test nicht - dort die Angabe von Hand ergaenzen.
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FlywayStartSetupConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class DatenbankKompatibilitaetSortierungTest {

    private static final Logger LOG = LoggerFactory.getLogger(DatenbankKompatibilitaetSortierungTest.class);

    /** Spalten, die zwar NULL erlauben, aber nie leer sind - mit Begruendung. */
    static final Map<String, String> NIE_LEER = Map.of(
            "email.sent_at", "Import und Versand setzen das Datum immer; grosse Tabelle - mit NULLS LAST koennte "
                    + "PostgreSQL fuer 'neueste zuerst' den Index idx_email_sent_at nicht mehr nutzen",
            "zeitbuchung.start_zeit", "jede Buchung beginnt mit Startzeit; je Mitarbeiter hoechstens eine offene "
                    + "Buchung (V411); Index fuer 'letzte Buchung'",
            "ausgangs_geschaeftsdokument_audit.chain_index", "Pruefkette (GoBD) vergibt jedem Eintrag seinen Index",
            "beleg_audit.chain_index", "Pruefkette (GoBD) vergibt jedem Eintrag seinen Index",
            "email_draft.updated_at", "@PrePersist/@PreUpdate setzt den Wert immer",
            "email_signature_image.sort_order", "int-Feld in der Entitaet, wird nie NULL geschrieben");

    /** Abfragen, die leere Werte schon im WHERE ausschliessen. Schluessel: "Repository.methode spalte". */
    static final Set<String> OHNE_LEERE_WERTE = Set.of(
            "EmailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc email.deleted_at",
            "DokumentFreigabeRepository.findKuerzlichAkzeptiert dokument_freigabe.akzeptiert_am",
            "EmailAttachmentRepository.findDokumentAnhaengeMitGleicherGroesse email_attachment.size_bytes",
            "LieferantDokumentRepository.findDateiDuplikate lieferant_dokument.gespeicherter_dateiname",
            "LieferantenArtikelPreiseRepository.findeAktuellePreise lieferanten_artikel_preise.preis");

    @DynamicPropertySource
    static void datenbank(DynamicPropertyRegistry r) {
        KompatibilitaetsDatenbank.eintragen(r);
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    EntityManager em;

    @Test
    @DisplayName("Sortierung nach Spalten, die leer sein duerfen, legt fest, wohin leere Werte gehoeren")
    void leereWerteHabenFesteSortierung() {
        Set<String> pflichtspalten = pflichtspalten();
        SessionFactoryImplementor fabrik = em.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class);
        Set<String> ohneAngabe = new TreeSet<>();
        int geprueft = 0;

        for (Object repository : context.getBeansOfType(Repository.class).values()) {
            for (Class<?> schnittstelle : repository.getClass().getInterfaces()) {
                if (!schnittstelle.getPackageName().startsWith("org.example")
                        || !Repository.class.isAssignableFrom(schnittstelle)) {
                    continue;
                }
                Class<?> domaene = AbstractRepositoryMetadata.getMetadata(schnittstelle).getDomainType();
                for (Method methode : schnittstelle.getDeclaredMethods()) {
                    String name = schnittstelle.getSimpleName() + "." + methode.getName();
                    Query abfrage = AnnotatedElementUtils.findMergedAnnotation(methode, Query.class);
                    if (abfrage != null && !abfrage.nativeQuery() && !abfrage.value().isBlank()) {
                        geprueft += pruefeJpql(fabrik, abfrage.value(), name, pflichtspalten, ohneAngabe);
                    } else if (abfrage == null && !methode.isDefault() && methode.getName().contains("OrderBy")) {
                        geprueft += pruefeAbgeleitet(fabrik, domaene, methode.getName(), name, pflichtspalten, ohneAngabe);
                    }
                }
            }
        }

        ohneAngabe.removeIf(eintrag -> OHNE_LEERE_WERTE.contains(eintrag)
                || NIE_LEER.containsKey(eintrag.substring(eintrag.indexOf(' ') + 1)));
        LOG.info("[kompat] {} Sortierungen geprueft, ohne Angabe fuer leere Werte: {}", geprueft, ohneAngabe);
        assertThat(ohneAngabe)
                .as("Sortierung nach Spalte, die leer sein darf - bitte NULLS FIRST (bei ASC) bzw. NULLS LAST (bei DESC)"
                        + " angeben oder begruendet in NIE_LEER/OHNE_LEERE_WERTE eintragen")
                .isEmpty();
        assertThat(geprueft).as("keine Sortierungen gefunden - Test waere wertlos").isGreaterThan(100);
    }

    private int pruefeJpql(SessionFactoryImplementor fabrik, String jpql, String name, Set<String> pflichtspalten,
            Set<String> ohneAngabe) {
        SqmStatement<?> anweisung;
        try {
            // Spring Data erlaubt "LIKE :wert%" - das ist kein HQL, hat aber auch keine Sortierung
            anweisung = fabrik.getQueryEngine().getHqlTranslator().translate(jpql.replaceAll(":(\\w++)%", ":$1"), null);
        } catch (RuntimeException e) {
            throw new IllegalStateException(name + ": " + e.getMessage(), e);
        }
        if (!(anweisung instanceof SqmSelectStatement<?> auswahl) || !(auswahl.getQueryPart() instanceof SqmQuerySpec<?> teil)
                || teil.getOrderByClause() == null) {
            return 0;
        }
        int anzahl = 0;
        for (SqmSortSpecification sortierung : teil.getOrderByClause().getSortSpecifications()) {
            anzahl++;
            if (sortierung.getNullPrecedence() != null && sortierung.getNullPrecedence() != Nulls.NONE) {
                continue;
            }
            if (sortierung.getSortExpression() instanceof SqmPath<?> pfad && pfad.getLhs() != null) {
                String entitaet = pfad.getLhs().getReferencedPathSource().getPathType().getTypeName();
                String spalte = spalte(fabrik, entitaet, pfad.getNavigablePath().getLocalName());
                if (spalte != null && !pflichtspalten.contains(spalte)) {
                    ohneAngabe.add(name + " " + spalte);
                }
            }
        }
        return anzahl;
    }

    private int pruefeAbgeleitet(SessionFactoryImplementor fabrik, Class<?> domaene, String methode, String name,
            Set<String> pflichtspalten, Set<String> ohneAngabe) {
        Sort sortierung;
        try {
            sortierung = new PartTree(methode, domaene).getSort();
        } catch (RuntimeException e) {
            return 0;
        }
        int anzahl = 0;
        for (Sort.Order order : sortierung) {
            anzahl++;
            String[] teile = order.getProperty().split("\\.");
            String entitaet = domaene.getName();
            for (int i = 0; i < teile.length - 1 && entitaet != null; i++) {
                entitaet = zielEntitaet(fabrik, entitaet, teile[i]);
            }
            String spalte = entitaet == null ? null : spalte(fabrik, entitaet, teile[teile.length - 1]);
            if (spalte != null && !pflichtspalten.contains(spalte)) {
                // Abgeleitete Abfragen koennen das nicht angeben - als @Query mit NULLS FIRST/LAST schreiben
                ohneAngabe.add(name + " " + spalte);
            }
        }
        return anzahl;
    }

    private static String zielEntitaet(SessionFactoryImplementor fabrik, String entitaet, String attribut) {
        AttributeMapping am = persister(fabrik, entitaet) == null ? null : persister(fabrik, entitaet).findAttributeMapping(attribut);
        if (am instanceof ToOneAttributeMapping verweis) {
            EntityMappingType ziel = verweis.getEntityMappingType();
            return ziel.getEntityName();
        }
        return null;
    }

    /** "tabelle.spalte" eines einfachen Attributs, sonst {@code null}. */
    private static String spalte(SessionFactoryImplementor fabrik, String entitaet, String attribut) {
        EntityPersister persister = persister(fabrik, entitaet);
        if (persister == null) {
            return null;
        }
        AttributeMapping am = persister.findAttributeMapping(attribut);
        if (!(am instanceof BasicAttributeMapping einfach)) {
            return null;
        }
        String tabelle = einfach.getContainingTableExpression().replace("\"", "").replace("`", "");
        tabelle = tabelle.substring(tabelle.lastIndexOf('.') + 1);
        return (tabelle + "." + einfach.getSelectionExpression().replace("\"", "").replace("`", "")).toLowerCase(Locale.ROOT);
    }

    private static EntityPersister persister(SessionFactoryImplementor fabrik, String entitaet) {
        try {
            return fabrik.getMappingMetamodel().getEntityDescriptor(entitaet);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Set<String> pflichtspalten() {
        String schema = "current_schema()";
        Set<String> spalten = new HashSet<>();
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> zeilen = em.createNativeQuery("SELECT table_name, column_name FROM information_schema.columns "
                + "WHERE is_nullable = 'NO' AND table_schema = " + schema).getResultList();
        zeilen.forEach(z -> spalten.add((z[0] + "." + z[1]).toLowerCase(Locale.ROOT)));
        return spalten;
    }
}

package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.BasicType;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.spi.TypeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import jakarta.persistence.EntityManager;

/**
 * Fehler aus dem ersten Betrieb auf PostgreSQL, die {@link DatenbankKompatibilitaetTest} nicht
 * sah - gegen echtes PostgreSQL, genau wie dort (gleiche Einrichtung, gleiche
 * System-Properties {@code kompat.db.*}, in der CI im Job "Datenbank-Kompatibilitaet").
 *
 * <ul>
 * <li>Leere lange Texte/Binärdaten ({@code LONG32VARCHAR}/{@code LONG32VARBINARY}): PostgreSQL
 * lehnte {@code setNull} mit Hibernates eigenem Typcode ab - der Mail-Import scheiterte an jeder
 * Nachricht ohne HTML-Teil ({@code PostgresLangtextTypeContributor}).</li>
 * <li>Lieferantensuche: {@code DISTINCT} plus Sortierung ohne Groß-/Kleinschreibung warf auf
 * PostgreSQL Fehler 42P10 ({@link LieferantenSuche}).</li>
 * </ul>
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FlywayStartSetupConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class DatenbankKompatibilitaetBetriebTest {

    @DynamicPropertySource
    static void datenbank(DynamicPropertyRegistry r) {
        KompatibilitaetsDatenbank.eintragen(r);
    }

    @Autowired
    EntityManager em;
    @Autowired
    LieferantenRepository lieferanten;

    @Test
    @Transactional
    void leererLangerTextUndLeereBinaerdatenLassenSichBinden() {
        TypeConfiguration typen = em.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class)
                .getTypeConfiguration();
        // Genau die Typen, die Entities mit @JdbcTypeCode(SqlTypes.LONG32...) bekommen
        BasicType<String> langtext = typen.getBasicTypeRegistry().resolve(
                typen.getJavaTypeRegistry().getDescriptor(String.class),
                typen.getJdbcTypeRegistry().getDescriptor(SqlTypes.LONG32VARCHAR));
        BasicType<byte[]> binaer = typen.getBasicTypeRegistry().resolve(
                typen.getJavaTypeRegistry().getDescriptor(byte[].class),
                typen.getJdbcTypeRegistry().getDescriptor(SqlTypes.LONG32VARBINARY));

        Object treffer = em.createNativeQuery("SELECT COUNT(*) FROM (SELECT 1 AS x) t WHERE :text IS NULL AND :daten IS NULL")
                .unwrap(NativeQuery.class)
                .setParameter("text", null, langtext)
                .setParameter("daten", null, binaer)
                .getSingleResult();

        assertThat(((Number) treffer).intValue()).isEqualTo(1);
    }

    @Test
    void lieferantensucheMitSortierungOhneGrossKleinschreibung() {
        String kennung = UUID.randomUUID().toString().substring(0, 8);
        Lieferanten wuerth = new Lieferanten();
        wuerth.setLieferantenname("Würth Testlauf " + kennung);
        wuerth.setKundenEmails(List.of("einkauf-" + kennung + "@example.com", "rechnung-" + kennung + "@example.com"));
        lieferanten.saveAndFlush(wuerth);

        // Freitext, klein geschrieben (die Liste sortiert per lower(name) - auf PostgreSQL vorher 42P10)
        List<Lieferanten> perName = lieferanten.findAll(
                LieferantenSuche.spezifikation("würth testlauf " + kennung, null, null, null, null),
                PageRequest.of(0, 50, LieferantenSuche.SORTIERUNG)).getContent();
        // E-Mail-Suche passt auf beide Adressen - trotzdem genau ein Treffer
        List<Lieferanten> perMail = lieferanten.findAll(
                LieferantenSuche.spezifikation(null, null, null, null, kennung + "@example"),
                PageRequest.of(0, 50, LieferantenSuche.SORTIERUNG)).getContent();

        assertThat(perName).extracting(Lieferanten::getId).containsExactly(wuerth.getId());
        assertThat(perMail).extracting(Lieferanten::getId).containsExactly(wuerth.getId());
    }
}

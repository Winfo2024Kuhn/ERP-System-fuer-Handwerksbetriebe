package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieLoeschenDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

/**
 * Ordner löschen gegen echtes PostgreSQL mit allen Fremdschlüsseln: Die Mockito-Tests sehen nicht,
 * in welcher Reihenfolge Hibernate die DELETEs abschickt. Geprüft wird das Umziehen, wenn ein Projekt
 * den Ziel-Ordner schon hat (Zuordnungen zusammenlegen), und das Löschen mit Inhalt.
 *
 * <p>Läuft nur mit {@code -Dkompat.db.url=jdbc:postgresql://…} (Wegwerf-Datenbank, siehe
 * BACKEND_ARCH.md), in der CI im Job „Datenbank-Kompatibilitaet (PostgreSQL)". Der Service läuft in
 * seiner eigenen, echt committeten Transaktion; die Testdaten räumt {@link #aufraeumen()} weg.</p>
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ FlywayStartSetupConfig.class, ProduktkategorieVerwaltungService.class, ProduktkategorieService.class,
        ProduktkategorieMapper.class, ZeitbuchungAuditService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class ProduktkategorieLoeschenPostgresTest {

    @DynamicPropertySource
    static void datenbank(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getProperty("kompat.db.url"));
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.flyway.locations", () -> "classpath:db/postgresql/migration");
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET client_encoding TO 'UTF8'");
        r.add("spring.datasource.username", () -> System.getProperty("kompat.db.user", "kompat"));
        r.add("spring.datasource.password", () -> System.getProperty("kompat.db.pass", "kompat"));
    }

    @Autowired ProduktkategorieVerwaltungService service;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager tm;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DateiSpeicherService dateiSpeicherService;
    @MockitoBean AngemeldeterMitarbeiterService angemeldeterMitarbeiterService;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> ordnerIds = new ArrayList<>();
    private Long mitarbeiterId;
    private Long projektId;

    @BeforeEach
    void mitarbeiter() {
        Mitarbeiter max = inTx(() -> {
            Mitarbeiter m = new Mitarbeiter();
            m.setVorname("Max");
            m.setNachname("Mustermann " + suffix);
            em.persist(m);
            return m;
        });
        mitarbeiterId = max.getId();
        when(angemeldeterMitarbeiterService.bearbeiter(any())).thenReturn(max);
    }

    @AfterEach
    void aufraeumen() {
        if (projektId != null) {
            jdbc.update("DELETE FROM zeitbuchung_audit WHERE projekt_id = ?", projektId);
            jdbc.update("DELETE FROM zeitbuchung WHERE projekt_id = ?", projektId);
            jdbc.update("DELETE FROM projekt_produktkategorie WHERE projekt_id = ?", projektId);
            jdbc.update("DELETE FROM projekt WHERE id = ?", projektId);
        }
        for (Long id : ordnerIds) {
            jdbc.update("DELETE FROM leistung WHERE kategorie_id = ?", id);
        }
        // Unterordner zuerst
        for (int i = ordnerIds.size() - 1; i >= 0; i--) {
            jdbc.update("DELETE FROM produktkategorie WHERE id = ?", ordnerIds.get(i));
        }
        jdbc.update("DELETE FROM zeitbuchung_audit WHERE geaendert_von_mitarbeiter_id = ?", mitarbeiterId);
        jdbc.update("DELETE FROM mitarbeiter WHERE id = ?", mitarbeiterId);
    }

    @Test
    void umziehenLegtZuordnungenZusammenUndHaengtZeitenProtokolliertUm() {
        Long[] ids = inTx(() -> {
            Produktkategorie tore = ordner("Tore", null);
            Produktkategorie fliesen = ordner("Fliesen", null);
            Projekt projekt = projekt();
            ProjektProduktkategorie alt = zuordnung(projekt, tore, "2");
            ProjektProduktkategorie ziel = zuordnung(projekt, fliesen, "3");
            Zeitbuchung montag = buchung(projekt, alt);
            leistung("Gartentor", tore);
            em.flush();
            return new Long[] { tore.getId(), fliesen.getId(), ziel.getId(), montag.getId() };
        });

        service.loesche(ids[0], new KategorieLoeschenDto("UMZIEHEN", ids[1]), null);

        assertThat(zaehle("produktkategorie", "id", ids[0])).isZero();
        List<Map<String, Object>> zuordnungen = jdbc.queryForList(
                "SELECT id, produktkategorie_id, menge FROM projekt_produktkategorie WHERE projekt_id = ?", projektId);
        assertThat(zuordnungen).singleElement().satisfies(z -> {
            assertThat(z.get("id")).isEqualTo(ids[2]);
            assertThat((BigDecimal) z.get("menge")).isEqualByComparingTo("5");
        });
        assertThat(jdbc.queryForObject("SELECT projekt_produktkategorie_id FROM zeitbuchung WHERE id = ?", Long.class,
                ids[3])).isEqualTo(ids[2]);
        assertThat(zaehle("zeitbuchung_audit", "zeitbuchung_id", ids[3])).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leistung WHERE kategorie_id = ?", Long.class, ids[1]))
                .isEqualTo(1);
    }

    @Test
    void allesLoeschenEntferntAstUndLoestZeitenVonDerKategorie() {
        Long[] ids = inTx(() -> {
            Produktkategorie metall = ordner("Metallbau", null);
            Produktkategorie gelaender = ordner("Geländer", metall);
            Projekt projekt = projekt();
            ProjektProduktkategorie ppk = zuordnung(projekt, gelaender, "4");
            Zeitbuchung montag = buchung(projekt, ppk);
            leistung("Handlauf", gelaender);
            em.flush();
            return new Long[] { metall.getId(), gelaender.getId(), montag.getId() };
        });

        service.loesche(ids[0], new KategorieLoeschenDto("ALLES", null), null);

        assertThat(zaehle("produktkategorie", "id", ids[0])).isZero();
        assertThat(zaehle("produktkategorie", "id", ids[1])).isZero();
        assertThat(zaehle("projekt_produktkategorie", "projekt_id", projektId)).isZero();
        assertThat(zaehle("leistung", "kategorie_id", ids[1])).isZero();
        assertThat(jdbc.queryForObject("SELECT projekt_produktkategorie_id FROM zeitbuchung WHERE id = ?", Long.class,
                ids[2])).isNull();
        assertThat(zaehle("zeitbuchung_audit", "zeitbuchung_id", ids[2])).isEqualTo(1);
    }

    private Produktkategorie ordner(String name, Produktkategorie parent) {
        Produktkategorie k = new Produktkategorie();
        k.setBezeichnung(name + " " + suffix);
        k.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        k.setUebergeordneteKategorie(parent);
        em.persist(k);
        ordnerIds.add(k.getId());
        return k;
    }

    private Projekt projekt() {
        Projekt p = new Projekt();
        p.setBauvorhaben("Neubau Mustermann");
        p.setAuftragsnummer("P-" + suffix);
        p.setAnlegedatum(LocalDate.of(2026, 3, 1));
        p.setBruttoPreis(BigDecimal.valueOf(1190));
        em.persist(p);
        projektId = p.getId();
        return p;
    }

    private ProjektProduktkategorie zuordnung(Projekt projekt, Produktkategorie ordner, String menge) {
        ProjektProduktkategorie ppk = new ProjektProduktkategorie();
        ppk.setProjekt(projekt);
        ppk.setProduktkategorie(ordner);
        ppk.setMenge(new BigDecimal(menge));
        projekt.getProjektProduktkategorien().add(ppk);
        em.persist(ppk);
        return ppk;
    }

    private Zeitbuchung buchung(Projekt projekt, ProjektProduktkategorie ppk) {
        Zeitbuchung z = new Zeitbuchung();
        z.setMitarbeiter(em.find(Mitarbeiter.class, mitarbeiterId));
        z.setProjekt(projekt);
        z.setProjektProduktkategorie(ppk);
        LocalDateTime start = LocalDateTime.of(2026, 3, 2, 7, 0).plusMinutes(ThreadLocalRandom.current().nextInt(100_000));
        z.setStartZeit(start);
        z.setEndeZeit(start.plusHours(4));
        z.setAnzahlInStunden(new BigDecimal("4"));
        em.persist(z);
        return z;
    }

    private void leistung(String name, Produktkategorie ordner) {
        Leistung l = new Leistung();
        l.setBezeichnung(name);
        l.setEinheit(Verrechnungseinheit.STUECK);
        l.setKategorie(ordner);
        em.persist(l);
    }

    private <T> T inTx(Supplier<T> arbeit) {
        return new TransactionTemplate(tm).execute(status -> arbeit.get());
    }

    private long zaehle(String tabelle, String spalte, Long wert) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabelle + " WHERE " + spalte + " = ?", Long.class, wert);
    }
}

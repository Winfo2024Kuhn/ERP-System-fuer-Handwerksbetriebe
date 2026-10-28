package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.UeberschneidungErgebnis;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

/**
 * "Handy gewinnt" gegen echtes PostgreSQL mit allen Migrationen: Die Mockito-Tests sehen die
 * Unique-Indizes nicht ({@code uk_zeitbuchung_mitarbeiter_start}, {@code uk_zeitbuchung_aktiv_pro_mitarbeiter}).
 * Hier läuft der Offline-Tag eines Mitarbeiters so durch, wie die Warteschlange ihn nachreicht
 * (Anstechen, Pause, Feierabend), jeweils in eigener Transaktion – gegen einen ganztägigen
 * Büro-Eintrag, der dabei geteilt und gekürzt wird.
 *
 * <p>Läuft nur mit {@code -Dkompat.db.url=jdbc:postgresql://…} (Wegwerf-Datenbank, siehe
 * BACKEND_ARCH.md), in der CI im Job „Datenbank-Kompatibilitaet (PostgreSQL)".</p>
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ FlywayStartSetupConfig.class, ZeitbuchungUeberschneidungService.class, ZeitbuchungAuditService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class ZeitbuchungUeberschneidungPostgresTest {

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

    private static final LocalDateTime TAG = LocalDateTime.of(2026, 10, 28, 0, 0);

    @Autowired ZeitbuchungUeberschneidungService service;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager tm;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MonatsSaldoService monatsSaldoService;

    private TransactionTemplate tx;
    private Long mitarbeiterId;

    private static LocalDateTime um(int stunde, int minute) {
        return TAG.withHour(stunde).withMinute(minute);
    }

    @BeforeEach
    void anlegen() {
        tx = new TransactionTemplate(tm);
        mitarbeiterId = tx.execute(status -> {
            Mitarbeiter max = new Mitarbeiter();
            max.setVorname("Max");
            max.setNachname("Mustermann");
            max.setAktiv(true);
            em.persist(max);
            return max.getId();
        });
    }

    @AfterEach
    void aufraeumen() {
        jdbc.update("DELETE FROM zeitbuchung_ueberschneidung WHERE mitarbeiter_id = ?", mitarbeiterId);
        jdbc.update("DELETE FROM zeitbuchung_audit WHERE mitarbeiter_id = ?", mitarbeiterId);
        jdbc.update("DELETE FROM zeitbuchung WHERE mitarbeiter_id = ?", mitarbeiterId);
        jdbc.update("DELETE FROM mitarbeiter WHERE id = ?", mitarbeiterId);
    }

    private Long buchung(LocalDateTime start, LocalDateTime ende, ErfassungsQuelle quelle, BuchungsTyp typ) {
        return tx.execute(status -> {
            Mitarbeiter max = em.find(Mitarbeiter.class, mitarbeiterId);
            Zeitbuchung z = new Zeitbuchung();
            z.setMitarbeiter(max);
            z.setStartZeit(start);
            z.setEndeZeit(ende);
            z.setTyp(typ);
            z.setErfasstVon(max);
            z.setErfasstVia(quelle);
            z.setErfasstAm(LocalDateTime.now());
            z.setVersion(1);
            em.persist(z);
            em.flush();
            return z.getId();
        });
    }

    /** Wie das Anstechen am Server: erst Büro-Einträge weichen lassen, dann die Handy-Buchung speichern. */
    private Long handyAnstechen(LocalDateTime start, BuchungsTyp typ) {
        List<ZeitbuchungUeberschneidung> meldungen = tx.execute(status ->
                service.vorNeuerHandyBuchung(em.find(Mitarbeiter.class, mitarbeiterId), start));
        Long id = buchung(start, null, ErfassungsQuelle.MOBILE_APP, typ);
        tx.executeWithoutResult(status -> service.handyBuchungZuordnen(
                meldungen.stream().map(m -> em.merge(m)).toList(), id));
        return id;
    }

    /** Wie Feierabend/Wechsel am Server: Ende setzen, dann Überschneidungen auflösen. */
    private void handyBeenden(Long id, LocalDateTime ende) {
        tx.executeWithoutResult(status -> {
            Zeitbuchung z = em.find(Zeitbuchung.class, id);
            z.setEndeZeit(ende);
            em.flush();
            service.nachHandyBuchung(z, em.find(Mitarbeiter.class, mitarbeiterId));
        });
    }

    private List<Map<String, Object>> buchungen() {
        return jdbc.queryForList("SELECT start_zeit, ende_zeit, erfasst_via FROM zeitbuchung "
                + "WHERE mitarbeiter_id = ? ORDER BY start_zeit", mitarbeiterId);
    }

    @Test
    void offlineTagTeiltUndKuerztDenGanztaegigenBueroEintrag() {
        buchung(um(7, 0), um(16, 0), ErfassungsQuelle.DESKTOP, BuchungsTyp.ARBEIT);

        // Offline gestempelt, später in dieser Reihenfolge nachgereicht:
        Long arbeit = handyAnstechen(um(7, 30), BuchungsTyp.ARBEIT);
        handyBeenden(arbeit, um(10, 0));
        Long pause = handyAnstechen(um(10, 0), BuchungsTyp.PAUSE);
        handyBeenden(pause, um(10, 30));

        assertThat(buchungen()).extracting(b -> b.get("start_zeit").toString() + "|" + b.get("ende_zeit")
                + "|" + b.get("erfasst_via")).containsExactly(
                        "2026-10-28 07:00:00.0|2026-10-28 07:30:00.0|DESKTOP",
                        "2026-10-28 07:30:00.0|2026-10-28 10:00:00.0|MOBILE_APP",
                        "2026-10-28 10:00:00.0|2026-10-28 10:30:00.0|MOBILE_APP",
                        "2026-10-28 10:30:00.0|2026-10-28 16:00:00.0|DESKTOP");

        List<ZeitbuchungUeberschneidung> meldungen = tx.execute(status ->
                service.ungeleseneFuerMitarbeiter(mitarbeiterId));
        assertThat(meldungen).singleElement().satisfies(m -> {
            assertThat(m.getErgebnis()).isEqualTo(UeberschneidungErgebnis.GETEILT);
            assertThat(m.getHandyVon()).isEqualTo(um(7, 30));
            assertThat(m.getHandyBis()).isEqualTo(um(10, 30));
        });
        String text = tx.execute(status -> service.anzeigeFuerMitarbeiter(meldungen.getFirst()).text());
        assertThat(text).isEqualTo("Deine Handy-Buchung 07:30–10:30 hatte Vorrang. Büro-Eintrag 07:00–16:00 "
                + "→ jetzt 07:00–07:30 und 10:30–16:00.");
    }

    @Test
    void bueroEintragGanzInDerHandyZeitWirdStorniertUndBleibtImProtokoll() {
        Long buero = buchung(um(11, 0), um(12, 0), ErfassungsQuelle.DESKTOP, BuchungsTyp.ARBEIT);

        Long handy = handyAnstechen(um(10, 45), BuchungsTyp.ARBEIT);
        handyBeenden(handy, um(12, 30));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM zeitbuchung WHERE id = ?", Integer.class, buero))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM zeitbuchung_audit WHERE zeitbuchung_id = ? "
                + "AND aktion = 'STORNIERT'", Integer.class, buero)).isEqualTo(1);
        assertThat(buchungen()).hasSize(1);
    }

    @Test
    void imBueroLaufendeBuchungEndetBeimAnstechenOhneIndexVerletzung() {
        buchung(um(8, 0), null, ErfassungsQuelle.DESKTOP, BuchungsTyp.ARBEIT);

        Long handy = handyAnstechen(um(9, 0), BuchungsTyp.ARBEIT);

        assertThat(jdbc.queryForList("SELECT id FROM zeitbuchung WHERE mitarbeiter_id = ? AND ende_zeit IS NULL",
                Long.class, mitarbeiterId)).containsExactly(handy);
        assertThat(buchungen().getFirst().get("ende_zeit").toString()).isEqualTo("2026-10-28 09:00:00.0");
    }
}

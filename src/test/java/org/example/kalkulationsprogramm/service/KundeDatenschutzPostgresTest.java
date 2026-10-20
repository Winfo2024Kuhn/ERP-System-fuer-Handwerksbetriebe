package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;
import java.util.UUID;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.KontaktRufnummer;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.KundeNotiz;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Sprachnachricht;
import org.example.kalkulationsprogramm.domain.TelefonAnruf;
import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
 * {@link KundeDatenschutzService#loesche} und {@link KundeDatenschutzService#sperre} gegen echtes
 * PostgreSQL (Issue #264): Fremdschlüssel-Reihenfolge beim Löschen, die Bulk-Updates auf Termine,
 * Anrufe und Sprachnachrichten, Notizen, Rufnummern und E-Mail-Adressen. Die Mockito-Tests sehen
 * davon nichts.
 *
 * <p>Läuft nur mit {@code -Dkompat.db.url=jdbc:postgresql://…} (Wegwerf-Datenbank, siehe
 * BACKEND_ARCH.md), in der CI im Job „Datenbank-Kompatibilitaet (PostgreSQL)". Der Service läuft
 * in seiner eigenen, echt committeten Transaktion; die Testdaten räumt {@link #aufraeumen()} weg.</p>
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ FlywayStartSetupConfig.class, KundeDatenschutzService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class KundeDatenschutzPostgresTest {

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

    @Autowired
    KundeDatenschutzService service;
    @Autowired
    EntityManager em;
    @Autowired
    PlatformTransactionManager tm;
    @Autowired
    JdbcTemplate jdbc;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private Long kundeId;
    private Long[] abgeloeste = new Long[0];

    @AfterEach
    void aufraeumen() {
        if (kundeId == null) {
            return;
        }
        jdbc.update("DELETE FROM kunde_loeschprotokoll WHERE kunde_id = ?", kundeId);
        jdbc.update("DELETE FROM ausgangs_geschaeftsdokument WHERE kunde_id = ?", kundeId);
        jdbc.update("DELETE FROM kalender_eintrag WHERE kunde_id = ?", kundeId);
        jdbc.update("DELETE FROM sprachnachricht WHERE kunde_id = ?", kundeId);
        jdbc.update("DELETE FROM telefon_anruf WHERE kunde_id = ?", kundeId);
        // Der Service löst diese Zeilen vom Kunden (kunde_id = NULL): per ID wegräumen
        // (ids = Kunde, Termin, Anruf, ggf. Sprachnachricht) - Sprachnachricht zuerst (FK auf den Anruf)
        if (abgeloeste.length > 3) {
            jdbc.update("DELETE FROM sprachnachricht WHERE id = ?", abgeloeste[3]);
        }
        if (abgeloeste.length > 2) {
            jdbc.update("DELETE FROM telefon_anruf WHERE id = ?", abgeloeste[2]);
            jdbc.update("DELETE FROM kalender_eintrag WHERE id = ?", abgeloeste[1]);
        }
        jdbc.update("DELETE FROM kontakt_rufnummer WHERE kunde_id = ?", kundeId);
        jdbc.update("DELETE FROM kunde_notiz WHERE kunde_id = ?", kundeId);
        jdbc.update("DELETE FROM projekt_kunden_emails WHERE projekt_id IN (SELECT id FROM projekt WHERE kunden_id = ?)", kundeId);
        jdbc.update("DELETE FROM projekt WHERE kunden_id = ?", kundeId);
        jdbc.update("DELETE FROM kunden_emails WHERE kunden_id = ?", kundeId);
        jdbc.update("DELETE FROM kunde WHERE id = ?", kundeId);
    }

    @Test
    void loeschenEntferntKundeUndNebendatenUndLoestTermineAnrufeSprachnachrichten() {
        Long[] ids = inTx(() -> {
            Kunde kunde = neuerKunde();
            notiz(kunde);
            rufnummer(kunde);
            KalenderEintrag termin = termin(kunde);
            TelefonAnruf anruf = anruf(kunde);
            Sprachnachricht nachricht = sprachnachricht(kunde, anruf);
            em.flush();
            return new Long[] { kunde.getId(), termin.getId(), anruf.getId(), nachricht.getId() };
        });
        kundeId = ids[0];
        abgeloeste = ids;

        service.loesche(kundeId, "admin");

        assertThat(zaehle("kunde", "id", kundeId)).isZero();
        assertThat(zaehle("kunde_notiz", "kunde_id", kundeId)).isZero();
        assertThat(zaehle("kontakt_rufnummer", "kunde_id", kundeId)).isZero();
        assertThat(zaehle("kunden_emails", "kunden_id", kundeId)).isZero();
        // kundeBezug wirft, wenn die Zeile fehlt: Termin, Anruf, Sprachnachricht bleiben also bestehen
        assertThat(kundeBezug("kalender_eintrag", ids[1])).isNull();
        assertThat(kundeBezug("telefon_anruf", ids[2])).isNull();
        assertThat(kundeBezug("sprachnachricht", ids[3])).isNull();
        Map<String, Object> protokoll = jdbc.queryForMap(
                "SELECT aktion, ausgefuehrt_von, kundennummer FROM kunde_loeschprotokoll WHERE kunde_id = ?", kundeId);
        assertThat(protokoll).containsEntry("aktion", "GELOESCHT").containsEntry("ausgefuehrt_von", "admin")
                .containsEntry("kundennummer", "K-" + suffix);
    }

    @Test
    void sperrenLoeschtKontaktdatenUndBehaeltNameAnschriftProjektUndRechnung() {
        Long[] ids = inTx(() -> {
            Kunde kunde = neuerKunde();
            notiz(kunde);
            rufnummer(kunde);
            KalenderEintrag termin = termin(kunde);
            TelefonAnruf anruf = anruf(kunde);
            Projekt projekt = abgeschlossenesProjekt(kunde);
            AusgangsGeschaeftsDokument rechnung = rechnung(kunde, projekt);
            em.flush();
            return new Long[] { kunde.getId(), termin.getId(), anruf.getId(), projekt.getId(), rechnung.getId() };
        });
        kundeId = ids[0];
        abgeloeste = ids;

        service.sperre(kundeId, "Löschwunsch per Brief", "admin");

        Map<String, Object> kunde = jdbc.queryForMap("SELECT * FROM kunde WHERE id = ?", kundeId);
        assertThat(kunde.get("name")).isEqualTo("Max Mustermann " + suffix);
        assertThat(kunde.get("strasse")).isEqualTo("Musterstraße 1");
        assertThat(kunde.get("plz")).isEqualTo("12345");
        assertThat(kunde.get("ort")).isEqualTo("Musterstadt");
        assertThat(kunde.get("telefon")).isNull();
        assertThat(kunde.get("mobiltelefon")).isNull();
        assertThat(kunde.get("ansprechspartner")).isNull();
        assertThat(kunde.get("gesperrt_am")).isNotNull();
        // Rechnung vom 30.06.2024: 8 Jahre ab Jahresende (länger als das Projekt mit 6 Jahren)
        assertThat(kunde.get("aufbewahren_bis")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2032, 12, 31)));

        assertThat(zaehle("kunden_emails", "kunden_id", kundeId)).isZero();
        assertThat(zaehle("kunde_notiz", "kunde_id", kundeId)).isZero();
        assertThat(zaehle("kontakt_rufnummer", "kunde_id", kundeId)).isZero();
        assertThat(zaehle("projekt_kunden_emails", "projekt_id", ids[3])).isZero();
        assertThat(kundeBezug("kalender_eintrag", ids[1])).isNull();
        assertThat(kundeBezug("telefon_anruf", ids[2])).isNull();
        assertThat(zaehle("projekt", "kunden_id", kundeId)).isEqualTo(1);
        assertThat(zaehle("ausgangs_geschaeftsdokument", "id", ids[4])).isEqualTo(1);
        assertThat(zaehle("ausgangs_geschaeftsdokument", "kunde_id", kundeId)).isEqualTo(1);

        Map<String, Object> protokoll = jdbc.queryForMap(
                "SELECT * FROM kunde_loeschprotokoll WHERE kunde_id = ?", kundeId);
        assertThat(protokoll.get("aktion")).isEqualTo("GESPERRT");
        assertThat(protokoll.get("grund")).isEqualTo("Löschwunsch per Brief");
        assertThat(protokoll.get("aufbewahren_bis")).isEqualTo(kunde.get("aufbewahren_bis"));
        // Das Protokoll hält keinen Namen und keine Anschrift fest
        assertThat(protokoll.keySet()).noneMatch(spalte -> spalte.equals("name") || spalte.equals("strasse"));
        assertThat(protokoll.values().stream().filter(String.class::isInstance).map(Object::toString))
                .noneMatch(wert -> wert.contains("Mustermann"));
    }

    private <T> T inTx(Supplier<T> arbeit) {
        return new TransactionTemplate(tm).execute(status -> arbeit.get());
    }

    private long zaehle(String tabelle, String spalte, Long wert) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabelle + " WHERE " + spalte + " = ?", Long.class, wert);
    }

    private Long kundeBezug(String tabelle, Long id) {
        return jdbc.queryForObject("SELECT kunde_id FROM " + tabelle + " WHERE id = ?", Long.class, id);
    }

    private Kunde neuerKunde() {
        Kunde kunde = new Kunde();
        kunde.setKundennummer("K-" + suffix);
        kunde.setName("Max Mustermann " + suffix);
        kunde.setStrasse("Musterstraße 1");
        kunde.setPlz("12345");
        kunde.setOrt("Musterstadt");
        kunde.setTelefon("0123 456789");
        kunde.setMobiltelefon("0123 111111");
        kunde.setAnsprechspartner("Erika Mustermann");
        kunde.getKundenEmails().add("max.mustermann@example.com");
        em.persist(kunde);
        return kunde;
    }

    private void notiz(Kunde kunde) {
        KundeNotiz notiz = new KundeNotiz();
        notiz.setKunde(kunde);
        notiz.setText("Bitte vormittags anrufen");
        em.persist(notiz);
    }

    private void rufnummer(Kunde kunde) {
        KontaktRufnummer nummer = new KontaktRufnummer();
        nummer.setKunde(kunde);
        nummer.setNummerRoh("0123 999999");
        nummer.setNummerNormalisiert("0123999999");
        nummer.setAngelegtAm(LocalDateTime.now());
        em.persist(nummer);
    }

    private KalenderEintrag termin(Kunde kunde) {
        KalenderEintrag termin = new KalenderEintrag();
        termin.setTitel("Aufmaß bei Mustermann");
        termin.setDatum(LocalDate.now());
        termin.setKunde(kunde);
        termin.setErstelltAm(LocalDateTime.now());
        em.persist(termin);
        return termin;
    }

    private TelefonAnruf anruf(Kunde kunde) {
        TelefonAnruf anruf = new TelefonAnruf();
        anruf.setZeitpunkt(LocalDateTime.now());
        anruf.setArt(TelefonAnrufArt.ANGENOMMEN);
        anruf.setEigeneNummer("0123 000000");
        anruf.setKunde(kunde);
        anruf.setAngelegtAm(LocalDateTime.now());
        em.persist(anruf);
        return anruf;
    }

    private Sprachnachricht sprachnachricht(Kunde kunde, TelefonAnruf anruf) {
        Sprachnachricht nachricht = new Sprachnachricht();
        nachricht.setAnrufbeantworter(1);
        nachricht.setZeitpunkt(LocalDateTime.now());
        nachricht.setDauerSekunden(12);
        nachricht.setDateiName("nachricht-" + suffix + ".wav");
        nachricht.setKunde(kunde);
        nachricht.setAnruf(anruf);
        nachricht.setAngelegtAm(LocalDateTime.now());
        em.persist(nachricht);
        return nachricht;
    }

    private Projekt abgeschlossenesProjekt(Kunde kunde) {
        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Geländer Musterstraße");
        projekt.setAuftragsnummer("P-" + suffix);
        projekt.setAnlegedatum(LocalDate.of(2024, 3, 1));
        projekt.setAbschlussdatum(LocalDate.of(2024, 6, 30));
        projekt.setBruttoPreis(BigDecimal.valueOf(1190));
        projekt.setBezahlt(true);
        projekt.setAbgeschlossen(true);
        projekt.setKundenId(kunde);
        projekt.getKundenEmails().add("statiker@example.com");
        em.persist(projekt);
        return projekt;
    }

    private AusgangsGeschaeftsDokument rechnung(Kunde kunde, Projekt projekt) {
        AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
        rechnung.setDokumentNummer("R-" + suffix);
        rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        rechnung.setDatum(LocalDate.of(2024, 6, 30));
        rechnung.setKunde(kunde);
        rechnung.setProjekt(projekt);
        em.persist(rechnung);
        return rechnung;
    }
}

package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailBezug;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektArt;
import org.example.kalkulationsprogramm.util.LikeMuster;
import org.example.kalkulationsprogramm.util.suche.EmailSuchNormalisierung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

/**
 * E-Mail-Reiter ({@link EmailReiterRepositoryErweiterung}) gegen echtes PostgreSQL: Verläufe über
 * {@code parent_email_id} (Wurzel per {@code WITH RECURSIVE}), Sortierung nach der neuesten Mail,
 * Seiten, Suche und Richtung, Kundenmenge, Kreise, Kennzahlen und Anhänge. Alles in zurückgerollten
 * Transaktionen.
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FlywayStartSetupConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class DatenbankKompatibilitaetEmailReiterTest {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(
            DatenbankKompatibilitaetEmailReiterTest.class);

    @DynamicPropertySource
    static void datenbank(DynamicPropertyRegistry r) {
        KompatibilitaetsDatenbank.eintragen(r);
    }

    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transaktionen;
    @Autowired EmailReiterRepository reiterRepository;

    TransactionTemplate tx;
    String kennung;
    int laufendeNummer;
    Kunde kunde;
    Projekt projekt;

    @BeforeEach
    void vorbereiten() {
        tx = new TransactionTemplate(transaktionen);
        kennung = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private void zurueckgerollt(Consumer<Void> fall) {
        tx.executeWithoutResult(status -> {
            kunde = kunde("K-" + kennung);
            projekt = projekt(kunde, "Geländer Musterstraße");
            fall.accept(null);
            status.setRollbackOnly();
        });
    }

    // ------------------------------------------------------------------
    // Testdaten
    // ------------------------------------------------------------------

    private Kunde kunde(String nummer) {
        Kunde k = new Kunde();
        k.setName("Max Mustermann " + kennung);
        k.setKundennummer(nummer);
        em.persist(k);
        return k;
    }

    private Projekt projekt(Kunde k, String bauvorhaben) {
        Projekt p = new Projekt();
        p.setBauvorhaben(bauvorhaben + " " + kennung);
        p.setAuftragsnummer("A-" + kennung + "-" + (++laufendeNummer));
        p.setAnlegedatum(LocalDate.of(2026, 10, 1));
        p.setBruttoPreis(BigDecimal.TEN);
        p.setBezahlt(false);
        p.setAbgeschlossen(false);
        p.setProjektArt(ProjektArt.values()[0]);
        p.setKundenId(k);
        em.persist(p);
        return p;
    }

    private Anfrage anfrage(Kunde k, Projekt p) {
        Anfrage a = new Anfrage();
        a.setBauvorhaben("Treppe " + kennung);
        a.setAnlegedatum(LocalDate.of(2026, 9, 1));
        a.setKunde(k);
        a.setProjekt(p);
        em.persist(a);
        return a;
    }

    private Lieferanten lieferant() {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("Mustermann Stahlhandel " + kennung);
        em.persist(l);
        return l;
    }

    private Email mail(String betreff, LocalDateTime gesendet, Email vorgaenger, Consumer<Email> anpassen) {
        Email e = new Email();
        e.setMessageId("<" + kennung + "-" + (++laufendeNummer) + "@example.com>");
        e.setDirection(EmailDirection.IN);
        e.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        e.setFromAddress("Max Mustermann <max.mustermann@example.com>");
        e.setRecipient("info@example.com");
        e.setSubject(betreff);
        e.setSentAt(gesendet);
        e.setParentEmail(vorgaenger);
        anpassen.accept(e);
        em.persist(e);
        return e;
    }

    private Email projektMail(String betreff, LocalDateTime gesendet, Email vorgaenger) {
        return mail(betreff, gesendet, vorgaenger, e -> e.assignToProjekt(projekt));
    }

    private static LocalDateTime tag(int monat, int tag) {
        return LocalDateTime.of(2026, monat, tag, 9, 0);
    }

    private EmailReiterAbfrage abfrage(EmailBezug bezug, Long id, String suche, EmailDirection richtung,
            Set<Long> postfaecher, int offset, int limit) {
        List<String> woerter = suche == null ? List.of() : EmailSuchNormalisierung.suchwoerter(suche).stream()
                .map(w -> "%" + LikeMuster.escape(w) + "%").toList();
        em.flush();
        return new EmailReiterAbfrage(bezug, id, woerter, richtung, postfaecher, offset, limit);
    }

    private EmailReiterAbfrage projektAbfrage() {
        return abfrage(EmailBezug.PROJEKT, projekt.getId(), null, null, null, 0, 25);
    }

    // ------------------------------------------------------------------
    // Verläufe und Sortierung
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Antwort und Wurzel = eine Zeile mit der neuesten Mail; Antwort auf alten Verlauf zieht ihn nach oben")
    void verlaufNachOben() {
        zurueckgerollt(x -> {
            Email angebot = projektMail("Angebot Geländer", tag(1, 10), null);
            Email aufmass = projektMail("Aufmaß", tag(10, 1), null);
            Email antwort = projektMail("AW: Angebot Geländer", tag(11, 3), angebot);

            List<EmailVerlaufZeile> zeilen = reiterRepository.verlaeufe(projektAbfrage());

            assertThat(zeilen).containsExactly(new EmailVerlaufZeile(angebot.getId(), antwort.getId()),
                    new EmailVerlaufZeile(aufmass.getId(), aufmass.getId()));
            assertThat(reiterRepository.zaehleVerlaeufe(projektAbfrage())).isEqualTo(2L);
            assertThat(reiterRepository.anzahlVerlaeufe(EmailBezug.PROJEKT, projekt.getId())).isEqualTo(2L);
        });
    }

    @Test
    @DisplayName("Wurzel gehört nicht zum Projekt: die Zeile hängt trotzdem an der obersten Mail")
    void wurzelAusserhalb() {
        zurueckgerollt(x -> {
            Email fremd = mail("Anfrage von Max Mustermann", tag(3, 1), null, e -> { });
            Email zwischen = mail("AW: Anfrage", tag(3, 2), fremd, e -> { });
            Email imProjekt = projektMail("AW: AW: Anfrage", tag(3, 3), zwischen);

            assertThat(reiterRepository.verlaeufe(projektAbfrage()))
                    .containsExactly(new EmailVerlaufZeile(fremd.getId(), imProjekt.getId()));
        });
    }

    @Test
    @DisplayName("Ohne Datum zuletzt, bei gleicher Zeit die höhere Wurzel-ID zuerst")
    void sortierung() {
        zurueckgerollt(x -> {
            Email ohneDatum = projektMail("Ohne Datum", null, null);
            Email gleich1 = projektMail("Gleich 1", tag(5, 1), null);
            Email gleich2 = projektMail("Gleich 2", tag(5, 1), null);
            Email neu = projektMail("Neu", tag(9, 1), null);

            assertThat(reiterRepository.verlaeufe(projektAbfrage())).extracting(EmailVerlaufZeile::emailId)
                    .containsExactly(neu.getId(), gleich2.getId(), gleich1.getId(), ohneDatum.getId());
        });
    }

    @Test
    @DisplayName("Seiten: mehr als eine Seite, Seite 2 liefert andere Verläufe, Gesamtzahl stimmt")
    void seiten() {
        zurueckgerollt(x -> {
            for (int i = 1; i <= 5; i++) {
                Email wurzel = projektMail("Verlauf " + i, tag(1, i), null);
                projektMail("AW: Verlauf " + i, tag(2, i), wurzel);
            }
            List<EmailVerlaufZeile> seite1 = reiterRepository.verlaeufe(
                    abfrage(EmailBezug.PROJEKT, projekt.getId(), null, null, null, 0, 2));
            List<EmailVerlaufZeile> seite2 = reiterRepository.verlaeufe(
                    abfrage(EmailBezug.PROJEKT, projekt.getId(), null, null, null, 2, 2));
            List<EmailVerlaufZeile> seite3 = reiterRepository.verlaeufe(
                    abfrage(EmailBezug.PROJEKT, projekt.getId(), null, null, null, 4, 2));

            assertThat(seite1).hasSize(2);
            assertThat(seite2).hasSize(2).doesNotContainAnyElementsOf(seite1);
            assertThat(seite3).hasSize(1).doesNotContainAnyElementsOf(seite1).doesNotContainAnyElementsOf(seite2);
            assertThat(reiterRepository.zaehleVerlaeufe(projektAbfrage())).isEqualTo(5L);
        });
    }

    @Test
    @DisplayName("Kreis in der Kette (A↔B, C hängt am Kreis): bricht ab, eine Zeile")
    void kreis() {
        zurueckgerollt(x -> {
            Email a = projektMail("Kreis A", tag(4, 1), null);
            Email b = projektMail("Kreis B", tag(4, 2), a);
            Email c = projektMail("Am Kreis C", tag(4, 3), a);
            a.setParentEmail(b);
            em.flush();

            List<EmailVerlaufZeile> zeilen = reiterRepository.verlaeufe(projektAbfrage());

            assertThat(zeilen).hasSize(1);
            assertThat(zeilen.get(0).emailId()).isEqualTo(c.getId());
            assertThat(zeilen.get(0).wurzelId()).isEqualTo(Math.min(a.getId(), b.getId()));
            assertThat(reiterRepository.kennzahlen(List.of(zeilen.get(0).wurzelId()), null))
                    .extracting(EmailVerlaufKennzahl::anzahl).containsExactly(3);
        });
    }

    // ------------------------------------------------------------------
    // Suche und Richtung
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Suche und Richtung wirken auf die Bezugs-Mails; die Zeile zeigt die neueste passende")
    void sucheUndRichtung() {
        zurueckgerollt(x -> {
            Email angebot = projektMail("Angebot Geländer", tag(1, 10), null);
            Email rueckfrage = mail("Rückfrage Geländer", tag(1, 12), angebot, e -> {
                e.assignToProjekt(projekt);
                e.setDirection(EmailDirection.OUT);
            });
            projektMail("AW: Termin", tag(1, 14), rueckfrage);
            Email treppe = projektMail("Treppe Würth", tag(2, 1), null);

            assertThat(reiterRepository.verlaeufe(abfrage(EmailBezug.PROJEKT, projekt.getId(), "gelaender", null,
                    null, 0, 25))).containsExactly(new EmailVerlaufZeile(angebot.getId(), rueckfrage.getId()));
            assertThat(reiterRepository.verlaeufe(abfrage(EmailBezug.PROJEKT, projekt.getId(), "wuerth", null,
                    null, 0, 25))).containsExactly(new EmailVerlaufZeile(treppe.getId(), treppe.getId()));
            assertThat(reiterRepository.verlaeufe(abfrage(EmailBezug.PROJEKT, projekt.getId(), null,
                    EmailDirection.OUT, null, 0, 25)))
                    .containsExactly(new EmailVerlaufZeile(angebot.getId(), rueckfrage.getId()));
            assertThat(reiterRepository.zaehleVerlaeufe(abfrage(EmailBezug.PROJEKT, projekt.getId(), "geländer",
                    EmailDirection.IN, null, 0, 25))).isEqualTo(1L);
            assertThat(reiterRepository.zaehleVerlaeufe(abfrage(EmailBezug.PROJEKT, projekt.getId(),
                    "geländer nirgends", null, null, 0, 25))).isZero();
        });
    }

    @Test
    @DisplayName("Angriffstexte und LIKE-Platzhalter sind nur Text")
    void angriffe() {
        zurueckgerollt(x -> {
            Email rabatt = projektMail("Rabatt 50% auf_Lager", tag(1, 1), null);
            projektMail("Rabatt 500 aufXLager", tag(1, 2), null);

            assertThat(reiterRepository.verlaeufe(abfrage(EmailBezug.PROJEKT, projekt.getId(), "50%", null, null,
                    0, 25))).extracting(EmailVerlaufZeile::emailId).containsExactly(rabatt.getId());
            assertThat(reiterRepository.verlaeufe(abfrage(EmailBezug.PROJEKT, projekt.getId(),
                    "'; DROP TABLE email; --", null, null, 0, 25))).isEmpty();
            assertThat(reiterRepository.verlaeufe(abfrage(EmailBezug.PROJEKT, projekt.getId(),
                    "<script>alert(1)</script>", null, null, 0, 25))).isEmpty();
            assertThat(reiterRepository.zaehleVerlaeufe(projektAbfrage())).isEqualTo(2L);
        });
    }

    // ------------------------------------------------------------------
    // Bereiche
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Kunde: Projekte, Anfragen der Projekte, Anfragen mit seiner Kundennummer oder an ihm – sonst nichts")
    void kundenMenge() {
        zurueckgerollt(x -> {
            Projekt zweites = projekt(kunde, "Carport");
            Anfrage anfrageImProjekt = anfrage(null, zweites);
            Kunde doppelt = kunde("k-" + kennung.toUpperCase());
            Anfrage perNummer = anfrage(doppelt, null);
            Anfrage direkt = anfrage(kunde, null);
            Kunde fremd = kunde("F-" + kennung);
            Projekt fremdesProjekt = projekt(fremd, "Fremd");

            Email m1 = projektMail("Projekt 1", tag(1, 1), null);
            Email m2 = mail("Projekt 2", tag(1, 2), null, e -> e.assignToProjekt(zweites));
            Email m3 = mail("Anfrage im Projekt", tag(1, 3), null, e -> e.assignToAnfrage(anfrageImProjekt));
            Email m4 = mail("Anfrage per Nummer", tag(1, 4), null, e -> e.assignToAnfrage(perNummer));
            Email m5 = mail("Anfrage direkt", tag(1, 5), null, e -> e.assignToAnfrage(direkt));
            mail("Fremdes Projekt", tag(1, 6), null, e -> e.assignToProjekt(fremdesProjekt));
            mail("Ohne Zuordnung", tag(1, 7), null, e -> { });

            assertThat(reiterRepository.verlaeufe(abfrage(EmailBezug.KUNDE, kunde.getId(), null, null, null, 0, 25)))
                    .extracting(EmailVerlaufZeile::emailId)
                    .containsExactly(m5.getId(), m4.getId(), m3.getId(), m2.getId(), m1.getId());
            assertThat(reiterRepository.anzahlVerlaeufe(EmailBezug.KUNDE, kunde.getId())).isEqualTo(5L);
            assertThat(reiterRepository.anzahlVerlaeufe(EmailBezug.KUNDE, fremd.getId())).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("EXPLAIN: bei 20 000 fremden Mails läuft keine Reiter-Abfrage (Kunde, Seite, Kennzahlen) über alle Mails")
    void abfragenNutzenIndizes() {
        zurueckgerollt(x -> {
            Anfrage anfrage = anfrage(kunde, null);
            Email wurzel = projektMail("Projekt", tag(1, 1), null);
            mail("Anfrage", tag(1, 2), wurzel, e -> e.assignToAnfrage(anfrage));
            // Wie im Betrieb: viele Kunden, fast alle Mails gehören zu deren Projekten und Anfragen.
            for (int i = 0; i < 100; i++) {
                Kunde fremd = kunde("F-" + kennung + "-" + i);
                projekt(fremd, "Fremd");
                anfrage(fremd, null);
            }
            em.flush();
            em.createNativeQuery("""
                    INSERT INTO email (message_id, direction, zuordnung_typ, processing_status, is_newsletter,
                        is_potential_inquiry, is_read, is_spam, is_starred, automatische_bestaetigung, sent_at,
                        projekt_id, anfrage_id)
                    SELECT '<last-' || :kennung || '-' || z.art || z.id || '-' || g || '@example.com>', 'IN', 'KEINE',
                        'DONE', FALSE, FALSE, FALSE, FALSE, FALSE, FALSE,
                        TIMESTAMP '2026-01-01 00:00' + g * INTERVAL '1 minute',
                        CASE WHEN z.art = 'p' THEN z.id END, CASE WHEN z.art = 'a' THEN z.id END
                    FROM generate_series(1, 100) g CROSS JOIN (
                        SELECT 'p' AS art, p.id FROM projekt p JOIN kunde k ON k.id = p.kunden_id
                            WHERE k.kundennummer LIKE :fremd
                        UNION ALL SELECT 'a', a.id FROM anfrage a JOIN kunde k ON k.id = a.kunde_id
                            WHERE k.kundennummer LIKE :fremd) z
                    """).setParameter("kennung", kennung).setParameter("fremd", "F-" + kennung + "-%")
                    .executeUpdate();
            em.flush();
            for (String tabelle : List.of("email", "projekt", "anfrage", "kunde")) {
                em.createNativeQuery("ANALYZE " + tabelle).executeUpdate();
            }
            String anker = EmailReiterRepositoryErweiterungImpl.anker(
                    EmailReiterAbfrage.ohneFilter(EmailBezug.KUNDE, kunde.getId()), new java.util.HashMap<>());
            String id = String.valueOf(kunde.getId());

            String anzahl = plan(EmailReiterSql.anzahl(anker).replace(":" + EmailReiterSql.BEZUG_ID, id));
            String seite = plan(EmailReiterSql.seite(anker).replace(":" + EmailReiterSql.BEZUG_ID, id));
            String kennzahlen = plan(EmailReiterSql.kennzahlen("TRUE")
                    .replace(":" + EmailReiterSql.WURZELN, String.valueOf(wurzel.getId())));
            String anhaenge = plan(EmailReiterSql.anhaenge(EmailBezug.KUNDE)
                    .replace(":" + EmailReiterSql.WURZELN, String.valueOf(wurzel.getId()))
                    .replace(":" + EmailReiterSql.BEZUG_ID, id));

            assertThat(anzahl).contains("idx_email_projekt").contains("idx_email_anfrage");
            for (String plan : List.of(anzahl, seite, kennzahlen, anhaenge)) {
                assertThat(plan).doesNotContain("Seq Scan on email ");
            }
            assertThat(reiterRepository.anzahlVerlaeufe(EmailBezug.KUNDE, kunde.getId())).isEqualTo(1L);
            assertThat(reiterRepository.kennzahlen(List.of(wurzel.getId()), null))
                    .extracting(EmailVerlaufKennzahl::anzahl).containsExactly(2);
        });
    }

    private String plan(String sql) {
        String plan = ((List<?>) em.createNativeQuery("EXPLAIN " + sql).getResultList()).stream()
                .map(String::valueOf).collect(java.util.stream.Collectors.joining("\n"));
        LOG.info("[EmailReiter] EXPLAIN:\n{}", plan);
        return plan;
    }

    @Test
    @DisplayName("Kunde ohne Kundennummer: leere Nummer verbindet nicht mit anderen Kunden ohne Nummer")
    void kundeOhneNummer() {
        zurueckgerollt(x -> {
            Kunde ohne1 = kunde("");
            Kunde ohne2 = kunde(" ");
            Anfrage a2 = anfrage(ohne2, null);
            mail("Anfrage eines anderen", tag(1, 1), null, e -> e.assignToAnfrage(a2));

            assertThat(reiterRepository.anzahlVerlaeufe(EmailBezug.KUNDE, ohne1.getId())).isZero();
        });
    }

    @Test
    @DisplayName("Anfrage und Lieferant: jeweils nur die eigenen Mails; vorhandene Datensätze werden erkannt")
    void anfrageUndLieferant() {
        zurueckgerollt(x -> {
            Anfrage anfrage = anfrage(kunde, null);
            Lieferanten lieferant = lieferant();
            Email anfrageMail = mail("Anfrage", tag(1, 1), null, e -> e.assignToAnfrage(anfrage));
            Email lieferantMail = mail("Lieferschein", tag(1, 2), null, e -> e.assignToLieferant(lieferant));
            projektMail("Projekt", tag(1, 3), null);

            assertThat(reiterRepository.verlaeufe(abfrage(EmailBezug.ANFRAGE, anfrage.getId(), null, null, null, 0,
                    25))).extracting(EmailVerlaufZeile::emailId).containsExactly(anfrageMail.getId());
            assertThat(reiterRepository.verlaeufe(abfrage(EmailBezug.LIEFERANT, lieferant.getId(), null, null, null,
                    0, 25))).extracting(EmailVerlaufZeile::emailId).containsExactly(lieferantMail.getId());

            assertThat(reiterRepository.bezugVorhanden(EmailBezug.PROJEKT, projekt.getId())).isTrue();
            assertThat(reiterRepository.bezugVorhanden(EmailBezug.ANFRAGE, anfrage.getId())).isTrue();
            assertThat(reiterRepository.bezugVorhanden(EmailBezug.KUNDE, kunde.getId())).isTrue();
            assertThat(reiterRepository.bezugVorhanden(EmailBezug.LIEFERANT, lieferant.getId())).isTrue();
            for (EmailBezug bezug : EmailBezug.values()) {
                assertThat(reiterRepository.bezugVorhanden(bezug, 0L)).isFalse();
                assertThat(reiterRepository.bezugVorhanden(bezug, -1L)).isFalse();
                assertThat(reiterRepository.bezugVorhanden(bezug, Long.MAX_VALUE)).isFalse();
            }
        });
    }

    // ------------------------------------------------------------------
    // Kennzahlen und Anhänge
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Nachrichten im ganzen Verlauf, die der Benutzer lesen darf; Mail nur in fremdem Postfach zählt nicht")
    void kennzahlenSichtbarkeit() {
        zurueckgerollt(x -> {
            EmailAbsender info = postfach("info");
            EmailAbsender chef = postfach("chef");
            Email wurzel = mail("Anfrage", tag(1, 1), null, e -> e.ordnePostfachZu(info, "INBOX", 1L));
            Email geheim = mail("AW: intern", tag(1, 2), wurzel, e -> e.ordnePostfachZu(chef, "INBOX", 2L));
            projektMail("AW: AW: Anfrage", tag(1, 3), geheim);

            List<EmailVerlaufKennzahl> admin = reiterRepository.kennzahlen(List.of(wurzel.getId()), null);
            List<EmailVerlaufKennzahl> benutzer = reiterRepository.kennzahlen(List.of(wurzel.getId()),
                    Set.of(info.getId()));
            List<EmailVerlaufKennzahl> niemand = reiterRepository.kennzahlen(List.of(wurzel.getId()), Set.of());

            assertThat(admin).extracting(EmailVerlaufKennzahl::anzahl).containsExactly(3);
            assertThat(benutzer).extracting(EmailVerlaufKennzahl::anzahl).containsExactly(2);
            assertThat(niemand).as("zugeordnete Mail darf jeder öffnen").extracting(EmailVerlaufKennzahl::anzahl)
                    .containsExactly(1);
            assertThat(reiterRepository.verlaeufe(abfrage(EmailBezug.PROJEKT, projekt.getId(), null, null, Set.of(),
                    0, 25))).hasSize(1);
        });
    }

    @Test
    @DisplayName("Unzustellbar: irgendeine Ausgangsmail im Verlauf, Grund der neuesten")
    void unzustellbar() {
        zurueckgerollt(x -> {
            Email angebot = mail("Angebot", tag(1, 1), null, e -> {
                e.assignToProjekt(projekt);
                e.setDirection(EmailDirection.OUT);
                e.markiereUnzustellbar("mailbox full");
            });
            Email erinnerung = mail("Erinnerung", tag(1, 5), angebot, e -> {
                e.setDirection(EmailDirection.OUT);
                e.markiereUnzustellbar("unknown user");
            });
            mail("Eingang", tag(1, 9), erinnerung, e -> e.assignToProjekt(projekt));
            Email ok = projektMail("Alles gut", tag(2, 1), null);

            List<EmailVerlaufKennzahl> kennzahlen = reiterRepository.kennzahlen(
                    List.of(angebot.getId(), ok.getId()), null);

            assertThat(kennzahlen).containsExactlyInAnyOrder(
                    new EmailVerlaufKennzahl(angebot.getId(), 3, true, "unknown user"),
                    new EmailVerlaufKennzahl(ok.getId(), 1, false, null));
        });
    }

    @Test
    @DisplayName("Anhänge: nur nicht eingebettete an Bezugs-Mails des Verlaufs")
    void anhaenge() {
        zurueckgerollt(x -> {
            Email wurzel = mail("Fremd mit Anhang", tag(1, 1), null, e -> e.addAttachment(anhang("plan.pdf", false)));
            Email inline = projektMail("Mit Logo", tag(1, 2), wurzel);
            EmailAttachment logo = anhang("logo.png", true);
            inline.addAttachment(logo);
            EmailAttachment rechnung = anhang("rechnung.pdf", null);
            Email mitRechnung = projektMail("Rechnung", tag(1, 3), inline);
            mitRechnung.addAttachment(rechnung);
            Email ohne = projektMail("Ohne Anhang", tag(2, 1), null);
            em.flush();

            List<EmailVerlaufAnhang> anhaenge = reiterRepository.anhaenge(projektAbfrage(),
                    List.of(wurzel.getId(), ohne.getId()));

            assertThat(anhaenge).containsExactly(new EmailVerlaufAnhang(wurzel.getId(), rechnung.getId()));
        });
    }

    private EmailAttachment anhang(String name, Boolean eingebettet) {
        EmailAttachment a = new EmailAttachment();
        a.setOriginalFilename(name);
        a.setInlineAttachment(eingebettet);
        return a;
    }

    private EmailAbsender postfach(String name) {
        EmailAbsender p = new EmailAbsender();
        p.setEmailAdresse(name + "." + kennung + "@example.com");
        em.persist(p);
        return p;
    }
}

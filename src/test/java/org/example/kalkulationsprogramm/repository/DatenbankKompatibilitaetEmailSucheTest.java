package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.config.FlywayStartSetupConfig;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailOrdner;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektArt;
import org.example.kalkulationsprogramm.dto.SteuerberaterKontaktDto;
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService;
import org.example.kalkulationsprogramm.service.mail.liste.SteuerberaterOrdner;
import org.example.kalkulationsprogramm.util.suche.EmailSuchNormalisierung;
import org.example.kalkulationsprogramm.util.LikeMuster;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

/**
 * E-Mail-Suche ({@link EmailSucheRepositoryErweiterung}) gegen echtes PostgreSQL: Ordner-Regeln,
 * Umlaute, LIKE-Platzhalter, Sichtbarkeit, Seiten und der Trigramm-Index.
 *
 * <p>Kernprüfung: Für jeden Ordner findet die Suche genau die Mails, die die Ordnerliste zeigt
 * (gleiche Repository-Abfragen wie {@code GET /api/emails/{ordner}}), eingeschränkt auf das
 * Suchwort. Alles in zurückgerollten Transaktionen; eine Kennung je Testfall trennt die Testdaten
 * von allem, was sonst in der Wegwerf-Datenbank liegt.</p>
 */
@DataJpaTest(showSql = false, properties = { "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.out-of-order=true",
        "spring.flyway.validate-on-migrate=false", "spring.jpa.show-sql=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ FlywayStartSetupConfig.class, SteuerberaterOrdner.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "kompat.db.url", matches = "jdbc:postgresql:.*")
class DatenbankKompatibilitaetEmailSucheTest {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DatenbankKompatibilitaetEmailSucheTest.class);

    @DynamicPropertySource
    static void datenbank(DynamicPropertyRegistry r) {
        KompatibilitaetsDatenbank.eintragen(r);
    }

    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transaktionen;
    @Autowired EmailRepository emailRepository;
    @Autowired EmailSucheRepository sucheRepository;
    @Autowired SteuerberaterOrdner steuerberaterOrdner;
    @MockitoBean SteuerberaterKontaktService steuerberaterKontaktService;

    TransactionTemplate tx;
    String kennung;
    int laufendeNummer;

    @BeforeEach
    void vorbereiten() {
        tx = new TransactionTemplate(transaktionen);
        // nur Buchstaben: die Kennung ist selbst ein Suchwort
        kennung = "k" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                .chars().map(c -> Character.isDigit(c) ? 'g' + (c - '0') : c)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append) + "q";
    }

    private void zurueckgerollt(Consumer<Void> fall) {
        tx.executeWithoutResult(status -> {
            fall.accept(null);
            status.setRollbackOnly();
        });
    }

    private Email mail(String betreff, Consumer<Email> anpassen) {
        Email e = new Email();
        e.setMessageId("<" + kennung + "-" + (++laufendeNummer) + "@example.com>");
        e.setDirection(EmailDirection.IN);
        e.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        e.setFromAddress("Max Mustermann <max@example.com>");
        e.setRecipient("info@example.com");
        e.setSubject(betreff + " " + kennung);
        e.setSentAt(LocalDateTime.of(2026, 10, 1, 8, 0).plusMinutes(laufendeNummer));
        anpassen.accept(e);
        em.persist(e);
        return e;
    }

    private List<Long> suche(EmailOrdner ordner, Set<Long> sichtbar, String eingabe, int offset, int limit) {
        List<String> woerter = EmailSuchNormalisierung.suchwoerter(eingabe).stream()
                .map(w -> "%" + LikeMuster.escape(w) + "%")
                .toList();
        em.flush();
        return sucheRepository.sucheIds(new EmailSuchAbfrage(woerter, ordner, sichtbar,
                steuerberaterOrdner.domains(), offset, limit));
    }

    private List<Long> suche(String eingabe) {
        return suche(EmailOrdner.ALLE, null, kennung + " " + eingabe, 0, 50);
    }

    // ------------------------------------------------------------------
    // Ordner: Suche = Ordnerliste
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Je Ordner findet die Suche genau die Mails der Ordnerliste")
    void jeOrdnerWieDieOrdnerliste() {
        zurueckgerollt(x -> {
            SteuerberaterKontaktDto kanzlei = new SteuerberaterKontaktDto();
            kanzlei.setEmail("info@kanzlei-" + kennung + ".de");
            given(steuerberaterKontaktService.findAll()).willReturn(List.of(kanzlei));
            ordnerDatenAnlegen();
            em.flush();
            em.clear();

            Map<EmailOrdner, List<Email>> listen = ordnerlisten();
            List<String> fehler = new ArrayList<>();
            for (Map.Entry<EmailOrdner, List<Email>> eintrag : listen.entrySet()) {
                Set<Long> erwartet = eintrag.getValue().stream()
                        .filter(e -> e.getSubject() != null && e.getSubject().contains(kennung))
                        .map(Email::getId).collect(Collectors.toSet());
                Set<Long> gefunden = Set.copyOf(suche(eintrag.getKey(), null, kennung, 0, 500));
                if (erwartet.isEmpty()) {
                    fehler.add(eintrag.getKey() + ": keine Testmail im Ordner – Testdaten ergänzen");
                } else if (!erwartet.equals(gefunden)) {
                    fehler.add(eintrag.getKey() + ": Liste " + erwartet + ", Suche " + gefunden);
                }
            }
            Set<Long> alle = Set.copyOf(suche(EmailOrdner.ALLE, null, kennung, 0, 500));
            assertThat(alle).as("„Alle Ordner“ = alle Testmails").hasSize(laufendeNummer);
            assertThat(fehler).isEmpty();
        });
    }

    private void ordnerDatenAnlegen() {
        mail("Posteingang", e -> { });
        mail("Mögliche Anfrage", e -> e.setPotentialInquiry(true));
        mail("Spam", e -> e.setSpam(true));
        mail("Spam, aber Projekt", e -> { e.setSpam(true); e.setZuordnungTyp(EmailZuordnungTyp.PROJEKT); });
        mail("Newsletter", e -> e.setNewsletter(true));
        mail("Newsletter und Spam", e -> { e.setNewsletter(true); e.setSpam(true); });
        mail("Markiert", e -> e.setStarred(true));
        mail("Markiert im Papierkorb", e -> { e.setStarred(true); e.setDeletedAt(LocalDateTime.now()); });
        mail("Papierkorb", e -> e.setDeletedAt(LocalDateTime.now()));
        mail("Gesendet", e -> e.setDirection(EmailDirection.OUT));
        mail("Gesendet gelöscht", e -> { e.setDirection(EmailDirection.OUT); e.setDeletedAt(LocalDateTime.now()); });
        mail("Projekt", e -> e.setZuordnungTyp(EmailZuordnungTyp.PROJEKT));
        mail("Projekt gesendet", e -> { e.setZuordnungTyp(EmailZuordnungTyp.PROJEKT); e.setDirection(EmailDirection.OUT); });
        mail("Anfrage", e -> e.setZuordnungTyp(EmailZuordnungTyp.ANFRAGE));
        mail("Lieferant", e -> e.setZuordnungTyp(EmailZuordnungTyp.LIEFERANT));
        mail("Steuerberater", e -> e.setFromAddress("Kanzlei <info@kanzlei-" + kennung + ".de>"));
        mail("Steuerberater in CC", e -> e.setCc("lohn@KANZLEI-" + kennung + ".DE"));
        mail("Fremde Domain", e -> e.setRecipient("info@kanzlei-" + kennung + ".de.example.org"));
        mail("Zugeordnet Steuerberater", e -> e.setZuordnungTyp(EmailZuordnungTyp.STEUERBERATER));
        nichtZugeordnetAnlegen();
    }

    /** Bekannter Kunde (Projekt-Kunden-Adresse) und Sendedatum in der Karenzzeit. */
    private void nichtZugeordnetAnlegen() {
        Kunde kunde = new Kunde();
        kunde.setName("Erika Musterfrau " + kennung);
        kunde.setKundennummer("K-" + kennung);
        em.persist(kunde);
        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Treppe " + kennung);
        projekt.setAuftragsnummer("A-" + kennung);
        projekt.setAnlegedatum(LocalDate.of(2026, 10, 1));
        projekt.setBruttoPreis(BigDecimal.TEN);
        projekt.setBezahlt(false);
        projekt.setAbgeschlossen(false);
        projekt.setProjektArt(ProjektArt.values()[0]);
        projekt.setKundenId(kunde);
        projekt.setKundenEmails(new ArrayList<>(List.of("erika." + kennung + "@example.com")));
        em.persist(projekt);
        mail("Bekannter Kunde", e -> e.setFromAddress("Erika Musterfrau <erika." + kennung + "@example.com>"));
    }

    private Map<EmailOrdner, List<Email>> ordnerlisten() {
        Map<EmailOrdner, List<Email>> listen = new EnumMap<>(EmailOrdner.class);
        List<Email> nichtZugeordnet = emailRepository.findUnassigned();
        Set<Long> nichtZugeordnetIds = nichtZugeordnet.stream().map(Email::getId).collect(Collectors.toSet());
        listen.put(EmailOrdner.INBOX, emailRepository.findInboxFiltered().stream()
                .filter(e -> !nichtZugeordnetIds.contains(e.getId())).toList());
        listen.put(EmailOrdner.SENT, emailRepository.findByDirectionOrderBySentAtDesc(EmailDirection.OUT).stream()
                .filter(e -> e.getDeletedAt() == null).toList());
        listen.put(EmailOrdner.TRASH, emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc());
        listen.put(EmailOrdner.SPAM, emailRepository.findSpamOrdner());
        listen.put(EmailOrdner.NEWSLETTER, emailRepository.findNewsletterOrdner());
        listen.put(EmailOrdner.STARRED, emailRepository.findStarred());
        listen.put(EmailOrdner.PROJECTS, emailRepository.findProjectEmails());
        listen.put(EmailOrdner.OFFERS, emailRepository.findAnfrageEmails());
        listen.put(EmailOrdner.SUPPLIERS, emailRepository.findLieferantEmails());
        listen.put(EmailOrdner.TAX_ADVISORS, steuerberaterOrdner.emails());
        listen.put(EmailOrdner.UNASSIGNED, nichtZugeordnet);
        listen.put(EmailOrdner.INQUIRIES, emailRepository.findPotentialInquiries());
        return listen;
    }

    // ------------------------------------------------------------------
    // Wörter, Umlaute, Platzhalter
    // ------------------------------------------------------------------

    @Test
    @DisplayName("„müller geländer“: beide Wörter irgendwo – Anzeigename, HTML-Text, Anhang; Umlaut-Schreibweisen")
    void woerterUndUmlaute() {
        zurueckgerollt(x -> {
            Email html = mail("Aufmaß", e -> {
                e.setFromAddress("Max Müller <max@example.com>");
                e.setHtmlBody("<p>Das <b>Geländer</b> für die Treppe</p><blockquote>Würth Lieferung</blockquote>");
            });
            Email anhang = mail("Rechnung", e -> {
                e.setCc("Erika Muller <erika@example.com>");
                EmailAttachment a = new EmailAttachment();
                a.setOriginalFilename("Gelaender_Plan.pdf");
                e.addAttachment(a);
            });
            Email nurEines = mail("Nur Müller", e -> { });

            // „Gelaender_Plan.pdf“ zählt dank Umschreibung als „geländer“
            assertThat(suche("müller geländer")).containsExactlyInAnyOrder(html.getId(), anhang.getId());
            assertThat(suche("MULLER")).containsExactlyInAnyOrder(html.getId(), anhang.getId(), nurEines.getId());
            assertThat(suche("wuerth")).containsExactly(html.getId());
            assertThat(suche("wurth")).containsExactly(html.getId());
            assertThat(suche("Würth")).containsExactly(html.getId());
            assertThat(suche("gelaender_plan")).containsExactly(anhang.getId());
            assertThat(suche("geländer treppe würth")).containsExactly(html.getId());
            assertThat(suche("geländer nirgends")).isEmpty();
        });
    }

    @Test
    @DisplayName("Umlaut-Schreibweisen in beide Richtungen: Mail „Wuerth“ ↔ „Würth“, Mail „Würth“ ↔ „wuerth“")
    void umlauteBeidseitig() {
        zurueckgerollt(x -> {
            Email umschrieben = mail("Lieferung Wuerth", e -> e.setFromAddress("Hans Mueller <hans@example.org>"));
            Email mitUmlaut = mail("Rechnung Würth", e -> { });
            Email steuer = mail("Post vom Steuerberater", e -> { });

            assertThat(suche("Würth")).containsExactlyInAnyOrder(umschrieben.getId(), mitUmlaut.getId());
            assertThat(suche("wurth")).containsExactlyInAnyOrder(umschrieben.getId(), mitUmlaut.getId());
            assertThat(suche("wuerth")).containsExactlyInAnyOrder(umschrieben.getId(), mitUmlaut.getId());
            assertThat(suche("Müller")).containsExactly(umschrieben.getId());
            assertThat(suche("steuer")).containsExactly(steuer.getId());
            assertThat(suche("Steuerberater")).containsExactly(steuer.getId());
        });
    }

    @Test
    @DisplayName("LIKE-Platzhalter und Angriffstexte sind nur Text")
    void platzhalterUndAngriffe() {
        zurueckgerollt(x -> {
            Email rabatt = mail("Rabatt 50% auf_Lager", e -> { });
            mail("Rabatt 500 aufXLager", e -> { });

            assertThat(suche("50%")).containsExactly(rabatt.getId());
            assertThat(suche("auf_lager")).containsExactly(rabatt.getId());
            assertThat(suche("'; DROP TABLE email; --")).isEmpty();
            assertThat(suche("<script>alert(1)</script>")).isEmpty();
            assertThat(suche("\\")).isEmpty();
            assertThat(emailRepository.count()).isPositive();
        });
    }

    // ------------------------------------------------------------------
    // Sichtbarkeit und Seiten
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Mails aus nicht sichtbaren Postfächern fehlen schon in der Abfrage; ohne Zuordnung = Hauptpostfach")
    void sichtbarkeit() {
        zurueckgerollt(x -> {
            EmailAbsender info = postfach("info." + kennung + "@example.com");
            EmailAbsender max = postfach("max." + kennung + "@example.com");
            Email inInfo = mail("Info", e -> e.ordnePostfachZu(info, "INBOX", 1L));
            Email inMax = mail("Max", e -> e.ordnePostfachZu(max, "INBOX", 2L));
            Email inBeiden = mail("Beide", e -> { e.ordnePostfachZu(info, "INBOX", 3L); e.ordnePostfachZu(max, "INBOX", 4L); });
            Email ohne = mail("Ohne", e -> { });

            assertThat(suche(EmailOrdner.ALLE, Set.of(info.getId()), kennung, 0, 50))
                    .containsExactlyInAnyOrder(inInfo.getId(), inBeiden.getId(), ohne.getId());
            assertThat(suche(EmailOrdner.ALLE, Set.of(), kennung, 0, 50)).containsExactly(ohne.getId());
            assertThat(suche(EmailOrdner.ALLE, null, kennung, 0, 50))
                    .containsExactlyInAnyOrder(inInfo.getId(), inMax.getId(), inBeiden.getId(), ohne.getId());
            assertThat(suche(EmailOrdner.INBOX, Set.of(max.getId()), kennung, 0, 50))
                    .containsExactlyInAnyOrder(inMax.getId(), inBeiden.getId(), ohne.getId());
        });
    }

    private EmailAbsender postfach(String adresse) {
        EmailAbsender p = new EmailAbsender();
        p.setEmailAdresse(adresse);
        em.persist(p);
        return p;
    }

    @Test
    @DisplayName("Seiten: neueste zuerst, ohne Datum zuletzt, gleiche Zeit nach ID; keine Mail doppelt")
    void seiten() {
        zurueckgerollt(x -> {
            Email ohneDatum = mail("Ohne Datum", e -> e.setSentAt(null));
            Email alt = mail("Alt", e -> e.setSentAt(LocalDateTime.of(2026, 1, 1, 8, 0)));
            Email gleich1 = mail("Gleich", e -> e.setSentAt(LocalDateTime.of(2026, 5, 1, 8, 0)));
            Email gleich2 = mail("Gleich", e -> e.setSentAt(LocalDateTime.of(2026, 5, 1, 8, 0)));
            Email neu = mail("Neu", e -> e.setSentAt(LocalDateTime.of(2026, 9, 1, 8, 0)));

            List<Long> seite1 = suche(EmailOrdner.ALLE, null, kennung, 0, 2);
            List<Long> seite2 = suche(EmailOrdner.ALLE, null, kennung, 2, 2);
            List<Long> seite3 = suche(EmailOrdner.ALLE, null, kennung, 4, 2);

            assertThat(seite1).containsExactly(neu.getId(), gleich2.getId());
            assertThat(seite2).containsExactly(gleich1.getId(), alt.getId());
            assertThat(seite3).containsExactly(ohneDatum.getId());
        });
    }

    // ------------------------------------------------------------------
    // Trigramm-Index
    // ------------------------------------------------------------------

    @Test
    @DisplayName("EXPLAIN: bei einigen tausend Mails (je ~1,3 KB nicht komprimierbarer Suchtext) nutzt die Suche den Trigramm-Index")
    void trigrammIndexGreift() {
        zurueckgerollt(x -> {
            em.createNativeQuery("""
                    INSERT INTO email (message_id, direction, zuordnung_typ, processing_status, is_newsletter,
                        is_potential_inquiry, is_read, is_spam, is_starred, automatische_bestaetigung, sent_at, suchtext)
                    SELECT '<last-' || :kennung || '-' || g || '@example.com>', 'IN', 'KEINE', 'DONE', FALSE, FALSE,
                        FALSE, FALSE, FALSE, FALSE, TIMESTAMP '2026-01-01 00:00' + g * INTERVAL '1 minute',
                        'max mustermann <max@example.com> angebot nummer ' || g || ' treppe gelander verzinkt '
                            || (SELECT string_agg(md5(g::text || '-' || i::text), ' ') FROM generate_series(1, 40) i)
                    FROM generate_series(1, 5000) g
                    """).setParameter("kennung", kennung).executeUpdate();
            mail("Seltener Begriff zylinderschloss", e -> { });
            em.flush();
            em.createNativeQuery("ANALYZE email").executeUpdate();

            String plan = ((List<?>) em.createNativeQuery("""
                    EXPLAIN SELECT e.id FROM email e WHERE (TRUE)
                      AND (e.suchtext LIKE '%zylinderschloss%' ESCAPE '\\')
                    ORDER BY e.sent_at DESC NULLS LAST, e.id DESC LIMIT 26
                    """).getResultList()).stream().map(String::valueOf).collect(Collectors.joining("\n"));
            LOG.info("[EmailSuche] EXPLAIN bei 5000+ Mails:\n{}", plan);

            assertThat(plan).contains("idx_email_suchtext_trgm");
        });
    }
}

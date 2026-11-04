package org.example.kalkulationsprogramm.service.mail.suche;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManager;

/** Altbestand ohne Suchtext wird in Blöcken mit derselben Normalisierung nachgefüllt (H2). */
@DataJpaTest
@Import(EmailSuchtextNachpflege.class)
@TestPropertySource(properties = { "email.suchtext.nachpflege-aktiv=true", "email.suchtext.nachpflege-block=2" })
class EmailSuchtextNachpflegeJpaTest {

    @Autowired private EmailSuchtextNachpflege nachpflege;
    @Autowired private EmailRepository emailRepository;
    @Autowired private EntityManager em;

    private int zaehler;

    private Email altbestand(String betreff, String dateiname) {
        Email e = new Email();
        e.setMessageId("<alt-" + (++zaehler) + "@example.com>");
        e.setDirection(EmailDirection.IN);
        e.setFromAddress("Max Mustermann <max@example.com>");
        e.setSubject(betreff);
        e.setHtmlBody("<p>Gr&uuml;&szlig;e aus W&uuml;rzburg</p>");
        e.setSentAt(LocalDateTime.of(2026, 9, 1, 8, 0));
        if (dateiname != null) {
            EmailAttachment a = new EmailAttachment();
            a.setOriginalFilename(dateiname);
            e.addAttachment(a);
        }
        return emailRepository.save(e);
    }

    /** Wie vor der Migration: Suchtext fehlt. */
    private void suchtextLeeren() {
        em.flush();
        em.createQuery("UPDATE Email e SET e.suchtext = NULL").executeUpdate();
        em.clear();
    }

    private String suchtext(Long id) {
        em.clear();
        return emailRepository.findById(id).orElseThrow().getSuchtext();
    }

    @Test
    @DisplayName("Füllt alle Mails ohne Suchtext in Blöcken – samt HTML-Text und Anhang-Namen")
    void fuelltAltbestand() {
        Email a = altbestand("Angebot Geländer", "Skizze.pdf");
        Email b = altbestand("Rechnung Treppe", null);
        Email c = altbestand("Aufmaß Balkon", "Plan_1.pdf");
        suchtextLeeren();

        int gefuellt = nachpflege.nachpflegen();

        assertThat(gefuellt).isEqualTo(3);
        assertThat(suchtext(a.getId())).contains("angebot gelander").contains("skizze.pdf")
                .contains("grusse aus wurzburg").contains("max mustermann <max@example.com>");
        assertThat(suchtext(b.getId())).contains("rechnung treppe");
        assertThat(suchtext(c.getId())).contains("aufmass balkon").contains("plan_1.pdf");
    }

    @Test
    @DisplayName("Idempotent: Mails mit Suchtext bleiben unberührt, zweiter Lauf tut nichts")
    void idempotent() {
        Email vorhanden = altbestand("Schon da", null);
        em.flush();
        em.createQuery("UPDATE Email e SET e.suchtext = 'eigener stand' WHERE e.id = :id")
                .setParameter("id", vorhanden.getId()).executeUpdate();
        Email alt = altbestand("Ohne Suchtext", null);
        em.flush();
        em.createQuery("UPDATE Email e SET e.suchtext = NULL WHERE e.id = :id")
                .setParameter("id", alt.getId()).executeUpdate();
        em.clear();

        assertThat(nachpflege.nachpflegen()).isEqualTo(1);
        assertThat(nachpflege.nachpflegen()).isZero();
        assertThat(suchtext(vorhanden.getId())).isEqualTo("eigener stand");
        assertThat(suchtext(alt.getId())).contains("ohne suchtext");
    }

    @Test
    @DisplayName("Nichts zu tun: 0")
    void nichtsZuTun() {
        altbestand("Neu gespeichert", null);
        em.flush();
        em.clear();

        assertThat(nachpflege.nachpflegen()).isZero();
        assertThat(emailRepository.findAll()).extracting(Email::getSuchtext).doesNotContainNull();
    }
}

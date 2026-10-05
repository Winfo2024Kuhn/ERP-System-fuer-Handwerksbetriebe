package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.junit.jupiter.api.Test;

class EmailThreadTeilnehmerTest {

    private static final Set<String> EIGENE = Set.of("info@musterbetrieb.example");

    private static Email mail(EmailDirection richtung, String von, String an, String cc, String betreff) {
        Email email = new Email();
        email.setDirection(richtung);
        email.setFromAddress(von);
        email.setRecipient(an);
        email.setCc(cc);
        email.setSubject(betreff);
        return email;
    }

    @Test
    void antwortenVerschiedenerLieferantenGehoerenNichtZusammen() {
        Email lieferantA = mail(EmailDirection.IN, "angebot@lieferant-a.example", "info@musterbetrieb.example", null, "AW: Bitte um Angebot");
        Email lieferantB = mail(EmailDirection.IN, "verkauf@lieferant-b.example", "info@musterbetrieb.example", null, "AW: Bitte um Angebot");
        assertThat(EmailThreadTeilnehmer.gehoertZumGespraech(lieferantB, lieferantA, EIGENE)).isFalse();
    }

    @Test
    void antwortDesAngeschriebenenGehoertZumGespraech() {
        Email anfrage = mail(EmailDirection.OUT, "info@musterbetrieb.example", "Lieferant A <angebot@lieferant-a.example>", null, "Bitte um Angebot");
        Email antwort = mail(EmailDirection.IN, "ANGEBOT@lieferant-a.example", "info@musterbetrieb.example", null, "AW: Bitte um Angebot");
        assertThat(EmailThreadTeilnehmer.gehoertZumGespraech(antwort, anfrage, EIGENE)).isTrue();
    }

    @Test
    void ccTeilnehmerZaehltUndAliasDesAbsendersGiltAlsEigeneAdresse() {
        Email original = mail(EmailDirection.IN, "max@example.com", "info@musterbetrieb.example", "erika@example.com", "Termin");
        // Gesendet über einen Alias, der nicht in der eigenen Liste steht.
        Email antwort = mail(EmailDirection.OUT, "buero@musterbetrieb.example", "erika@example.com", null, "AW: Termin");
        assertThat(EmailThreadTeilnehmer.gehoertZumGespraech(antwort, original, EIGENE)).isTrue();
        assertThat(EmailThreadTeilnehmer.externeTeilnehmer(antwort, EIGENE)).containsExactly("erika@example.com");
    }

    @Test
    void eigeneWeiterleitungBleibtImVerlauf() {
        Email original = mail(EmailDirection.IN, "max@example.com", "info@musterbetrieb.example", null, "Garagentor");
        Email weiterleitung = mail(EmailDirection.OUT, "info@musterbetrieb.example", "monteur@example.org", null, "WG: Garagentor");
        assertThat(EmailThreadTeilnehmer.gehoertZumGespraech(weiterleitung, original, EIGENE)).isTrue();
    }

    @Test
    void ohneExterneTeilnehmerEntscheidetDerBetreff() {
        Email original = mail(EmailDirection.OUT, "info@musterbetrieb.example", "info@musterbetrieb.example", null, "Angebot");
        Email antwort = mail(EmailDirection.IN, "max@example.com", "info@musterbetrieb.example", null, "AW: Angebot");
        assertThat(EmailThreadTeilnehmer.gehoertZumGespraech(antwort, original, EIGENE)).isTrue();
    }

    @Test
    void adressenWerdenAusAllenFormatenGelesen() {
        assertThat(EmailThreadTeilnehmer.adressen("\"Mustermann, Max\" <Max@Example.com>; erika@example.com", null, "  "))
                .containsExactly("max@example.com", "erika@example.com");
        assertThat(EmailThreadTeilnehmer.istWeiterleitung("Fwd: x")).isTrue();
        assertThat(EmailThreadTeilnehmer.istWeiterleitung("AW: x")).isFalse();
        assertThat(EmailThreadTeilnehmer.istWeiterleitung(null)).isFalse();
    }

    @Test
    void replyToZaehltAlsTeilnehmer() {
        Email formular = mail(EmailDirection.IN, "noreply@formular.example", "info@musterbetrieb.example", null, "Anfrage");
        formular.setReplyToAddress("max@example.com");
        Email antwort = mail(EmailDirection.OUT, "info@musterbetrieb.example", "max@example.com", null, "AW: Anfrage");
        assertThat(EmailThreadTeilnehmer.gehoertZumGespraech(antwort, formular, EIGENE)).isTrue();
    }

    @Test
    void geloestWerdenNurEingehendeMailsVerschiedenerFirmenOhneGemeinsamenTeilnehmer() {
        Email lieferantA = mail(EmailDirection.IN, "angebot@lieferant-a.example", "info@musterbetrieb.example", null, "AW: Bitte um Angebot");
        Email lieferantB = mail(EmailDirection.IN, "verkauf@lieferant-b.example", "info@musterbetrieb.example", null, "AW: Bitte um Angebot");
        assertThat(EmailThreadTeilnehmer.darfGeloestWerden(lieferantB, lieferantA, EIGENE)).isTrue();

        // Kollege derselben Firma antwortet per In-Reply-To – bleibt verknüpft.
        Email kollege = mail(EmailDirection.IN, "erika@lieferant-a.example", "info@musterbetrieb.example", null, "AW: Bitte um Angebot");
        assertThat(EmailThreadTeilnehmer.darfGeloestWerden(kollege, lieferantA, EIGENE)).isFalse();

        // Unsere Mail beteiligt (Antwort im ERP oder Antwort auf unsere Mail) – bleibt verknüpft.
        Email unsere = mail(EmailDirection.OUT, "info@musterbetrieb.example", "info@kunde.example", null, "Angebot");
        Email antwortKunde = mail(EmailDirection.IN, "max@kunde.example", "info@musterbetrieb.example", null, "AW: Angebot");
        assertThat(EmailThreadTeilnehmer.darfGeloestWerden(antwortKunde, unsere, EIGENE)).isFalse();
        assertThat(EmailThreadTeilnehmer.darfGeloestWerden(unsere, lieferantA, EIGENE)).isFalse();

        Email ohneAbsender = mail(EmailDirection.IN, null, "info@musterbetrieb.example", null, "AW: Bitte um Angebot");
        assertThat(EmailThreadTeilnehmer.darfGeloestWerden(ohneAbsender, lieferantA, EIGENE)).isFalse();
    }
}

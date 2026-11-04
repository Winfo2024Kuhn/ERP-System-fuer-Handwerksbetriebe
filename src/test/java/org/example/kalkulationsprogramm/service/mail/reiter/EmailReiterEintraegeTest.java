package org.example.kalkulationsprogramm.service.mail.reiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailBezug;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.ZustellStatus;
import org.example.kalkulationsprogramm.dto.Email.EmailReiterEintragDto;
import org.example.kalkulationsprogramm.repository.EmailReiterAbfrage;
import org.example.kalkulationsprogramm.repository.EmailReiterRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.EmailVerlaufAnhang;
import org.example.kalkulationsprogramm.repository.EmailVerlaufKennzahl;
import org.example.kalkulationsprogramm.repository.EmailVerlaufZeile;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailReiterEintraegeTest {

    private static final EmailReiterAbfrage PROJEKT = new EmailReiterAbfrage(EmailBezug.PROJEKT, 5L, List.of(), null,
            Set.of(4L), 0, 25);
    private static final EmailReiterAbfrage LIEFERANT = new EmailReiterAbfrage(EmailBezug.LIEFERANT, 9L, List.of(),
            null, null, 0, 25);

    @Mock private EmailReiterRepository reiterRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private LieferantDokumentZugriffService dokumentZugriffService;
    @InjectMocks private EmailReiterEintraege eintraege;

    private Email angebot;
    private Email antwort;

    @BeforeEach
    void daten() {
        angebot = mail(11L, "Angebot Geländer", EmailDirection.OUT);
        angebot.setHtmlBody("<p>Angebot</p>");
        antwort = mail(22L, "AW: Treppe", EmailDirection.IN);
        antwort.setBody("Danke, passt so.");
        given(emailRepository.findAllById(anyCollection())).willReturn(List.of(antwort, angebot));
        given(reiterRepository.kennzahlen(anyCollection(), any())).willReturn(List.of(
                new EmailVerlaufKennzahl(1L, 3, false, null),
                new EmailVerlaufKennzahl(2L, 1, false, null)));
        given(reiterRepository.anhaenge(any(), anyCollection())).willReturn(List.of());
    }

    private static Email mail(long id, String betreff, EmailDirection richtung) {
        Email e = new Email();
        e.setId(id);
        e.setSubject(betreff);
        e.setDirection(richtung);
        e.setFromAddress("max.mustermann@example.com");
        e.setRecipient("info@example.com");
        e.setSentAt(LocalDateTime.of(2026, 11, 3, 10, 15));
        return e;
    }

    private static final List<EmailVerlaufZeile> ZEILEN = List.of(new EmailVerlaufZeile(1L, 11L),
            new EmailVerlaufZeile(2L, 22L));

    @Test
    @DisplayName("Felder der angezeigten Mail, Reihenfolge der Seite, Anzahl je Verlauf")
    void felder() {
        List<EmailReiterEintragDto> liste = eintraege.baue(ZEILEN, PROJEKT, null);

        assertThat(liste).extracting(EmailReiterEintragDto::id).containsExactly(11L, 22L);
        EmailReiterEintragDto erster = liste.get(0);
        assertThat(erster.subject()).isEqualTo("Angebot Geländer");
        assertThat(erster.fromAddress()).isEqualTo("max.mustermann@example.com");
        assertThat(erster.recipient()).isEqualTo("info@example.com");
        assertThat(erster.direction()).isEqualTo(EmailDirection.OUT);
        assertThat(erster.sentAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 10, 15));
        assertThat(erster.bodyHtml()).isEqualTo("<p>Angebot</p>");
        assertThat(erster.nachrichtenAnzahl()).isEqualTo(3);
        assertThat(erster.mitAnhang()).isFalse();
        assertThat(erster.zustellStatus()).isEqualTo("OFFEN");
        assertThat(erster.zustellFehler()).isNull();
        assertThat(liste.get(1).nachrichtenAnzahl()).isEqualTo(1);
    }

    @Test
    @DisplayName("Eine Abfrage je Art für die ganze Seite (kein N+1), Postfach-Grenze geht an die Kennzahlen")
    void keinNPlusEins() {
        eintraege.baue(ZEILEN, PROJEKT, null);

        verify(emailRepository, times(1)).findAllById(eq(List.of(11L, 22L)));
        verify(reiterRepository, times(1)).kennzahlen(eq(List.of(1L, 2L)), eq(Set.of(4L)));
        verify(reiterRepository, times(1)).anhaenge(eq(PROJEKT), eq(List.of(1L, 2L)));
    }

    @Test
    void leereSeiteOhneAbfragen() {
        assertThat(eintraege.baue(List.of(), PROJEKT, null)).isEmpty();
        verifyNoInteractions(emailRepository, reiterRepository, dokumentZugriffService);
    }

    @Test
    @DisplayName("Text: HTML, sonst Klartext, sonst Rohtext, sonst null (leer zählt als fehlend)")
    void textReihenfolge() {
        Email nurRoh = mail(33L, "Roh", EmailDirection.IN);
        nurRoh.setHtmlBody("  ");
        nurRoh.setRawBody("Rohtext");
        Email ohne = mail(44L, "Ohne", EmailDirection.IN);
        given(emailRepository.findAllById(anyCollection())).willReturn(List.of(angebot, antwort, nurRoh, ohne));

        List<EmailReiterEintragDto> liste = eintraege.baue(List.of(new EmailVerlaufZeile(1L, 11L),
                new EmailVerlaufZeile(2L, 22L), new EmailVerlaufZeile(3L, 33L), new EmailVerlaufZeile(4L, 44L)),
                PROJEKT, null);

        assertThat(liste).extracting(EmailReiterEintragDto::bodyHtml)
                .containsExactly("<p>Angebot</p>", "Danke, passt so.", "Rohtext", null);
    }

    @Test
    @DisplayName("Fehlt eine Kennzahl (Mail inzwischen weg), zählt der Verlauf mindestens 1")
    void mindestensEine() {
        given(reiterRepository.kennzahlen(anyCollection(), any())).willReturn(List.of());

        assertThat(eintraege.baue(ZEILEN, PROJEKT, null)).extracting(EmailReiterEintragDto::nachrichtenAnzahl)
                .containsExactly(1, 1);
    }

    @Test
    @DisplayName("Inzwischen gelöschte Mail fällt aus der Seite, statt eine leere Zeile zu zeigen")
    void geloeschteMail() {
        given(emailRepository.findAllById(anyCollection())).willReturn(List.of(antwort));

        assertThat(eintraege.baue(ZEILEN, PROJEKT, null)).extracting(EmailReiterEintragDto::id).containsExactly(22L);
    }

    @Test
    @DisplayName("Unzustellbar irgendwo im Verlauf: Status und Grund der neuesten nicht angekommenen Mail")
    void unzustellbarImVerlauf() {
        given(reiterRepository.kennzahlen(anyCollection(), any())).willReturn(List.of(
                new EmailVerlaufKennzahl(2L, 4, true, "unknown user")));

        EmailReiterEintragDto zeile = eintraege.baue(ZEILEN, PROJEKT, null).get(1);

        assertThat(zeile.zustellStatus()).isEqualTo("UNZUSTELLBAR");
        assertThat(zeile.zustellFehler()).isEqualTo("unknown user");
    }

    @Test
    @DisplayName("Sonst Status der angezeigten Mail; ohne Status null")
    void statusDerMail() {
        angebot.setZustellStatus(null);
        antwort.setZustellStatus(ZustellStatus.OFFEN);

        List<EmailReiterEintragDto> liste = eintraege.baue(ZEILEN, PROJEKT, null);

        assertThat(liste.get(0).zustellStatus()).isNull();
        assertThat(liste.get(1).zustellStatus()).isEqualTo("OFFEN");
    }

    @Test
    @DisplayName("Anhang an einer Bezugs-Mail des Verlaufs → mitAnhang; ohne Dokumentrechte-Prüfung außer beim Lieferanten")
    void mitAnhang() {
        given(reiterRepository.anhaenge(any(), anyCollection())).willReturn(List.of(new EmailVerlaufAnhang(2L, 70L)));

        assertThat(eintraege.baue(ZEILEN, PROJEKT, null)).extracting(EmailReiterEintragDto::mitAnhang)
                .containsExactly(false, true);
        verifyNoInteractions(dokumentZugriffService);
    }

    @Test
    @DisplayName("Lieferant: Anhänge, die als Dokument eines nicht sichtbaren Typs abgelegt sind, zählen nicht")
    void lieferantNurSichtbareAnhaenge() {
        Set<LieferantDokumentTyp> sichtbar = EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN);
        given(reiterRepository.anhaenge(any(), anyCollection())).willReturn(List.of(
                new EmailVerlaufAnhang(1L, 70L), new EmailVerlaufAnhang(2L, 80L), new EmailVerlaufAnhang(2L, 81L)));
        given(dokumentZugriffService.gesperrteAnhaenge(anyCollection(), eq(sichtbar))).willReturn(Set.of(70L, 80L));

        assertThat(eintraege.baue(ZEILEN, LIEFERANT, sichtbar)).extracting(EmailReiterEintragDto::mitAnhang)
                .containsExactly(false, true);
    }
}

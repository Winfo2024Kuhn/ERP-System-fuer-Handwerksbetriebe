package org.example.kalkulationsprogramm.service.mail.reiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.EmailBezug;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.dto.Email.EmailReiterEintragDto;
import org.example.kalkulationsprogramm.dto.Email.EmailReiterSeiteDto;
import org.example.kalkulationsprogramm.repository.EmailReiterAbfrage;
import org.example.kalkulationsprogramm.repository.EmailReiterRepository;
import org.example.kalkulationsprogramm.repository.EmailVerlaufZeile;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeit;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailReiterServiceTest {

    private static final Authentication MAX = new UsernamePasswordAuthenticationToken("max", null, List.of());

    @Mock private EmailReiterRepository reiterRepository;
    @Mock private PostfachSichtbarkeitService postfachSichtbarkeitService;
    @Mock private LieferantDokumentZugriffService dokumentZugriffService;
    @Mock private EmailReiterEintraege eintraege;
    @InjectMocks private EmailReiterService service;

    @BeforeEach
    void standard() {
        given(postfachSichtbarkeitService.fuer(any())).willReturn(PostfachSichtbarkeit.ALLES);
        given(reiterRepository.bezugVorhanden(any(), anyLong())).willReturn(true);
        given(reiterRepository.zaehleVerlaeufe(any())).willReturn(3L);
        given(reiterRepository.verlaeufe(any())).willReturn(List.of(new EmailVerlaufZeile(1L, 11L)));
        given(dokumentZugriffService.sichtbareTypen(isNull(), any()))
                .willReturn(Optional.of(EnumSet.allOf(LieferantDokumentTyp.class)));
    }

    private EmailReiterSeiteDto seite(String bezug, long id) {
        return service.seite(bezug, id, 0, 25, null, null, MAX);
    }

    private EmailReiterAbfrage abfrage() {
        ArgumentCaptor<EmailReiterAbfrage> captor = ArgumentCaptor.forClass(EmailReiterAbfrage.class);
        verify(reiterRepository).zaehleVerlaeufe(captor.capture());
        return captor.getValue();
    }

    private static void erwarteFehler(Runnable aufruf, HttpStatus status, String meldung) {
        assertThatThrownBy(aufruf::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(status);
                    assertThat(ex.getReason()).isEqualTo(meldung);
                });
    }

    @Test
    @DisplayName("Antwort: Einträge aus dem Aufbau, Gesamtzahl aus derselben Abfrage, Seite und Größe")
    void antwort() {
        EmailReiterEintragDto eintrag = new EmailReiterEintragDto(11L, "AW: Angebot Geländer",
                "max.mustermann@example.com", "info@example.com", EmailDirection.IN,
                LocalDateTime.of(2026, 11, 3, 10, 15), "<p>Hallo</p>", 3, true, null, null);
        given(eintraege.baue(any(), any(), any())).willReturn(List.of(eintrag));

        EmailReiterSeiteDto antwort = service.seite("projekt", 5L, 0, 25, null, null, MAX);

        assertThat(antwort.eintraege()).containsExactly(eintrag);
        assertThat(antwort.gesamt()).isEqualTo(3L);
        assertThat(antwort.seite()).isZero();
        assertThat(antwort.seitenGroesse()).isEqualTo(25);
        EmailReiterAbfrage abfrage = abfrage();
        assertThat(abfrage.bezug()).isEqualTo(EmailBezug.PROJEKT);
        assertThat(abfrage.bezugId()).isEqualTo(5L);
        assertThat(abfrage.woerter()).isEmpty();
        assertThat(abfrage.richtung()).isNull();
        assertThat(abfrage.sichtbarePostfachIds()).as("Admin: keine Einschränkung").isNull();
        verify(eintraege).baue(eq(List.of(new EmailVerlaufZeile(1L, 11L))), eq(abfrage), isNull());
    }

    @Test
    @DisplayName("Alle vier Bereiche werden erkannt, Groß-/Kleinschreibung egal")
    void bereiche() {
        assertThat(seite("projekt", 1L)).isNotNull();
        assertThat(seite("ANFRAGE", 1L)).isNotNull();
        assertThat(seite("Kunde", 1L)).isNotNull();
        assertThat(seite("lieferant", 1L)).isNotNull();
        verify(reiterRepository).bezugVorhanden(EmailBezug.PROJEKT, 1L);
        verify(reiterRepository).bezugVorhanden(EmailBezug.ANFRAGE, 1L);
        verify(reiterRepository).bezugVorhanden(EmailBezug.KUNDE, 1L);
        verify(reiterRepository).bezugVorhanden(EmailBezug.LIEFERANT, 1L);
    }

    @ParameterizedTest
    @ValueSource(strings = { "rechnung", "'; DROP TABLE email; --", "<script>alert(1)</script>", " " })
    @DisplayName("Unbekannter Bereich (auch Angriffstexte) → 400, ohne Datenbankzugriff")
    void unbekannterBereich(String bezug) {
        erwarteFehler(() -> seite(bezug, 1L), HttpStatus.BAD_REQUEST, "Unbekannter Bereich für E-Mails.");
        verifyNoInteractions(reiterRepository);
    }

    @Test
    void ohneBereich400() {
        erwarteFehler(() -> service.seite(null, 1L, 0, 25, null, null, MAX), HttpStatus.BAD_REQUEST,
                "Unbekannter Bereich für E-Mails.");
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, -1L, Long.MAX_VALUE })
    @DisplayName("Datensatz fehlt (0, negativ, Long.MAX_VALUE) → 404 je Bereich")
    void nichtGefunden(long id) {
        given(reiterRepository.bezugVorhanden(any(), eq(id))).willReturn(false);

        erwarteFehler(() -> seite("projekt", id), HttpStatus.NOT_FOUND, "Projekt nicht gefunden.");
        erwarteFehler(() -> seite("anfrage", id), HttpStatus.NOT_FOUND, "Anfrage nicht gefunden.");
        erwarteFehler(() -> seite("kunde", id), HttpStatus.NOT_FOUND, "Kunde nicht gefunden.");
        erwarteFehler(() -> seite("lieferant", id), HttpStatus.NOT_FOUND, "Lieferant nicht gefunden.");
        verify(reiterRepository, never()).zaehleVerlaeufe(any());
    }

    @Test
    void ohneIdNichtGefunden() {
        erwarteFehler(() -> service.seite("kunde", null, 0, 25, null, null, MAX), HttpStatus.NOT_FOUND,
                "Kunde nicht gefunden.");
        verify(reiterRepository, never()).bezugVorhanden(any(), anyLong());
    }

    @Test
    @DisplayName("Suche: getrimmt, normalisiert, je Wort ein LIKE-Muster mit maskierten Platzhaltern")
    void suchwoerter() {
        service.seite("projekt", 5L, 0, 25, "  Geländer 50%_Rabatt ", null, MAX);

        assertThat(abfrage().woerter()).containsExactly("%gelander%", "%50\\%\\_rabatt%");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "a", "  ä  " })
    @DisplayName("Unter 2 Zeichen: keine Suche, alle Verläufe")
    void zuKurzKeineSuche(String q) {
        service.seite("projekt", 5L, 0, 25, q, null, MAX);

        assertThat(abfrage().woerter()).isEmpty();
    }

    @Test
    @DisplayName("200 Zeichen gehen, 201 → 400")
    void zuLang() {
        service.seite("projekt", 5L, 0, 25, "x".repeat(200), null, MAX);
        assertThat(abfrage().woerter()).hasSize(1);

        erwarteFehler(() -> service.seite("projekt", 5L, 0, 25, "x".repeat(201), null, MAX), HttpStatus.BAD_REQUEST,
                "Der Suchbegriff ist zu lang (höchstens 200 Zeichen).");
    }

    @Test
    @DisplayName("Angriffstexte in q sind nur Suchwörter (Named-Params im Repository)")
    void angriffstexte() {
        service.seite("projekt", 5L, 0, 25, "'; DROP TABLE email; --", null, MAX);
        assertThat(abfrage().woerter()).containsExactly("%';%", "%drop%", "%table%", "%email;%", "%--%");
    }

    @Test
    void xssText() {
        service.seite("projekt", 5L, 0, 25, "<script>alert(1)</script>", null, MAX);
        assertThat(abfrage().woerter()).containsExactly("%<script>alert(1)</script>%");
    }

    @Test
    @DisplayName("Richtung IN/OUT; fehlt oder leer = alle; anderer Wert → 400")
    void richtung() {
        service.seite("projekt", 5L, 0, 25, null, "OUT", MAX);
        assertThat(abfrage().richtung()).isEqualTo(EmailDirection.OUT);
    }

    @Test
    void richtungEingang() {
        service.seite("projekt", 5L, 0, 25, null, "IN", MAX);
        assertThat(abfrage().richtung()).isEqualTo(EmailDirection.IN);
    }

    @Test
    void richtungLeer() {
        service.seite("projekt", 5L, 0, 25, null, " ", MAX);
        assertThat(abfrage().richtung()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "SEITWAERTS", "in", "'; DROP TABLE email; --" })
    void unbekannteRichtung(String richtung) {
        erwarteFehler(() -> service.seite("projekt", 5L, 0, 25, null, richtung, MAX), HttpStatus.BAD_REQUEST,
                "Unbekannte Richtung.");
    }

    @Test
    @DisplayName("size=100000 → 50, page=-1 → 0")
    void grenzen() {
        EmailReiterSeiteDto antwort = service.seite("projekt", 5L, -1, 100_000, null, null, MAX);

        assertThat(antwort.seite()).isZero();
        assertThat(antwort.seitenGroesse()).isEqualTo(50);
        assertThat(abfrage().offset()).isZero();
        assertThat(abfrage().limit()).isEqualTo(50);
    }

    @Test
    @DisplayName("size=0 → 1; Seite 2 → Offset 2 × Größe")
    void zweiteSeite() {
        EmailReiterSeiteDto antwort = service.seite("projekt", 5L, 2, 0, null, null, MAX);

        assertThat(antwort.seitenGroesse()).isEqualTo(1);
        assertThat(abfrage().offset()).isEqualTo(2);
    }

    @Test
    @DisplayName("Riesige Seitenzahl: kein Überlauf, leere Seite mit Gesamtzahl")
    void riesigeSeite() {
        EmailReiterSeiteDto antwort = service.seite("projekt", 5L, Integer.MAX_VALUE, 50, null, null, MAX);

        assertThat(antwort.eintraege()).isEmpty();
        assertThat(antwort.gesamt()).isEqualTo(3L);
        verify(reiterRepository, never()).verlaeufe(any());
    }

    @Test
    @DisplayName("Keine Treffer: keine Seitenabfrage")
    void keineTreffer() {
        given(reiterRepository.zaehleVerlaeufe(any())).willReturn(0L);

        EmailReiterSeiteDto antwort = seite("projekt", 5L);

        assertThat(antwort.eintraege()).isEmpty();
        assertThat(antwort.gesamt()).isZero();
        verify(reiterRepository, never()).verlaeufe(any());
    }

    @Test
    @DisplayName("Benutzer ohne Admin: nur seine Postfächer zählen")
    void postfachSichtbarkeit() {
        given(postfachSichtbarkeitService.fuer(MAX)).willReturn(new PostfachSichtbarkeit(false, Set.of(4L, 7L)));

        seite("projekt", 5L);

        assertThat(abfrage().sichtbarePostfachIds()).containsExactlyInAnyOrder(4L, 7L);
    }

    @Test
    @DisplayName("Lieferant ohne sichtbare Dokumenttypen-Angabe (nicht zuzuordnen) → 401")
    void lieferantOhneTypen401() {
        given(dokumentZugriffService.sichtbareTypen(isNull(), any())).willReturn(Optional.empty());

        erwarteFehler(() -> seite("lieferant", 5L), HttpStatus.UNAUTHORIZED, "Bitte erneut anmelden.");
        verifyNoInteractions(reiterRepository);
    }

    @Test
    @DisplayName("Lieferant: sichtbare Dokumenttypen gehen an den Aufbau (Anhänge filtern)")
    void lieferantTypen() {
        given(dokumentZugriffService.sichtbareTypen(isNull(), any()))
                .willReturn(Optional.of(EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN)));

        seite("lieferant", 5L);

        verify(eintraege).baue(any(), any(), eq(EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN)));
    }

    @Test
    @DisplayName("Projekt, Anfrage, Kunde fragen keine Dokumentrechte ab")
    void andereOhneDokumentrechte() {
        seite("projekt", 5L);
        seite("anfrage", 5L);
        seite("kunde", 5L);
        verifyNoInteractions(dokumentZugriffService);
    }
}

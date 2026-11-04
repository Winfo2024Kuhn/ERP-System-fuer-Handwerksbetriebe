package org.example.kalkulationsprogramm.service.mail.suche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailOrdner;
import org.example.kalkulationsprogramm.dto.Email.EmailSuchErgebnis;
import org.example.kalkulationsprogramm.dto.Email.UnifiedEmailDto;
import org.example.kalkulationsprogramm.repository.EmailSuchAbfrage;
import org.example.kalkulationsprogramm.repository.EmailSucheRepository;
import org.example.kalkulationsprogramm.service.EmailGelesenService;
import org.example.kalkulationsprogramm.service.GelesenStand;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeit;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.example.kalkulationsprogramm.service.mail.liste.EmailListenDtoMapper;
import org.example.kalkulationsprogramm.service.mail.liste.SteuerberaterOrdner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
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
class EmailSucheServiceTest {

    private static final Authentication ERIKA = new UsernamePasswordAuthenticationToken("erika", null, List.of());

    @Mock private EmailSucheRepository sucheRepository;
    @Mock private PostfachSichtbarkeitService postfachSichtbarkeitService;
    @Mock private EmailGelesenService emailGelesenService;
    @Mock private SteuerberaterOrdner steuerberaterOrdner;
    @Mock private EmailListenDtoMapper mapper;
    @InjectMocks private EmailSucheService service;

    @BeforeEach
    void standard() {
        given(postfachSichtbarkeitService.fuer(any())).willReturn(PostfachSichtbarkeit.ALLES);
        given(emailGelesenService.fuer(any())).willReturn(GelesenStand.GEMEINSAM);
        given(mapper.listenDto(any(), any())).willAnswer(aufruf -> {
            UnifiedEmailDto dto = new UnifiedEmailDto();
            dto.setId(aufruf.<Email>getArgument(0).getId());
            return dto;
        });
    }

    private static Email mail(long id) {
        Email e = new Email();
        e.setId(id);
        return e;
    }

    private EmailSuchAbfrage abfrage() {
        ArgumentCaptor<EmailSuchAbfrage> captor = ArgumentCaptor.forClass(EmailSuchAbfrage.class);
        verify(sucheRepository).sucheIds(captor.capture());
        return captor.getValue();
    }

    private static void erwarte400(Runnable aufruf, String meldung) {
        assertThatThrownBy(aufruf::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo(meldung);
                });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "a", "  a  ", " \t" })
    @DisplayName("Weniger als 2 Zeichen: 400, keine Abfrage")
    void zuKurz(String q) {
        erwarte400(() -> service.suche(q, null, 0, 25, ERIKA), "Bitte mindestens 2 Zeichen eingeben.");
        verifyNoInteractions(sucheRepository);
    }

    @Test
    @DisplayName("Mehr als 200 Zeichen (nach Trimmen): 400; genau 200 sind erlaubt")
    void zuLang() {
        erwarte400(() -> service.suche("x".repeat(201), null, 0, 25, ERIKA),
                "Der Suchbegriff ist zu lang (höchstens 200 Zeichen).");
        verifyNoInteractions(sucheRepository);

        service.suche("  " + "x".repeat(200) + "  ", null, 0, 25, ERIKA);
        verify(sucheRepository).sucheIds(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "drafts", "INBOX", "'; DROP TABLE email; --", "<script>alert(1)</script>" })
    void unbekannterOrdner(String ordner) {
        erwarte400(() -> service.suche("geländer", ordner, 0, 25, ERIKA), "Unbekannter Ordner.");
        verifyNoInteractions(sucheRepository);
    }

    @Test
    @DisplayName("Standard: alle Ordner, Seite 0, 25 Einträge (26 werden abgefragt für hatMehr)")
    void standardwerte() {
        EmailSuchErgebnis ergebnis = service.suche("geländer", null, 0, 25, ERIKA);

        EmailSuchAbfrage a = abfrage();
        assertThat(a.ordner()).isEqualTo(EmailOrdner.ALLE);
        assertThat(a.offset()).isZero();
        assertThat(a.limit()).isEqualTo(26);
        assertThat(ergebnis.seite()).isZero();
        assertThat(ergebnis.seitenGroesse()).isEqualTo(25);
        assertThat(ergebnis.eintraege()).isEmpty();
        assertThat(ergebnis.hatMehr()).isFalse();
    }

    @Test
    @DisplayName("Grenzen: size höchstens 50, mindestens 1; page negativ wird 0")
    void grenzen() {
        EmailSuchErgebnis gross = service.suche("geländer", "inbox", -1, 100_000, ERIKA);
        assertThat(gross.seite()).isZero();
        assertThat(gross.seitenGroesse()).isEqualTo(50);
        assertThat(abfrage().limit()).isEqualTo(51);

        EmailSuchErgebnis klein = service.suche("geländer", "inbox", 3, 0, ERIKA);
        assertThat(klein.seitenGroesse()).isEqualTo(1);
        assertThat(klein.seite()).isEqualTo(3);
    }

    @Test
    @DisplayName("Seite 2 bei 25 je Seite beginnt bei Zeile 50")
    void offset() {
        service.suche("geländer", "trash", 2, 25, ERIKA);
        EmailSuchAbfrage a = abfrage();
        assertThat(a.offset()).isEqualTo(50);
        assertThat(a.ordner()).isEqualTo(EmailOrdner.TRASH);
    }

    @Test
    @DisplayName("Riesige Seitenzahl: keine Abfrage, leere Seite")
    void riesigeSeite() {
        EmailSuchErgebnis ergebnis = service.suche("geländer", null, Integer.MAX_VALUE, 50, ERIKA);
        assertThat(ergebnis.eintraege()).isEmpty();
        assertThat(ergebnis.hatMehr()).isFalse();
        verifyNoInteractions(sucheRepository);
    }

    @Test
    @DisplayName("Suchwörter als maskierte LIKE-Muster, normalisiert wie der Suchtext (ue → u)")
    void muster() {
        service.suche("Wuerth 50%_\\", null, 0, 25, ERIKA);

        assertThat(abfrage().woerter()).containsExactly("%wurth%", "%50\\%\\_\\\\%");
    }

    @Test
    @DisplayName("Höchstens 10 Wörter gehen in die Abfrage")
    void zehnWoerter() {
        service.suche("a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13", null, 0, 25, ERIKA);
        assertThat(abfrage().woerter()).hasSize(10);
    }

    @Test
    @DisplayName("Eingabe nur aus Akzentzeichen: keine Wörter, keine Abfrage")
    void keineWoerter() {
        EmailSuchErgebnis ergebnis = service.suche("\u0301\u0301", null, 0, 25, ERIKA);
        assertThat(ergebnis.eintraege()).isEmpty();
        verifyNoInteractions(sucheRepository);
    }

    @Test
    @DisplayName("Admin sieht alles (keine Einschränkung), Benutzer nur seine Postfächer")
    void sichtbarkeit() {
        service.suche("geländer", null, 0, 25, ERIKA);
        assertThat(abfrage().sichtbarePostfachIds()).isNull();

        org.mockito.Mockito.reset(sucheRepository);
        given(postfachSichtbarkeitService.fuer(ERIKA)).willReturn(new PostfachSichtbarkeit(false, Set.of(1L, 4L)));
        service.suche("geländer", null, 0, 25, ERIKA);
        assertThat(abfrage().sichtbarePostfachIds()).containsExactlyInAnyOrder(1L, 4L);
    }

    @Test
    @DisplayName("Steuerberater-Domains nur für den Steuerberater-Ordner")
    void steuerberater() {
        service.suche("geländer", "inbox", 0, 25, ERIKA);
        verify(steuerberaterOrdner, never()).domains();

        org.mockito.Mockito.reset(sucheRepository);
        given(steuerberaterOrdner.domains()).willReturn(Set.of("kanzlei.de"));
        service.suche("geländer", "tax-advisors", 0, 25, ERIKA);
        assertThat(abfrage().steuerberaterDomains()).containsExactly("kanzlei.de");
    }

    @Test
    @DisplayName("hatMehr: eine Zeile mehr als die Seite; Reihenfolge der Treffer bleibt, Fehlende fallen weg")
    void seiteUndReihenfolge() {
        given(sucheRepository.sucheIds(any())).willReturn(List.of(5L, 3L, 9L));
        given(sucheRepository.findSeite(anyCollection())).willReturn(List.of(mail(3), mail(5)));

        EmailSuchErgebnis ergebnis = service.suche("geländer", null, 0, 2, ERIKA);

        assertThat(ergebnis.hatMehr()).isTrue();
        assertThat(ergebnis.eintraege()).extracting(UnifiedEmailDto::getId).containsExactly(5L, 3L);
        verify(sucheRepository).findSeite(List.of(5L, 3L));
    }

    @Test
    @DisplayName("Letzte Seite: hatMehr false; gelesen-Stand des Benutzers geht in die DTOs")
    void letzteSeite() {
        GelesenStand stand = new GelesenStand(Set.of(1L), true);
        given(emailGelesenService.fuer(ERIKA)).willReturn(stand);
        List<Long> ids = LongStream.rangeClosed(1, 2).boxed().toList();
        given(sucheRepository.sucheIds(any())).willReturn(ids);
        given(sucheRepository.findSeite(anyCollection())).willReturn(List.of(mail(1), mail(2)));

        EmailSuchErgebnis ergebnis = service.suche("geländer", null, 0, 25, ERIKA);

        assertThat(ergebnis.hatMehr()).isFalse();
        assertThat(ergebnis.eintraege()).hasSize(2);
        verify(mapper, org.mockito.Mockito.times(2)).listenDto(any(), org.mockito.ArgumentMatchers.eq(stand));
    }

    @Test
    @DisplayName("Keine Treffer: keine zweite Abfrage")
    void keineTreffer() {
        given(sucheRepository.sucheIds(any())).willReturn(List.of());

        assertThat(service.suche("geländer", null, 0, 25, ERIKA).eintraege()).isEmpty();
        verify(sucheRepository, never()).findSeite(anyCollection());
    }
}

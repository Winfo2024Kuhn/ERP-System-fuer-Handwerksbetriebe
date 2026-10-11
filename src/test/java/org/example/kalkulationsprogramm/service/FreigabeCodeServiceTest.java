package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Jeder Zweig von {@link FreigabeCodeService}: Versandgrenzen, Prüfung, Sperre, Maskierung.
 * Uhr fest auf den 11.10.2026, 14:00 Uhr (Europe/Berlin). Nur Dummy-Daten.
 */
@ExtendWith(MockitoExtension.class)
class FreigabeCodeServiceTest
{
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDateTime JETZT = LocalDateTime.of(2026, 10, 11, 14, 0, 0);

    @Mock
    private DokumentFreigabeRepository repository;
    @Mock
    private FreigabeCodeMailService mailService;
    @Mock
    private WebPushService webPushService;

    private FreigabeCodeService service;

    @BeforeEach
    void setUp()
    {
        Clock clock = Clock.fixed(JETZT.atZone(BERLIN).toInstant(), BERLIN);
        service = new FreigabeCodeService(repository, mailService, webPushService, Runnable::run, clock);
        ReflectionTestUtils.setField(service, "hashSalt", "test-salz");
    }

    private static DokumentFreigabe offeneFreigabe(String uuid)
    {
        DokumentFreigabe f = new DokumentFreigabe();
        f.setUuid(uuid);
        f.setStatus(FreigabeStatus.PENDING);
        f.setAblaufDatum(LocalDateTime.now().plusDays(14));
        f.setKundeEmail("max.mustermann@example.de");
        f.setDokumentArt("Angebot");
        f.setDokumentNummer("AN-2026-0001");
        return f;
    }

    private DokumentFreigabe gesperrtGeladen(DokumentFreigabe f)
    {
        when(repository.findByUuidZumSperrenOhneWarten(f.getUuid())).thenReturn(Optional.of(f));
        return f;
    }

    // ===================== fordereCodeAn =====================

    @Test
    void fordereCodeAn_unbekannteUuid_wirftIllegalArgument()
    {
        when(repository.findByUuidZumSperrenOhneWarten("gibts-nicht")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fordereCodeAn("gibts-nicht"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(DokumentFreigabeService.UNBEKANNTE_UUID_MESSAGE);
        verifyNoInteractions(mailService);
    }

    @Test
    void fordereCodeAn_nichtMehrOffen_wirftNichtOffen()
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-angenommen"));
        f.setStatus(FreigabeStatus.ACCEPTED);

        assertThatThrownBy(() -> service.fordereCodeAn("u-angenommen"))
                .isInstanceOfSatisfying(FreigabeCodeAnforderungException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeAnforderungException.Grund.NICHT_OFFEN);
                    assertThat(e.getErneutAnfordernInSekunden()).isNull();
                });
        verifyNoInteractions(mailService);
    }

    @Test
    void fordereCodeAn_abgelaufen_wirftNichtOffen()
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-abgelaufen"));
        f.setAblaufDatum(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.fordereCodeAn("u-abgelaufen"))
                .isInstanceOfSatisfying(FreigabeCodeAnforderungException.class,
                        e -> assertThat(e.getGrund()).isEqualTo(FreigabeCodeAnforderungException.Grund.NICHT_OFFEN));
    }

    @Test
    void fordereCodeAn_ohneEmail_wirftKeineEmail()
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-ohne-mail"));
        f.setKundeEmail(null);

        assertThatThrownBy(() -> service.fordereCodeAn("u-ohne-mail"))
                .isInstanceOfSatisfying(FreigabeCodeAnforderungException.class,
                        e -> assertThat(e.getGrund()).isEqualTo(FreigabeCodeAnforderungException.Grund.KEINE_EMAIL));
    }

    @Test
    void fordereCodeAn_leereEmail_wirftKeineEmail()
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-leere-mail"));
        f.setKundeEmail("   ");

        assertThatThrownBy(() -> service.fordereCodeAn("u-leere-mail"))
                .isInstanceOfSatisfying(FreigabeCodeAnforderungException.class,
                        e -> assertThat(e.getGrund()).isEqualTo(FreigabeCodeAnforderungException.Grund.KEINE_EMAIL));
    }

    @Test
    void fordereCodeAn_ersterCode_speichertNurHmacUndVerschicktCode() throws Exception
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-erster"));

        FreigabeCodeService.CodeVersendet antwort = service.fordereCodeAn("u-erster");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendeCode(eq(f), code.capture(), eq(JETZT.plusMinutes(15)));
        assertThat(code.getValue()).matches("\\d{6}");
        assertThat(f.getCodeHash()).isEqualTo(service.hmac("u-erster", code.getValue()))
                .doesNotContain(code.getValue());
        assertThat(f.getCodeGueltigBis()).isEqualTo(JETZT.plusMinutes(15));
        assertThat(f.getCodeAngefordertAm()).isEqualTo(JETZT);
        assertThat(f.getCodeFensterStart()).isEqualTo(JETZT);
        assertThat(f.getCodeFensterAnzahl()).isEqualTo(1);
        assertThat(f.getCodeFehlversuche()).isZero();
        verify(repository).saveAndFlush(f);

        assertThat(antwort.emailMaskiert()).isEqualTo("m•••@example.de");
        assertThat(antwort.gueltigBis()).isEqualTo(JETZT.plusMinutes(15));
        assertThat(antwort.erneutAnfordernInSekunden()).isEqualTo(30);
    }

    @Test
    void fordereCodeAn_neuerCodeSetztFehlversucheZurueck() throws Exception
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-reset"));
        f.setCodeHash("alter-hash");
        f.setCodeFehlversuche(3);
        f.setCodeAngefordertAm(JETZT.minusMinutes(5));
        f.setCodeFensterStart(JETZT.minusMinutes(5));
        f.setCodeFensterAnzahl(1);

        service.fordereCodeAn("u-reset");

        assertThat(f.getCodeHash()).isNotEqualTo("alter-hash");
        assertThat(f.getCodeFehlversuche()).isZero();
        assertThat(f.getCodeFensterStart()).isEqualTo(JETZT.minusMinutes(5));
        assertThat(f.getCodeFensterAnzahl()).isEqualTo(2);
    }

    @Test
    void fordereCodeAn_innerhalbDerWartezeit_wirftZuFruehMitRestsekunden()
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-wartezeit"));
        f.setCodeAngefordertAm(JETZT.minusSeconds(10));

        assertThatThrownBy(() -> service.fordereCodeAn("u-wartezeit"))
                .isInstanceOfSatisfying(FreigabeCodeAnforderungException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeAnforderungException.Grund.ZU_FRUEH);
                    assertThat(e.getErneutAnfordernInSekunden()).isEqualTo(20L);
                });
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(mailService);
    }

    @Test
    void fordereCodeAn_genauNachDreissigSekunden_erlaubt() throws Exception
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-grenze"));
        f.setCodeAngefordertAm(JETZT.minusSeconds(30));
        f.setCodeFensterStart(JETZT.minusSeconds(30));
        f.setCodeFensterAnzahl(1);

        service.fordereCodeAn("u-grenze");

        verify(mailService).sendeCode(eq(f), anyString(), any());
    }

    @Test
    void fordereCodeAn_stundenlimitErreicht_wirftZuFruehBisFensterende()
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-limit"));
        f.setCodeAngefordertAm(JETZT.minusMinutes(2));
        f.setCodeFensterStart(JETZT.minusMinutes(40));
        f.setCodeFensterAnzahl(FreigabeCodeService.MAX_CODES_PRO_FENSTER);

        assertThatThrownBy(() -> service.fordereCodeAn("u-limit"))
                .isInstanceOfSatisfying(FreigabeCodeAnforderungException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeAnforderungException.Grund.ZU_FRUEH);
                    assertThat(e.getErneutAnfordernInSekunden()).isEqualTo(20L * 60);
                    assertThat(e.getMessage()).contains("später");
                });
        verifyNoInteractions(mailService);
    }

    @Test
    void fordereCodeAn_stundenlimitInDerLetztenSekunde_meldetMindestensEineSekunde()
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-limit-rest"));
        f.setCodeAngefordertAm(JETZT.minusMinutes(2));
        f.setCodeFensterStart(JETZT.minusHours(1).plusNanos(500_000_000));
        f.setCodeFensterAnzahl(FreigabeCodeService.MAX_CODES_PRO_FENSTER);

        assertThatThrownBy(() -> service.fordereCodeAn("u-limit-rest"))
                .isInstanceOfSatisfying(FreigabeCodeAnforderungException.class,
                        e -> assertThat(e.getErneutAnfordernInSekunden()).isEqualTo(1L));
    }

    @Test
    void fordereCodeAn_fensterAbgelaufen_beginntNeuesFenster() throws Exception
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-neues-fenster"));
        f.setCodeAngefordertAm(JETZT.minusHours(1));
        f.setCodeFensterStart(JETZT.minusHours(1));
        f.setCodeFensterAnzahl(FreigabeCodeService.MAX_CODES_PRO_FENSTER);

        service.fordereCodeAn("u-neues-fenster");

        assertThat(f.getCodeFensterStart()).isEqualTo(JETZT);
        assertThat(f.getCodeFensterAnzahl()).isEqualTo(1);
        verify(mailService).sendeCode(eq(f), anyString(), any());
    }

    @Test
    void fordereCodeAn_versandScheitert_wirftVersandFehlgeschlagen() throws Exception
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-smtp-kaputt"));
        doThrow(new jakarta.mail.MessagingException("SMTP nicht erreichbar"))
                .when(mailService).sendeCode(eq(f), anyString(), any());

        assertThatThrownBy(() -> service.fordereCodeAn("u-smtp-kaputt"))
                .isInstanceOfSatisfying(FreigabeCodeAnforderungException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeAnforderungException.Grund.VERSAND_FEHLGESCHLAGEN);
                    assertThat(e.getErneutAnfordernInSekunden()).isNull();
                });
        assertThat(f.getCodeHash()).isNull();
        assertThat(f.getCodeAngefordertAm()).isNull();
        assertThat(f.getCodeFensterStart()).isNull();
        assertThat(f.getCodeFensterAnzahl()).isZero();
    }

    // ===================== pruefeUndVerbrauche =====================

    private DokumentFreigabe mitOffenemCode(String uuid, String code)
    {
        DokumentFreigabe f = offeneFreigabe(uuid);
        f.setCodeHash(service.hmac(uuid, code));
        f.setCodeGueltigBis(JETZT.plusMinutes(10));
        return f;
    }

    @Test
    void pruefe_ohneAngefordertenCode_wirftNichtAngefordert()
    {
        DokumentFreigabe f = offeneFreigabe("p-ohne");

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "123456"))
                .isInstanceOfSatisfying(FreigabeCodeException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.NICHT_ANGEFORDERT);
                    assertThat(e.getVerbleibendeVersuche()).isNull();
                });
    }

    @Test
    void pruefe_ohneAblaufzeit_giltAlsAbgelaufen()
    {
        DokumentFreigabe f = mitOffenemCode("p-ohne-ablauf", "123456");
        f.setCodeGueltigBis(null);

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "123456"))
                .isInstanceOfSatisfying(FreigabeCodeException.class,
                        e -> assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.ABGELAUFEN));
        assertThat(f.getCodeHash()).isNull();
    }

    @Test
    void pruefe_abgelaufenerCode_wirdVerworfen()
    {
        DokumentFreigabe f = mitOffenemCode("p-abgelaufen", "123456");
        f.setCodeGueltigBis(JETZT.minusSeconds(1));

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "123456"))
                .isInstanceOfSatisfying(FreigabeCodeException.class,
                        e -> assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.ABGELAUFEN));
        assertThat(f.getCodeHash()).isNull();
        assertThat(f.getCodeBestaetigtAm()).isNull();
    }

    @Test
    void pruefe_genauZumAblaufzeitpunkt_giltNoch()
    {
        DokumentFreigabe f = mitOffenemCode("p-grenze", "123456");
        f.setCodeGueltigBis(JETZT);

        service.pruefeUndVerbrauche(f, "123456");

        assertThat(f.getCodeBestaetigtAm()).isEqualTo(JETZT);
    }

    @ParameterizedTest
    @ValueSource(strings = { "654321", "12345", "1234567", "", "abcdef", "'; DROP TABLE dokument_freigabe; --",
            "<script>alert(1)</script>" })
    void pruefe_falscherCode_zaehltFehlversuch(String eingabe)
    {
        DokumentFreigabe f = mitOffenemCode("p-falsch", "123456");

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, eingabe))
                .isInstanceOfSatisfying(FreigabeCodeException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.FALSCH);
                    assertThat(e.getVerbleibendeVersuche()).isEqualTo(4);
                });
        assertThat(f.getCodeFehlversuche()).isEqualTo(1);
        assertThat(f.getCodeHash()).isNotNull();
    }

    @Test
    void pruefe_nullAlsCode_zaehltFehlversuch()
    {
        DokumentFreigabe f = mitOffenemCode("p-null", "123456");

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, null))
                .isInstanceOfSatisfying(FreigabeCodeException.class,
                        e -> assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.FALSCH));
    }

    @Test
    void pruefe_codeEinerAnderenFreigabe_passtNicht()
    {
        // Der HMAC bindet den Code an die UUID: derselbe Code einer anderen Freigabe zählt nicht.
        DokumentFreigabe f = mitOffenemCode("p-eigene", "123456");
        f.setCodeHash(service.hmac("p-fremde", "123456"));

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "123456"))
                .isInstanceOf(FreigabeCodeException.class);
    }

    @Test
    void pruefe_fuenfterFehlversuch_schicktAutomatischNeuenCode() throws Exception
    {
        DokumentFreigabe f = mitOffenemCode("p-auto", "123456");
        f.setCodeFehlversuche(FreigabeCodeService.MAX_FEHLVERSUCHE - 1);
        f.setCodeFehlversucheGesamt(4);
        f.setCodeAngefordertAm(JETZT.minusMinutes(3));
        f.setCodeFensterStart(JETZT.minusMinutes(3));
        f.setCodeFensterAnzahl(1);
        String alterHash = f.getCodeHash();

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "000000"))
                .isInstanceOfSatisfying(FreigabeCodeException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.NEUER_CODE_VERSCHICKT);
                    assertThat(e.getVerbleibendeVersuche()).isNull();
                    assertThat(e.getErneutAnfordernInSekunden()).isEqualTo(30L);
                    assertThat(e.getEmailMaskiert()).isEqualTo("m•••@example.de");
                });

        ArgumentCaptor<String> neuerCode = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendeCode(eq(f), neuerCode.capture(), eq(JETZT.plusMinutes(15)));
        assertThat(f.getCodeHash()).isNotEqualTo(alterHash).isEqualTo(service.hmac("p-auto", neuerCode.getValue()));
        assertThat(f.getCodeFehlversuche()).isZero();
        assertThat(f.getCodeFehlversucheGesamt()).isEqualTo(5);
        assertThat(f.getCodeFensterAnzahl()).isEqualTo(2);

        // Der alte Code gilt nicht mehr, der neue schon.
        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "123456")).isInstanceOf(FreigabeCodeException.class);
        f.setCodeFehlversuche(0);
        service.pruefeUndVerbrauche(f, neuerCode.getValue());
        assertThat(f.getCodeBestaetigtAm()).isEqualTo(JETZT);
    }

    @Test
    void pruefe_fuenfterFehlversuchInnerhalbDerWartezeit_sperrtMitCountdown()
    {
        DokumentFreigabe f = mitOffenemCode("p-auto-wartezeit", "123456");
        f.setCodeFehlversuche(FreigabeCodeService.MAX_FEHLVERSUCHE - 1);
        f.setCodeAngefordertAm(JETZT.minusSeconds(12));

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "000000"))
                .isInstanceOfSatisfying(FreigabeCodeException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.GESPERRT);
                    assertThat(e.getErneutAnfordernInSekunden()).isEqualTo(18L);
                    assertThat(e.getEmailMaskiert()).isNull();
                });
        assertThat(f.getCodeHash()).isNull();
        assertThat(f.getCodeFehlversuche()).isEqualTo(FreigabeCodeService.MAX_FEHLVERSUCHE);
        verifyNoInteractions(mailService);
    }

    @Test
    void pruefe_fuenfterFehlversuchUndMailAusfall_sperrtOhneCountdownUndSetztZurueck() throws Exception
    {
        DokumentFreigabe f = mitOffenemCode("p-auto-smtp", "123456");
        f.setCodeFehlversuche(FreigabeCodeService.MAX_FEHLVERSUCHE - 1);
        f.setCodeAngefordertAm(JETZT.minusMinutes(3));
        f.setCodeFensterStart(JETZT.minusMinutes(3));
        f.setCodeFensterAnzahl(1);
        doThrow(new jakarta.mail.MessagingException("SMTP weg")).when(mailService).sendeCode(eq(f), anyString(), any());

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "000000"))
                .isInstanceOfSatisfying(FreigabeCodeException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.GESPERRT);
                    assertThat(e.getErneutAnfordernInSekunden()).isNull();
                });
        // Kein Code, den der Kunde nie bekam; Zähler und Fenster wie vor dem Versuch.
        assertThat(f.getCodeHash()).isNull();
        assertThat(f.getCodeFehlversuche()).isEqualTo(FreigabeCodeService.MAX_FEHLVERSUCHE);
        assertThat(f.getCodeAngefordertAm()).isEqualTo(JETZT.minusMinutes(3));
        assertThat(f.getCodeFensterAnzahl()).isEqualTo(1);
    }

    @Test
    void pruefe_gesamtgrenzeErreicht_sperrtOnlineUndMeldetBuero()
    {
        DokumentFreigabe f = mitOffenemCode("p-gesamt", "123456");
        f.setKundeName("Max Mustermann");
        f.setCodeFehlversuche(4);
        f.setCodeFehlversucheGesamt(FreigabeCodeService.MAX_FEHLVERSUCHE_GESAMT - 1);

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "000000"))
                .isInstanceOfSatisfying(FreigabeCodeException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.ONLINE_GESPERRT);
                    assertThat(e.getMessage()).contains("rufen Sie uns an");
                });
        assertThat(f.getCodeHash()).isNull();
        assertThat(FreigabeCodeService.istOnlineGesperrt(f)).isTrue();
        verify(webPushService).notifyFreigabeAnnahme(eq("Online-Freigabe gesperrt"),
                contains("Max Mustermann: Angebot AN-2026-0001"), anyString());
        verifyNoInteractions(mailService);

        // Auch der richtige Code hilft danach nicht mehr.
        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "123456"))
                .isInstanceOfSatisfying(FreigabeCodeException.class,
                        e -> assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.ONLINE_GESPERRT));
    }

    @Test
    void pruefe_gesamtgrenzeOhneKundenname_undPushFehler_bleibtGesperrt()
    {
        DokumentFreigabe f = mitOffenemCode("p-gesamt-push", "123456");
        f.setKundeName(null);
        f.setDokumentArt(null);
        f.setCodeFehlversucheGesamt(FreigabeCodeService.MAX_FEHLVERSUCHE_GESAMT - 1);
        doThrow(new IllegalStateException("Push-Dienst weg"))
                .when(webPushService).notifyFreigabeAnnahme(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "000000"))
                .isInstanceOfSatisfying(FreigabeCodeException.class,
                        e -> assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.ONLINE_GESPERRT));
        verify(webPushService).notifyFreigabeAnnahme(anyString(), contains("Ein Kunde: Dokument AN-2026-0001"), anyString());
    }

    @Test
    void pruefe_gesamtgrenzeMitLeeremKundennamen_nenntEinenKunden()
    {
        DokumentFreigabe f = mitOffenemCode("p-gesamt-leer", "123456");
        f.setKundeName("  ");
        f.setCodeFehlversucheGesamt(FreigabeCodeService.MAX_FEHLVERSUCHE_GESAMT - 1);

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "000000")).isInstanceOf(FreigabeCodeException.class);
        verify(webPushService).notifyFreigabeAnnahme(anyString(), contains("Ein Kunde: Angebot"), anyString());
    }

    @Test
    void fordereCodeAn_onlineGesperrt_verschicktNichts()
    {
        DokumentFreigabe f = gesperrtGeladen(offeneFreigabe("u-online-gesperrt"));
        f.setCodeFehlversucheGesamt(FreigabeCodeService.MAX_FEHLVERSUCHE_GESAMT);

        assertThatThrownBy(() -> service.fordereCodeAn("u-online-gesperrt"))
                .isInstanceOfSatisfying(FreigabeCodeAnforderungException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeAnforderungException.Grund.ONLINE_GESPERRT);
                    assertThat(e.getMessage()).contains("rufen Sie uns an");
                });
        verifyNoInteractions(mailService);
    }

    @Test
    void pruefe_richtigerCodeMitLeerzeichen_wirdVerbrauchtUndBelegt()
    {
        DokumentFreigabe f = mitOffenemCode("p-richtig", "012345");
        f.setCodeFehlversuche(2);

        service.pruefeUndVerbrauche(f, " 012 345 ");

        assertThat(f.getCodeHash()).isNull();
        assertThat(f.getCodeGueltigBis()).isNull();
        assertThat(f.getCodeFehlversuche()).isZero();
        assertThat(f.getCodeBestaetigtAm()).isEqualTo(JETZT);
        assertThat(f.getCodeBestaetigtEmail()).isEqualTo("max.mustermann@example.de");

        // Einmal-Code: ein zweiter Versuch mit demselben Code scheitert.
        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "012345"))
                .isInstanceOfSatisfying(FreigabeCodeException.class,
                        e -> assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.NICHT_ANGEFORDERT));
    }

    // ===================== Hilfen =====================

    @Test
    void erzeugeCode_liefertImmerSechsZiffern()
    {
        for (int i = 0; i < 1_000; i++)
        {
            assertThat(FreigabeCodeService.erzeugeCode()).matches("\\d{6}");
        }
    }

    @Test
    void hmac_ohneSchluessel_wirftIllegalState()
    {
        ReflectionTestUtils.setField(service, "hashSalt", "");

        assertThatThrownBy(() -> service.hmac("u", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HMAC");
    }

    @Test
    void hmac_haengtVonSchluesselUuidUndCodeAb()
    {
        String basis = service.hmac("u-1", "123456");

        assertThat(basis).hasSize(64).isEqualTo(service.hmac("u-1", "123456"));
        assertThat(service.hmac("u-2", "123456")).isNotEqualTo(basis);
        assertThat(service.hmac("u-1", "123457")).isNotEqualTo(basis);
        ReflectionTestUtils.setField(service, "hashSalt", "anderes-salz");
        assertThat(service.hmac("u-1", "123456")).isNotEqualTo(basis);
    }

    @Test
    void maskiereEmail_deckteAlleFormenAb()
    {
        assertThat(FreigabeCodeService.maskiereEmail(null)).isEmpty();
        assertThat(FreigabeCodeService.maskiereEmail("max@example.de")).isEqualTo("m•••@example.de");
        assertThat(FreigabeCodeService.maskiereEmail("a@b.de")).isEqualTo("a•••@b.de");
        assertThat(FreigabeCodeService.maskiereEmail("max@example.de; erika@example.com, ,"))
                .isEqualTo("m•••@example.de, e•••@example.com");
        assertThat(FreigabeCodeService.maskiereEmail("ohne-at")).isEqualTo("•••");
        assertThat(FreigabeCodeService.maskiereEmail("@nur-domain.de")).isEqualTo("•••");
    }

    @Test
    void zeitpunkteKommenAusDerUhr()
    {
        // Absicherung, dass der Service nicht versehentlich LocalDateTime.now() ohne Uhr nutzt.
        Instant spaeter = JETZT.plusDays(1).atZone(BERLIN).toInstant();
        FreigabeCodeService morgen = new FreigabeCodeService(repository, mailService, webPushService, Runnable::run, Clock.fixed(spaeter, BERLIN));
        ReflectionTestUtils.setField(morgen, "hashSalt", "test-salz");
        DokumentFreigabe f = offeneFreigabe("z-uhr");
        f.setCodeHash(morgen.hmac("z-uhr", "111111"));
        f.setCodeGueltigBis(JETZT.plusHours(1));

        assertThatThrownBy(() -> morgen.pruefeUndVerbrauche(f, "111111"))
                .isInstanceOfSatisfying(FreigabeCodeException.class,
                        e -> assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.ABGELAUFEN));
    }

    @Test
    void pruefe_kurzVorDerGesamtgrenze_nenntDieEchtenRestversuche()
    {
        DokumentFreigabe f = mitOffenemCode("p-rest", "123456");
        f.setCodeFehlversucheGesamt(FreigabeCodeService.MAX_FEHLVERSUCHE_GESAMT - 2);

        assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "000000"))
                .isInstanceOfSatisfying(FreigabeCodeException.class, e -> {
                    assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.FALSCH);
                    assertThat(e.getVerbleibendeVersuche()).isEqualTo(1);
                });
    }

    @Test
    void pruefe_sperreInLaufenderTransaktion_meldetBueroErstNachDemCommit()
    {
        DokumentFreigabe f = mitOffenemCode("p-commit", "123456");
        f.setCodeFehlversucheGesamt(FreigabeCodeService.MAX_FEHLVERSUCHE_GESAMT - 1);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try
        {
            assertThatThrownBy(() -> service.pruefeUndVerbrauche(f, "000000")).isInstanceOf(FreigabeCodeException.class);
            verifyNoInteractions(webPushService);

            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(webPushService).notifyFreigabeAnnahme(eq("Online-Freigabe gesperrt"), anyString(), anyString());
        }
        finally
        {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void pruefe_sperreMitVollerWarteschlange_bleibtGesperrtOhnePush()
    {
        FreigabeCodeService ohneExecutor = new FreigabeCodeService(repository, mailService, webPushService,
                aufgabe -> { throw new org.springframework.core.task.TaskRejectedException("voll"); },
                Clock.fixed(JETZT.atZone(BERLIN).toInstant(), BERLIN));
        ReflectionTestUtils.setField(ohneExecutor, "hashSalt", "test-salz");
        DokumentFreigabe f = offeneFreigabe("p-voll");
        f.setCodeHash(ohneExecutor.hmac("p-voll", "123456"));
        f.setCodeGueltigBis(JETZT.plusMinutes(10));
        f.setCodeFehlversucheGesamt(FreigabeCodeService.MAX_FEHLVERSUCHE_GESAMT - 1);

        assertThatThrownBy(() -> ohneExecutor.pruefeUndVerbrauche(f, "000000"))
                .isInstanceOfSatisfying(FreigabeCodeException.class,
                        e -> assertThat(e.getGrund()).isEqualTo(FreigabeCodeException.Grund.ONLINE_GESPERRT));
        verifyNoInteractions(webPushService);
    }

    @Test
    void pruefe_bestaetigungszeitIstAufMikrosekundenGekuerzt()
    {
        Clock mitNanos = Clock.fixed(JETZT.plusNanos(123_456_789).atZone(BERLIN).toInstant(), BERLIN);
        FreigabeCodeService genau = new FreigabeCodeService(repository, mailService, webPushService, Runnable::run, mitNanos);
        ReflectionTestUtils.setField(genau, "hashSalt", "test-salz");
        DokumentFreigabe f = offeneFreigabe("p-micros");
        f.setCodeHash(genau.hmac("p-micros", "123456"));
        f.setCodeGueltigBis(JETZT.plusMinutes(10));

        genau.pruefeUndVerbrauche(f, "123456");

        assertThat(f.getCodeBestaetigtAm()).isEqualTo(JETZT.plusNanos(123_456_000));
    }
}

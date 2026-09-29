package org.example.kalkulationsprogramm.service.telefon;

import org.example.kalkulationsprogramm.domain.TelefonAnrufArt;
import org.example.kalkulationsprogramm.dto.Telefon.AbholErgebnisDto;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufbeantworterDto;
import org.example.kalkulationsprogramm.repository.SprachnachrichtRepository;
import org.example.kalkulationsprogramm.repository.TelefonAnrufRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelefonAbholServiceTest {

    @Mock TelefonAnlage anlage;
    @Mock TelefonEinstellungenService einstellungen;
    @Mock RufnummernZuordnungService zuordnung;
    @Mock TelefonAnrufRepository anrufe;
    @Mock SprachnachrichtRepository nachrichten;
    @Mock SprachnachrichtDateiablage ablage;
    @Mock PlatformTransactionManager tm;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-29T10:00:00Z"), ZoneId.of("Europe/Berlin"));
    private final TelefonZugang zugang = new TelefonZugang("fritz.box", "erp", "pw");
    private TelefonAbholService service;

    @BeforeEach
    void setUp() {
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new TelefonAbholService(anlage, einstellungen, zuordnung, anrufe, nachrichten, ablage, tm, clock);
        when(einstellungen.zugang()).thenReturn(Optional.of(zugang));
        when(einstellungen.istAktiv()).thenReturn(true);
        when(einstellungen.geschaeftsnummern()).thenReturn(List.of("2323"));
        when(einstellungen.anrufbeantworter()).thenReturn(List.of());
        when(einstellungen.aufbewahrungAnrufeMonate()).thenReturn(24);
        when(einstellungen.aufbewahrungSprachnachrichtenMonate()).thenReturn(3);
        when(zuordnung.frischesVerzeichnis()).thenReturn(new RufnummernZuordnungService.Verzeichnis());
        when(zuordnung.normalisiere(anyString())).thenAnswer(i -> RufnummerNormalisierer.normalisiere(i.getArgument(0), "49", "931"));
    }

    @Test
    @DisplayName("Nicht eingerichtet → nichts wird abgefragt")
    void nichtEingerichtet() {
        when(einstellungen.zugang()).thenReturn(Optional.empty());
        AbholErgebnisDto e = service.abholen();
        assertThat(e.erfolgreich()).isFalse();
        assertThat(e.meldung()).contains("nicht eingerichtet");
        verifyNoInteractions(anlage);
    }

    @Test
    @DisplayName("Ausgeschaltet → auch \"Jetzt abholen\" und Nachholen fragen die Box nicht ab")
    void ausgeschaltet() {
        when(einstellungen.istAktiv()).thenReturn(false);
        AbholErgebnisDto e = service.abholen();
        assertThat(e.erfolgreich()).isFalse();
        assertThat(e.meldung()).contains("ausgeschaltet");
        assertThat(service.nachholen(30).erfolgreich()).isFalse();
        verifyNoInteractions(anlage);
    }

    @Test
    @DisplayName("Einträge älter als die Aufbewahrungsfrist werden nicht angelegt und nicht heruntergeladen")
    void abgelaufeneEintraegeUebersprungen() {
        when(einstellungen.anrufbeantworter()).thenReturn(List.of(new AnrufbeantworterDto(0, "AB")));
        when(anlage.ladeAnrufe(any(), anyInt())).thenReturn(List.of(
                new AnlagenAnruf(LocalDateTime.of(2024, 1, 10, 8, 0), TelefonAnrufArt.ANGENOMMEN, "09311234567", "2323", 1, null, null)));
        when(anlage.ladeSprachnachrichten(any(), eq(0))).thenReturn(List.of(
                new AnlagenSprachnachricht(0, LocalDateTime.of(2026, 5, 1, 8, 0), "09311234567", "2323",
                        "/download.lua?path=/data/tam/rec/rec.0.000", "sid")));

        AbholErgebnisDto e = service.nachholen(999);

        assertThat(e.erfolgreich()).isTrue();
        assertThat(e.neueAnrufe()).isZero();
        assertThat(e.neueSprachnachrichten()).isZero();
        verify(anrufe, never()).save(any());
        verify(nachrichten, never()).save(any());
        verify(anlage, never()).ladeAudio(any(), any());
    }

    @Test
    @DisplayName("Geplante Abholung läuft nur, wenn aktiv")
    void geplant() {
        when(einstellungen.istAktiv()).thenReturn(false);
        service.geplanteAbholung();
        verifyNoInteractions(anlage);

        when(einstellungen.istAktiv()).thenReturn(true);
        service.geplanteAbholung();
        verify(anlage).ladeAnrufe(eq(zugang), eq(TelefonAbholService.ERSTER_LAUF_TAGE));
    }

    @Test
    @DisplayName("Zeitraum: seit letzter Abholung + 1 Tag, begrenzt")
    void zeitraum() {
        when(einstellungen.letzteAbholung()).thenReturn(LocalDateTime.of(2026, 9, 25, 12, 0));
        service.abholen();
        verify(anlage).ladeAnrufe(zugang, 5);

        when(einstellungen.letzteAbholung()).thenReturn(LocalDateTime.of(2020, 1, 1, 0, 0));
        service.abholen();
        verify(anlage).ladeAnrufe(zugang, TelefonAbholService.MAX_TAGE);
    }

    @Test
    @DisplayName("Fehler: Status wird gemerkt, Log nur beim Wechsel, danach Erfolg setzt zurück")
    void fehlerStatus() {
        when(anlage.ladeAnrufe(any(), anyInt()))
                .thenThrow(new TelefonAnlageException(TelefonAnlageException.Grund.NICHT_ERREICHBAR));
        AbholErgebnisDto e = service.abholen();
        assertThat(e.erfolgreich()).isFalse();
        assertThat(e.meldung()).isEqualTo("FRITZ!Box nicht erreichbar");
        verify(einstellungen).merkeFehler("FRITZ!Box nicht erreichbar");
        verify(einstellungen, never()).merkeErfolg(any());
    }

    @Test
    @DisplayName("Keine Geschäftsnummer gewählt → keine Anrufe übernommen")
    void ohneGeschaeftsnummer() {
        when(einstellungen.geschaeftsnummern()).thenReturn(List.of());
        assertThat(service.abholen().neueAnrufe()).isZero();
        verify(anlage, never()).ladeAnrufe(any(), anyInt());
        verify(anrufe, never()).save(any());
    }

    @Test
    @DisplayName("Private Nummern werden verworfen und nie gespeichert")
    void privatVerworfen() {
        when(anlage.ladeAnrufe(any(), anyInt())).thenReturn(List.of(
                new AnlagenAnruf(LocalDateTime.of(2026, 9, 29, 8, 0), TelefonAnrufArt.ANGENOMMEN, "09311234567", "555000", 1, null, null),
                new AnlagenAnruf(LocalDateTime.of(2026, 9, 29, 8, 1), TelefonAnrufArt.VERPASST, "09311234567", "", 0, null, null)));
        assertThat(service.abholen().neueAnrufe()).isZero();
        verify(anrufe, never()).save(any());
    }

    @Test
    @DisplayName("Geschäftsnummer erkannt: gleich, mit Ortsvorwahl oder international")
    void geschaeftsnummer() {
        List<String> g = List.of("2323");
        assertThat(service.istGeschaeftsnummer("2323", g)).isTrue();
        assertThat(service.istGeschaeftsnummer("0931 2323", g)).isTrue();
        assertThat(service.istGeschaeftsnummer("+499312323", g)).isTrue();
        assertThat(service.istGeschaeftsnummer("555000", g)).isFalse();
        assertThat(service.istGeschaeftsnummer("", g)).isFalse();
        assertThat(service.istGeschaeftsnummer(null, g)).isFalse();
    }

    @Test
    @DisplayName("Nachholen: nur 1–999 Tage")
    void nachholenGrenzen() {
        assertThatThrownBy(() -> service.nachholen(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.nachholen(1000)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.nachholen(365).erfolgreich()).isTrue();
        verify(anlage).ladeAnrufe(zugang, 365);
    }
}

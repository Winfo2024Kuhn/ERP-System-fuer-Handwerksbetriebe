package org.example.kalkulationsprogramm.service.telefon;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException.Grund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelefonWaehlServiceTest {

    private static final TelefonZugang ZUGANG = new TelefonZugang("fritz.box", "erp", "geheim-test");
    private static final List<String> TELEFONE = List.of("FON1: Werkstatt", "LAN: PC Büro");

    @Mock TelefonAnlage anlage;
    @Mock TelefonEinstellungenService einstellungen;
    @InjectMocks TelefonWaehlService service;

    @BeforeEach
    void eingerichtet() {
        lenient().when(einstellungen.istAktiv()).thenReturn(true);
        lenient().when(einstellungen.zugang()).thenReturn(Optional.of(ZUGANG));
        lenient().when(anlage.ladeTelefone(ZUGANG)).thenReturn(TELEFONE);
    }

    @Test
    @DisplayName("Telefone kommen direkt aus der Anlage")
    void telefone() {
        assertThat(service.telefone()).containsExactly("FON1: Werkstatt", "LAN: PC Büro");
    }

    @Test
    @DisplayName("Anrufen: Telefon aus der Liste, Nummer bereinigt an die Anlage")
    void anrufen() {
        service.anrufen("LAN: PC Büro", " 0931 / 123-45 67 ");

        verify(anlage).anrufen(ZUGANG, "LAN: PC Büro", "09311234567");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "09311234567, 09311234567",
            "'0931 123 45 67', 09311234567",
            "(0931) 123-4567, 09311234567",
            "0931/1234567, 09311234567",
            "+49 931 1234567, 00499311234567",
            "0171.2345678, 01712345678",
            "110, 110"
    })
    @DisplayName("Nummern: Leerzeichen, Klammern, Striche, Punkte und Schrägstriche fallen weg; + wird 00")
    void waehlbareNummer(String roh, String erwartet) {
        assertThat(TelefonWaehlService.waehlbareNummer(roh)).isEqualTo(erwartet);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "0", "+", "abc", "0931 abc",
            "#96*7*", "*21#", "**620", "0931#", "0931*1",
            "0931+123", "++49931",
            "'; DROP TABLE telefon_anruf; --", "<script>alert(1)</script>",
            "1234567890123456789012345678901"
    })
    @DisplayName("Ungültige Nummern (Steuercodes, Text, Injection, zu lang) werden abgelehnt")
    void ungueltigeNummern(String roh) {
        assertThatThrownBy(() -> service.anrufen("LAN: PC Büro", roh))
                .isInstanceOf(IllegalArgumentException.class);
        verify(anlage, never()).anrufen(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Fehlende oder überlange Nummer wird abgelehnt")
    void nummerNullOderRiesig() {
        assertThatThrownBy(() -> service.anrufen("LAN: PC Büro", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.anrufen("LAN: PC Büro", "0".repeat(10_001))).isInstanceOf(IllegalArgumentException.class);
        verify(anlage, never()).anrufen(any(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "LAN: PC Keller", "<script>alert(1)</script>", "'; DROP TABLE x; --"})
    @DisplayName("Telefon, das die FRITZ!Box nicht kennt, wird abgelehnt")
    void unbekanntesTelefon(String telefon) {
        assertThatThrownBy(() -> service.anrufen(telefon, "09311234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Telefon");
        verify(anlage, never()).anrufen(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Telefon null oder riesig wird abgelehnt, ohne die Box zu fragen")
    void telefonNullOderRiesig() {
        assertThatThrownBy(() -> service.anrufen(null, "09311234567")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.anrufen("x".repeat(10_001), "09311234567")).isInstanceOf(IllegalArgumentException.class);
        verify(anlage, never()).ladeTelefone(any());
        verify(anlage, never()).anrufen(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Nicht eingerichtet (aus oder ohne Zugang) → NICHT_EINGERICHTET, die Box wird nicht gefragt")
    void nichtEingerichtet() {
        when(einstellungen.istAktiv()).thenReturn(false);
        assertThatThrownBy(() -> service.telefone())
                .extracting(e -> ((TelefonAnlageException) e).getGrund())
                .isEqualTo(Grund.NICHT_EINGERICHTET);

        when(einstellungen.istAktiv()).thenReturn(true);
        when(einstellungen.zugang()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.anrufen("LAN: PC Büro", "09311234567"))
                .extracting(e -> ((TelefonAnlageException) e).getGrund())
                .isEqualTo(Grund.NICHT_EINGERICHTET);
        verify(anlage, never()).ladeTelefone(any());
    }

    @Test
    @DisplayName("Fehler der Anlage (z.B. Wählhilfe aus) werden unverändert weitergereicht")
    void fehlerDerAnlage() {
        doThrow(new TelefonAnlageException(Grund.WAEHLHILFE_AUS))
                .when(anlage).anrufen(ZUGANG, "LAN: PC Büro", "09311234567");
        assertThatThrownBy(() -> service.anrufen("LAN: PC Büro", "09311234567"))
                .extracting(e -> ((TelefonAnlageException) e).getGrund())
                .isEqualTo(Grund.WAEHLHILFE_AUS);
    }
}

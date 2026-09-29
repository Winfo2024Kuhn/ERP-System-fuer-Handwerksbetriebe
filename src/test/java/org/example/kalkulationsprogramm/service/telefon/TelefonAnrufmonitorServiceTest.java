package org.example.kalkulationsprogramm.service.telefon;

import org.example.kalkulationsprogramm.dto.Telefon.KontaktKurzDto;
import org.example.kalkulationsprogramm.dto.Telefon.LiveAnrufDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelefonAnrufmonitorServiceTest {

    @Mock TelefonEinstellungenService einstellungen;
    @Mock RufnummernZuordnungService zuordnung;
    @Mock TelefonLiveService live;
    @Mock TelefonAbholService abholService;

    private TelefonAnrufmonitorService monitor;
    private final KontaktKurzDto kunde = new KontaktKurzDto("KUNDE", 1L, "Mustermann GmbH", "K-1", "Würzburg");

    @BeforeEach
    void setUp() {
        monitor = new TelefonAnrufmonitorService(einstellungen, zuordnung, live, abholService);
        when(einstellungen.geschaeftsnummern()).thenReturn(List.of("2323"));
        when(abholService.istGeschaeftsnummer(anyString(), anyList()))
                .thenAnswer(i -> "2323".equals(i.getArgument(0)));
        when(zuordnung.normalisiere(anyString())).thenAnswer(i -> RufnummerNormalisierer.normalisiere(i.getArgument(0), "49", "931"));
        RufnummernZuordnungService.Verzeichnis v = new RufnummernZuordnungService.Verzeichnis();
        v.fuegeHinzu("+499311234567", kunde);
        v.fuegeHinzu("+49931999", kunde);
        v.fuegeHinzu("+49931999", new KontaktKurzDto("LIEFERANT", 2L, "Stahl Muster KG", null, null));
        when(zuordnung.verzeichnis()).thenReturn(v);
    }

    @AfterEach
    void tearDown() {
        monitor.stoppe();
    }

    private LiveAnrufDto letztes(int anzahl) {
        ArgumentCaptor<LiveAnrufDto> c = ArgumentCaptor.forClass(LiveAnrufDto.class);
        verify(live, times(anzahl)).sende(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("Klingeln auf Geschäftsnummer → Fenster mit Kunde; Annahme → im Gespräch; Ende → beendet")
    void ablauf() {
        monitor.verarbeite(AnrufmonitorEreignis.parse("29.09.26 11:55:01;RING;0;09311234567;2323;SIP0;"));
        LiveAnrufDto klingelt = letztes(1);
        assertThat(klingelt.status()).isEqualTo("KLINGELT");
        assertThat(klingelt.kontakt()).isEqualTo(kunde);

        monitor.verarbeite(AnrufmonitorEreignis.parse("29.09.26 11:55:09;CONNECT;0;10;09311234567;"));
        assertThat(letztes(2).status()).isEqualTo("IM_GESPRAECH");

        monitor.verarbeite(AnrufmonitorEreignis.parse("29.09.26 11:56:09;DISCONNECT;0;60;"));
        LiveAnrufDto ende = letztes(3);
        assertThat(ende.status()).isEqualTo("BEENDET");
        assertThat(ende.angenommen()).isTrue();
    }

    @Test
    @DisplayName("Anrufbeantworter nimmt ab → Status ANRUFBEANTWORTER, nicht angenommen")
    void anrufbeantworter() {
        monitor.verarbeite(AnrufmonitorEreignis.parse("x;RING;1;0931999;2323;SIP0;"));
        LiveAnrufDto klingelt = letztes(1);
        assertThat(klingelt.kontakt()).isNull();
        assertThat(klingelt.kandidaten()).hasSize(2);
        monitor.verarbeite(AnrufmonitorEreignis.parse("x;CONNECT;1;40;0931999;"));
        LiveAnrufDto ab = letztes(2);
        assertThat(ab.status()).isEqualTo("ANRUFBEANTWORTER");
        assertThat(ab.angenommen()).isFalse();
        monitor.verarbeite(AnrufmonitorEreignis.parse("x;DISCONNECT;1;20;"));
        assertThat(letztes(3).angenommen()).isFalse();
        verify(abholService, timeout(8000)).abholen();
    }

    @Test
    @DisplayName("Private Nummer, ausgehende und unbekannte Verbindungen erzeugen kein Fenster")
    void keinFenster() {
        monitor.verarbeite(AnrufmonitorEreignis.parse("x;RING;2;09311234567;555000;SIP1;"));
        monitor.verarbeite(AnrufmonitorEreignis.parse("x;CALL;3;10;2323;09311234567;SIP0;"));
        monitor.verarbeite(AnrufmonitorEreignis.parse("x;CONNECT;3;10;09311234567;"));
        monitor.verarbeite(AnrufmonitorEreignis.parse("x;DISCONNECT;2;0;"));
        verify(live, never()).sende(any());
    }

    @Test
    @DisplayName("TCP: verbindet sich zum Anrufmonitor, liest Zeilen, meldet Status, verbindet nach Abbruch neu")
    void tcpVerbindung() throws Exception {
        try (ServerSocket box = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
            box.setSoTimeout(10_000);
            when(einstellungen.istAktiv()).thenReturn(true);
            when(einstellungen.zugang()).thenReturn(Optional.of(new TelefonZugang("127.0.0.1", "erp", "pw")));
            TelefonAnrufmonitorService tcp = new TelefonAnrufmonitorService(einstellungen, zuordnung, live, abholService, box.getLocalPort());
            try {
                tcp.starte();
                try (Socket verbindung = box.accept()) {
                    org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(tcp::istVerbunden);
                    schreibe(verbindung, "Kaputte Zeile");
                    schreibe(verbindung, "29.09.26 11:55:01;RING;7;09311234567;2323;SIP0;");
                    verify(live, timeout(5000)).sende(any());
                }
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> !tcp.istVerbunden());
                tcp.einstellungenGeaendert();
                try (Socket zweite = box.accept()) {
                    assertThat(zweite.isConnected()).isTrue();
                }
            } finally {
                tcp.stoppe();
            }
        }
    }

    @Test
    @DisplayName("Ausgeschaltet oder ohne Zugang wird nicht verbunden")
    void ausgeschaltet() {
        when(einstellungen.istAktiv()).thenReturn(false);
        monitor.starte();
        monitor.einstellungenGeaendert();
        assertThat(monitor.istVerbunden()).isFalse();
    }

    private static void schreibe(Socket s, String zeile) throws IOException {
        OutputStream out = s.getOutputStream();
        out.write((zeile + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}

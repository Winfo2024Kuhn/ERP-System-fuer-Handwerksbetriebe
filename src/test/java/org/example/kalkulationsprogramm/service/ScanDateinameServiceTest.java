package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiErgebnis;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiNichtVerfuegbar;
import org.example.kalkulationsprogramm.testsupport.LogMitschnitt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Dateinamen für Scans: über {@link KiDienst}, nie ohne Ersatzname. Nur Dummy-Daten. */
class ScanDateinameServiceTest {

    private static final byte[] BILD = "jpeg-dummy".getBytes(StandardCharsets.UTF_8);

    private final KiDienst kiDienst = mock(KiDienst.class);
    private final ScanDateinameService service = new ScanDateinameService(kiDienst);

    @Test
    void ohneKiKommtEinZeitstempelnameUndNichtsGehtRaus() {
        when(kiDienst.verfuegbar(KiFunktion.SCAN_BENENNUNG)).thenReturn(false);

        assertThat(service.generateFilename(BILD)).matches("Scan_\\d{10,}");
        verify(kiDienst, never()).sende(any(), any());
    }

    @Test
    void bildGehtAlsDateiMitTemperaturNullAnDieScanBenennung() {
        when(kiDienst.verfuegbar(KiFunktion.SCAN_BENENNUNG)).thenReturn(true);
        when(kiDienst.sende(eq(KiFunktion.SCAN_BENENNUNG), any()))
                .thenReturn(new KiErgebnis(" Rechnung_Mustermann_2026-03-10 \n", false, KiAnbieterTyp.MISTRAL, "m"));

        String name = service.generateFilename(BILD);

        assertThat(name).isEqualTo("Rechnung_Mustermann_2026-03-10");
        ArgumentCaptor<KiAnfrage> anfrage = ArgumentCaptor.forClass(KiAnfrage.class);
        verify(kiDienst).sende(eq(KiFunktion.SCAN_BENENNUNG), anfrage.capture());
        assertThat(anfrage.getValue().temperatur()).isEqualTo(0.0);
        assertThat(anfrage.getValue().dateien()).singleElement().satisfies(datei -> {
            assertThat(datei.mimeType()).isEqualTo("image/jpeg");
            assertThat(datei.daten()).isEqualTo(BILD);
        });
    }

    @Test
    void leerzeichenWerdenUnterstricheUndUnerlaubteZeichenFallenWeg() {
        when(kiDienst.verfuegbar(KiFunktion.SCAN_BENENNUNG)).thenReturn(true);
        when(kiDienst.sende(eq(KiFunktion.SCAN_BENENNUNG), any()))
                .thenReturn(new KiErgebnis("Brief: Finanzamt/../Müller 2026-01-05.pdf", false, KiAnbieterTyp.GEMINI, "g"));

        String name = service.generateFilename(BILD);

        assertThat(name).isEqualTo("Brief_FinanzamtMller_2026-01-05pdf");
        assertThat(name).doesNotContain("/", "\\", ".", ":");
    }

    @Test
    void leereAntwortErgibtErsatzname() {
        when(kiDienst.verfuegbar(KiFunktion.SCAN_BENENNUNG)).thenReturn(true);
        when(kiDienst.sende(eq(KiFunktion.SCAN_BENENNUNG), any()))
                .thenReturn(new KiErgebnis("  ***  ", false, KiAnbieterTyp.GEMINI, "g"));

        assertThat(service.generateFilename(BILD)).matches("Scan_\\d{10,}");
    }

    @Test
    void anbieterFehlerErgibtErsatznameUndLogNenntNurDieFehlerart() {
        when(kiDienst.verfuegbar(KiFunktion.SCAN_BENENNUNG)).thenReturn(true);
        when(kiDienst.sende(eq(KiFunktion.SCAN_BENENNUNG), any()))
                .thenThrow(new KiAnbieterFehler("Mistral antwortet gerade nicht (Max Mustermann)", 503, true));

        List<String> log = LogMitschnitt.von(ScanDateinameService.class,
                () -> assertThat(service.generateFilename(BILD)).matches("Scan_\\d{10,}"));

        assertThat(log).anyMatch(zeile -> zeile.contains("KiAnbieterFehler"));
        assertThat(log).noneMatch(zeile -> zeile.contains("Mustermann"));
    }

    @Test
    void wirdDieFunktionZwischenPruefungUndAufrufAbgeschaltetGibtEsTrotzdemEinenNamen() {
        when(kiDienst.verfuegbar(KiFunktion.SCAN_BENENNUNG)).thenReturn(true);
        when(kiDienst.sende(eq(KiFunktion.SCAN_BENENNUNG), any()))
                .thenThrow(new KiNichtVerfuegbar(KiFunktion.SCAN_BENENNUNG, "Kein Anbieter eingerichtet."));

        assertThat(service.generateFilename(BILD)).matches("Scan_\\d{10,}");
    }
}

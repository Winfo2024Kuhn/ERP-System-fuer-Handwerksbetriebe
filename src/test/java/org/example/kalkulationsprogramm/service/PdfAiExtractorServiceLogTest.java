package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiErgebnis;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTeil;
import org.example.kalkulationsprogramm.testsupport.LogMitschnitt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.json.JsonMapper;

/** Was die PDF-Auslesung per KI ins Log schreibt: nie Rechnungsinhalte oder Pfade. Nur Dummy-Daten. */
class PdfAiExtractorServiceLogTest {

    private final KiDienst kiDienst = mock(KiDienst.class);
    private final PdfAiExtractorService service =
            new PdfAiExtractorService(JsonMapper.builderWithJackson2Defaults().build(), kiDienst);

    @TempDir
    Path ordner;

    @Test
    void antwortMitRechnungsdatenLandetNichtImLog() {
        String antwort = "```json\n{\"dokumentnummer\":\"RE-2026-0905\",\"kundenName\":\"Max Mustermann\"}\n```";

        List<String> log = LogMitschnitt.von(PdfAiExtractorService.class,
                () -> assertThat(service.parseKiAntwort(antwort)).isPresent());

        assertThat(log).noneMatch(zeile -> zeile.contains("Max Mustermann") || zeile.contains("RE-2026-0905"));
    }

    @Test
    void leereUndKaputteAntwortNennenNurFehlerart() {
        String kaputt = "{\"kunde\":\"Max Mustermann\"";

        List<String> log = LogMitschnitt.von(PdfAiExtractorService.class, () -> {
            assertThat(service.parseKiAntwort("  ")).isEmpty();
            assertThat(service.parseKiAntwort(kaputt)).isEmpty();
            assertThat(service.parseKiAntwort("Mustermann sagt nein")).isEmpty();
        });

        // Jackson zitiert das erste Token ('Mustermann') – das darf nicht ins Log.
        assertThat(log).noneMatch(zeile -> zeile.contains("Mustermann"));
        assertThat(log).contains("[PdfAI/KI] Leere Antwort");
        assertThat(log).anyMatch(zeile -> zeile.startsWith("[PdfAI/KI] Parse-Fehler: "));
    }

    @Test
    void fehlendesPdfNenntKeinenPfad() {
        when(kiDienst.verfuegbar(KiFunktion.DOKUMENT_ANALYSE)).thenReturn(true);
        List<String> log = LogMitschnitt.von(PdfAiExtractorService.class,
                () -> service.analyze("/nicht-erlaubt/0b1c2d3e_Rechnung_Max_Mustermann.pdf"));

        assertThat(log).noneMatch(zeile -> zeile.contains("Max_Mustermann") || zeile.contains("nicht-erlaubt"));
        assertThat(log).contains("[PdfAI/KI] PDF nicht gefunden");
    }

    @Test
    void ohneKiWirdNichtsGeschickt() {
        when(kiDienst.verfuegbar(KiFunktion.DOKUMENT_ANALYSE)).thenReturn(false);

        assertThat(service.analyze("/tmp/egal.pdf")).isEmpty();
        verify(kiDienst, never()).sende(any(), any());
    }

    @Test
    void pdfGehtAlsDateiAnDenEingestelltenAnbieter() throws Exception {
        Path pdf = ordner.resolve("rechnung.pdf");
        Files.write(pdf, new byte[] {'%', 'P', 'D', 'F'});
        when(kiDienst.verfuegbar(KiFunktion.DOKUMENT_ANALYSE)).thenReturn(true);
        when(kiDienst.sende(eq(KiFunktion.DOKUMENT_ANALYSE), any())).thenReturn(new KiErgebnis(
                "{\"dokumentnummer\":\"2026/01/00001\",\"betrag\":119.0}", false, KiAnbieterTyp.MISTRAL, "m"));

        assertThat(service.analyze(pdf.toString())).get()
                .extracting(ZugferdDaten::getRechnungsnummer).isEqualTo("2026/01/00001");

        ArgumentCaptor<KiAnfrage> anfrage = ArgumentCaptor.forClass(KiAnfrage.class);
        verify(kiDienst).sende(eq(KiFunktion.DOKUMENT_ANALYSE), anfrage.capture());
        assertThat(anfrage.getValue().dateien()).singleElement()
                .extracting(KiTeil.Datei::mimeType).isEqualTo("application/pdf");
    }

    @Test
    void anbieterFehlerFuehrtZuLeeremErgebnis() throws Exception {
        Path pdf = ordner.resolve("rechnung.pdf");
        Files.write(pdf, new byte[] {'%', 'P', 'D', 'F'});
        when(kiDienst.verfuegbar(KiFunktion.DOKUMENT_ANALYSE)).thenReturn(true);
        when(kiDienst.sende(eq(KiFunktion.DOKUMENT_ANALYSE), any()))
                .thenThrow(new KiAnbieterFehler("Mistral hat gerade eine Störung (HTTP 503).", 503, true));

        assertThat(service.analyze(pdf.toString())).isEmpty();
    }
}

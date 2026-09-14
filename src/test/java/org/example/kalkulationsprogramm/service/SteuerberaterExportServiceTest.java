package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.SteuerberaterPaketDto;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SteuerberaterExportServiceTest {
    @TempDir Path uploads;

    @Test
    void paketEnthaeltCsvPdfBelegeUndFehlendeDateiInLiesmich() throws Exception {
        Beleg bezahlt = beleg(1L, "bezahlt.pdf", "Musterbaustoffe GmbH");
        Beleg offen = beleg(2L, "offen.jpg", "Musterbaustoffe GmbH");
        Beleg fehlt = beleg(3L, "fehlt.png", "Musterbaustoffe GmbH");
        Files.createDirectories(uploads.resolve("belege"));
        Files.writeString(uploads.resolve("belege/bezahlt.pdf"), "pdf");
        Files.writeString(uploads.resolve("belege/offen.jpg"), "jpg");
        SteuerberaterExportService service = service(List.of(bezahlt, offen, fehlt));
        LieferantGeschaeftsdokument daten = new LieferantGeschaeftsdokument(); daten.setBezahlt(true); daten.setBezahltAm(LocalDate.of(2026, 2, 12));
        LieferantDokument dokument = new LieferantDokument(); dokument.setGeschaeftsdaten(daten);
        when(lieferantDokumente.findByBelegIds(any())).thenReturn(List.of(verknuepfung(bezahlt, dokument)));

        Map<String, String> entries = unzip(service.erzeugeZip(2026, 2, new Mitarbeiter(), true));

        assertThat(entries.keySet()).anyMatch(n -> n.endsWith("01_Kassenbuch_2026-2.pdf"));
        assertThat(entries.keySet()).anyMatch(n -> n.endsWith("02_Buchungen_DATEV_2026-2.csv"));
        String rechnungen = entries.entrySet().stream().filter(e -> e.getKey().endsWith("03_Eingangsrechnungen_2026-2.csv")).findFirst().orElseThrow().getValue();
        assertThat(rechnungen).contains("12.02.2026", "offen");
        assertThat(entries.keySet()).anyMatch(n -> n.contains("04_Belege/0001_2026-02-10_Musterbaustoffe_GmbH.pdf"));
        assertThat(entries.keySet()).anyMatch(n -> n.contains("04_Belege/0002_2026-02-10_Musterbaustoffe_GmbH.jpg"));
        assertThat(entries.entrySet().stream().filter(e -> e.getKey().endsWith("LIESMICH.txt")).findFirst().orElseThrow().getValue()).contains("Fehlende Dateien", "fehlt.png");
        verify(anteile).findByBelegIds(List.of(1L, 2L, 3L));
        verify(lieferantDokumente).findByBelegIds(List.of(1L, 2L, 3L));
        verify(lieferantDokumente, never()).findByBelegId(anyLong());
    }

    @Test
    void vorpruefungNenntFehlendesKontoUndZahlungsart() {
        Beleg ohneKonto = beleg(1L, "a.pdf", "Musterbaustoffe GmbH"); ohneKonto.setSachkonto(null);
        Beleg ohneZahlungsart = beleg(2L, "b.pdf", "Musterbaustoffe GmbH"); ohneZahlungsart.setZahlungsart(" ");
        SteuerberaterExportService service = service(List.of(ohneKonto, ohneZahlungsart));

        SteuerberaterPaketDto.Vorpruefung result = service.pruefe(2026, 2);

        assertThat(result.getAnzahlBelege()).isEqualTo(2);
        assertThat(result.getOffenePunkte()).extracting(SteuerberaterPaketDto.OffenerPunkt::getWasFehlt)
                .containsExactly("Konto fehlt", "Zahlungsart fehlt");
        assertThat(result.isBeraternummerFehlt()).isFalse();
        assertThat(result.isMandantennummerFehlt()).isFalse();
    }

    private BelegKostenstellenAnteilRepository anteile;
    private LieferantDokumentRepository lieferantDokumente;

    private SteuerberaterExportService service(List<Beleg> belege) {
        BelegRepository belegRepository = mock(BelegRepository.class); anteile = mock(BelegKostenstellenAnteilRepository.class); lieferantDokumente = mock(LieferantDokumentRepository.class);
        KasseEinstellungRepository einstellungen = mock(KasseEinstellungRepository.class); FirmeninformationRepository firmen = mock(FirmeninformationRepository.class);
        BelegeKasseExportPdfService pdf = mock(BelegeKasseExportPdfService.class); KasseDatevExportService datev = mock(KasseDatevExportService.class);
        when(belegRepository.findGeprueftImZeitraumNachNummer(any(), any())).thenReturn(belege);
        when(anteile.findByBelegIds(any())).thenReturn(List.of());
        when(lieferantDokumente.findByBelegIds(any())).thenReturn(List.of());
        KasseEinstellung e = new KasseEinstellung(); e.setDatevBeraternummer("123"); e.setDatevMandantennummer("45"); e.setKassenkontoNummer("1000"); e.setBankkontoNummer("1200"); when(einstellungen.findSingleton()).thenReturn(Optional.of(e));
        Firmeninformation f = new Firmeninformation(); f.setFirmenname("Musterbetrieb GmbH"); when(firmen.findFirmeninformation()).thenReturn(Optional.of(f));
        try { Path p = Files.createTempFile(uploads, "pdf", ".pdf"); Files.writeString(p, "pdf"); when(pdf.generatePdf(anyInt(), anyInt(), any(), eq(true))).thenReturn(p); } catch (Exception ex) { throw new RuntimeException(ex); }
        when(datev.erzeugeCsvBytes(any())).thenReturn("datev".getBytes(StandardCharsets.UTF_8));
        SteuerberaterExportService service = new SteuerberaterExportService(belegRepository, anteile, lieferantDokumente, einstellungen, firmen, pdf, datev);
        ReflectionTestUtils.setField(service, "uploadPath", uploads.toString()); return service;
    }

    private static Beleg beleg(Long id, String datei, String lieferantName) {
        Beleg b = new Beleg(); b.setId(id); b.setBelegDatum(LocalDate.of(2026, 2, 10)); b.setLaufendeNummer(id); b.setGespeicherterDateiname(datei); b.setOriginalDateiname(datei); b.setBetragNetto(BigDecimal.TEN); b.setBetragBrutto(new BigDecimal("11.90")); b.setMwstSatz(new BigDecimal("19")); b.setZahlungsart("Bar"); b.setBeschreibung("Material"); b.setBelegKategorie(BelegKategorie.KASSE_AUSGABE);
        Sachkonto konto = new Sachkonto(); konto.setNummer("4930"); b.setSachkonto(konto); Lieferanten l = new Lieferanten(); l.setLieferantenname(lieferantName); b.setLieferant(l); return b;
    }
    private static LieferantDokument verknuepfung(Beleg b, LieferantDokument d) { d.setBeleg(b); return d; }
    private static Map<String, String> unzip(byte[] bytes) throws Exception { Map<String,String> result = new LinkedHashMap<>(); try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) { java.util.zip.ZipEntry entry; while ((entry = zip.getNextEntry()) != null) result.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8)); } return result; }
}

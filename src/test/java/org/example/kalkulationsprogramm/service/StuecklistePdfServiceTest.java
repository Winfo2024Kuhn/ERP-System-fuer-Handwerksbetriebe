package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.junit.jupiter.api.Test;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.example.kalkulationsprogramm.service.pdf.PdfDesignPruefung;
import org.example.kalkulationsprogramm.service.pdf.PdfFarben;
import org.example.kalkulationsprogramm.service.pdf.PdfFirmenDesign;
import org.mockito.Mockito;

import java.awt.Color;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StuecklistePdfServiceTest {

    @Test
    void createsPdfWithWerkstoffMm() throws Exception {
        byte[] pdf = stueckliste(design(null, null));
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        String content = new String(pdf, StandardCharsets.ISO_8859_1);
        // Simple markers likely present in raw text
        assertTrue(content.contains("Bauvorhaben:"));
        assertTrue(content.toLowerCase().contains("stueckliste") || content.contains("Stückliste"));
    }

    @Test
    void summenzeileDerWerkstoffTabelleErscheintImPdf() throws Exception {
        byte[] pdf = stueckliste(design(null, null));

        String text;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
        }
        // 12,5 m + 3 m aus den beiden Positionen; Kilo und Mantelfläche ohne Stammdaten = 0
        assertTrue(text.contains("Summe"), "Summenzeile fehlt: " + text);
        assertTrue(text.contains("15.5"), "Gesamtmeter fehlen: " + text);
        // Ohne Σ: die CP1252-Schrift des PDFs kennt das Zeichen nicht und ließe es still weg
        assertTrue(text.contains("Summe kg: 0"), "Gewichtssumme fehlt: " + text);
        assertTrue(text.contains("Summe Mant.: 0"), "Mantelflächensumme fehlt: " + text);
    }

    private static final Color BLAU = new Color(0x1d, 0x4e, 0xd8);

    @Test
    void mitFirmenfarbeErscheinenTitelUndTabellenkopfInDieserFarbeMitLogo() throws Exception {
        byte[] pdf = stueckliste(design("#1d4ed8", PdfDesignPruefung.testLogo()));

        assertTrue(PdfDesignPruefung.enthaeltFarbe(pdf, BLAU));
        assertFalse(PdfDesignPruefung.enthaeltFarbe(pdf, new Color(204, 0, 0)), "altes festes Rot");
        assertFalse(PdfDesignPruefung.enthaeltFarbe(pdf, PdfFarben.STANDARD_AKZENT));
        assertTrue(PdfDesignPruefung.enthaeltFarbe(pdf, new Color(245, 245, 245)), "graue Wechselzeile bleibt");
        assertEquals(1, PdfDesignPruefung.anzahlBilder(pdf));
    }

    @Test
    void ohneFirmenfarbeWirdDasBisherigeRotGenutzt() throws Exception {
        byte[] pdf = stueckliste(design(null, null));

        assertTrue(PdfDesignPruefung.enthaeltFarbe(pdf, PdfFarben.STANDARD_AKZENT));
        assertEquals(0, PdfDesignPruefung.anzahlBilder(pdf));
    }

    private static PdfFirmenDesign design(String farbe, org.openpdf.text.Image logo) {
        PdfFirmenDesign design = Mockito.mock(PdfFirmenDesign.class);
        Mockito.when(design.laden()).thenReturn(PdfDesign.aus(farbe, logo, "Musterbetrieb GmbH"));
        return design;
    }

    private static byte[] stueckliste(PdfFirmenDesign design) {
        ProjektRepository projektRepository = Mockito.mock(ProjektRepository.class);
        ArtikelInProjektRepository aipRepo = Mockito.mock(ArtikelInProjektRepository.class);
        SchnittbilderRepository schnittbilderRepository = Mockito.mock(SchnittbilderRepository.class);
        DateiSpeicherService dateiSpeicherService = Mockito.mock(DateiSpeicherService.class);

        Projekt p = new Projekt();
        p.setId(10L);
        p.setBauvorhaben("BV X");
        p.setAuftragsnummer("A-123");
        Kunde kunde = new Kunde();
        kunde.setName("Muster GmbH");
        kunde.setKundennummer("K-TEST");
        p.setKundenId(kunde);
        Mockito.when(projektRepository.findById(10L)).thenReturn(Optional.of(p));

        Kategorie rootWerk = new Kategorie(); rootWerk.setId(1); rootWerk.setBeschreibung("Werkstoffe");
        Kategorie subWerk = new Kategorie(); subWerk.setId(11); subWerk.setBeschreibung("Profile"); subWerk.setParentKategorie(rootWerk);

        Werkstoff w = new Werkstoff(); w.setName("S235");

        ArtikelWerkstoffe art = new ArtikelWerkstoffe();
        art.setId(5L);
        art.setProduktname("U-Profil");
        art.setKategorie(subWerk);
        art.setWerkstoff(w);
        art.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(99L);
        aip.setProjekt(p);
        aip.setArtikel(art);
        aip.setStueckzahl(5);
        aip.setMeter(new BigDecimal("12.5"));
        aip.setKommentar("Zuschnitt");

        ArtikelInProjekt zweite = new ArtikelInProjekt();
        zweite.setId(100L);
        zweite.setProjekt(p);
        zweite.setArtikel(art);
        zweite.setStueckzahl(2);
        zweite.setMeter(new BigDecimal("3"));

        Mockito.when(aipRepo.findByProjekt_Id(10L)).thenReturn(List.of(aip, zweite));

        StuecklistePdfService service = new StuecklistePdfService(projektRepository, aipRepo, schnittbilderRepository, dateiSpeicherService, design);
        return service.generateForProjekt(10L);
    }
}

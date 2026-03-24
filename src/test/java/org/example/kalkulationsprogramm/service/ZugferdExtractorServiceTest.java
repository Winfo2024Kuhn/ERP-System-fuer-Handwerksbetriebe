package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ZugferdExtractorServiceTest {

    private ZugferdExtractorService service;

    @BeforeEach
    void setUp() {
        service = new ZugferdExtractorService();
    }

    @Nested
    class GeschaeftsdokumentartErkennung {

        @Test
        void erkenntAuftragsbestaetigungAusDateiname() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "Auftragsbestätigung_2025.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Auftragsbestätigung");
        }

        @Test
        void erkenntAuftragsbestaetigungOhneUmlaute() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "auftragsbestaetigung_123.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Auftragsbestätigung");
        }

        @Test
        void erkenntAngebotAusDateiname() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "Angebot_kunde_2025.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Angebot");
        }

        @Test
        void erkenntGutschriftAusDateiname() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "Gutschrift_2025.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Gutschrift");
        }

        @Test
        void erkenntLieferscheinAusDateiname() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "Lieferschein_2025.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Lieferschein");
        }

        @Test
        void defaultIstRechnungBeiUnbekanntemDateinamen() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "dokument_12345.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Rechnung");
        }

        @Test
        void defaultIstRechnungBeiNullDateiname() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", null);
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Rechnung");
        }
    }

    @Nested
    class TypeCodeMapping {

        @Test
        void mapptTypeCode380ZuRechnung() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("380")).isEqualTo("Rechnung");
        }

        @Test
        void mapptTypeCode384ZuRechnung() {
            // 384 = Korrigierte Rechnung
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("384")).isEqualTo("Rechnung");
        }

        @Test
        void mapptTypeCode389ZuRechnung() {
            // 389 = Eigenrechnung (Self-billed invoice)
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("389")).isEqualTo("Rechnung");
        }

        @Test
        void mapptTypeCode381ZuGutschrift() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("381")).isEqualTo("Gutschrift");
        }

        @Test
        void mapptTypeCode351ZuAngebot() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("351")).isEqualTo("Angebot");
        }

        @Test
        void mapptTypeCode231ZuAuftragsbestaetigung() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("231")).isEqualTo("Auftragsbestätigung");
        }

        @Test
        void mapptTypeCode261ZuLieferschein() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("261")).isEqualTo("Lieferschein");
        }

        @Test
        void mapptTypeCode270ZuLieferschein() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("270")).isEqualTo("Lieferschein");
        }

        @Test
        void gibtNullBeiUnbekanntemTypeCode() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("999")).isNull();
        }

        @Test
        void gibtNullBeiNullTypeCode() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart(null)).isNull();
        }

        @Test
        void trimtWhitespace() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart(" 380 ")).isEqualTo("Rechnung");
        }
    }

    @Nested
    class FallbackBeiUngueltigemPdf {

        @Test
        void gibtDatenMitDokumentartZurueckWennPdfNichtExistiert() {
            ZugferdDaten result = service.extract("pfad/der/nicht/existiert.pdf", "rechnung_123.pdf");

            assertThat(result).isNotNull();
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Rechnung");
            // Keine ZUGFeRD-Daten extrahiert
            assertThat(result.getRechnungsnummer()).isNull();
            assertThat(result.getBetrag()).isNull();
        }

        @Test
        void liefertLeereArtikelpositionenBeiUngueltigemPdf() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "anfrage_xyz.pdf");

            assertThat(result.getArtikelPositionen()).isNotNull().isEmpty();
        }
    }
}

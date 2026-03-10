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
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "angebot_xyz.pdf");

            assertThat(result.getArtikelPositionen()).isNotNull().isEmpty();
        }
    }
}

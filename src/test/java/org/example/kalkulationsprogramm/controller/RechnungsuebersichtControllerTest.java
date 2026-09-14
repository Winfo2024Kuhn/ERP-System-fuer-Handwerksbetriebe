package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RechnungsuebersichtControllerTest {
    @Mock ProjektDokumentRepository projektDokumentRepository;
    @Mock AusgangsGeschaeftsDokumentRepository ausgangsRepository;
    @Mock LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    @Mock MitarbeiterRepository mitarbeiterRepository;
    @Mock LieferantenRepository lieferantenRepository;
    @Mock GeminiDokumentAnalyseService geminiService;
    @Mock LieferantDokumentRepository lieferantDokumentRepository;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path uploads;
    RechnungsuebersichtController controller;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        controller = new RechnungsuebersichtController(
                new org.example.kalkulationsprogramm.service.RechnungsuebersichtService(ausgangsRepository, projektDokumentRepository, uploads.toString()),
                lieferantGeschaeftsdokumentRepository, mitarbeiterRepository, lieferantenRepository, geminiService, lieferantDokumentRepository);
    }

    @Test
    void monatEnthaeltStornoOhneProjektGeschaeftsdokument() {
        var storno = new AusgangsGeschaeftsDokument();
        storno.setId(42L);
        storno.setTyp(AusgangsGeschaeftsDokumentTyp.STORNO);
        storno.setDokumentNummer("ST-2026/09/00001");
        storno.setDatum(LocalDate.of(2026, 9, 13));
        storno.setBetragBrutto(new BigDecimal("-119.00"));
        storno.setGebucht(true);
        when(ausgangsRepository.findRechnungenFuerUebersicht(anySet(),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)))).thenReturn(List.of(storno));

        var result = controller.getAusgangsrechnungen(2026, 9, null).getBody();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id).isEqualTo(42L);
        assertThat(result.getFirst().geschaeftsdokumentart).isEqualTo("Stornorechnung");
        assertThat(result.getFirst().bruttoBetrag).isEqualTo(-119.0);
    }

    private AusgangsGeschaeftsDokument dokument(long id, AusgangsGeschaeftsDokumentTyp typ, String nummer, String datum, String betrag) {
        var d = new AusgangsGeschaeftsDokument();
        d.setId(id); d.setTyp(typ); d.setDokumentNummer(nummer);
        d.setDatum(LocalDate.parse(datum)); d.setBetragBrutto(new BigDecimal(betrag)); d.setGebucht(true);
        return d;
    }

    @Test
    void laedtAlleRechnungsartenAusNeuerDomaeneUndKeineAngeboteOderEntwuerfe() {
        var original = dokument(1, AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-1", "2026-09-01", "119");
        original.setStorniert(true);
        var storno = dokument(2, AusgangsGeschaeftsDokumentTyp.STORNO, "ST-1", "2026-09-02", "-119");
        var gutschrift = dokument(3, AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT, "GU-1", "2026-09-03", "-59.5");
        var teil = dokument(4, AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG, "TR-1", "2026-09-04", "119");
        var abschlag = dokument(5, AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG, "AR-1", "2026-09-05", "238");
        var schluss = dokument(6, AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG, "SR-1", "2026-09-06", "357");
        var angebot = dokument(7, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "AG-1", "2026-09-07", "5000");
        var entwurf = dokument(8, AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-2", "2026-09-08", "9999");
        entwurf.setGebucht(false);
        when(ausgangsRepository.findRechnungenFuerUebersicht(anySet(), isNull(), isNull())).thenReturn(List.of(original, storno, gutschrift, teil, abschlag, schluss, angebot, entwurf));
        var result = controller.getAusgangsrechnungen(null, null, null).getBody();
        assertThat(result).extracting(d -> d.id).containsExactly(6L, 5L, 4L, 3L, 2L, 1L);
        assertThat(result).extracting(d -> d.bruttoBetrag).containsExactly(357.0, 238.0, 119.0, -59.5, -119.0, 119.0);
        assertThat(result.get(4).storno).isTrue();
        assertThat(result.get(5).storniert).isTrue();
        verify(projektDokumentRepository, never()).findAllGeschaeftsdokumente();
    }

    @Test
    void jahresfilterSuchtKundenAuchOhneProjektUndNutztOriginalbetrag() {
        var d = dokument(42, AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-42", "2026-09-13", "119");
        var kunde = new Kunde(); kunde.setName("Max Mustermann"); kunde.setZahlungsziel(14); d.setKunde(kunde);
        var alt = new ProjektGeschaeftsdokument(); alt.setId(999L); alt.setDokumentid("RE-42");
        alt.setBruttoBetrag(new BigDecimal("9999")); alt.setBezahlt(true);
        when(ausgangsRepository.findRechnungenFuerUebersicht(anySet(), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31)))).thenReturn(List.of(d));
        when(projektDokumentRepository.findGeschaeftsdokumenteByDokumentidIn(List.of("RE-42"))).thenReturn(List.of(alt));
        var result = controller.getAusgangsrechnungen(2026, null, "MUSTERMANN").getBody();
        assertThat(result).hasSize(1);
        var dto = result.getFirst();
        assertThat(dto.id).isEqualTo(42L);
        assertThat(dto.bruttoBetrag).isEqualTo(119.0);
        assertThat(dto.bezahlt).isTrue();
        assertThat(dto.projektKunde).isEqualTo("Max Mustermann");
        assertThat(dto.faelligkeitsdatum).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(dto.editorUrl).isEqualTo("/dokument-editor?dokumentId=42&dokumentTyp=RECHNUNG");
        assertThat(controller.getAusgangsrechnungen(2026, null, "unbekannt").getBody()).isEmpty();
    }

    @Test
    void suchtNachStornoTypOhneEinenAlteintragVorauszusetzen() {
        when(ausgangsRepository.findRechnungenFuerUebersicht(anySet(), isNull(), isNull())).thenReturn(List.of(
                dokument(42, AusgangsGeschaeftsDokumentTyp.STORNO, "ST-42", "2026-09-13", "-119")));
        assertThat(controller.getAusgangsrechnungen(null, null, "storno").getBody()).hasSize(1);
    }

    @Test
    void fehlendesAusgangsPdfLiefertFehlerStattEinenUnvollstaendigenExport() {
        when(ausgangsRepository.findById(42L)).thenReturn(java.util.Optional.of(
                dokument(42, AusgangsGeschaeftsDokumentTyp.STORNO, "ST-42", "2026-09-13", "-119")));
        var response = controller.mergePdfs(new RechnungsuebersichtController.MergePdfRequest(List.of(42L), List.of()));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(projektDokumentRepository, never()).findById(anyLong());
    }

    @Test
    void pdfExportVerwendetNeueIdUndFindetArchivUeberDokumentnummer() throws Exception {
        var d = dokument(42, AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-42", "2026-09-13", "119");
        var detail = new ProjektGeschaeftsdokument();
        detail.setId(999L); detail.setDokumentid("RE-42"); detail.setGespeicherterDateiname("test.pdf");
        try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            pdf.save(uploads.resolve("test.pdf").toFile());
        }
        when(ausgangsRepository.findById(42L)).thenReturn(java.util.Optional.of(d));
        when(projektDokumentRepository.findGeschaeftsdokumenteByDokumentid("RE-42")).thenReturn(List.of(detail));
        var result = controller.mergePdfs(new RechnungsuebersichtController.MergePdfRequest(List.of(42L), List.of()));
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        try (var pdf = org.apache.pdfbox.Loader.loadPDF((byte[]) result.getBody())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
        }
        verify(projektDokumentRepository, never()).findById(anyLong());
    }

    @Test
    void pdfExportLehntPfadAusserhalbDesUploadOrdnersAb() {
        var d = dokument(42, AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-42", "2026-09-13", "119");
        var detail = new ProjektGeschaeftsdokument(); detail.setGespeicherterDateiname("../anderes.pdf");
        when(ausgangsRepository.findById(42L)).thenReturn(java.util.Optional.of(d));
        when(projektDokumentRepository.findGeschaeftsdokumenteByDokumentid("RE-42")).thenReturn(List.of(detail));
        assertThat(controller.mergePdfs(new RechnungsuebersichtController.MergePdfRequest(List.of(42L), List.of())).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void eingangBehaeltGutschriftUndNegativenBetragBei() {
        var g = new LieferantGeschaeftsdokument(); g.setId(11L); g.setDokumentNummer("GU-11");
        g.setDokumentDatum(LocalDate.of(2026, 9, 13)); g.setBetragBrutto(new BigDecimal("-119"));
        when(lieferantGeschaeftsdokumentRepository.findRechnungenByDatumBetween(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(new java.util.ArrayList<>(List.of(g)));
        var result = controller.getEingangsrechnungen(2026, 9, null).getBody();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().betragBrutto).isEqualTo(-119.0);
    }

    @Test
    void ohneZahlungseintragIstStatusUnbekanntStattOffen() {
        var d = dokument(42, AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-42", "2026-09-13", "119");
        when(ausgangsRepository.findRechnungenFuerUebersicht(anySet(), isNull(), isNull())).thenReturn(List.of(d));
        assertThat(controller.getAusgangsrechnungen(null, null, null).getBody().getFirst().bezahlt).isNull();
    }

    @Test
    void exportiertStornoPdfDirektAusAusgangsdokumentOhneAlteintrag() throws Exception {
        var d = dokument(42, AusgangsGeschaeftsDokumentTyp.STORNO, "ST-42", "2026-09-13", "-119");
        d.setPdfDateiname("storno.pdf");
        try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage()); pdf.save(uploads.resolve("storno.pdf").toFile());
        }
        when(ausgangsRepository.findById(42L)).thenReturn(java.util.Optional.of(d));
        var result = controller.mergePdfs(new RechnungsuebersichtController.MergePdfRequest(List.of(42L), List.of()));
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(projektDokumentRepository);
    }
}

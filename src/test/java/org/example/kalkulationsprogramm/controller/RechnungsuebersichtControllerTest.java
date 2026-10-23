package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.LieferantDokumentDateiService;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
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
    @Mock LieferantDokumentZugriffService zugriffService;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path uploads;
    RechnungsuebersichtController controller;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        controller = controllerMitAblage(uploads);
        // Angemeldeter Nutzer mit Zugriff auf alle Dokumenttypen (z. B. Admin)
        lenient().when(zugriffService.sichtbareTypen(any(), any()))
                .thenReturn(java.util.Optional.of(java.util.EnumSet.allOf(LieferantDokumentTyp.class)));
    }

    /** Controller mit echter Dateisuche; die Ordner liegen unter {@code ablage} wie im Standard-Setup. */
    private RechnungsuebersichtController controllerMitAblage(java.nio.file.Path ablage) {
        var dateiService = new LieferantDokumentDateiService(ablage.toString(), ablage.toString(),
                ablage.resolve("attachments").toString(), ablage.resolve("attachments").resolve("lieferanten").toString());
        return new RechnungsuebersichtController(
                new org.example.kalkulationsprogramm.service.RechnungsuebersichtService(ausgangsRepository, projektDokumentRepository, uploads.toString()),
                lieferantGeschaeftsdokumentRepository, mitarbeiterRepository, lieferantenRepository, geminiService,
                lieferantDokumentRepository, zugriffService, dateiService,
                new org.example.kalkulationsprogramm.service.LieferantDateiablage(ablage.toString()));
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
        var response = controller.mergePdfs(new RechnungsuebersichtController.MergePdfRequest(List.of(42L), List.of()), null);
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
        var result = controller.mergePdfs(new RechnungsuebersichtController.MergePdfRequest(List.of(42L), List.of()), null);
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
        assertThat(controller.mergePdfs(new RechnungsuebersichtController.MergePdfRequest(List.of(42L), List.of()), null).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void eingangBehaeltGutschriftUndNegativenBetragBei() {
        var g = new LieferantGeschaeftsdokument(); g.setId(11L); g.setDokumentNummer("GU-11");
        var gutschriftDok = new LieferantDokument(); gutschriftDok.setTyp(LieferantDokumentTyp.GUTSCHRIFT); g.setDokument(gutschriftDok);
        g.setDokumentDatum(LocalDate.of(2026, 9, 13)); g.setBetragBrutto(new BigDecimal("-119"));
        when(lieferantGeschaeftsdokumentRepository.findRechnungenByDatumBetween(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(new java.util.ArrayList<>(List.of(g)));
        var result = controller.getEingangsrechnungen(2026, 9, null, null).getBody();
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
        var result = controller.mergePdfs(new RechnungsuebersichtController.MergePdfRequest(List.of(42L), List.of()), null);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(projektDokumentRepository);
    }

    // ---------------------------------------------------------------- Dokumentrechte (Eingang)

    private LieferantGeschaeftsdokument eingang(long id, LieferantDokumentTyp typ) {
        var dok = new LieferantDokument();
        dok.setId(id);
        dok.setTyp(typ);
        var gd = new LieferantGeschaeftsdokument();
        gd.setId(id);
        gd.setDokumentNummer("NR-" + id);
        gd.setDokumentDatum(LocalDate.of(2026, 9, 13));
        gd.setDokument(dok);
        return gd;
    }

    private void sichtbar(LieferantDokumentTyp... typen) {
        when(zugriffService.sichtbareTypen(any(), any())).thenReturn(java.util.Optional.of(
                typen.length == 0 ? java.util.EnumSet.noneOf(LieferantDokumentTyp.class)
                        : java.util.EnumSet.copyOf(List.of(typen))));
    }

    @Test
    void eingangZeigtNurSichtbareDokumenttypen() {
        sichtbar(LieferantDokumentTyp.RECHNUNG);
        var ohneDokument = new LieferantGeschaeftsdokument();
        ohneDokument.setId(3L);
        when(lieferantGeschaeftsdokumentRepository.findAllEingangsrechnungen()).thenReturn(new java.util.ArrayList<>(
                List.of(eingang(1L, LieferantDokumentTyp.RECHNUNG), eingang(2L, LieferantDokumentTyp.GUTSCHRIFT),
                        ohneDokument)));

        var result = controller.getEingangsrechnungen(null, null, null, null).getBody();

        assertThat(result).extracting(d -> d.id).containsExactly(1L);
    }

    @Test
    void eingangOhneSichtbareTypenIstLeer() {
        sichtbar();
        when(lieferantGeschaeftsdokumentRepository.findAllEingangsrechnungen())
                .thenReturn(new java.util.ArrayList<>(List.of(eingang(1L, LieferantDokumentTyp.RECHNUNG))));

        assertThat(controller.getEingangsrechnungen(null, null, null, null).getBody()).isEmpty();
    }

    @Test
    void eingangOhneAnmeldungGibt401UndLaedtNichts() {
        when(zugriffService.sichtbareTypen(any(), any())).thenReturn(java.util.Optional.empty());

        var response = controller.getEingangsrechnungen(2026, 9, "'; DROP TABLE x; --", null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(lieferantGeschaeftsdokumentRepository);
    }

    @Test
    void mergeUeberspringtNichtSichtbareEingangsrechnungen() throws Exception {
        sichtbar(LieferantDokumentTyp.RECHNUNG);
        var erlaubt = eingang(1L, LieferantDokumentTyp.RECHNUNG);
        var gesperrt = eingang(2L, LieferantDokumentTyp.GUTSCHRIFT);
        erlaubt.getDokument().setGespeicherterDateiname("erlaubt.pdf");
        gesperrt.getDokument().setGespeicherterDateiname("gesperrt.pdf");
        for (String name : List.of("erlaubt.pdf", "gesperrt.pdf")) {
            try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
                pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
                pdf.save(uploads.resolve(name).toFile());
            }
        }
        when(lieferantGeschaeftsdokumentRepository.findById(1L)).thenReturn(java.util.Optional.of(erlaubt));
        when(lieferantGeschaeftsdokumentRepository.findById(2L)).thenReturn(java.util.Optional.of(gesperrt));

        var result = controller.mergePdfs(
                new RechnungsuebersichtController.MergePdfRequest(List.of(), List.of(1L, 2L)), null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        try (var pdf = org.apache.pdfbox.Loader.loadPDF((byte[]) result.getBody())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
        }
    }

    private static void leeresPdf(java.nio.file.Path ziel) throws Exception {
        java.nio.file.Files.createDirectories(ziel.getParent());
        try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            pdf.save(ziel.toFile());
        }
    }

    @Test
    void mergeFindetManuellHochgeladeneRechnungImLieferantenOrdner() throws Exception {
        var lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Musterlieferant GmbH");
        var rechnung = eingang(1L, LieferantDokumentTyp.RECHNUNG);
        rechnung.getDokument().setLieferant(lieferant);
        rechnung.getDokument().setGespeicherterDateiname("0b6f-uuid_Rechnung_Muster.pdf");
        // Liegt nur dort, wo import-upload und der Lieferanten-Import speichern.
        leeresPdf(uploads.resolve("lieferanten").resolve("7").resolve("0b6f-uuid_Rechnung_Muster.pdf"));
        when(lieferantGeschaeftsdokumentRepository.findById(1L)).thenReturn(java.util.Optional.of(rechnung));

        var result = controller.mergePdfs(
                new RechnungsuebersichtController.MergePdfRequest(List.of(), List.of(1L)), null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        try (var pdf = org.apache.pdfbox.Loader.loadPDF((byte[]) result.getBody())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void mergeLiestKeineEingangsrechnungAusserhalbDesUploadOrdners() throws Exception {
        var lieferant = new Lieferanten();
        lieferant.setId(7L);
        var rechnung = eingang(1L, LieferantDokumentTyp.RECHNUNG);
        rechnung.getDokument().setLieferant(lieferant);
        rechnung.getDokument().setGespeicherterDateiname("../../../draussen.pdf");
        // Die Datei gibt es – aber neben dem Upload-Ordner, nicht darin.
        var ablage = uploads.resolve("ablage");
        java.nio.file.Files.createDirectories(ablage.resolve("lieferanten").resolve("7"));
        leeresPdf(uploads.resolve("draussen.pdf"));
        controller = controllerMitAblage(ablage);
        when(lieferantGeschaeftsdokumentRepository.findById(1L)).thenReturn(java.util.Optional.of(rechnung));

        var result = controller.mergePdfs(
                new RechnungsuebersichtController.MergePdfRequest(List.of(), List.of(1L)), null);

        assertThat(result.getStatusCode().value()).isEqualTo(400);
    }

    private LieferantGeschaeftsdokument rechnungVonLieferant7(long id, String gespeicherterName) {
        var lieferant = new Lieferanten();
        lieferant.setId(7L);
        var rechnung = eingang(id, LieferantDokumentTyp.RECHNUNG);
        rechnung.getDokument().setLieferant(lieferant);
        rechnung.getDokument().setGespeicherterDateiname(gespeicherterName);
        when(lieferantGeschaeftsdokumentRepository.findById(id)).thenReturn(java.util.Optional.of(rechnung));
        return rechnung;
    }

    private static int seitenzahl(org.springframework.http.ResponseEntity<?> antwort) throws Exception {
        try (var pdf = org.apache.pdfbox.Loader.loadPDF((byte[]) antwort.getBody())) {
            return pdf.getNumberOfPages();
        }
    }

    @Test
    void mergeLaesstFotoUndFehlendeDateiAusUndMeldetSieImHeader() throws Exception {
        var ordner = uploads.resolve("lieferanten").resolve("7");
        rechnungVonLieferant7(1L, "rechnung.pdf");
        rechnungVonLieferant7(2L, "foto.jpg");
        rechnungVonLieferant7(3L, "gibt-es-nicht.pdf");
        leeresPdf(ordner.resolve("rechnung.pdf"));
        // JPEG-Anfang: früher brach PDFBox daran das ganze Gesamt-PDF mit 500 ab.
        java.nio.file.Files.write(ordner.resolve("foto.jpg"), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        when(lieferantGeschaeftsdokumentRepository.findById(4L)).thenReturn(java.util.Optional.empty());

        var result = controller.mergePdfs(
                new RechnungsuebersichtController.MergePdfRequest(List.of(), List.of(1L, 2L, 3L, 4L)), null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(seitenzahl(result)).isEqualTo(1);
        // Die unbekannte ID 4 bleibt stumm – der Header verrät nicht, welche IDs es gibt.
        assertThat(result.getHeaders().getFirst(RechnungsuebersichtController.HEADER_NICHT_ENTHALTEN)).isEqualTo("2,3");
    }

    @Test
    void mergeBehandeltGesperrteUndUnbekannteIdsGleichUndNenntSieNicht() throws Exception {
        sichtbar(LieferantDokumentTyp.RECHNUNG);
        rechnungVonLieferant7(1L, "rechnung.pdf");
        leeresPdf(uploads.resolve("lieferanten").resolve("7").resolve("rechnung.pdf"));
        var gesperrt = eingang(2L, LieferantDokumentTyp.GUTSCHRIFT);
        when(lieferantGeschaeftsdokumentRepository.findById(2L)).thenReturn(java.util.Optional.of(gesperrt));
        when(lieferantGeschaeftsdokumentRepository.findById(3L)).thenReturn(java.util.Optional.empty());

        var result = controller.mergePdfs(
                new RechnungsuebersichtController.MergePdfRequest(List.of(), List.of(1L, 2L, 3L)), null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(seitenzahl(result)).isEqualTo(1);
        assertThat(result.getHeaders().containsHeader(RechnungsuebersichtController.HEADER_NICHT_ENTHALTEN)).isFalse();
    }

    @Test
    void mergeNimmtDiePdfWennUnterDemAnderenNamenNurDieXmlLiegt() throws Exception {
        var rechnung = rechnungVonLieferant7(1L, "e-rechnung.xml");
        var anhang = new EmailAttachment();
        anhang.setStoredFilename("e-rechnung.pdf");
        rechnung.getDokument().setAttachment(anhang);
        var ordner = uploads.resolve("lieferanten").resolve("7");
        java.nio.file.Files.createDirectories(ordner);
        java.nio.file.Files.writeString(ordner.resolve("e-rechnung.xml"), "<?xml version=\"1.0\"?><Invoice/>");
        leeresPdf(uploads.resolve("attachments").resolve("e-rechnung.pdf"));

        var result = controller.mergePdfs(
                new RechnungsuebersichtController.MergePdfRequest(List.of(), List.of(1L)), null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(seitenzahl(result)).isEqualTo(1);
        assertThat(result.getHeaders().containsHeader(RechnungsuebersichtController.HEADER_NICHT_ENTHALTEN)).isFalse();
    }

    @Test
    void mergeOhneEinzigePdfGibt400MitVerstaendlicherMeldung() throws Exception {
        rechnungVonLieferant7(1L, "e-rechnung.xml");
        var xml = uploads.resolve("lieferanten").resolve("7").resolve("e-rechnung.xml");
        java.nio.file.Files.createDirectories(xml.getParent());
        java.nio.file.Files.writeString(xml, "<?xml version=\"1.0\"?><Invoice/>");

        var result = controller.mergePdfs(
                new RechnungsuebersichtController.MergePdfRequest(List.of(), List.of(1L)), null);

        assertThat(result.getStatusCode().value()).isEqualTo(400);
        assertThat(result.getBody()).isEqualTo(java.util.Map.of("message",
                "Zu den ausgewählten Rechnungen gibt es keine PDF-Datei. Das Gesamt-PDF kann nicht erstellt werden."));
    }

    @Test
    void mergeFindetRechnungAusEmailEingangWieDieVorschau() throws Exception {
        rechnungVonLieferant7(1L, "vendor.pdf");
        rechnungVonLieferant7(2L, "lieferanten-anhang.pdf");
        rechnungVonLieferant7(3L, "email-ordner.pdf");
        leeresPdf(uploads.resolve("attachments").resolve("vendor-invoices").resolve("vendor.pdf"));
        leeresPdf(uploads.resolve("attachments").resolve("lieferanten").resolve("7").resolve("lieferanten-anhang.pdf"));
        leeresPdf(uploads.resolve("attachments").resolve("email").resolve("7").resolve("email-ordner.pdf"));

        var result = controller.mergePdfs(
                new RechnungsuebersichtController.MergePdfRequest(List.of(), List.of(1L, 2L, 3L)), null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(seitenzahl(result)).isEqualTo(3);
        assertThat(result.getHeaders().containsHeader(RechnungsuebersichtController.HEADER_NICHT_ENTHALTEN)).isFalse();
    }

    @Test
    void mergeFindetAnhangOhneEigenenDateinamenAuchFlachUndImMailOrdner() throws Exception {
        var email = new Email();
        email.setId(55L);
        var flach = rechnungVonLieferant7(1L, null);
        var anhangFlach = new EmailAttachment();
        anhangFlach.setStoredFilename("flach.pdf");
        anhangFlach.setEmail(email);
        flach.getDokument().setAttachment(anhangFlach);
        var imMailOrdner = rechnungVonLieferant7(2L, null);
        var anhangImOrdner = new EmailAttachment();
        anhangImOrdner.setStoredFilename("im-mail-ordner.pdf");
        anhangImOrdner.setEmail(email);
        imMailOrdner.getDokument().setAttachment(anhangImOrdner);
        leeresPdf(uploads.resolve("attachments").resolve("flach.pdf"));
        leeresPdf(uploads.resolve("attachments").resolve("55").resolve("im-mail-ordner.pdf"));

        var result = controller.mergePdfs(
                new RechnungsuebersichtController.MergePdfRequest(List.of(), List.of(1L, 2L)), null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(seitenzahl(result)).isEqualTo(2);
    }

    @Test
    void mergeNurMitNichtSichtbarenRechnungenGibt400() {
        sichtbar(LieferantDokumentTyp.LIEFERSCHEIN);
        when(lieferantGeschaeftsdokumentRepository.findById(2L))
                .thenReturn(java.util.Optional.of(eingang(2L, LieferantDokumentTyp.RECHNUNG)));

        var result = controller.mergePdfs(
                new RechnungsuebersichtController.MergePdfRequest(List.of(), List.of(2L)), null);

        assertThat(result.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void mergeOhneAnmeldungGibt401() {
        when(zugriffService.sichtbareTypen(any(), any())).thenReturn(java.util.Optional.empty());

        var result = controller.mergePdfs(
                new RechnungsuebersichtController.MergePdfRequest(List.of(), List.of(1L)), null);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(lieferantGeschaeftsdokumentRepository);
    }
}

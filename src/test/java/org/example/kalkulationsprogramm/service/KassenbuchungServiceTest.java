package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.KassenbuchungDto;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KassenbuchungServiceTest {
    @Mock BelegRepository belege;
    @Mock SachkontoRepository konten;
    @Mock KostenstelleRepository kostenstellen;
    @Mock BelegKostenstellenAnteilRepository anteile;
    @Mock ProjektDokumentRepository dokumente;
    @Mock KasseSaldoService saldo;
    @Mock KassenbuchSchreibschutz schutz;
    @Mock BelegAuditService audit;
    @Mock BelegPdfService pdf;
    @Mock KasseEinstellungRepository einstellungen;
    @TempDir Path upload;
    KassenbuchungService service;
    Mitarbeiter ersteller;

    @BeforeEach void setup() {
        service = new KassenbuchungService(belege, konten, kostenstellen, anteile, dokumente,
                saldo, schutz, audit, pdf, einstellungen);
        ReflectionTestUtils.setField(service, "uploadPath", upload.toString());
        ersteller = new Mitarbeiter();
        ersteller.setId(42L);
        ersteller.setVorname("Max");
        ersteller.setNachname("Mustermann");
    }

    private KassenbuchungDto.CreateRequest request(String art) {
        var req = new KassenbuchungDto.CreateRequest();
        req.setArt(art);
        req.setBetragBrutto(new BigDecimal("119.00"));
        req.setBelegDatum(LocalDate.of(2026, 9, 14));
        req.setMwstSatz(new BigDecimal("19"));
        req.setBeschreibung("Material Musterbaustelle");
        req.setGegenpartei("Max Mustermann");
        return req;
    }

    private Sachkonto konto(String nummer) {
        Sachkonto konto = new Sachkonto();
        konto.setId(Long.valueOf(nummer));
        konto.setNummer(nummer);
        konto.setBezeichnung("Musterkonto");
        return konto;
    }

    private void speichern() {
        when(belege.save(any(Beleg.class))).thenAnswer(inv -> {
            Beleg b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });
    }

    private BelegPdfService.ErzeugtesPdf pdfDatei() throws Exception {
        Files.createDirectories(upload.resolve("belege"));
        Files.writeString(upload.resolve("belege/test.pdf"), "PDF dummy");
        return new BelegPdfService.ErzeugtesPdf("test.pdf", "Ersatzbeleg.pdf", "application/pdf", "a".repeat(64));
    }

    @ParameterizedTest
    @CsvSource({
        "GELD_EINGENOMMEN,KASSE_EINNAHME,QUITTUNG,8400,false",
        "GELD_AUSGEGEBEN,KASSE_AUSGABE,SCAN,4930,false",
        "VON_BANK_GEHOLT,KASSE_EINNAHME,TRANSFER,1200,true",
        "ZUR_BANK_GEBRACHT,KASSE_AUSGABE,TRANSFER,1200,true",
        "EIGENES_GELD_EINGELEGT,PRIVATEINLAGE,TRANSFER,1810,true",
        "GELD_PRIVAT_ENTNOMMEN,PRIVATENTNAHME,TRANSFER,1800,true"
    })
    void buchtAlleSechsKachelnMitDateiUndAudit(String art, BelegKategorie kategorie,
            BelegQuelle quelle, String nummer, boolean umbuchung) throws Exception {
        var req = request(art);
        MockMultipartFile datei = null;
        if (art.equals("GELD_AUSGEGEBEN")) {
            req.setSachkontoId(4930L);
            when(konten.findById(4930L)).thenReturn(Optional.of(konto(nummer)));
            datei = new MockMultipartFile("datei", "beleg.pdf", "application/pdf", "dummy".getBytes());
        } else {
            when(konten.findByNummer(nummer)).thenReturn(Optional.of(konto(nummer)));
            if (art.equals("GELD_EINGENOMMEN")) when(pdf.erzeugeQuittung(any())).thenReturn(pdfDatei());
            else when(pdf.erzeugeEigenbeleg(any())).thenReturn(pdfDatei());
        }
        speichern();
        Beleg b = service.buche(req, datei, ersteller);
        assertThat(b.getBelegKategorie()).isEqualTo(kategorie);
        assertThat(b.getQuelle()).isEqualTo(quelle);
        assertThat(b.getSachkonto().getNummer()).isEqualTo(nummer);
        assertThat(b.getIstUmbuchung()).isEqualTo(umbuchung);
        assertThat(b.getStatus()).isEqualTo(BelegStatus.VALIDIERT);
        assertThat(b.getKiAnalyseStatus()).isEqualTo(BelegKiAnalyseStatus.DONE);
        assertThat(b.getGespeicherterDateiname()).isNotBlank();
        assertThat(b.getDateiHash()).matches("[a-f0-9]{64}");
        assertThat(upload.resolve("belege").resolve(b.getGespeicherterDateiname())).exists();
        assertThat(b.getUploadedBy()).isSameAs(ersteller);
        assertThat(b.getValidiertVon()).isSameAs(ersteller);
        assertThat(b.getGegenpartei()).isEqualTo("Max Mustermann");
        assertThat(b.getBetragNetto()).isEqualByComparingTo(umbuchung ? "119" : "100");
        verify(audit).protokolliereErfassung(b, ersteller, null);
        var order = inOrder(schutz, saldo, belege, audit);
        order.verify(schutz).assertMonatOffen(req.getBelegDatum());
        order.verify(saldo).projiziereSaldo(null, null, kategorie, req.getBetragBrutto());
        order.verify(saldo).assertSaldoMindestensMindestbestand(any());
        order.verify(belege).save(b);
        order.verify(audit).protokolliereErfassung(b, ersteller, null);
    }

    @Test void ersatzbelegHatKeineVorsteuerUndBewahrtGrund() throws Exception {
        var req = request("GELD_AUSGEGEBEN");
        req.setSachkontoId(4930L);
        req.setKeinBelegVorhanden(true);
        req.setGrundOhneBeleg("Beleg verloren");
        when(konten.findById(4930L)).thenReturn(Optional.of(konto("4930")));
        when(pdf.erzeugeEigenbeleg(any())).thenReturn(pdfDatei());
        speichern();
        Beleg b = service.buche(req, null, ersteller);
        assertThat(b.getMwstSatz()).isEqualByComparingTo("0");
        assertThat(b.getBetragNetto()).isEqualByComparingTo("119");
        assertThat(b.getQuelle()).isEqualTo(BelegQuelle.EIGENBELEG);
        assertThat(b.getIstUmbuchung()).isTrue();
        var daten = ArgumentCaptor.forClass(BelegPdfService.EigenbelegDaten.class);
        verify(pdf).erzeugeEigenbeleg(daten.capture());
        assertThat(daten.getValue().grund()).isEqualTo("Beleg verloren");
    }

    @Test void verwendetKonfiguriertesEinlagekonto() throws Exception {
        var setting = new KasseEinstellung();
        setting.setPrivateinlageSachkonto(konto("1890"));
        when(einstellungen.findSingleton()).thenReturn(Optional.of(setting));
        when(pdf.erzeugeEigenbeleg(any())).thenReturn(pdfDatei());
        speichern();
        assertThat(service.buche(request("EIGENES_GELD_EINGELEGT"), null, ersteller)
                .getSachkonto().getNummer()).isEqualTo("1890");
        verifyNoInteractions(konten);
    }

    @Test void unterdeckungSchreibtWederDateiNochBeleg() {
        var req = request("GELD_PRIVAT_ENTNOMMEN");
        when(konten.findByNummer("1800")).thenReturn(Optional.of(konto("1800")));
        when(saldo.projiziereSaldo(null, null, BelegKategorie.PRIVATENTNAHME, req.getBetragBrutto()))
                .thenReturn(new BigDecimal("-19"));
        doThrow(new KasseUnterdeckungException(new BigDecimal("-19"), BigDecimal.ZERO))
                .when(saldo).assertSaldoMindestensMindestbestand(new BigDecimal("-19"));
        assertThatThrownBy(() -> service.buche(req, null, ersteller)).isInstanceOf(KasseUnterdeckungException.class);
        verifyNoInteractions(belege, pdf, audit);
    }

    @Test void abgeschlossenerMonatWirdVorSaldoAbgewiesen() {
        var req = request("VON_BANK_GEHOLT");
        when(konten.findByNummer("1200")).thenReturn(Optional.of(konto("1200")));
        doThrow(new KassenbuchGesperrtException("Monat abgeschlossen")).when(schutz).assertMonatOffen(req.getBelegDatum());
        assertThatThrownBy(() -> service.buche(req, null, ersteller)).isInstanceOf(KassenbuchGesperrtException.class);
        verifyNoInteractions(saldo, belege, pdf, audit);
    }

    @Test void eingabefehlerKommenVorMonatsschutz() {
        var req = request("VON_BANK_GEHOLT");
        req.setBetragBrutto(null);
        assertThatThrownBy(() -> service.buche(req, null, ersteller))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Betrag fehlt oder ist nicht positiv");
        verifyNoInteractions(schutz, saldo, belege, pdf);
    }

    @Test void kostenstelleErhaeltVollenNettoAnteilUndRechnungWirdBezahlt() throws Exception {
        var req = request("GELD_EINGENOMMEN");
        req.setKostenstelleId(9L);
        req.setAusgangsrechnungId(7L);
        var ks = new Kostenstelle(); ks.setId(9L);
        var rechnung = new ProjektGeschaeftsdokument();
        rechnung.setId(7L); rechnung.setGeschaeftsdokumentart("Rechnung");
        rechnung.setDokumentid("R-MUSTER-7"); rechnung.setBruttoBetrag(req.getBetragBrutto());
        when(kostenstellen.findById(9L)).thenReturn(Optional.of(ks));
        when(dokumente.findGeschaeftsdokumentByIdForUpdate(7L)).thenReturn(Optional.of(rechnung));
        when(konten.findByNummer("8400")).thenReturn(Optional.of(konto("8400")));
        when(pdf.erzeugeQuittung(any())).thenReturn(pdfDatei());
        speichern();
        Beleg b = service.buche(req, null, ersteller);
        assertThat(b.getAusgangsrechnungId()).isEqualTo(7L);
        assertThat(rechnung.isBezahlt()).isTrue();
        verify(dokumente).save(rechnung);
        assertThat(b.getKostenstelle()).isSameAs(ks);
        var split = ArgumentCaptor.forClass(BelegKostenstellenAnteil.class);
        verify(anteile).save(split.capture());
        assertThat(split.getValue().getBeleg()).isSameAs(b);
        assertThat(split.getValue().getKostenstelle()).isSameAs(ks);
        assertThat(split.getValue().getProzent()).isEqualTo(100);
        assertThat(split.getValue().getBerechneterBetrag()).isEqualByComparingTo("100");
        var daten = ArgumentCaptor.forClass(BelegPdfService.QuittungDaten.class);
        verify(pdf).erzeugeQuittung(daten.capture());
        assertThat(daten.getValue().rechnungsNummer()).isEqualTo("R-MUSTER-7");
    }

    @Test void bezahlteRechnungWirdAbgewiesenBevorDateiEntsteht() {
        var req = request("GELD_EINGENOMMEN"); req.setAusgangsrechnungId(7L);
        var rechnung = new ProjektGeschaeftsdokument(); rechnung.setBezahlt(true);
        rechnung.setGeschaeftsdokumentart("Rechnung");
        when(dokumente.findGeschaeftsdokumentByIdForUpdate(7L)).thenReturn(Optional.of(rechnung));
        lenient().when(konten.findByNummer("8400")).thenReturn(Optional.of(konto("8400")));
        assertThatThrownBy(() -> service.buche(req, null, ersteller)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bereits bezahlt");
        verifyNoInteractions(pdf, belege, audit);
    }

    @ParameterizedTest @ValueSource(strings = {"../../beleg.pdf", "a\\b.pdf", "x.exe", "x.svg", "x.js"})
    void unsichereDateienWerdenVorBuchhaltungAbgewiesen(String name) {
        var req = request("GELD_AUSGEGEBEN"); req.setSachkontoId(4930L);
        var datei = new MockMultipartFile("datei", name, "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> service.buche(req, datei, ersteller)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(schutz, saldo, belege, pdf);
    }

    @Test void geloeschteDateiBeiAuditFehler() throws Exception {
        var req = request("VON_BANK_GEHOLT");
        when(konten.findByNummer("1200")).thenReturn(Optional.of(konto("1200")));
        when(pdf.erzeugeEigenbeleg(any())).thenReturn(pdfDatei());
        speichern();
        doThrow(new IllegalStateException("Audit nicht verfügbar")).when(audit).protokolliereErfassung(any(), eq(ersteller), isNull());
        assertThatThrownBy(() -> service.buche(req, null, ersteller)).isInstanceOf(IllegalStateException.class);
        assertThat(upload.resolve("belege/test.pdf")).doesNotExist();
    }

    @Test void scanWirdAuchBeiTransaktionsRollbackGeloescht() {
        var req = request("GELD_AUSGEGEBEN"); req.setSachkontoId(4930L);
        when(konten.findById(4930L)).thenReturn(Optional.of(konto("4930")));
        speichern();
        TransactionSynchronizationManager.initSynchronization();
        try {
            Beleg b = service.buche(req, new MockMultipartFile("datei", "test.pdf", "application/pdf", new byte[]{1}), ersteller);
            Path datei = upload.resolve("belege").resolve(b.getGespeicherterDateiname());
            assertThat(datei).exists();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(datei).doesNotExist();
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void offeneRechnungenSindAbsteigendSortiertAuf100BegrenztUndNullSicher() {
        List<ProjektGeschaeftsdokument> liste = IntStream.range(0, 103).mapToObj(i -> {
            var g = new ProjektGeschaeftsdokument(); g.setId((long)i);
            g.setRechnungsdatum(LocalDate.of(2026, 1, 1).plusDays(i));
            g.setDokumentid("R-" + i); g.setBruttoBetrag(BigDecimal.TEN);
            g.setGeschaeftsdokumentart("Rechnung");
            return g;
        }).toList();
        when(dokumente.findOffeneGeschaeftsdokumente()).thenReturn(liste);
        var result = service.offeneAusgangsrechnungen();
        assertThat(result).hasSize(100);
        assertThat(result.getFirst().getId()).isEqualTo(102L);
        assertThat(result.getLast().getId()).isEqualTo(3L);
        assertThat(result.getFirst().getKundeName()).isNull();
    }
    @Test void rechnungsauswahlFiltertNichtZuordenbareDokumenteVorDemLimit() {
        var liste = new java.util.ArrayList<ProjektGeschaeftsdokument>();
        for (int i = 0; i < 103; i++) {
            var rechnung = new ProjektGeschaeftsdokument();
            rechnung.setId((long) i);
            rechnung.setGeschaeftsdokumentart(i % 2 == 0 ? "Rechnung" : "ABSCHLAGSRECHNUNG");
            rechnung.setRechnungsdatum(LocalDate.of(2026, 1, 1).plusDays(i));
            liste.add(rechnung);
        }
        String[] ungueltigeArten = {"1. Mahnung", "Zahlungserinnerung", null, "Rechnung"};
        for (int i = 0; i < ungueltigeArten.length; i++) {
            var dokument = new ProjektGeschaeftsdokument();
            dokument.setId(200L + i);
            dokument.setGeschaeftsdokumentart(ungueltigeArten[i]);
            dokument.setRechnungsdatum(LocalDate.of(2026, 9, 14));
            if (i == 3) dokument.setMahnstufe(Mahnstufe.ERSTE_MAHNUNG);
            liste.add(dokument);
        }
        when(dokumente.findOffeneGeschaeftsdokumente()).thenReturn(liste);

        var auswahl = service.offeneAusgangsrechnungen();

        assertThat(auswahl).hasSize(100);
        assertThat(auswahl).extracting(KassenbuchungDto.OffeneRechnung::getId)
                .containsExactlyElementsOf(IntStream.iterate(102, i -> i - 1).limit(100)
                        .mapToObj(i -> (long) i).toList());
    }

    @ParameterizedTest
    @CsvSource({"KASSE_EINNAHME,KASSE_EINNAHME", "KASSE_AUSGABE,KASSE_AUSGABE",
            "PRIVATEINLAGE,PRIVATEINLAGE", "PRIVATENTNAHME,PRIVATENTNAHME"})
    void legacyAdapterErhaeltKontoZahlungsartUndTexte(String altKategorie, BelegKategorie kategorie) throws Exception {
        var req = new org.example.kalkulationsprogramm.dto.BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie(altKategorie); req.setBelegDatum(LocalDate.of(2026, 9, 14));
        req.setBetragBrutto(BigDecimal.TEN); req.setSachkontoId(1890L);
        req.setZahlungsart("Bargeld"); req.setBeschreibung("Musterbuchung"); req.setNotiz("Musternotiz");
        when(konten.findById(1890L)).thenReturn(Optional.of(konto("1890")));
        when(pdf.erzeugeEigenbeleg(any())).thenReturn(pdfDatei());
        speichern();
        Beleg b = service.bucheUmbuchung(req, ersteller);
        assertThat(b.getBelegKategorie()).isEqualTo(kategorie);
        assertThat(b.getSachkonto().getNummer()).isEqualTo("1890");
        assertThat(b.getZahlungsart()).isEqualTo("Bargeld");
        assertThat(b.getBeschreibung()).isEqualTo("Musterbuchung");
        assertThat(b.getNotiz()).isEqualTo("Musternotiz");
        assertThat(b.getDateiHash()).hasSize(64);
        verify(audit).protokolliereErfassung(b, ersteller, null);
    }

    @Test void legacyBuchungOhneKontoBleibtMoeglich() throws Exception {
        var req = new org.example.kalkulationsprogramm.dto.BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie("KASSE_EINNAHME"); req.setBelegDatum(LocalDate.of(2026, 9, 14));
        req.setBetragBrutto(BigDecimal.TEN);
        when(pdf.erzeugeEigenbeleg(any())).thenReturn(pdfDatei());
        speichern();
        Beleg b = service.bucheUmbuchung(req, ersteller);
        assertThat(b.getSachkonto()).isNull();
        assertThat(b.getZahlungsart()).isNull();
        verifyNoInteractions(konten, einstellungen);
    }

    @Test void fehlenderPdfFingerabdruckVerhindertSpeicherungUndLoeschtDatei() throws Exception {
        when(konten.findByNummer("1200")).thenReturn(Optional.of(konto("1200")));
        pdfDatei();
        when(pdf.erzeugeEigenbeleg(any())).thenReturn(new BelegPdfService.ErzeugtesPdf("test.pdf", "test.pdf", "application/pdf", null));
        assertThatThrownBy(() -> service.buche(request("VON_BANK_GEHOLT"), null, ersteller))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Fingerabdruck");
        assertThat(upload.resolve("belege/test.pdf")).doesNotExist();
        verifyNoInteractions(belege, audit);
    }

    @Test void teilweiserUploadWirdBeiSchreibfehlerGeloescht() throws Exception {
        var req = request("GELD_AUSGEGEBEN"); req.setSachkontoId(4930L);
        when(konten.findById(4930L)).thenReturn(Optional.of(konto("4930")));
        var datei = new MockMultipartFile("datei", "test.pdf", "application/pdf", new byte[]{1}) {
            @Override public void transferTo(Path ziel) throws java.io.IOException {
                Files.writeString(ziel, "unvollständig");
                throw new java.io.IOException("Test: Speicher voll");
            }
        };
        assertThatThrownBy(() -> service.buche(req, datei, ersteller)).isInstanceOf(java.io.UncheckedIOException.class);
        try (var dateien = Files.list(upload.resolve("belege"))) { assertThat(dateien).isEmpty(); }
        verifyNoInteractions(belege, audit);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "0.001", "10000000000000"})
    void ungueltigeBetraegeErreichenDieBuchhaltungNicht(String betrag) {
        var req = request("VON_BANK_GEHOLT"); req.setBetragBrutto(new BigDecimal(betrag));
        assertThatThrownBy(() -> service.buche(req, null, ersteller)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(schutz, saldo, belege, pdf);
    }

    @ParameterizedTest @ValueSource(strings = {"art", "datum", "gegenpartei", "beschreibung", "notiz", "grund", "steuersatz", "kostenstelle", "rechnung", "konto"})
    void ungueltigeFelderWerdenVorDenBuchhaltungsregelnAbgewiesen(String feld) {
        var req = request("GELD_EINGENOMMEN");
        switch (feld) {
            case "art" -> req.setArt("UNBEKANNT");
            case "datum" -> req.setBelegDatum(null);
            case "gegenpartei" -> req.setGegenpartei("a".repeat(121));
            case "beschreibung" -> req.setBeschreibung("a".repeat(501));
            case "notiz" -> req.setNotiz("a".repeat(1001));
            case "grund" -> req.setGrundOhneBeleg("a".repeat(256));
            case "steuersatz" -> req.setMwstSatz(new BigDecimal("20"));
            case "kostenstelle" -> req.setKostenstelleId(-1L);
            case "rechnung" -> req.setAusgangsrechnungId(0L);
            case "konto" -> req.setSachkontoId(-1L);
            default -> throw new AssertionError(feld);
        }
        assertThatThrownBy(() -> service.buche(req, null, ersteller)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(schutz, saldo, belege, pdf);
    }

    @Test void grundIstBeiErsatzbelegPflicht() {
        var req = request("GELD_AUSGEGEBEN"); req.setKeinBelegVorhanden(true); req.setGrundOhneBeleg(" ");
        assertThatThrownBy(() -> service.buche(req, null, ersteller)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warum kein Beleg");
        verifyNoInteractions(schutz, saldo, belege, pdf);
    }

    @Test void unbekannteRechnungIstEingabefehler() {
        var req = request("GELD_EINGENOMMEN"); req.setAusgangsrechnungId(Long.MAX_VALUE);
        when(konten.findByNummer("8400")).thenReturn(Optional.of(konto("8400")));
        assertThatThrownBy(() -> service.buche(req, null, ersteller)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kundenrechnung wurde nicht gefunden");
        verifyNoInteractions(schutz, saldo, belege, pdf);
    }

    @Test void normalesProjektdokumentBehaeltBisherigeFehlermeldung() {
        var req = request("GELD_EINGENOMMEN"); req.setAusgangsrechnungId(7L);
        when(konten.findByNummer("8400")).thenReturn(Optional.of(konto("8400")));
        when(dokumente.existsById(7L)).thenReturn(true);
        assertThatThrownBy(() -> service.buche(req, null, ersteller))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Bitte eine Kundenrechnung wählen");
        verifyNoInteractions(schutz, saldo, belege, pdf, audit);
    }

    @Test void sqlUndHtmlBleibenDatenInAllenTextfeldern() throws Exception {
        var req = request("GELD_AUSGEGEBEN"); req.setSachkontoId(4930L); req.setKeinBelegVorhanden(true);
        String text = "'; DROP TABLE x; -- <script>alert(1)</script>";
        req.setGegenpartei(text); req.setBeschreibung(text); req.setNotiz(text); req.setGrundOhneBeleg(text);
        when(konten.findById(4930L)).thenReturn(Optional.of(konto("4930")));
        when(pdf.erzeugeEigenbeleg(any())).thenReturn(pdfDatei());
        speichern();
        Beleg b = service.buche(req, null, ersteller);
        assertThat(b.getGegenpartei()).isEqualTo(text);
        assertThat(b.getBeschreibung()).isEqualTo(text);
        assertThat(b.getNotiz()).isEqualTo(text);
        var daten = ArgumentCaptor.forClass(BelegPdfService.EigenbelegDaten.class);
        verify(pdf).erzeugeEigenbeleg(daten.capture());
        assertThat(daten.getValue().grund()).isEqualTo(text);
    }

    @Test void httpAltUndNeuVerwendenEchteBuchungsregeln() throws Exception {
        BelegService belegService = mock(BelegService.class);
        when(belegService.findCaller(any(), any())).thenReturn(ersteller);
        when(belegService.darfScannen(ersteller)).thenReturn(true);
        when(belegService.toDto(any())).thenAnswer(inv -> {
            Beleg b = inv.getArgument(0);
            return org.example.kalkulationsprogramm.dto.BelegDto.Response.builder().id(b.getId())
                    .betragBrutto(b.getBetragBrutto()).zahlungsart(b.getZahlungsart())
                    .beschreibung(b.getBeschreibung()).build();
        });
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new org.example.kalkulationsprogramm.controller.KassenbuchungController(belegService, service),
                new org.example.kalkulationsprogramm.controller.BelegController(belegService, mock(MwstRechnerService.class), service)).build();
        when(konten.findByNummer("1200")).thenReturn(Optional.of(konto("1200")));
        when(konten.findById(1890L)).thenReturn(Optional.of(konto("1890")));
        when(pdf.erzeugeEigenbeleg(any())).thenReturn(pdfDatei());
        speichern();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/buchhaltung/kassenbuch/buchungen")
                .file(new MockMultipartFile("daten", "", "application/json", "{\"art\":\"VON_BANK_GEHOLT\",\"belegDatum\":\"2026-09-14\",\"betragBrutto\":119}".getBytes())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.zahlungsart").value("Bar"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/buchhaltung/umbuchungen")
                .contentType("application/json")
                .content("{\"belegKategorie\":\"KASSE_EINNAHME\",\"belegDatum\":\"2026-09-14\",\"betragBrutto\":119,\"sachkontoId\":1890,\"zahlungsart\":\"Bargeld\",\"beschreibung\":\"Muster\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.zahlungsart").value("Bargeld"));
        var gespeichert = ArgumentCaptor.forClass(Beleg.class);
        verify(belege, times(2)).save(gespeichert.capture());
        assertThat(gespeichert.getAllValues().get(0).getSachkonto().getNummer()).isEqualTo("1200");
        assertThat(gespeichert.getAllValues().get(1).getSachkonto().getNummer()).isEqualTo("1890");
    }

}

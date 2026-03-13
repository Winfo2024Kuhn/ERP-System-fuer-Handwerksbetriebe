package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.Dokument;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Mahnstufe;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.exception.ForbiddenException;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockMultipartFile;

class DateiSpeicherServiceTest {

    private DateiSpeicherService createService(Path localRoot, String networkUrl) throws IOException {
        return createService(localRoot, networkUrl, mock(ProjektDokumentRepository.class));
    }

    private DateiSpeicherService createService(Path localRoot, String networkUrl, ProjektDokumentRepository dokRepo)
            throws IOException {
        ProjektRepository projRepo = mock(ProjektRepository.class);
        AnfrageDokumentRepository anfrageDokRepo = mock(AnfrageDokumentRepository.class);
        AnfrageRepository anfrageRepo = mock(AnfrageRepository.class);
        ProduktkategorieRepository prodRepo = mock(ProduktkategorieRepository.class);
        KundeRepository kundeRepo = mock(KundeRepository.class);
        ZugferdExtractorService zugferd = mock(ZugferdExtractorService.class);
        ProduktkategorieMapper mapper = mock(ProduktkategorieMapper.class);
        String path = localRoot.toString();
        return new DateiSpeicherService(path, path, path, path, path, networkUrl, "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper);
    }

    private static class ServiceSetup {
        final DateiSpeicherService service;
        final ProjektDokumentRepository dokumentRepository;
        final ProjektRepository projektRepository;

        ServiceSetup(DateiSpeicherService service,
                ProjektDokumentRepository dokumentRepository,
                ProjektRepository projektRepository) {
            this.service = service;
            this.dokumentRepository = dokumentRepository;
            this.projektRepository = projektRepository;
        }
    }

    private ServiceSetup createMahnungService() throws IOException {
        Path docRoot = Files.createTempDirectory("docRoot");
        Path offerRoot = Files.createTempDirectory("offerRoot");
        Path hicadRoot = Files.createTempDirectory("hicadRoot");
        Path iconRoot = Files.createTempDirectory("iconRoot");
        String networkUrl = "\\\\server\\share";

        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        ProjektRepository projRepo = mock(ProjektRepository.class);
        AnfrageDokumentRepository anfrageDokRepo = mock(AnfrageDokumentRepository.class);
        AnfrageRepository anfrageRepo = mock(AnfrageRepository.class);
        ProduktkategorieRepository prodRepo = mock(ProduktkategorieRepository.class);
        KundeRepository kundeRepo = mock(KundeRepository.class);
        ZugferdExtractorService zugferd = mock(ZugferdExtractorService.class);
        ProduktkategorieMapper mapper = mock(ProduktkategorieMapper.class);

        DateiSpeicherService service = new DateiSpeicherService(
                docRoot.toString(), offerRoot.toString(), hicadRoot.toString(), iconRoot.toString(),
                iconRoot.toString(),
                networkUrl, "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper);
        return new ServiceSetup(service, dokRepo, projRepo);
    }

    @Test
    void liefertUncPfadInnerhalbRoot() throws IOException {
        Path temp = Files.createTempDirectory("shareRoot");
        String networkUrl = "\\\\server\\share";
        DateiSpeicherService service = createService(temp, networkUrl);
        String result = service.holeNetzwerkPfad("test.txt");
        String expected = networkUrl + "\\test.txt";
        assertEquals(expected, result);
    }

    @Test
    void wirftExceptionBeiPfadTraversal() throws IOException {
        Path temp = Files.createTempDirectory("shareRoot");
        String networkUrl = "\\\\server\\share";
        DateiSpeicherService service = createService(temp, networkUrl);
        assertThrows(ForbiddenException.class, () -> service.holeNetzwerkPfad("../geheim.txt"));
    }

    @Test
    void findetDokumentUeberOriginalNamenIgnoreCase() throws IOException {
        Path temp = Files.createTempDirectory("shareRoot");
        String networkUrl = "\\\\server\\share";
        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        ProjektDokument doc = new ProjektDokument();
        when(dokRepo.findByGespeicherterDateinameIgnoreCase("FILE.SZA")).thenReturn(Optional.empty());
        when(dokRepo.findByOriginalDateinameIgnoreCase("FILE.SZA")).thenReturn(Optional.of(doc));
        DateiSpeicherService service = createService(temp, networkUrl, dokRepo);
        Dokument result = service.ladeDokumentMetadaten("FILE.SZA");
        assertSame(doc, result);
    }

    @Test
    void wirftNotFoundBeiUnbekanntemDokument() throws IOException {
        Path temp = Files.createTempDirectory("shareRoot");
        String networkUrl = "\\\\server\\share";
        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        when(dokRepo.findByGespeicherterDateinameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(dokRepo.findByOriginalDateinameIgnoreCase(anyString())).thenReturn(Optional.empty());
        DateiSpeicherService service = createService(temp, networkUrl, dokRepo);
        assertThrows(NotFoundException.class, () -> service.ladeDokumentMetadaten("fehlend.txt"));
    }

    @Test
    void verschiebtAnfragesDateiAlsBasisDokumentWennKeinGeschaeftsDokument() throws IOException {
        Path temp = Files.createTempDirectory("shareRoot");
        String networkUrl = "\\\\server\\share";
        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        ProjektRepository projRepo = mock(ProjektRepository.class);
        AnfrageDokumentRepository anfrageDokRepo = mock(AnfrageDokumentRepository.class);
        AnfrageRepository anfrageRepo = mock(AnfrageRepository.class);
        ProduktkategorieRepository prodRepo = mock(ProduktkategorieRepository.class);
        KundeRepository kundeRepo = mock(KundeRepository.class);
        ZugferdExtractorService zugferd = mock(ZugferdExtractorService.class);
        ProduktkategorieMapper mapper = mock(ProduktkategorieMapper.class);
        String path = temp.toString();
        DateiSpeicherService service = new DateiSpeicherService(path, path, path, path, path, networkUrl, "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper);

        Projekt projekt = new Projekt();
        projekt.setId(1L);
        when(projRepo.findById(1L)).thenReturn(Optional.of(projekt));
        when(dokRepo.findByProjektId(1L)).thenReturn(java.util.Collections.emptyList());
        when(projRepo.save(any(Projekt.class))).thenReturn(projekt);

        AnfrageDokument anfrageDok = new AnfrageDokument();
        anfrageDok.setOriginalDateiname("test.pdf");
        anfrageDok.setGespeicherterDateiname("abc.pdf");

        final ProjektDokument[] saved = new ProjektDokument[1];
        when(dokRepo.save(any(ProjektDokument.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        service.verschiebeAnfragesDatei(anfrageDok, projekt);

        assertNotNull(saved[0]);
        assertFalse(saved[0] instanceof org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument);
    }

    @Test
    void speichertHiCADDateiImSpezialVerzeichnis() throws IOException {
        Path docRoot = Files.createTempDirectory("docRoot");
        Path offerRoot = Files.createTempDirectory("offerRoot");
        Path hicadRoot = Files.createTempDirectory("hicadRoot");
        Path iconRoot = Files.createTempDirectory("iconRoot");
        String networkUrl = "\\\\server\\share";

        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        ProjektRepository projRepo = mock(ProjektRepository.class);
        AnfrageDokumentRepository anfrageDokRepo = mock(AnfrageDokumentRepository.class);
        AnfrageRepository anfrageRepo = mock(AnfrageRepository.class);
        ProduktkategorieRepository prodRepo = mock(ProduktkategorieRepository.class);
        KundeRepository kundeRepo = mock(KundeRepository.class);
        ZugferdExtractorService zugferd = mock(ZugferdExtractorService.class);
        ProduktkategorieMapper mapper = mock(ProduktkategorieMapper.class);

        Anfrage anfrage = new Anfrage();
        when(anfrageRepo.findById(1L)).thenReturn(Optional.of(anfrage));

        final AnfrageDokument[] saved = new AnfrageDokument[1];
        when(anfrageDokRepo.save(any(AnfrageDokument.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        DateiSpeicherService service = new DateiSpeicherService(
                docRoot.toString(), offerRoot.toString(), hicadRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), networkUrl, "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper);

        MockMultipartFile file = new MockMultipartFile(
                "datei", "zeichnung.sza", "application/octet-stream", "data".getBytes());

        service.speichereAnfragesDatei(file, 1L, DokumentGruppe.DIVERSE_DOKUMENTE);

        assertNotNull(saved[0]);
        assertTrue(Files.exists(hicadRoot.resolve(saved[0].getGespeicherterDateiname())));
        assertFalse(Files.exists(docRoot.resolve(saved[0].getGespeicherterDateiname())));
        assertFalse(Files.exists(offerRoot.resolve(saved[0].getGespeicherterDateiname())));
    }

    @Test
    void speichertNormaleAnfragesDateiImOfferVerzeichnis() throws IOException {
        Path docRoot = Files.createTempDirectory("docRoot");
        Path offerRoot = Files.createTempDirectory("offerRoot");
        Path hicadRoot = Files.createTempDirectory("hicadRoot");
        Path iconRoot = Files.createTempDirectory("iconRoot");
        String networkUrl = "\\\\server\\share";

        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        ProjektRepository projRepo = mock(ProjektRepository.class);
        AnfrageDokumentRepository anfrageDokRepo = mock(AnfrageDokumentRepository.class);
        AnfrageRepository anfrageRepo = mock(AnfrageRepository.class);
        ProduktkategorieRepository prodRepo = mock(ProduktkategorieRepository.class);
        KundeRepository kundeRepo = mock(KundeRepository.class);
        ZugferdExtractorService zugferd = mock(ZugferdExtractorService.class);
        ProduktkategorieMapper mapper = mock(ProduktkategorieMapper.class);

        Anfrage anfrage = new Anfrage();
        when(anfrageRepo.findById(1L)).thenReturn(Optional.of(anfrage));

        final AnfrageDokument[] saved = new AnfrageDokument[1];
        when(anfrageDokRepo.save(any(AnfrageDokument.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        DateiSpeicherService service = new DateiSpeicherService(
                docRoot.toString(), offerRoot.toString(), hicadRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), networkUrl, "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper);

        MockMultipartFile file = new MockMultipartFile(
                "datei", "anfrage.pdf", "application/pdf", "data".getBytes());

        service.speichereAnfragesDatei(file, 1L, DokumentGruppe.DIVERSE_DOKUMENTE);

        assertNotNull(saved[0]);
        assertTrue(Files.exists(offerRoot.resolve(saved[0].getGespeicherterDateiname())));
        assertFalse(Files.exists(docRoot.resolve(saved[0].getGespeicherterDateiname())));
        assertFalse(Files.exists(hicadRoot.resolve(saved[0].getGespeicherterDateiname())));
    }

    @Test
    void speichertExcelDateiImSpezialVerzeichnis_Anfrage() throws IOException {
        Path docRoot = Files.createTempDirectory("docRoot");
        Path offerRoot = Files.createTempDirectory("offerRoot");
        Path hicadRoot = Files.createTempDirectory("hicadRoot");
        Path iconRoot = Files.createTempDirectory("iconRoot");
        String networkUrl = "\\\\server\\share";

        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        ProjektRepository projRepo = mock(ProjektRepository.class);
        AnfrageDokumentRepository anfrageDokRepo = mock(AnfrageDokumentRepository.class);
        AnfrageRepository anfrageRepo = mock(AnfrageRepository.class);
        ProduktkategorieRepository prodRepo = mock(ProduktkategorieRepository.class);
        KundeRepository kundeRepo = mock(KundeRepository.class);
        ZugferdExtractorService zugferd = mock(ZugferdExtractorService.class);
        ProduktkategorieMapper mapper = mock(ProduktkategorieMapper.class);

        Anfrage anfrage = new Anfrage();
        when(anfrageRepo.findById(1L)).thenReturn(java.util.Optional.of(anfrage));

        final AnfrageDokument[] saved = new AnfrageDokument[1];
        when(anfrageDokRepo.save(any(AnfrageDokument.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        DateiSpeicherService service = new DateiSpeicherService(
                docRoot.toString(), offerRoot.toString(), hicadRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), networkUrl, "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper);

        MockMultipartFile file = new MockMultipartFile(
                "datei", "tabelle.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "data".getBytes());

        service.speichereAnfragesDatei(file, 1L, DokumentGruppe.DIVERSE_DOKUMENTE);

        assertNotNull(saved[0]);
        assertTrue(Files.exists(hicadRoot.resolve(saved[0].getGespeicherterDateiname())));
        assertFalse(Files.exists(docRoot.resolve(saved[0].getGespeicherterDateiname())));
        assertFalse(Files.exists(offerRoot.resolve(saved[0].getGespeicherterDateiname())));
    }

    @Test
    void speichertZugferdAusgangsrechnungAlsGeschaeftsdokument() throws IOException {
        Path docRoot = Files.createTempDirectory("docRoot");
        Path offerRoot = Files.createTempDirectory("offerRoot");
        Path hicadRoot = Files.createTempDirectory("hicadRoot");
        Path iconRoot = Files.createTempDirectory("iconRoot");
        String networkUrl = "\\\\server\\share";

        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        ProjektRepository projRepo = mock(ProjektRepository.class);
        AnfrageDokumentRepository anfrageDokRepo = mock(AnfrageDokumentRepository.class);
        AnfrageRepository anfrageRepo = mock(AnfrageRepository.class);
        ProduktkategorieRepository prodRepo = mock(ProduktkategorieRepository.class);
        KundeRepository kundeRepo = mock(KundeRepository.class);
        ZugferdExtractorService zugferd = mock(ZugferdExtractorService.class);
        ProduktkategorieMapper mapper = mock(ProduktkategorieMapper.class);

        Projekt projekt = new Projekt();
        projekt.setId(1L);
        when(projRepo.findById(1L)).thenReturn(Optional.of(projekt));
        when(dokRepo.findByProjektId(1L)).thenReturn(List.of());

        ZugferdDaten daten = new ZugferdDaten();
        daten.setRechnungsnummer("2024/03/12345");
        daten.setGeschaeftsdokumentart("Rechnung");
        daten.setBetrag(BigDecimal.TEN);
        when(zugferd.extract(anyString(), anyString())).thenReturn(daten);

        final ProjektDokument[] saved = new ProjektDokument[1];
        when(dokRepo.save(any(ProjektDokument.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        DateiSpeicherService service = new DateiSpeicherService(
                docRoot.toString(), offerRoot.toString(), hicadRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), networkUrl, "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper);

        MockMultipartFile file = new MockMultipartFile(
                "datei", "rechnung.pdf", "application/pdf", "data".getBytes());

        service.speichereDatei(file, 1L, DokumentGruppe.GESCHAEFTSDOKUMENTE);

        assertNotNull(saved[0]);
        assertTrue(saved[0] instanceof ProjektGeschaeftsdokument);
        ProjektGeschaeftsdokument geschaeftsdokument = (ProjektGeschaeftsdokument) saved[0];
        assertEquals("2024/03/12345", geschaeftsdokument.getDokumentid());
        assertEquals(BigDecimal.TEN, geschaeftsdokument.getBruttoBetrag());
    }

    @Test
    void speichertZugferdEingangsrechnungAlsNormalesDokument() throws IOException {
        Path docRoot = Files.createTempDirectory("docRoot");
        Path offerRoot = Files.createTempDirectory("offerRoot");
        Path hicadRoot = Files.createTempDirectory("hicadRoot");
        Path iconRoot = Files.createTempDirectory("iconRoot");
        String networkUrl = "\\\\server\\share";

        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        ProjektRepository projRepo = mock(ProjektRepository.class);
        AnfrageDokumentRepository anfrageDokRepo = mock(AnfrageDokumentRepository.class);
        AnfrageRepository anfrageRepo = mock(AnfrageRepository.class);
        ProduktkategorieRepository prodRepo = mock(ProduktkategorieRepository.class);
        KundeRepository kundeRepo = mock(KundeRepository.class);
        ZugferdExtractorService zugferd = mock(ZugferdExtractorService.class);
        ProduktkategorieMapper mapper = mock(ProduktkategorieMapper.class);

        Projekt projekt = new Projekt();
        projekt.setId(1L);
        when(projRepo.findById(1L)).thenReturn(Optional.of(projekt));
        when(dokRepo.findByProjektId(1L)).thenReturn(List.of());

        ZugferdDaten daten = new ZugferdDaten();
        daten.setRechnungsnummer("EINGANG-123");
        daten.setGeschaeftsdokumentart("Rechnung");
        daten.setBetrag(BigDecimal.valueOf(99));
        when(zugferd.extract(anyString(), anyString())).thenReturn(daten);

        final ProjektDokument[] saved = new ProjektDokument[1];
        when(dokRepo.save(any(ProjektDokument.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        DateiSpeicherService service = new DateiSpeicherService(
                docRoot.toString(), offerRoot.toString(), hicadRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), networkUrl, "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper);

        MockMultipartFile file = new MockMultipartFile(
                "datei", "eingang.pdf", "application/pdf", "data".getBytes());

        service.speichereDatei(file, 1L, DokumentGruppe.GESCHAEFTSDOKUMENTE);

        assertNotNull(saved[0]);
        assertFalse(saved[0] instanceof ProjektGeschaeftsdokument);
        assertEquals(ProjektDokument.class, saved[0].getClass());
    }

    @Test
    void speichertExcelDateiImSpezialVerzeichnis_Projekt() throws IOException {
        Path docRoot = Files.createTempDirectory("docRoot");
        Path offerRoot = Files.createTempDirectory("offerRoot");
        Path hicadRoot = Files.createTempDirectory("hicadRoot");
        Path iconRoot = Files.createTempDirectory("iconRoot");
        String networkUrl = "\\\\server\\share";

        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        ProjektRepository projRepo = mock(ProjektRepository.class);
        AnfrageDokumentRepository anfrageDokRepo = mock(AnfrageDokumentRepository.class);
        AnfrageRepository anfrageRepo = mock(AnfrageRepository.class);
        ProduktkategorieRepository prodRepo = mock(ProduktkategorieRepository.class);
        KundeRepository kundeRepo = mock(KundeRepository.class);
        ZugferdExtractorService zugferd = mock(ZugferdExtractorService.class);
        ProduktkategorieMapper mapper = mock(ProduktkategorieMapper.class);

        Projekt projekt = new Projekt();
        projekt.setId(1L);
        when(projRepo.findById(1L)).thenReturn(java.util.Optional.of(projekt));

        final ProjektDokument[] saved = new ProjektDokument[1];
        when(dokRepo.save(any(ProjektDokument.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        DateiSpeicherService service = new DateiSpeicherService(
                docRoot.toString(), offerRoot.toString(), hicadRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), networkUrl, "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper);

        MockMultipartFile file = new MockMultipartFile(
                "datei", "mappe.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "data".getBytes());

        service.speichereDatei(file, 1L, DokumentGruppe.DIVERSE_DOKUMENTE);

        assertNotNull(saved[0]);
        assertTrue(Files.exists(hicadRoot.resolve(saved[0].getGespeicherterDateiname())));
        assertFalse(Files.exists(docRoot.resolve(saved[0].getGespeicherterDateiname())));
    }

    @Test
    void speichertMahnungMitReferenzUebernimmtDaten() throws IOException {
        ServiceSetup setup = createMahnungService();
        Projekt projekt = new Projekt();
        projekt.setId(1L);
        when(setup.projektRepository.findById(1L)).thenReturn(Optional.of(projekt));

        ProjektGeschaeftsdokument referenz = new ProjektGeschaeftsdokument();
        referenz.setId(42L);
        referenz.setProjekt(projekt);
        referenz.setDokumentid("RE-2024-001");
        referenz.setGeschaeftsdokumentart("Rechnung");
        referenz.setBruttoBetrag(BigDecimal.valueOf(199.99));
        referenz.setRechnungsdatum(LocalDate.of(2024, 1, 15));

        when(setup.dokumentRepository.findById(42L)).thenReturn(Optional.of(referenz));
        when(setup.dokumentRepository.findByProjektId(1L)).thenReturn(List.of(referenz));
        when(setup.dokumentRepository.save(any(ProjektDokument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Path zugferdTmp = Files.createTempFile("zugferd-mahnung", ".pdf");
        Files.writeString(zugferdTmp, "dummy");

        ZugferdDaten daten = new ZugferdDaten();
        daten.setGeschaeftsdokumentart("Mahnung");
        daten.setReferenzDokumentId(42L);
        daten.setMahnstufe("ERSTE_MAHNUNG");
        daten.setFaelligkeitsdatum(LocalDate.of(2024, 2, 20));
        daten.setRechnungsdatum(LocalDate.of(2024, 2, 1));

        ProjektGeschaeftsdokument gespeichert = setup.service.speichereZugferdDatei(zugferdTmp, "mahnung.pdf", 1L,
                daten);

        assertNotNull(gespeichert);
        assertSame(referenz, gespeichert.getReferenzDokument());
        assertEquals("RE-2024-001", gespeichert.getDokumentid());
        assertEquals(LocalDate.of(2024, 2, 1), gespeichert.getRechnungsdatum());
        assertEquals(LocalDate.of(2024, 2, 20), gespeichert.getFaelligkeitsdatum());
        assertEquals(BigDecimal.valueOf(199.99), gespeichert.getBruttoBetrag());
        assertEquals(Mahnstufe.ERSTE_MAHNUNG, gespeichert.getMahnstufe());
        assertFalse(gespeichert.isBezahlt());
    }

    @Test
    void speichertMahnungMitUngueltigerStufeSetztStandard() throws IOException {
        ServiceSetup setup = createMahnungService();
        Projekt projekt = new Projekt();
        projekt.setId(2L);
        when(setup.projektRepository.findById(2L)).thenReturn(Optional.of(projekt));

        ProjektGeschaeftsdokument referenz = new ProjektGeschaeftsdokument();
        referenz.setId(7L);
        referenz.setProjekt(projekt);
        referenz.setDokumentid("RE-77");
        referenz.setGeschaeftsdokumentart("Rechnung");
        referenz.setBruttoBetrag(BigDecimal.TEN);

        when(setup.dokumentRepository.findById(7L)).thenReturn(Optional.of(referenz));
        when(setup.dokumentRepository.findByProjektId(2L)).thenReturn(List.of(referenz));
        when(setup.dokumentRepository.save(any(ProjektDokument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Path tmp = Files.createTempFile("zugferd-mahnung", ".pdf");
        Files.writeString(tmp, "dummy");

        ZugferdDaten daten = new ZugferdDaten();
        daten.setGeschaeftsdokumentart("Mahnung");
        daten.setReferenzDokumentId(7L);
        daten.setMahnstufe(""); // ungültig -> Standard
        daten.setFaelligkeitsdatum(LocalDate.of(2024, 3, 5));

        ProjektGeschaeftsdokument gespeichert = setup.service.speichereZugferdDatei(tmp, "mahnung.pdf", 2L, daten);

        assertEquals(Mahnstufe.ZAHLUNGSERINNERUNG, gespeichert.getMahnstufe());
    }

    @Test
    void speichertMahnungOhneFaelligkeitWirftException() throws IOException {
        ServiceSetup setup = createMahnungService();
        Projekt projekt = new Projekt();
        projekt.setId(3L);
        when(setup.projektRepository.findById(3L)).thenReturn(Optional.of(projekt));

        ProjektGeschaeftsdokument referenz = new ProjektGeschaeftsdokument();
        referenz.setId(11L);
        referenz.setProjekt(projekt);
        referenz.setGeschaeftsdokumentart("Rechnung");
        referenz.setDokumentid("RE-11");
        referenz.setBruttoBetrag(BigDecimal.ONE);

        when(setup.dokumentRepository.findById(11L)).thenReturn(Optional.of(referenz));
        when(setup.dokumentRepository.findByProjektId(3L)).thenReturn(List.of(referenz));

        Path tmp = Files.createTempFile("zugferd-mahnung", ".pdf");
        Files.writeString(tmp, "dummy");

        ZugferdDaten daten = new ZugferdDaten();
        daten.setGeschaeftsdokumentart("Mahnung");
        daten.setReferenzDokumentId(11L);
        daten.setMahnstufe("ERSTE_MAHNUNG");

        assertThrows(IllegalArgumentException.class,
                () -> setup.service.speichereZugferdDatei(tmp, "mahnung.pdf", 3L, daten));
    }

    @Test
    void speichertMahnungMitNichtRechnungsReferenzWirftException() throws IOException {
        ServiceSetup setup = createMahnungService();
        Projekt projekt = new Projekt();
        projekt.setId(4L);
        when(setup.projektRepository.findById(4L)).thenReturn(Optional.of(projekt));

        ProjektGeschaeftsdokument referenz = new ProjektGeschaeftsdokument();
        referenz.setId(9L);
        referenz.setProjekt(projekt);
        referenz.setGeschaeftsdokumentart("Auftragsbestätigung");
        referenz.setDokumentid("AB-9");

        when(setup.dokumentRepository.findById(9L)).thenReturn(Optional.of(referenz));
        when(setup.dokumentRepository.findByProjektId(4L)).thenReturn(List.of(referenz));

        Path tmp = Files.createTempFile("zugferd-mahnung", ".pdf");
        Files.writeString(tmp, "dummy");

        ZugferdDaten daten = new ZugferdDaten();
        daten.setGeschaeftsdokumentart("Mahnung");
        daten.setReferenzDokumentId(9L);
        daten.setMahnstufe("ERSTE_MAHNUNG");
        daten.setFaelligkeitsdatum(LocalDate.of(2024, 4, 10));

        assertThrows(IllegalArgumentException.class,
                () -> setup.service.speichereZugferdDatei(tmp, "mahnung.pdf", 4L, daten));
    }

    @Test
    void speichertMahnungMitReferenzAusFremdemProjektWirftException() throws IOException {
        ServiceSetup setup = createMahnungService();
        Projekt projekt = new Projekt();
        projekt.setId(5L);
        when(setup.projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

        Projekt anderesProjekt = new Projekt();
        anderesProjekt.setId(99L);

        ProjektGeschaeftsdokument referenz = new ProjektGeschaeftsdokument();
        referenz.setId(12L);
        referenz.setProjekt(anderesProjekt);
        referenz.setGeschaeftsdokumentart("Rechnung");
        referenz.setDokumentid("RE-12");

        when(setup.dokumentRepository.findById(12L)).thenReturn(Optional.of(referenz));
        when(setup.dokumentRepository.findByProjektId(5L)).thenReturn(List.of());

        Path tmp = Files.createTempFile("zugferd-mahnung", ".pdf");
        Files.writeString(tmp, "dummy");

        ZugferdDaten daten = new ZugferdDaten();
        daten.setGeschaeftsdokumentart("Mahnung");
        daten.setReferenzDokumentId(12L);
        daten.setMahnstufe("ERSTE_MAHNUNG");
        daten.setFaelligkeitsdatum(LocalDate.now());

        assertThrows(RuntimeException.class,
                () -> setup.service.speichereZugferdDatei(tmp, "mahnung.pdf", 5L, daten));
    }

    @Test
    void speichertMahnungOhneReferenzWirftException() throws IOException {
        ServiceSetup setup = createMahnungService();
        Projekt projekt = new Projekt();
        projekt.setId(6L);
        when(setup.projektRepository.findById(6L)).thenReturn(Optional.of(projekt));

        Path tmp = Files.createTempFile("zugferd-mahnung", ".pdf");
        Files.writeString(tmp, "dummy");

        ZugferdDaten daten = new ZugferdDaten();
        daten.setGeschaeftsdokumentart("Mahnung");
        daten.setMahnstufe("ERSTE_MAHNUNG");
        daten.setFaelligkeitsdatum(LocalDate.now());

        assertThrows(IllegalArgumentException.class,
                () -> setup.service.speichereZugferdDatei(tmp, "mahnung.pdf", 6L, daten));
    }

    // --- Tests: setzeGeschaeftsdokumentBezahlt + pruefeProjektAbschluss ---

    @Test
    void setzeGeschaeftsdokumentBezahltSetztProjektBezahltWennAllePostenBezahlt() throws IOException {
        ServiceSetup setup = createMahnungService();

        Projekt projekt = new Projekt();
        projekt.setId(10L);
        projekt.setBezahlt(false);
        projekt.setAbgeschlossen(false);

        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setId(100L);
        rechnung.setProjekt(projekt);
        rechnung.setBezahlt(false);

        when(setup.dokumentRepository.findById(100L)).thenReturn(Optional.of(rechnung));
        when(setup.dokumentRepository.save(any(ProjektDokument.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(setup.projektRepository.findById(10L)).thenReturn(Optional.of(projekt));
        // Keine offenen Posten mehr nach dem Bezahlen
        when(setup.dokumentRepository.existsOffenePostenByProjektId(10L)).thenReturn(false);

        setup.service.setzeGeschaeftsdokumentBezahlt(100L, true);

        assertTrue(rechnung.isBezahlt());
        assertTrue(projekt.isBezahlt());
        assertTrue(projekt.isAbgeschlossen());
        verify(setup.projektRepository).save(projekt);
    }

    @Test
    void setzeGeschaeftsdokumentBezahltSetztProjektNichtBezahltWennNochOffenePosten() throws IOException {
        ServiceSetup setup = createMahnungService();

        Projekt projekt = new Projekt();
        projekt.setId(11L);
        projekt.setBezahlt(true);
        projekt.setAbgeschlossen(true);

        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setId(101L);
        rechnung.setProjekt(projekt);
        rechnung.setBezahlt(true);

        when(setup.dokumentRepository.findById(101L)).thenReturn(Optional.of(rechnung));
        when(setup.dokumentRepository.save(any(ProjektDokument.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(setup.projektRepository.findById(11L)).thenReturn(Optional.of(projekt));
        // Es gibt noch offene Posten
        when(setup.dokumentRepository.existsOffenePostenByProjektId(11L)).thenReturn(true);

        setup.service.setzeGeschaeftsdokumentBezahlt(101L, false);

        assertFalse(rechnung.isBezahlt());
        assertFalse(projekt.isBezahlt());
        assertFalse(projekt.isAbgeschlossen());
        verify(setup.projektRepository).save(projekt);
    }

    @Test
    void setzeGeschaeftsdokumentBezahltOeffnetProjektWennPostenWiederOffen() throws IOException {
        ServiceSetup setup = createMahnungService();

        Projekt projekt = new Projekt();
        projekt.setId(12L);
        projekt.setBezahlt(true);
        projekt.setAbgeschlossen(true);

        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setId(102L);
        rechnung.setProjekt(projekt);
        rechnung.setBezahlt(true);

        when(setup.dokumentRepository.findById(102L)).thenReturn(Optional.of(rechnung));
        when(setup.dokumentRepository.save(any(ProjektDokument.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(setup.projektRepository.findById(12L)).thenReturn(Optional.of(projekt));
        // Rechnung wurde als unbezahlt markiert → es gibt offene Posten
        when(setup.dokumentRepository.existsOffenePostenByProjektId(12L)).thenReturn(true);

        setup.service.setzeGeschaeftsdokumentBezahlt(102L, false);

        assertFalse(projekt.isBezahlt());
        assertFalse(projekt.isAbgeschlossen());
    }

    @Test
    void setzeGeschaeftsdokumentBezahltWirftExceptionFuerNichtGeschaeftsdokument() throws IOException {
        ServiceSetup setup = createMahnungService();

        ProjektDokument normalDoc = new ProjektDokument();
        normalDoc.setId(200L);

        when(setup.dokumentRepository.findById(200L)).thenReturn(Optional.of(normalDoc));

        assertThrows(RuntimeException.class,
                () -> setup.service.setzeGeschaeftsdokumentBezahlt(200L, true));
    }

    @Test
    void setzeGeschaeftsdokumentBezahltWirftExceptionWennDokumentNichtGefunden() throws IOException {
        ServiceSetup setup = createMahnungService();

        when(setup.dokumentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> setup.service.setzeGeschaeftsdokumentBezahlt(999L, true));
    }
}

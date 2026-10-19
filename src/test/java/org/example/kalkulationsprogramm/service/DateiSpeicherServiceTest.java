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
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class DateiSpeicherServiceTest {

    private DateiSpeicherService createService(Path localRoot, String networkUrl) throws IOException {
        return createService(localRoot, networkUrl, mock(ProjektDokumentRepository.class));
    }

    /**
     * Der HiCAD-/Datei-Ordner kommt seit der Ersteinrichtungs-Integration aus den
     * System-Einstellungen (DB-Wert mit Property-Fallback) statt aus @Value-Feldern.
     */
    private SystemSettingsService settingsMock(String hicadPfad, String networkUrl) {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.getDateiOrdnerPfad()).thenReturn(hicadPfad);
        when(settings.getDateiOrdnerNetworkUrl()).thenReturn(networkUrl);
        return settings;
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
        return new DateiSpeicherService(path, path, path, path, "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper,
                settingsMock(path, networkUrl), mock(AusgangsGeschaeftsDokumentService.class));
    }

    /** Wie {@link #createService(Path, String, ProjektDokumentRepository)}, aber mit greifbarer Preis-Automatik. */
    private DateiSpeicherService createServiceMitPreisAutomatik(Path localRoot,
            AusgangsGeschaeftsDokumentService preisAutomatik) throws IOException {
        String path = localRoot.toString();
        return new DateiSpeicherService(path, path, path, path, "",
                mock(ProjektDokumentRepository.class), mock(ProjektRepository.class),
                mock(AnfrageDokumentRepository.class), mock(AnfrageRepository.class),
                mock(ProduktkategorieRepository.class), mock(KundeRepository.class),
                mock(ZugferdExtractorService.class), mock(ProduktkategorieMapper.class),
                settingsMock(path, ""), preisAutomatik);
    }

    @Test
    void aktualisiereProjektFinanzstatusLaesstDenAuftragspreisNeuBerechnen() throws IOException {
        AusgangsGeschaeftsDokumentService preisAutomatik = mock(AusgangsGeschaeftsDokumentService.class);
        DateiSpeicherService service = createServiceMitPreisAutomatik(
                Files.createTempDirectory("finanzstatus"), preisAutomatik);

        service.aktualisiereProjektFinanzstatus(7L);

        verify(preisAutomatik).aktualisiereProjektPreisAusDokumenten(7L);
    }

    @Test
    void aktualisiereProjektFinanzstatusIgnoriertFehlendeProjektId() throws IOException {
        AusgangsGeschaeftsDokumentService preisAutomatik = mock(AusgangsGeschaeftsDokumentService.class);
        DateiSpeicherService service = createServiceMitPreisAutomatik(
                Files.createTempDirectory("finanzstatus-null"), preisAutomatik);

        service.aktualisiereProjektFinanzstatus(null);

        org.mockito.Mockito.verifyNoInteractions(preisAutomatik);
    }

    private static class ServiceSetup {
        final DateiSpeicherService service;
        final ProjektDokumentRepository dokumentRepository;
        final ProjektRepository projektRepository;
        final AusgangsGeschaeftsDokumentService preisAutomatik;
        /** Ablage der Projektdokumente (Temp-Ordner). */
        final Path dokumentenOrdner;

        ServiceSetup(DateiSpeicherService service,
                ProjektDokumentRepository dokumentRepository,
                ProjektRepository projektRepository,
                AusgangsGeschaeftsDokumentService preisAutomatik,
                Path dokumentenOrdner) {
            this.service = service;
            this.dokumentRepository = dokumentRepository;
            this.projektRepository = projektRepository;
            this.preisAutomatik = preisAutomatik;
            this.dokumentenOrdner = dokumentenOrdner;
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
        AusgangsGeschaeftsDokumentService preisAutomatik = mock(AusgangsGeschaeftsDokumentService.class);

        DateiSpeicherService service = new DateiSpeicherService(
                docRoot.toString(), offerRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper,
                settingsMock(hicadRoot.toString(), networkUrl), preisAutomatik);
        return new ServiceSetup(service, dokRepo, projRepo, preisAutomatik, docRoot);
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
        DateiSpeicherService service = new DateiSpeicherService(path, path, path, path, "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper,
                settingsMock(path, networkUrl), mock(AusgangsGeschaeftsDokumentService.class));

        Projekt projekt = new Projekt();
        projekt.setId(1L);
        when(projRepo.findById(1L)).thenReturn(Optional.of(projekt));
        when(dokRepo.findByProjektId(1L)).thenReturn(java.util.Collections.emptyList());
        when(projRepo.save(any(Projekt.class))).thenReturn(projekt);

        AnfrageDokument anfrageDok = new AnfrageDokument();
        anfrageDok.setOriginalDateiname("test.pdf");
        anfrageDok.setGespeicherterDateiname("abc.pdf");
        Mitarbeiter maxMustermann = new Mitarbeiter();
        anfrageDok.setUploadedBy(maxMustermann);

        final ProjektDokument[] saved = new ProjektDokument[1];
        when(dokRepo.save(any(ProjektDokument.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        service.verschiebeAnfragesDatei(anfrageDok, projekt);

        assertNotNull(saved[0]);
        assertFalse(saved[0] instanceof org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument);
        assertSame(maxMustermann, saved[0].getUploadedBy());
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
                docRoot.toString(), offerRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper,
                settingsMock(hicadRoot.toString(), networkUrl), mock(AusgangsGeschaeftsDokumentService.class));

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
                docRoot.toString(), offerRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper,
                settingsMock(hicadRoot.toString(), networkUrl), mock(AusgangsGeschaeftsDokumentService.class));

        MockMultipartFile file = new MockMultipartFile(
                "datei", "anfrage.pdf", "application/pdf", "data".getBytes());

        service.speichereAnfragesDatei(file, 1L, DokumentGruppe.DIVERSE_DOKUMENTE);

        assertNotNull(saved[0]);
        assertTrue(Files.exists(offerRoot.resolve(saved[0].getGespeicherterDateiname())));
        assertFalse(Files.exists(docRoot.resolve(saved[0].getGespeicherterDateiname())));
        assertFalse(Files.exists(hicadRoot.resolve(saved[0].getGespeicherterDateiname())));
    }

    @Test
    void speichertHochladerBeimAnfrageDokument() throws IOException {
        Path offerRoot = Files.createTempDirectory("offerRoot");
        Path iconRoot = Files.createTempDirectory("iconRoot");
        AnfrageDokumentRepository anfrageDokRepo = mock(AnfrageDokumentRepository.class);
        AnfrageRepository anfrageRepo = mock(AnfrageRepository.class);
        when(anfrageRepo.findById(1L)).thenReturn(Optional.of(new Anfrage()));
        when(anfrageDokRepo.save(any(AnfrageDokument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DateiSpeicherService service = new DateiSpeicherService(
                offerRoot.toString(), offerRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), "",
                mock(ProjektDokumentRepository.class), mock(ProjektRepository.class), anfrageDokRepo, anfrageRepo,
                mock(ProduktkategorieRepository.class), mock(KundeRepository.class),
                mock(ZugferdExtractorService.class), mock(ProduktkategorieMapper.class),
                settingsMock(iconRoot.toString(), ""), mock(AusgangsGeschaeftsDokumentService.class));

        Mitarbeiter maxMustermann = new Mitarbeiter();
        maxMustermann.setVorname("Max");
        maxMustermann.setNachname("Mustermann");
        MockMultipartFile file = new MockMultipartFile("datei", "foto.jpg", "image/jpeg", "data".getBytes());

        AnfrageDokument mitHochlader = service.speichereAnfragesDatei(file, 1L, DokumentGruppe.BILDER, maxMustermann);
        AnfrageDokument ohneHochlader = service.speichereAnfragesDatei(file, 1L, DokumentGruppe.BILDER);

        assertSame(maxMustermann, mitHochlader.getUploadedBy());
        assertNull(ohneHochlader.getUploadedBy());
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
                docRoot.toString(), offerRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper,
                settingsMock(hicadRoot.toString(), networkUrl), mock(AusgangsGeschaeftsDokumentService.class));

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
                docRoot.toString(), offerRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper,
                settingsMock(hicadRoot.toString(), networkUrl), mock(AusgangsGeschaeftsDokumentService.class));

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
                docRoot.toString(), offerRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper,
                settingsMock(hicadRoot.toString(), networkUrl), mock(AusgangsGeschaeftsDokumentService.class));

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
                docRoot.toString(), offerRoot.toString(), iconRoot.toString(),
                iconRoot.toString(), "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper,
                settingsMock(hicadRoot.toString(), networkUrl), mock(AusgangsGeschaeftsDokumentService.class));

        MockMultipartFile file = new MockMultipartFile(
                "datei", "mappe.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "data".getBytes());

        service.speichereDatei(file, 1L, DokumentGruppe.DIVERSE_DOKUMENTE);

        assertNotNull(saved[0]);
        assertTrue(Files.exists(hicadRoot.resolve(saved[0].getGespeicherterDateiname())));
        assertFalse(Files.exists(docRoot.resolve(saved[0].getGespeicherterDateiname())));
    }

    @Test
    void hicadPfadWechselWirktBeimNaechstenZugriffOhneNeustart() throws IOException {
        Path docRoot = Files.createTempDirectory("docRoot");
        Path ordnerAlt = Files.createTempDirectory("hicadAlt");
        Path ordnerNeu = Files.createTempDirectory("hicadNeu");
        Path iconRoot = Files.createTempDirectory("iconRoot");

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
        when(dokRepo.save(any(ProjektDokument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Einstellungs-Mock liefert erst den alten, nach "Assistent speichert" den neuen Ordner
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.getDateiOrdnerPfad()).thenReturn(ordnerAlt.toString());
        when(settings.getDateiOrdnerNetworkUrl()).thenReturn("");

        DateiSpeicherService service = new DateiSpeicherService(
                docRoot.toString(), docRoot.toString(), iconRoot.toString(), iconRoot.toString(), "",
                dokRepo, projRepo, anfrageDokRepo, anfrageRepo, prodRepo, kundeRepo, zugferd, mapper,
                settings, mock(AusgangsGeschaeftsDokumentService.class));

        MockMultipartFile datei1 = new MockMultipartFile(
                "datei", "zeichnung.sza", "application/octet-stream", "inhalt".getBytes());
        service.speichereDatei(datei1, 1L, DokumentGruppe.DIVERSE_DOKUMENTE);
        assertTrue(Files.list(ordnerAlt).findAny().isPresent());

        // Einstellungswechsel wie durch den Ersteinrichtungs-Assistenten – KEIN Neustart
        when(settings.getDateiOrdnerPfad()).thenReturn(ordnerNeu.toString());

        MockMultipartFile datei2 = new MockMultipartFile(
                "datei", "zeichnung2.sza", "application/octet-stream", "inhalt2".getBytes());
        service.speichereDatei(datei2, 1L, DokumentGruppe.DIVERSE_DOKUMENTE);
        assertTrue(Files.list(ordnerNeu).findAny().isPresent());
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
    void speichertMahnungMitReferenzAbschlagsrechnung() throws IOException {
        ServiceSetup setup = createMahnungService();
        Projekt projekt = new Projekt();
        projekt.setId(1L);
        when(setup.projektRepository.findById(1L)).thenReturn(Optional.of(projekt));

        ProjektGeschaeftsdokument referenz = new ProjektGeschaeftsdokument();
        referenz.setId(43L);
        referenz.setProjekt(projekt);
        referenz.setDokumentid("AR-2026/08/00036");
        referenz.setGeschaeftsdokumentart("Abschlagsrechnung");
        referenz.setBruttoBetrag(BigDecimal.valueOf(1500.00));
        referenz.setRechnungsdatum(LocalDate.of(2026, 8, 1));

        when(setup.dokumentRepository.findById(43L)).thenReturn(Optional.of(referenz));
        when(setup.dokumentRepository.findByProjektId(1L)).thenReturn(List.of(referenz));
        when(setup.dokumentRepository.save(any(ProjektDokument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Path zugferdTmp = Files.createTempFile("zugferd-mahnung-ar", ".pdf");
        Files.writeString(zugferdTmp, "dummy");

        ZugferdDaten daten = new ZugferdDaten();
        daten.setGeschaeftsdokumentart("Mahnung");
        daten.setReferenzDokumentId(43L);
        daten.setMahnstufe("ZAHLUNGSERINNERUNG");
        daten.setFaelligkeitsdatum(LocalDate.of(2026, 9, 20));
        daten.setRechnungsdatum(LocalDate.of(2026, 9, 1));

        ProjektGeschaeftsdokument gespeichert = setup.service.speichereZugferdDatei(zugferdTmp, "mahnung-ar.pdf", 1L, daten);

        assertNotNull(gespeichert);
        assertSame(referenz, gespeichert.getReferenzDokument());
        assertEquals("AR-2026/08/00036", gespeichert.getDokumentid());
        assertEquals(Mahnstufe.ZAHLUNGSERINNERUNG, gespeichert.getMahnstufe());
    }

    @Test
    void speichertMahnungMitReferenzStornorechnungWirftException() throws IOException {
        ServiceSetup setup = createMahnungService();
        Projekt projekt = new Projekt();
        projekt.setId(1L);
        when(setup.projektRepository.findById(1L)).thenReturn(Optional.of(projekt));

        ProjektGeschaeftsdokument referenz = new ProjektGeschaeftsdokument();
        referenz.setId(44L);
        referenz.setProjekt(projekt);
        referenz.setDokumentid("SR-2026/08/00001");
        referenz.setGeschaeftsdokumentart("Stornorechnung");
        referenz.setBruttoBetrag(BigDecimal.valueOf(-1500.00));
        referenz.setRechnungsdatum(LocalDate.of(2026, 8, 1));

        when(setup.dokumentRepository.findById(44L)).thenReturn(Optional.of(referenz));

        Path zugferdTmp = Files.createTempFile("zugferd-mahnung-sr", ".pdf");
        Files.writeString(zugferdTmp, "dummy");

        ZugferdDaten daten = new ZugferdDaten();
        daten.setGeschaeftsdokumentart("Mahnung");
        daten.setReferenzDokumentId(44L);
        daten.setMahnstufe("ZAHLUNGSERINNERUNG");
        daten.setFaelligkeitsdatum(LocalDate.of(2026, 9, 20));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> setup.service.speichereZugferdDatei(zugferdTmp, "mahnung-sr.pdf", 1L, daten));
        assertTrue(ex.getMessage().contains("Mahnung muss sich auf eine Rechnung beziehen."));
    }

    @Test
    void istMahnfaehigeRechnungPrueftWhitelistKorrekt() {
        assertTrue(DateiSpeicherService.istMahnfaehigeRechnung("Rechnung"));
        assertTrue(DateiSpeicherService.istMahnfaehigeRechnung("rechnung"));
        assertTrue(DateiSpeicherService.istMahnfaehigeRechnung("Teilrechnung"));
        assertTrue(DateiSpeicherService.istMahnfaehigeRechnung("Abschlagsrechnung"));
        assertTrue(DateiSpeicherService.istMahnfaehigeRechnung("Schlussrechnung"));
        assertTrue(DateiSpeicherService.istMahnfaehigeRechnung(" Schlussrechnung "));

        assertFalse(DateiSpeicherService.istMahnfaehigeRechnung("Stornorechnung"));
        assertFalse(DateiSpeicherService.istMahnfaehigeRechnung("stornorechnung"));
        assertFalse(DateiSpeicherService.istMahnfaehigeRechnung("Gutschrift"));
        assertFalse(DateiSpeicherService.istMahnfaehigeRechnung("Angebot"));
        assertFalse(DateiSpeicherService.istMahnfaehigeRechnung("Auftragsbestaetigung"));
        assertFalse(DateiSpeicherService.istMahnfaehigeRechnung("Mahnung"));
        assertFalse(DateiSpeicherService.istMahnfaehigeRechnung(null));
        assertFalse(DateiSpeicherService.istMahnfaehigeRechnung(""));
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

    // --- Tests: setzeGeschaeftsdokumentBezahlt ---
    // Haken setzen und Projektstatus nachziehen erledigt der gemeinsame Weg in
    // AusgangsGeschaeftsDokumentService (getestet in AusgangsGeschaeftsDokumentServiceBezahltTest).

    @Test
    void setzeGeschaeftsdokumentBezahltLaesstProjektStatusZentralNeuBerechnen() throws IOException {
        ServiceSetup setup = createMahnungService();

        Projekt projekt = new Projekt();
        projekt.setId(10L);

        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setId(100L);
        rechnung.setProjekt(projekt);
        rechnung.setBezahlt(false);

        when(setup.dokumentRepository.findById(100L)).thenReturn(Optional.of(rechnung));

        setup.service.setzeGeschaeftsdokumentBezahlt(100L, true);

        verify(setup.preisAutomatik).setzeOffenenPostenBezahlt(rechnung, true);
        // Keine zweite, eigene Abschluss-Regel mehr – das Projekt fasst nur die Zentrale an.
        org.mockito.Mockito.verifyNoInteractions(setup.projektRepository);
    }

    @Test
    void setzeGeschaeftsdokumentUnbezahltLaesstProjektStatusZentralNeuBerechnen() throws IOException {
        ServiceSetup setup = createMahnungService();

        Projekt projekt = new Projekt();
        projekt.setId(11L);

        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setId(101L);
        rechnung.setProjekt(projekt);
        rechnung.setBezahlt(true);

        when(setup.dokumentRepository.findById(101L)).thenReturn(Optional.of(rechnung));

        setup.service.setzeGeschaeftsdokumentBezahlt(101L, false);

        verify(setup.preisAutomatik).setzeOffenenPostenBezahlt(rechnung, false);
    }

    @Test
    void stornierteRechnungLaesstSichNichtWiederOeffnen() throws IOException {
        ServiceSetup setup = createMahnungService();

        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setId(102L);
        rechnung.setDokumentid("RE-2026-0042");
        rechnung.setBezahlt(true);

        when(setup.dokumentRepository.findById(102L)).thenReturn(Optional.of(rechnung));
        when(setup.dokumentRepository.istStorniert("RE-2026-0042")).thenReturn(true);

        ResponseStatusException fehler = assertThrows(ResponseStatusException.class,
                () -> setup.service.setzeGeschaeftsdokumentBezahlt(102L, false));
        assertEquals(HttpStatus.BAD_REQUEST, fehler.getStatusCode());
        assertEquals("Diese Rechnung wurde storniert und bleibt deshalb erledigt.", fehler.getReason());
        verify(setup.preisAutomatik, never()).setzeOffenenPostenBezahlt(any(), anyBoolean());
    }

    @Test
    void stornierteRechnungDarfWeiterAlsBezahltMarkiertWerden() throws IOException {
        ServiceSetup setup = createMahnungService();

        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setId(103L);
        rechnung.setDokumentid("RE-2026-0043");

        when(setup.dokumentRepository.findById(103L)).thenReturn(Optional.of(rechnung));

        setup.service.setzeGeschaeftsdokumentBezahlt(103L, true);

        verify(setup.preisAutomatik).setzeOffenenPostenBezahlt(rechnung, true);
        verify(setup.dokumentRepository, never()).istStorniert(any());
    }

    @Test
    void bezahlteRechnungenDerLetztenZwoelfMonate() throws IOException {
        ServiceSetup setup = createMahnungService();
        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        when(setup.dokumentRepository.findBezahlteRechnungenSeit(any())).thenReturn(List.of(rechnung));

        assertEquals(List.of(rechnung), setup.service.holeBezahlteRechnungen());

        org.mockito.ArgumentCaptor<java.time.LocalDate> seit = org.mockito.ArgumentCaptor.forClass(java.time.LocalDate.class);
        verify(setup.dokumentRepository).findBezahlteRechnungenSeit(seit.capture());
        assertEquals(java.time.LocalDate.now().minusMonths(12), seit.getValue());
    }

    @Test
    void setzeGeschaeftsdokumentBezahltWirftExceptionFuerNichtGeschaeftsdokument() throws IOException {
        ServiceSetup setup = createMahnungService();

        ProjektDokument normalDoc = new ProjektDokument();
        normalDoc.setId(200L);

        when(setup.dokumentRepository.findById(200L)).thenReturn(Optional.of(normalDoc));

        ResponseStatusException fehler = assertThrows(ResponseStatusException.class,
                () -> setup.service.setzeGeschaeftsdokumentBezahlt(200L, true));
        assertEquals(HttpStatus.BAD_REQUEST, fehler.getStatusCode());
        verify(setup.preisAutomatik, never()).setzeOffenenPostenBezahlt(any(), anyBoolean());
    }

    @Test
    void setzeGeschaeftsdokumentBezahltWirftExceptionWennDokumentNichtGefunden() throws IOException {
        ServiceSetup setup = createMahnungService();

        when(setup.dokumentRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException fehler = assertThrows(ResponseStatusException.class,
                () -> setup.service.setzeGeschaeftsdokumentBezahlt(999L, true));
        assertEquals(HttpStatus.NOT_FOUND, fehler.getStatusCode());
    }

    @Test
    void loescheDateiGibt404WennDokumentNichtGefunden() throws IOException {
        ServiceSetup setup = createMahnungService();

        when(setup.dokumentRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException fehler = assertThrows(ResponseStatusException.class,
                () -> setup.service.loescheDatei(999L));
        assertEquals(HttpStatus.NOT_FOUND, fehler.getStatusCode());
        verify(setup.dokumentRepository, never()).delete(any());
    }

    @Test
    void loescheDateiNenntBeiDateifehlerNurDieIdNichtDenDateinamen() throws IOException {
        ServiceSetup setup = createMahnungService();
        // Ein nicht leerer Ordner in der Ablage lässt sich nicht löschen – so entsteht ein
        // echter Dateifehler.
        Path volleAblage = Files.createDirectory(setup.dokumentenOrdner.resolve("nicht-leer"));
        Files.writeString(volleAblage.resolve("inhalt.txt"), "x");
        ProjektDokument dokument = new ProjektDokument();
        dokument.setId(77L);
        dokument.setOriginalDateiname("Angebot Max Mustermann.pdf");
        dokument.setGespeicherterDateiname("nicht-leer");
        when(setup.dokumentRepository.findById(77L)).thenReturn(Optional.of(dokument));

        IllegalStateException fehler = assertThrows(IllegalStateException.class,
                () -> setup.service.loescheDatei(77L));

        // Die Meldung landet im Server-Log: keine Klarnamen (DSGVO).
        assertTrue(fehler.getMessage().contains("77"));
        assertFalse(fehler.getMessage().contains("Mustermann"));
        verify(setup.dokumentRepository, never()).delete(any());
    }

    /** Baut eine bezahlte Rechnung samt Projekt und Kunde für die Top-Kunden-Auswertung. */
    private ProjektGeschaeftsdokument rechnung(long projektId, String kundenName, boolean abgeschlossen,
            String brutto) {
        org.example.kalkulationsprogramm.domain.Kunde kunde = new org.example.kalkulationsprogramm.domain.Kunde();
        kunde.setId(projektId);
        kunde.setName(kundenName);

        Projekt projekt = new Projekt();
        projekt.setId(projektId);
        projekt.setKundenId(kunde);
        projekt.setAbgeschlossen(abgeschlossen);
        projekt.setBruttoPreis(new BigDecimal(brutto));

        ProjektGeschaeftsdokument doc = new ProjektGeschaeftsdokument();
        doc.setProjekt(projekt);
        doc.setBezahlt(true);
        doc.setRechnungsdatum(LocalDate.of(2026, 3, 15));
        doc.setBruttoBetrag(new BigDecimal(brutto));
        return doc;
    }

    @Test
    void topKundenZaehltNurAbgeschlosseneProjekte() throws IOException {
        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        DateiSpeicherService service = createService(Files.createTempDirectory("topkunden"), "", dokRepo);

        when(dokRepo.findGeschaeftsdokumenteByRechnungsdatumBetween(any(), any()))
                .thenReturn(List.of(
                        rechnung(1L, "Max Mustermann", true, "1190.00"),
                        rechnung(2L, "Erika Musterfrau", false, "5950.00")));

        var topKunden = service.holeUmsatzStatistiken(2026, null).getTopKunden();

        assertEquals(1, topKunden.size());
        assertEquals("Max Mustermann", topKunden.get(0).getKundenName());
    }

    @Test
    void topKundenLiefertBruttoUndNettoSumme() throws IOException {
        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        DateiSpeicherService service = createService(Files.createTempDirectory("topkunden-netto"), "", dokRepo);

        when(dokRepo.findGeschaeftsdokumenteByRechnungsdatumBetween(any(), any()))
                .thenReturn(List.of(rechnung(1L, "Max Mustermann", true, "1190.00")));

        var kunde = service.holeUmsatzStatistiken(2026, null).getTopKunden().get(0);

        assertEquals(1190.00, kunde.getUmsatzBrutto(), 0.001);
        // 1190 brutto entspricht bei 19 % Umsatzsteuer genau 1000 netto
        assertEquals(1000.00, kunde.getUmsatzNetto(), 0.001);
    }

    @Test
    void topKundenSummiertMehrereRechnungenDesselbenKunden() throws IOException {
        ProjektDokumentRepository dokRepo = mock(ProjektDokumentRepository.class);
        DateiSpeicherService service = createService(Files.createTempDirectory("topkunden-summe"), "", dokRepo);

        ProjektGeschaeftsdokument ersteRechnung = rechnung(1L, "Max Mustermann", true, "1190.00");
        ProjektGeschaeftsdokument zweiteRechnung = rechnung(1L, "Max Mustermann", true, "2380.00");
        // Beide Rechnungen gehören zum selben Projekt desselben Kunden
        zweiteRechnung.setProjekt(ersteRechnung.getProjekt());

        when(dokRepo.findGeschaeftsdokumenteByRechnungsdatumBetween(any(), any()))
                .thenReturn(List.of(ersteRechnung, zweiteRechnung));

        var topKunden = service.holeUmsatzStatistiken(2026, null).getTopKunden();

        assertEquals(1, topKunden.size());
        assertEquals(3570.00, topKunden.get(0).getUmsatzBrutto(), 0.001);
        assertEquals(3000.00, topKunden.get(0).getUmsatzNetto(), 0.001);
        // Ein Projekt, auch wenn zwei Rechnungen dazu existieren
        assertEquals(1, topKunden.get(0).getProjektAnzahl());
    }
}

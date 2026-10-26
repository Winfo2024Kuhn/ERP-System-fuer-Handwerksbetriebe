package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageListenFilter;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageSeiteResponseDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieVorschlagDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.repository.AnfrageNotizBildRepository;
import org.example.kalkulationsprogramm.repository.AnfrageNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.AngemeldeterMitarbeiterService;
import org.example.kalkulationsprogramm.service.AnfrageService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.service.PdfAiExtractorService;
import org.example.kalkulationsprogramm.service.ZugferdErstellService;
import org.example.kalkulationsprogramm.service.ZugferdExtractorService;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.exception.FirmenstammdatenUnvollstaendigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnfrageControllerTest {

    @Test
    void projektVorlageUebernimmtKundenEmails() {
        AnfrageService anfrageService = mock(AnfrageService.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        ZugferdErstellService zugferdErstellService = mock(ZugferdErstellService.class);
        ZugferdExtractorService zugferdExtractorService = mock(ZugferdExtractorService.class);
        PdfAiExtractorService pdfAiExtractorService = mock(PdfAiExtractorService.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        AnfrageNotizRepository anfrageNotizRepository = mock(AnfrageNotizRepository.class);
        AnfrageNotizBildRepository anfrageNotizBildRepository = mock(AnfrageNotizBildRepository.class);
        DokumentFreigabeService dokumentFreigabeService = mock(DokumentFreigabeService.class);

        AnfrageController controller = new AnfrageController(anfrageService, ausgangsGeschaeftsDokumentService,
                dateiSpeicherService, zugferdErstellService, zugferdExtractorService, pdfAiExtractorService,
                kundeRepository, anfrageNotizRepository, anfrageNotizBildRepository,
                mock(AngemeldeterMitarbeiterService.class), dokumentFreigabeService);

        Anfrage anfrage = new Anfrage();
        anfrage.setId(42L);
        Kunde kunde = new Kunde();
        kunde.setName("Test Kunde");
        kunde.setKundennummer("KD123");
        anfrage.setKunde(kunde);
        anfrage.setKundenEmails(Arrays.asList("a@example.com", null, "b@example.com", "a@example.com"));

        when(anfrageService.finde(42L)).thenReturn(anfrage);
        when(kundeRepository.findByKundennummerIgnoreCase("KD123")).thenReturn(Optional.of(kunde));

        ResponseEntity<ProjektErstellenDto> response = controller.projektVorlage(42L);
        assertEquals(200, response.getStatusCode().value());
        List<String> emails = response.getBody().getKundenEmails();
        assertEquals(List.of("a@example.com", "b@example.com"), emails);
    }

    @Test
    void produktkategorienVorschlagDelegiertAnService() {
        AnfrageService anfrageService = mock(AnfrageService.class);
        AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService = mock(AusgangsGeschaeftsDokumentService.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        ZugferdErstellService zugferdErstellService = mock(ZugferdErstellService.class);
        ZugferdExtractorService zugferdExtractorService = mock(ZugferdExtractorService.class);
        PdfAiExtractorService pdfAiExtractorService = mock(PdfAiExtractorService.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        AnfrageNotizRepository anfrageNotizRepository = mock(AnfrageNotizRepository.class);
        AnfrageNotizBildRepository anfrageNotizBildRepository = mock(AnfrageNotizBildRepository.class);
        DokumentFreigabeService dokumentFreigabeService = mock(DokumentFreigabeService.class);

        AnfrageController controller = new AnfrageController(anfrageService, ausgangsGeschaeftsDokumentService,
                dateiSpeicherService, zugferdErstellService, zugferdExtractorService, pdfAiExtractorService,
                kundeRepository, anfrageNotizRepository, anfrageNotizBildRepository,
                mock(AngemeldeterMitarbeiterService.class), dokumentFreigabeService);

        KategorieVorschlagDto dto = new KategorieVorschlagDto();
        dto.setKategorieId(7L);
        dto.setBezeichnung("Rohbau");
        when(ausgangsGeschaeftsDokumentService.berechneKategorieVorschlagFuerAnfrage(99L))
                .thenReturn(List.of(dto));

        ResponseEntity<List<KategorieVorschlagDto>> response = controller.produktkategorienVorschlag(99L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals(7L, response.getBody().get(0).getKategorieId());
        verify(ausgangsGeschaeftsDokumentService).berechneKategorieVorschlagFuerAnfrage(99L);
    }

    private AnfrageController neuerController(AnfrageService anfrageService) {
        return new AnfrageController(
                anfrageService,
                mock(AusgangsGeschaeftsDokumentService.class),
                mock(DateiSpeicherService.class),
                mock(ZugferdErstellService.class),
                mock(ZugferdExtractorService.class),
                mock(PdfAiExtractorService.class),
                mock(KundeRepository.class),
                mock(AnfrageNotizRepository.class),
                mock(AnfrageNotizBildRepository.class),
                mock(AngemeldeterMitarbeiterService.class),
                mock(DokumentFreigabeService.class));
    }

    @Test
    void beendetHakenSetztNurAbgeschlossen() throws Exception {
        AnfrageService anfrageService = mock(AnfrageService.class);
        AnfrageResponseDto dto = new AnfrageResponseDto();
        dto.setId(42L);
        dto.setAbgeschlossen(true);
        dto.setKurzbeschreibung("Geländer 12 m");
        when(anfrageService.setzeAbgeschlossen(42L, true)).thenReturn(dto);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(neuerController(anfrageService)).build();

        mockMvc.perform(patch("/api/anfragen/42/abgeschlossen").param("abgeschlossen", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abgeschlossen").value(true))
                .andExpect(jsonPath("$.kurzbeschreibung").value("Geländer 12 m"));

        verify(anfrageService).setzeAbgeschlossen(42L, true);
        verify(anfrageService, never()).aktualisiereAnfrage(anyLong(), any());
    }

    @Test
    void beendetHakenUnbekannteAnfrageGibt404() {
        AnfrageService anfrageService = mock(AnfrageService.class);
        when(anfrageService.setzeAbgeschlossen(99L, false)).thenReturn(null);

        ResponseEntity<AnfrageResponseDto> response = neuerController(anfrageService).setzeAbgeschlossen(99L, false);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void listeOhnePageGibtFlacheListeZurueck() {
        AnfrageService anfrageService = mock(AnfrageService.class);
        AnfrageController controller = neuerController(anfrageService);

        AnfrageResponseDto dto = new AnfrageResponseDto();
        dto.setId(1L);
        when(anfrageService.suche(null, null, null, null, null, false))
                .thenReturn(List.of(dto));

        List<?> result = controller.liste(null, null, null, null, null, null, false, null);

        assertEquals(1, result.size());
        assertEquals(1L, ((AnfrageResponseDto) result.get(0)).getId());
        verify(anfrageService).suche(null, null, null, null, null, false);
        verify(anfrageService, never()).sucheSeiteGefiltert(any(), any(), any(), any(), any(), anyBoolean(), any(),
                anyInt(), anyInt());
    }

    @Test
    void listeMitPageRoutetAufPaginierteVariante() {
        AnfrageService anfrageService = mock(AnfrageService.class);
        AnfrageController controller = neuerController(anfrageService);

        AnfrageResponseDto dto = new AnfrageResponseDto();
        dto.setId(7L);
        AnfrageSeiteResponseDto seite = new AnfrageSeiteResponseDto(List.of(dto), 25L, 0, 12);
        AnfrageListenFilter ohneFilter = new AnfrageListenFilter(null, null, null, null);
        when(anfrageService.sucheSeiteGefiltert(null, null, null, null, null, false, ohneFilter, 0, 12))
                .thenReturn(seite);

        AnfrageSeiteResponseDto result = controller.listeSeite(
                null, null, null, null, null, null, false, null, null, null, null, 0, 12);

        assertEquals(25L, result.gesamt());
        assertEquals(0, result.seite());
        assertEquals(12, result.seitenGroesse());
        assertEquals(1, result.anfragen().size());
        assertEquals(7L, result.anfragen().get(0).getId());
        verify(anfrageService).sucheSeiteGefiltert(null, null, null, null, null, false, ohneFilter, 0, 12);
        verify(anfrageService, never()).suche(any(), any(), any(), any(), any(), anyBoolean());
    }

    /** Alle Listen-Filter müssen unverändert an den Service durchgereicht werden. */
    @Test
    void listeSeiteReichtAlleFilterDurch() {
        AnfrageService anfrageService = mock(AnfrageService.class);
        AnfrageController controller = neuerController(anfrageService);

        AnfrageListenFilter filter = new AnfrageListenFilter("pending", "offen", "webseite", "alt");
        AnfrageSeiteResponseDto seite = new AnfrageSeiteResponseDto(List.of(), 0L, 0, 12);
        when(anfrageService.sucheSeiteGefiltert(null, null, null, null, null, false, filter, 0, 12))
                .thenReturn(seite);

        controller.listeSeite(null, null, null, null, null, null, false, "pending", "offen", "webseite", "alt", 0, 12);

        verify(anfrageService).sucheSeiteGefiltert(null, null, null, null, null, false, filter, 0, 12);
    }

    @Test
    void erzeugeZugferdReichtFehlendeFirmendatenDurch() {
        ZugferdErstellService zugferdErstellService = mock(ZugferdErstellService.class);
        AnfrageController controller = new AnfrageController(
                mock(AnfrageService.class),
                mock(AusgangsGeschaeftsDokumentService.class),
                mock(DateiSpeicherService.class),
                zugferdErstellService,
                mock(ZugferdExtractorService.class),
                mock(PdfAiExtractorService.class),
                mock(KundeRepository.class),
                mock(AnfrageNotizRepository.class),
                mock(AnfrageNotizBildRepository.class),
                mock(AngemeldeterMitarbeiterService.class),
                mock(DokumentFreigabeService.class));
        when(zugferdErstellService.erzeuge(anyString(), any()))
                .thenThrow(new FirmenstammdatenUnvollstaendigException("die E-Rechnung", List.of("Ort")));
        MockMultipartFile pdf = new MockMultipartFile("datei", "a.pdf", "application/pdf", new byte[] { 1 });

        assertThrows(FirmenstammdatenUnvollstaendigException.class,
                () -> controller.erzeugeZugferd(1L, pdf, new ZugferdDaten()));
    }

    @Test
    void erzeugeZugferdMeldetAndereFehlerMitText() {
        ZugferdErstellService zugferdErstellService = mock(ZugferdErstellService.class);
        AnfrageController controller = new AnfrageController(
                mock(AnfrageService.class),
                mock(AusgangsGeschaeftsDokumentService.class),
                mock(DateiSpeicherService.class),
                zugferdErstellService,
                mock(ZugferdExtractorService.class),
                mock(PdfAiExtractorService.class),
                mock(KundeRepository.class),
                mock(AnfrageNotizRepository.class),
                mock(AnfrageNotizBildRepository.class),
                mock(AngemeldeterMitarbeiterService.class),
                mock(DokumentFreigabeService.class));
        when(zugferdErstellService.erzeuge(anyString(), any()))
                .thenThrow(new RuntimeException("ZUGFeRD Erstellung fehlgeschlagen"));
        MockMultipartFile pdf = new MockMultipartFile("datei", "a.pdf", "application/pdf", new byte[] { 1 });

        ResponseEntity<?> response = controller.erzeugeZugferd(1L, pdf, new ZugferdDaten());

        assertEquals(400, response.getStatusCode().value());
        assertEquals(java.util.Map.of("message", "Die E-Rechnung konnte nicht erstellt werden."), response.getBody());
    }

    // ==================== Notiz-Verfasser kommt aus der Anmeldung ====================

    private static org.example.kalkulationsprogramm.domain.Mitarbeiter mitarbeiter(long id) {
        var m = new org.example.kalkulationsprogramm.domain.Mitarbeiter();
        m.setId(id);
        m.setVorname(id == 7L ? "Max" : "Erika");
        m.setNachname("Mustermann");
        return m;
    }

    private static org.example.kalkulationsprogramm.domain.AnfrageNotiz notiz(long id,
            org.example.kalkulationsprogramm.domain.Mitarbeiter verfasser, boolean privat) {
        var n = new org.example.kalkulationsprogramm.domain.AnfrageNotiz();
        var anfrage = new Anfrage();
        anfrage.setId(1L);
        n.setId(id);
        n.setAnfrage(anfrage);
        n.setMitarbeiter(verfasser);
        n.setNotiz("Notiz " + id);
        n.setMobileSichtbar(true);
        n.setNurFuerErsteller(privat);
        n.setErstelltAm(java.time.LocalDateTime.of(2026, 9, 1, 8, 0));
        return n;
    }

    @Test
    void notizenZeigenNurEigenePrivateNotizenDesAngemeldetenMitarbeiters() {
        AngemeldeterMitarbeiterService angemeldet = mock(AngemeldeterMitarbeiterService.class);
        AnfrageNotizRepository notizen = mock(AnfrageNotizRepository.class);
        AnfrageController controller = new AnfrageController(mock(AnfrageService.class),
                mock(AusgangsGeschaeftsDokumentService.class), mock(DateiSpeicherService.class),
                mock(ZugferdErstellService.class), mock(ZugferdExtractorService.class),
                mock(PdfAiExtractorService.class), mock(KundeRepository.class), notizen,
                mock(AnfrageNotizBildRepository.class), angemeldet, mock(DokumentFreigabeService.class));
        var max = mitarbeiter(7L);
        var erika = mitarbeiter(3L);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(angemeldet.handelnderMitarbeiter(auth)).thenReturn(Optional.of(max));
        when(notizen.findByAnfrageIdOrderByErstelltAmDesc(1L))
                .thenReturn(List.of(notiz(1L, max, true), notiz(2L, erika, true), notiz(3L, erika, false)));

        var antwort = controller.getNotizen(1L, auth).getBody();

        assertEquals(List.of(1L, 3L), antwort.stream().map(AnfrageController.AnfrageNotizDto::getId).toList());
    }

    @Test
    void notizAnlegenSchreibtAngemeldetenMitarbeiterAlsVerfasser() {
        AngemeldeterMitarbeiterService angemeldet = mock(AngemeldeterMitarbeiterService.class);
        AnfrageNotizRepository notizen = mock(AnfrageNotizRepository.class);
        AnfrageService anfrageService = mock(AnfrageService.class);
        AnfrageController controller = new AnfrageController(anfrageService,
                mock(AusgangsGeschaeftsDokumentService.class), mock(DateiSpeicherService.class),
                mock(ZugferdErstellService.class), mock(ZugferdExtractorService.class),
                mock(PdfAiExtractorService.class), mock(KundeRepository.class), notizen,
                mock(AnfrageNotizBildRepository.class), angemeldet, mock(DokumentFreigabeService.class));
        var max = mitarbeiter(7L);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(angemeldet.handelnderMitarbeiter(auth)).thenReturn(Optional.of(max));
        when(anfrageService.finde(1L)).thenReturn(new Anfrage());
        when(notizen.save(any())).thenAnswer(inv -> {
            org.example.kalkulationsprogramm.domain.AnfrageNotiz n = inv.getArgument(0);
            n.setId(9L);
            n.setErstelltAm(java.time.LocalDateTime.of(2026, 9, 1, 8, 0));
            return n;
        });
        var dto = new AnfrageController.AnfrageNotizDto();
        dto.setNotiz("'; DROP TABLE anfrage_notiz; --");

        var antwort = controller.addNotiz(1L, dto, auth);

        assertEquals(200, antwort.getStatusCode().value());
        assertEquals(7L, antwort.getBody().getMitarbeiterId());
        assertEquals("'; DROP TABLE anfrage_notiz; --", antwort.getBody().getNotiz());
    }

    @Test
    void notizAnlegenOhneZugeordnetenMitarbeiterGibt403MitHinweis() {
        AngemeldeterMitarbeiterService angemeldet = mock(AngemeldeterMitarbeiterService.class);
        AnfrageNotizRepository notizen = mock(AnfrageNotizRepository.class);
        AnfrageService anfrageService = mock(AnfrageService.class);
        AnfrageController controller = new AnfrageController(anfrageService,
                mock(AusgangsGeschaeftsDokumentService.class), mock(DateiSpeicherService.class),
                mock(ZugferdErstellService.class), mock(ZugferdExtractorService.class),
                mock(PdfAiExtractorService.class), mock(KundeRepository.class), notizen,
                mock(AnfrageNotizBildRepository.class), angemeldet, mock(DokumentFreigabeService.class));
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(angemeldet.handelnderMitarbeiter(auth)).thenReturn(Optional.empty());
        when(anfrageService.finde(1L)).thenReturn(new Anfrage());

        var fehler = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.addNotiz(1L, new AnfrageController.AnfrageNotizDto(), auth));
        assertEquals(403, fehler.getStatusCode().value());
        assertEquals(AngemeldeterMitarbeiterService.KEIN_MITARBEITER, fehler.getReason());
        verify(notizen, never()).save(any());
    }

    @Test
    void dokumentHochladenSpeichertAngemeldetenMitarbeiterAlsHochlader() {
        AngemeldeterMitarbeiterService angemeldet = mock(AngemeldeterMitarbeiterService.class);
        DateiSpeicherService dateiSpeicher = mock(DateiSpeicherService.class);
        AnfrageController controller = uploadController(angemeldet, dateiSpeicher);
        var max = mitarbeiter(7L);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(angemeldet.handelnderMitarbeiter(auth)).thenReturn(Optional.of(max));
        when(dateiSpeicher.speichereAnfragesDatei(any(), eq(1L), any(), any())).thenAnswer(inv -> gespeichertesDokument());
        var datei = new MockMultipartFile("datei", "plan.pdf", "application/pdf", "data".getBytes());

        var antwort = controller.uploadDokument(1L, List.of(datei), null, auth);

        assertEquals(200, antwort.getStatusCode().value());
        verify(dateiSpeicher).speichereAnfragesDatei(datei, 1L,
                org.example.kalkulationsprogramm.domain.DokumentGruppe.DIVERSE_DOKUMENTE, max);
    }

    @Test
    void dokumentHochladenOhneZugeordnetenMitarbeiterSpeichertOhneHochlader() {
        AngemeldeterMitarbeiterService angemeldet = mock(AngemeldeterMitarbeiterService.class);
        DateiSpeicherService dateiSpeicher = mock(DateiSpeicherService.class);
        AnfrageController controller = uploadController(angemeldet, dateiSpeicher);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(angemeldet.handelnderMitarbeiter(auth)).thenReturn(Optional.empty());
        when(dateiSpeicher.speichereAnfragesDatei(any(), eq(1L), any(), any())).thenAnswer(inv -> gespeichertesDokument());
        var datei = new MockMultipartFile("datei", "foto.jpg", "image/jpeg", "data".getBytes());

        var antwort = controller.uploadDokument(1L, List.of(datei),
                org.example.kalkulationsprogramm.domain.DokumentGruppe.BILDER, auth);

        assertEquals(200, antwort.getStatusCode().value());
        verify(dateiSpeicher).speichereAnfragesDatei(datei, 1L,
                org.example.kalkulationsprogramm.domain.DokumentGruppe.BILDER, null);
    }

    @ParameterizedTest
    @ValueSource(strings = { "xlsx", "xls", "xlsm", "xlsb", "sza", "tcd", "XLSX" })
    void tabellenUndZeichnungenBekommenNetzwerkPfadZumDirektenOeffnen(String endung) {
        AngemeldeterMitarbeiterService angemeldet = mock(AngemeldeterMitarbeiterService.class);
        DateiSpeicherService dateiSpeicher = mock(DateiSpeicherService.class);
        AnfrageController controller = uploadController(angemeldet, dateiSpeicher);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(angemeldet.handelnderMitarbeiter(auth)).thenReturn(Optional.empty());
        var dokument = gespeichertesDokument();
        dokument.setOriginalDateiname("Kalkulation_Mustermann." + endung);
        dokument.setGespeicherterDateiname("abc_Kalkulation_Mustermann." + endung);
        when(dateiSpeicher.speichereAnfragesDatei(any(), eq(1L), any(), any())).thenReturn(dokument);
        when(dateiSpeicher.holeNetzwerkPfad("abc_Kalkulation_Mustermann." + endung))
                .thenReturn("\\\\SERVER\\Zeichnungen\\abc_Kalkulation_Mustermann." + endung);
        var datei = new MockMultipartFile("datei", "Kalkulation_Mustermann." + endung,
                "application/octet-stream", "data".getBytes());

        var antwort = controller.uploadDokument(1L, List.of(datei), null, auth);

        assertEquals(200, antwort.getStatusCode().value());
        assertEquals("\\\\SERVER\\Zeichnungen\\abc_Kalkulation_Mustermann." + endung,
                erstesDokument(antwort).getNetzwerkPfad());
    }

    @Test
    void pdfBekommtKeinenNetzwerkPfad() {
        AngemeldeterMitarbeiterService angemeldet = mock(AngemeldeterMitarbeiterService.class);
        DateiSpeicherService dateiSpeicher = mock(DateiSpeicherService.class);
        AnfrageController controller = uploadController(angemeldet, dateiSpeicher);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(angemeldet.handelnderMitarbeiter(auth)).thenReturn(Optional.empty());
        when(dateiSpeicher.speichereAnfragesDatei(any(), eq(1L), any(), any())).thenAnswer(inv -> gespeichertesDokument());
        var datei = new MockMultipartFile("datei", "plan.pdf", "application/pdf", "data".getBytes());

        var antwort = controller.uploadDokument(1L, List.of(datei), null, auth);

        assertEquals(200, antwort.getStatusCode().value());
        assertNull(erstesDokument(antwort).getNetzwerkPfad());
        verify(dateiSpeicher, never()).holeNetzwerkPfad(any());
    }

    private static org.example.kalkulationsprogramm.dto.Anfrage.AnfrageDokumentResponseDto erstesDokument(
            ResponseEntity<?> antwort) {
        return (org.example.kalkulationsprogramm.dto.Anfrage.AnfrageDokumentResponseDto) ((List<?>) antwort.getBody()).get(0);
    }

    private AnfrageController uploadController(AngemeldeterMitarbeiterService angemeldet,
            DateiSpeicherService dateiSpeicher) {
        return new AnfrageController(mock(AnfrageService.class),
                mock(AusgangsGeschaeftsDokumentService.class), dateiSpeicher,
                mock(ZugferdErstellService.class), mock(ZugferdExtractorService.class),
                mock(PdfAiExtractorService.class), mock(KundeRepository.class), mock(AnfrageNotizRepository.class),
                mock(AnfrageNotizBildRepository.class), angemeldet, mock(DokumentFreigabeService.class));
    }

    private static org.example.kalkulationsprogramm.domain.AnfrageDokument gespeichertesDokument() {
        var dokument = new org.example.kalkulationsprogramm.domain.AnfrageDokument();
        dokument.setId(5L);
        dokument.setOriginalDateiname("plan.pdf");
        dokument.setGespeicherterDateiname("abc_plan.pdf");
        return dokument;
    }

    private AnfrageController notizController(AngemeldeterMitarbeiterService angemeldet, AnfrageNotizRepository notizen) {
        return new AnfrageController(mock(AnfrageService.class),
                mock(AusgangsGeschaeftsDokumentService.class), mock(DateiSpeicherService.class),
                mock(ZugferdErstellService.class), mock(ZugferdExtractorService.class),
                mock(PdfAiExtractorService.class), mock(KundeRepository.class), notizen,
                mock(AnfrageNotizBildRepository.class), angemeldet, mock(DokumentFreigabeService.class));
    }

    @Test
    void notizenOhneZugeordnetenMitarbeiterSindNichtBearbeitbar() {
        AngemeldeterMitarbeiterService angemeldet = mock(AngemeldeterMitarbeiterService.class);
        AnfrageNotizRepository notizen = mock(AnfrageNotizRepository.class);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(angemeldet.handelnderMitarbeiter(auth)).thenReturn(Optional.empty());
        when(notizen.findByAnfrageIdOrderByErstelltAmDesc(1L))
                .thenReturn(List.of(notiz(1L, mitarbeiter(3L), false), notiz(2L, mitarbeiter(3L), true)));

        var antwort = notizController(angemeldet, notizen).getNotizen(1L, auth).getBody();

        assertEquals(List.of(1L), antwort.stream().map(AnfrageController.AnfrageNotizDto::getId).toList());
        assertFalse(antwort.get(0).isCanEdit());
    }

    @Test
    void fremdePrivateNotizDarfAmPcNichtGeaendertWerden() {
        AngemeldeterMitarbeiterService angemeldet = mock(AngemeldeterMitarbeiterService.class);
        AnfrageNotizRepository notizen = mock(AnfrageNotizRepository.class);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(angemeldet.handelnderMitarbeiter(auth)).thenReturn(Optional.of(mitarbeiter(7L)));
        var fremd = notiz(5L, mitarbeiter(3L), true);
        when(notizen.findById(5L)).thenReturn(Optional.of(fremd));

        var antwort = notizController(angemeldet, notizen).updateNotiz(1L, 5L, new AnfrageController.AnfrageNotizDto(), auth);

        assertEquals(403, antwort.getStatusCode().value());
        assertNull(antwort.getBody());
        assertTrue(fremd.isNurFuerErsteller());
        verify(notizen, never()).save(any());
    }

    @Test
    void fremdePrivateNotizDarfAmPcNichtGeloeschtWerden() {
        AngemeldeterMitarbeiterService angemeldet = mock(AngemeldeterMitarbeiterService.class);
        AnfrageNotizRepository notizen = mock(AnfrageNotizRepository.class);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(angemeldet.handelnderMitarbeiter(auth)).thenReturn(Optional.of(mitarbeiter(7L)));
        when(notizen.findById(5L)).thenReturn(Optional.of(notiz(5L, mitarbeiter(3L), true)));

        assertEquals(403, notizController(angemeldet, notizen).deleteNotiz(1L, 5L, auth).getStatusCode().value());
        verify(notizen, never()).delete(any());
    }

    @Test
    void notizAusAndererAnfrageGibtAmPc404() {
        AngemeldeterMitarbeiterService angemeldet = mock(AngemeldeterMitarbeiterService.class);
        AnfrageNotizRepository notizen = mock(AnfrageNotizRepository.class);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(notizen.findById(5L)).thenReturn(Optional.of(notiz(5L, mitarbeiter(7L), false)));

        assertEquals(404, notizController(angemeldet, notizen).deleteNotiz(2L, 5L, auth).getStatusCode().value());
        verify(notizen, never()).delete(any());
    }
}

package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageSeiteResponseDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieVorschlagDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.repository.AnfrageNotizBildRepository;
import org.example.kalkulationsprogramm.repository.AnfrageNotizRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.AnfrageService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.PdfAiExtractorService;
import org.example.kalkulationsprogramm.service.ZugferdErstellService;
import org.example.kalkulationsprogramm.service.ZugferdExtractorService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        AnfrageRepository anfrageRepository = mock(AnfrageRepository.class);
        MitarbeiterRepository mitarbeiterRepository = mock(MitarbeiterRepository.class);
        FrontendUserProfileService frontendUserProfileService = mock(FrontendUserProfileService.class);
        DokumentFreigabeService dokumentFreigabeService = mock(DokumentFreigabeService.class);

        AnfrageController controller = new AnfrageController(anfrageService, ausgangsGeschaeftsDokumentService,
                dateiSpeicherService, zugferdErstellService, zugferdExtractorService, pdfAiExtractorService,
                kundeRepository, anfrageNotizRepository, anfrageNotizBildRepository, anfrageRepository,
                mitarbeiterRepository, frontendUserProfileService, dokumentFreigabeService);

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
        AnfrageRepository anfrageRepository = mock(AnfrageRepository.class);
        MitarbeiterRepository mitarbeiterRepository = mock(MitarbeiterRepository.class);
        FrontendUserProfileService frontendUserProfileService = mock(FrontendUserProfileService.class);
        DokumentFreigabeService dokumentFreigabeService = mock(DokumentFreigabeService.class);

        AnfrageController controller = new AnfrageController(anfrageService, ausgangsGeschaeftsDokumentService,
                dateiSpeicherService, zugferdErstellService, zugferdExtractorService, pdfAiExtractorService,
                kundeRepository, anfrageNotizRepository, anfrageNotizBildRepository, anfrageRepository,
                mitarbeiterRepository, frontendUserProfileService, dokumentFreigabeService);

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
                mock(AnfrageRepository.class),
                mock(MitarbeiterRepository.class),
                mock(FrontendUserProfileService.class),
                mock(DokumentFreigabeService.class));
    }

    @Test
    void listeOhnePageGibtFlacheListeZurueck() {
        AnfrageService anfrageService = mock(AnfrageService.class);
        AnfrageController controller = neuerController(anfrageService);

        AnfrageResponseDto dto = new AnfrageResponseDto();
        dto.setId(1L);
        when(anfrageService.suche(null, null, null, null, null, false))
                .thenReturn(List.of(dto));

        List<AnfrageResponseDto> result = controller.liste(null, null, null, null, null, null, false);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        verify(anfrageService).suche(null, null, null, null, null, false);
        verify(anfrageService, never()).sucheSeite(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void listeMitPageRoutetAufPaginierteVariante() {
        AnfrageService anfrageService = mock(AnfrageService.class);
        AnfrageController controller = neuerController(anfrageService);

        AnfrageResponseDto dto = new AnfrageResponseDto();
        dto.setId(7L);
        AnfrageSeiteResponseDto seite = new AnfrageSeiteResponseDto(List.of(dto), 25L, 0, 12);
        when(anfrageService.sucheSeite(null, null, null, null, null, false, 0, 12))
                .thenReturn(seite);

        AnfrageSeiteResponseDto result = controller.listeSeite(null, null, null, null, null, null, false, 0, 12);

        assertEquals(25L, result.gesamt());
        assertEquals(0, result.seite());
        assertEquals(12, result.seitenGroesse());
        assertEquals(1, result.anfragen().size());
        assertEquals(7L, result.anfragen().get(0).getId());
        verify(anfrageService).sucheSeite(null, null, null, null, null, false, 0, 12);
        verify(anfrageService, never()).suche(any(), any(), any(), any(), any(), anyBoolean());
    }
}

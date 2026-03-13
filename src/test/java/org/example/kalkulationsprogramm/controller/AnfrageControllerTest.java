package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.repository.AnfrageNotizBildRepository;
import org.example.kalkulationsprogramm.repository.AnfrageNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.AnfrageService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
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
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        ZugferdErstellService zugferdErstellService = mock(ZugferdErstellService.class);
        ZugferdExtractorService zugferdExtractorService = mock(ZugferdExtractorService.class);
        PdfAiExtractorService pdfAiExtractorService = mock(PdfAiExtractorService.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        AnfrageNotizRepository anfrageNotizRepository = mock(AnfrageNotizRepository.class);
        AnfrageNotizBildRepository anfrageNotizBildRepository = mock(AnfrageNotizBildRepository.class);
        MitarbeiterRepository mitarbeiterRepository = mock(MitarbeiterRepository.class);
        FrontendUserProfileService frontendUserProfileService = mock(FrontendUserProfileService.class);

        AnfrageController controller = new AnfrageController(anfrageService, dateiSpeicherService,
                zugferdErstellService, zugferdExtractorService, pdfAiExtractorService, kundeRepository,
                anfrageNotizRepository, anfrageNotizBildRepository, mitarbeiterRepository, frontendUserProfileService);

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
}

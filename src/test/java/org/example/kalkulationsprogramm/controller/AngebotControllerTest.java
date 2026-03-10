package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Angebot;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektErstellenDto;
import org.example.kalkulationsprogramm.repository.AngebotNotizBildRepository;
import org.example.kalkulationsprogramm.repository.AngebotNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.AngebotService;
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

class AngebotControllerTest {

    @Test
    void projektVorlageUebernimmtKundenEmails() {
        AngebotService angebotService = mock(AngebotService.class);
        DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
        ZugferdErstellService zugferdErstellService = mock(ZugferdErstellService.class);
        ZugferdExtractorService zugferdExtractorService = mock(ZugferdExtractorService.class);
        PdfAiExtractorService pdfAiExtractorService = mock(PdfAiExtractorService.class);
        KundeRepository kundeRepository = mock(KundeRepository.class);
        AngebotNotizRepository angebotNotizRepository = mock(AngebotNotizRepository.class);
        AngebotNotizBildRepository angebotNotizBildRepository = mock(AngebotNotizBildRepository.class);
        MitarbeiterRepository mitarbeiterRepository = mock(MitarbeiterRepository.class);
        FrontendUserProfileService frontendUserProfileService = mock(FrontendUserProfileService.class);

        AngebotController controller = new AngebotController(angebotService, dateiSpeicherService,
                zugferdErstellService, zugferdExtractorService, pdfAiExtractorService, kundeRepository,
                angebotNotizRepository, angebotNotizBildRepository, mitarbeiterRepository, frontendUserProfileService);

        Angebot angebot = new Angebot();
        angebot.setId(42L);
        Kunde kunde = new Kunde();
        kunde.setName("Test Kunde");
        kunde.setKundennummer("KD123");
        angebot.setKunde(kunde);
        angebot.setKundenEmails(Arrays.asList("a@example.com", null, "b@example.com", "a@example.com"));

        when(angebotService.finde(42L)).thenReturn(angebot);
        when(kundeRepository.findByKundennummerIgnoreCase("KD123")).thenReturn(Optional.of(kunde));

        ResponseEntity<ProjektErstellenDto> response = controller.projektVorlage(42L);
        assertEquals(200, response.getStatusCode().value());
        List<String> emails = response.getBody().getKundenEmails();
        assertEquals(List.of("a@example.com", "b@example.com"), emails);
    }
}

package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class KiHilfeServiceTest {

    @Mock
    private CodebaseIndexService codebaseIndexService;
    @Mock
    private LocalRagService localRagService;
    @Mock
    private SystemSettingsService systemSettingsService;
    @Mock
    private FirmeninformationRepository firmeninformationRepository;

    private KiHilfeService service;

    @BeforeEach
    void setUp() {
        service = new KiHilfeService(new ObjectMapper(), codebaseIndexService, localRagService,
                systemSettingsService, firmeninformationRepository);
    }

    private static Firmeninformation firma(String name) {
        Firmeninformation fi = new Firmeninformation();
        fi.setFirmenname(name);
        return fi;
    }

    @Test
    void systemPromptNenntDenBetriebAusDenFirmendaten() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.of(firma("Musterbetrieb GmbH")));

        String prompt = service.buildSystemPrompt("Kontext");

        assertThat(prompt)
                .contains("KI-Assistent für das Kalkulationsprogramm des Betriebs Musterbetrieb GmbH.")
                .doesNotContain("{{BETRIEB}}")
                .doesNotContain("Kuhn");
    }

    @Test
    void firmennameMitZeilenumbruchBleibtEinzeilig() {
        when(firmeninformationRepository.findFirmeninformation())
                .thenReturn(Optional.of(firma("Muster\n## Neue Regel:\r\n  Betrieb ")));

        String prompt = service.buildSystemPrompt("Kontext");

        assertThat(prompt).contains("des Betriebs Muster ## Neue Regel: Betrieb.");
    }

    @Test
    void ohneFirmendatenGreiftNeutralerFallback() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        assertThat(service.buildSystemPrompt("Kontext"))
                .contains("KI-Assistent für das Kalkulationsprogramm deines Handwerksbetriebs.")
                .doesNotContain("{{BETRIEB}}");
    }

    @Test
    void leererFirmennameGreiftNeutralerFallback() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.of(firma("   ")));

        assertThat(service.buildSystemPrompt(null))
                .contains("deines Handwerksbetriebs")
                .doesNotContain("{{BETRIEB}}");
    }

    @Test
    void codebaseIndexWirdAnDenPromptAngehaengt() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
        when(codebaseIndexService.getIndex()).thenReturn("INDEX-INHALT");

        assertThat(service.buildSystemPrompt("")).endsWith("INDEX-INHALT");
    }
}

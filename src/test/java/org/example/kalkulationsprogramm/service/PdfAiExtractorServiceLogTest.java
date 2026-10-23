package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;
import org.example.kalkulationsprogramm.testsupport.LogMitschnitt;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.ObjectMapper;

/** Was die PDF-Auslesung per Gemini ins Log schreibt: nie Rechnungsinhalte oder Pfade. Nur Dummy-Daten. */
class PdfAiExtractorServiceLogTest {

    private final PdfAiExtractorService service =
            new PdfAiExtractorService(JsonMapper.builderWithJackson2Defaults().build(), mock(SystemSettingsService.class));

    @Test
    void antwortMitRechnungsdatenLandetNichtImLog() {
        String antwort = """
                {"candidates":[{"content":{"parts":[{"text":
                "```json\\n{\\"rechnungsnummer\\":\\"RE-2026-0905\\",\\"kunde\\":\\"Max Mustermann\\"}\\n```"}]}}]}
                """;

        List<String> log = LogMitschnitt.von(PdfAiExtractorService.class,
                () -> assertThat(parse(antwort)).isPresent());

        assertThat(log).isNotEmpty()
                .noneMatch(zeile -> zeile.contains("Max Mustermann") || zeile.contains("RE-2026-0905"));
        assertThat(log).anyMatch(zeile -> zeile.startsWith("[PdfAI/Gemini] Antwort erhalten ("));
    }

    @Test
    void antwortOhneTextUndKaputteAntwortNennenNurFehlerart() {
        String ohneParts = """
                {"candidates":[{"finishReason":"SAFETY","citationMetadata":{"titel":"Max Mustermann"}}]}
                """;
        String kaputt = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"kunde\\\":\\\"Max Mustermann\"}]}}]}";

        List<String> log = LogMitschnitt.von(PdfAiExtractorService.class, () -> {
            assertThat(parse(ohneParts)).isEmpty();
            assertThat(parse(kaputt)).isEmpty();
            assertThat(parse("Mustermann sagt nein")).isEmpty();
        });

        // Jackson zitiert das erste Token ('Mustermann') – das darf nicht ins Log.
        assertThat(log).noneMatch(zeile -> zeile.contains("Mustermann"));
        assertThat(log).contains(
                "[PdfAI/Gemini] Keine Parts in Antwort. FinishReason: SAFETY",
                "[PdfAI/Gemini] Parse-Fehler: StreamReadException");
    }

    @Test
    void fehlendesPdfNenntKeinenPfad() {
        ReflectionTestUtils.setField(service, "aiBackend", "gemini");
        List<String> log = LogMitschnitt.von(PdfAiExtractorService.class,
                () -> service.analyze("/nicht-erlaubt/0b1c2d3e_Rechnung_Max_Mustermann.pdf"));

        assertThat(log).noneMatch(zeile -> zeile.contains("Max_Mustermann") || zeile.contains("nicht-erlaubt"));
        assertThat(log).contains("[PdfAI/Gemini] PDF nicht gefunden");
    }

    @SuppressWarnings("unchecked")
    private Optional<Object> parse(String antwort) {
        try {
            Method parse = PdfAiExtractorService.class.getDeclaredMethod("parseGeminiResponse", String.class);
            parse.setAccessible(true);
            return (Optional<Object>) parse.invoke(service, antwort);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

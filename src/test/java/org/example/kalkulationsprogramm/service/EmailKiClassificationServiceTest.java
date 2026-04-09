package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.service.EmailKiClassificationService.ClassificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailKiClassificationServiceTest {

    @Mock
    private OllamaService ollamaService;

    @Mock
    private EmailRepository emailRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private EmailKiClassificationService classificationService;

    private Email testEmail;
    private Projekt projektA;
    private Projekt projektB;
    private Anfrage anfrageA;

    @BeforeEach
    void setUp() {
        // Test-Email erstellen
        testEmail = new Email();
        testEmail.setId(100L);
        testEmail.setSubject("Montage Geländer nächste Woche");
        testEmail.setFromAddress("test@example.com");
        testEmail.setBody("Hallo Herr Kuhn, können wir die Montage der Balkongeländer nächste Woche Mittwoch einplanen? Bitte um Rückmeldung. MfG Max Mustermann");
        testEmail.setSentAt(LocalDateTime.of(2026, 3, 15, 10, 30));
        testEmail.setDirection(EmailDirection.IN);

        // Projekt A
        projektA = new Projekt();
        projektA.setId(42L);
        projektA.setBauvorhaben("Balkongeländer Musterstraße 1");
        projektA.setKurzbeschreibung("Neue Balkongeländer aus Edelstahl");
        projektA.setAuftragsnummer("2026/03/42000");
        projektA.setAnlegedatum(LocalDate.of(2026, 2, 1));

        // Projekt B
        projektB = new Projekt();
        projektB.setId(55L);
        projektB.setBauvorhaben("Treppengeländer Hauptstraße 5");
        projektB.setKurzbeschreibung("Innentreppe Handlauf verzinkt");
        projektB.setAuftragsnummer("2026/03/55000");
        projektB.setAnlegedatum(LocalDate.of(2026, 2, 15));

        // Anfrage A
        anfrageA = new Anfrage();
        anfrageA.setId(17L);
        anfrageA.setBauvorhaben("Lichtschachtabdeckungen");
        anfrageA.setKurzbeschreibung("3x Lichtschachtgitter nach Maß");
        anfrageA.setAnlegedatum(LocalDate.of(2026, 3, 1));
    }

    // ═══════════════════════════════════════════════════════════════
    // classify() Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void classify_ollamaDisabled_returnsNone() {
        when(ollamaService.isEnabled()).thenReturn(false);

        ClassificationResult result = classificationService.classify(
                testEmail, List.of(projektA), List.of());

        assertThat(result.isAssigned()).isFalse();
        assertThat(result.reason()).contains("deaktiviert");
    }

    @Test
    void classify_noCandidates_returnsNone() {
        when(ollamaService.isEnabled()).thenReturn(true);

        ClassificationResult result = classificationService.classify(
                testEmail, List.of(), List.of());

        assertThat(result.isAssigned()).isFalse();
        assertThat(result.reason()).contains("Keine Kandidaten");
    }

    @Test
    void classify_kiReturnsProjekt_returnsCorrectResult() throws Exception {
        when(ollamaService.isEnabled()).thenReturn(true);
        when(emailRepository.findByProjektOrderBySentAtDesc(any())).thenReturn(List.of());
        when(ollamaService.chat(anyString(), anyString()))
                .thenReturn("""
                        {"key": "PROJEKT_42", "confidence": 0.9, "reason": "Balkongeländer wird im Betreff erwähnt"}
                        """);

        ClassificationResult result = classificationService.classify(
                testEmail, List.of(projektA, projektB), List.of());

        assertThat(result.isAssigned()).isTrue();
        assertThat(result.zuordnungTyp()).isEqualTo(EmailZuordnungTyp.PROJEKT);
        assertThat(result.entityId()).isEqualTo(42L);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.9);
        assertThat(result.key()).isEqualTo("PROJEKT_42");
    }

    @Test
    void classify_kiReturnsAnfrage_returnsCorrectResult() throws Exception {
        when(ollamaService.isEnabled()).thenReturn(true);
        when(emailRepository.findByProjektOrderBySentAtDesc(any())).thenReturn(List.of());
        when(emailRepository.findByAnfrageOrderBySentAtDesc(any())).thenReturn(List.of());
        when(ollamaService.chat(anyString(), anyString()))
                .thenReturn("""
                        {"key": "ANFRAGE_17", "confidence": 0.75, "reason": "Lichtschachtgitter passt"}
                        """);

        ClassificationResult result = classificationService.classify(
                testEmail, List.of(projektA), List.of(anfrageA));

        assertThat(result.isAssigned()).isTrue();
        assertThat(result.zuordnungTyp()).isEqualTo(EmailZuordnungTyp.ANFRAGE);
        assertThat(result.entityId()).isEqualTo(17L);
    }

    @Test
    void classify_kiReturnsNone_notAssigned() throws Exception {
        when(ollamaService.isEnabled()).thenReturn(true);
        when(emailRepository.findByProjektOrderBySentAtDesc(any())).thenReturn(List.of());
        when(ollamaService.chat(anyString(), anyString()))
                .thenReturn("""
                        {"key": "NONE", "confidence": 0.3, "reason": "Keine eindeutige Zuordnung möglich"}
                        """);

        ClassificationResult result = classificationService.classify(
                testEmail, List.of(projektA, projektB), List.of());

        assertThat(result.isAssigned()).isFalse();
        assertThat(result.zuordnungTyp()).isEqualTo(EmailZuordnungTyp.KEINE);
    }

    @Test
    void classify_lowConfidence_notAssigned() throws Exception {
        when(ollamaService.isEnabled()).thenReturn(true);
        when(emailRepository.findByProjektOrderBySentAtDesc(any())).thenReturn(List.of());
        when(ollamaService.chat(anyString(), anyString()))
                .thenReturn("""
                        {"key": "PROJEKT_42", "confidence": 0.3, "reason": "Unsicher"}
                        """);

        ClassificationResult result = classificationService.classify(
                testEmail, List.of(projektA, projektB), List.of());

        assertThat(result.isAssigned()).isFalse();
    }

    @Test
    void classify_invalidKey_returnsNone() throws Exception {
        when(ollamaService.isEnabled()).thenReturn(true);
        when(emailRepository.findByProjektOrderBySentAtDesc(any())).thenReturn(List.of());
        when(ollamaService.chat(anyString(), anyString()))
                .thenReturn("""
                        {"key": "PROJEKT_999", "confidence": 0.9, "reason": "Erfundener Key"}
                        """);

        ClassificationResult result = classificationService.classify(
                testEmail, List.of(projektA), List.of());

        assertThat(result.isAssigned()).isFalse();
        assertThat(result.reason()).contains("Ungültiger Key");
    }

    @Test
    void classify_ollamaError_returnsNone() throws Exception {
        when(ollamaService.isEnabled()).thenReturn(true);
        when(emailRepository.findByProjektOrderBySentAtDesc(any())).thenReturn(List.of());
        when(ollamaService.chat(anyString(), anyString()))
                .thenThrow(new java.io.IOException("Connection refused"));

        ClassificationResult result = classificationService.classify(
                testEmail, List.of(projektA), List.of());

        assertThat(result.isAssigned()).isFalse();
        assertThat(result.reason()).contains("KI-Fehler");
    }

    // ═══════════════════════════════════════════════════════════════
    // buildUserPrompt() Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void buildUserPrompt_containsEmailData() {
        when(emailRepository.findByProjektOrderBySentAtDesc(any())).thenReturn(List.of());

        String prompt = classificationService.buildUserPrompt(
                testEmail, List.of(projektA), List.of());

        assertThat(prompt).contains("EINGEHENDE E-MAIL");
        assertThat(prompt).contains("Montage Geländer nächste Woche");
        assertThat(prompt).contains("test@example.com");
        assertThat(prompt).contains("Balkongeländer");
    }

    @Test
    void buildUserPrompt_containsAllCandidates() {
        when(emailRepository.findByProjektOrderBySentAtDesc(any())).thenReturn(List.of());
        when(emailRepository.findByAnfrageOrderBySentAtDesc(any())).thenReturn(List.of());

        String prompt = classificationService.buildUserPrompt(
                testEmail, List.of(projektA, projektB), List.of(anfrageA));

        assertThat(prompt).contains("PROJEKT_42");
        assertThat(prompt).contains("PROJEKT_55");
        assertThat(prompt).contains("ANFRAGE_17");
        assertThat(prompt).contains("Balkongeländer Musterstraße 1");
        assertThat(prompt).contains("Treppengeländer Hauptstraße 5");
        assertThat(prompt).contains("Lichtschachtabdeckungen");
    }

    @Test
    void buildUserPrompt_includesEmailVerlauf() {
        // Verlauf für Projekt A
        Email verlaufEmail = new Email();
        verlaufEmail.setDirection(EmailDirection.OUT);
        verlaufEmail.setSentAt(LocalDateTime.of(2026, 3, 10, 14, 0));
        verlaufEmail.setFromAddress("bauschlosserei-kuhn@t-online.de");
        verlaufEmail.setSubject("Angebot Balkongeländer");
        verlaufEmail.setBody("Sehr geehrter Herr Mustermann, anbei unser Angebot für die Balkongeländer.");

        when(emailRepository.findByProjektOrderBySentAtDesc(projektA)).thenReturn(List.of(verlaufEmail));
        when(emailRepository.findByProjektOrderBySentAtDesc(projektB)).thenReturn(List.of());

        String prompt = classificationService.buildUserPrompt(
                testEmail, List.of(projektA, projektB), List.of());

        assertThat(prompt).contains("AUSGANG");
        assertThat(prompt).contains("Angebot Balkongeländer");
        assertThat(prompt).contains("anbei unser Angebot");
    }

    @Test
    void buildUserPrompt_truncatesLongBodies() {
        testEmail.setBody("A".repeat(5000));
        when(emailRepository.findByProjektOrderBySentAtDesc(any())).thenReturn(List.of());

        String prompt = classificationService.buildUserPrompt(
                testEmail, List.of(projektA), List.of());

        // Body sollte auf 2000 Zeichen begrenzt sein
        assertThat(prompt.length()).isLessThan(5000);
        assertThat(prompt).contains("…");
    }

    @Test
    void buildUserPrompt_handlesNullFields() {
        // Email mit null-Feldern
        Email nullEmail = new Email();
        nullEmail.setId(200L);
        // Subject, Body, FromAddress alle null

        when(emailRepository.findByProjektOrderBySentAtDesc(any())).thenReturn(List.of());

        String prompt = classificationService.buildUserPrompt(
                nullEmail, List.of(projektA), List.of());

        // Sollte nicht crashen
        assertThat(prompt).contains("EINGEHENDE E-MAIL");
        assertThat(prompt).contains("PROJEKT_42");
    }

    // ═══════════════════════════════════════════════════════════════
    // parseResponse() Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parseResponse_validProjektJson_correctResult() {
        ClassificationResult result = classificationService.parseResponse(
                """
                {"key": "PROJEKT_42", "confidence": 0.88, "reason": "Betreff erwähnt Balkongeländer"}
                """,
                List.of(projektA, projektB), List.of());

        assertThat(result.isAssigned()).isTrue();
        assertThat(result.zuordnungTyp()).isEqualTo(EmailZuordnungTyp.PROJEKT);
        assertThat(result.entityId()).isEqualTo(42L);
        assertThat(result.confidence()).isEqualTo(0.88);
    }

    @Test
    void parseResponse_validAnfrageJson_correctResult() {
        ClassificationResult result = classificationService.parseResponse(
                """
                {"key": "ANFRAGE_17", "confidence": 0.72, "reason": "Passt thematisch"}
                """,
                List.of(), List.of(anfrageA));

        assertThat(result.isAssigned()).isTrue();
        assertThat(result.zuordnungTyp()).isEqualTo(EmailZuordnungTyp.ANFRAGE);
        assertThat(result.entityId()).isEqualTo(17L);
    }

    @Test
    void parseResponse_jsonWithSurroundingText_extractsCorrectly() {
        // Manche Modelle schreiben Text drum herum
        ClassificationResult result = classificationService.parseResponse(
                """
                Hier ist meine Analyse:
                {"key": "PROJEKT_42", "confidence": 0.85, "reason": "Test"}
                Das war meine Antwort.
                """,
                List.of(projektA), List.of());

        assertThat(result.isAssigned()).isTrue();
        assertThat(result.entityId()).isEqualTo(42L);
    }

    @Test
    void parseResponse_noneKey_returnsUnassigned() {
        ClassificationResult result = classificationService.parseResponse(
                """
                {"key": "NONE", "confidence": 0.0, "reason": "Keine eindeutige Zuordnung"}
                """,
                List.of(projektA), List.of());

        assertThat(result.isAssigned()).isFalse();
    }

    @Test
    void parseResponse_invalidJson_returnsNone() {
        ClassificationResult result = classificationService.parseResponse(
                "Ich bin ein KI-Modell und hier ist keine JSON-Antwort",
                List.of(projektA), List.of());

        assertThat(result.isAssigned()).isFalse();
        assertThat(result.reason()).contains("Parse-Fehler");
    }

    @Test
    void parseResponse_keyNotInCandidates_returnsNone() {
        ClassificationResult result = classificationService.parseResponse(
                """
                {"key": "PROJEKT_999", "confidence": 0.95, "reason": "Erfundener Key"}
                """,
                List.of(projektA), List.of());

        assertThat(result.isAssigned()).isFalse();
        assertThat(result.reason()).contains("Ungültiger Key");
    }

    // ═══════════════════════════════════════════════════════════════
    // ClassificationResult Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void classificationResult_none_isNotAssigned() {
        ClassificationResult result = ClassificationResult.none("Test");
        assertThat(result.isAssigned()).isFalse();
        assertThat(result.zuordnungTyp()).isEqualTo(EmailZuordnungTyp.KEINE);
        assertThat(result.entityId()).isNull();
        assertThat(result.key()).isEqualTo("NONE");
    }

    @Test
    void classificationResult_withProjekt_isAssigned() {
        ClassificationResult result = new ClassificationResult(
                EmailZuordnungTyp.PROJEKT, 42L, 0.9, "Test", "PROJEKT_42");
        assertThat(result.isAssigned()).isTrue();
    }
}

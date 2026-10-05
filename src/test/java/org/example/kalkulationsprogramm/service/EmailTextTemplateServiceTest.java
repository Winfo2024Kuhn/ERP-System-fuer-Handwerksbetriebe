package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.repository.EmailTextTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailTextTemplateServiceTest {

    @Mock
    private EmailTextTemplateRepository repository;

    @Mock
    private FirmeninformationService firmeninformationService;

    @InjectMocks
    private EmailTextTemplateService service;

    private FirmeninformationDto firma;

    @BeforeEach
    void setUp() {
        firma = new FirmeninformationDto();
        firma.setBankName("Musterbank");
        firma.setIban("DE89 3704 0044 0532 0130 00");
        firma.setBic("COBADEFFXXX");
    }

    private EmailTextTemplate aktiveVorlage(String subject, String body) {
        EmailTextTemplate t = new EmailTextTemplate();
        t.setDokumentTyp("RECHNUNG");
        t.setName("Rechnungs-Standard");
        t.setSubjectTemplate(subject);
        t.setHtmlBody(body);
        t.setAktiv(true);
        return t;
    }

    @Test
    void rendertBankIbanBicAusFirmenstammdatenInBetreffUndBody() {
        given(firmeninformationService.getFirmeninformation()).willReturn(firma);
        given(repository.findByDokumentTyp("RECHNUNG"))
                .willReturn(Optional.of(aktiveVorlage(
                        "Rechnung – bitte überweisen Sie an {{BANK}}",
                        "<p>IBAN: {{IBAN}}</p><p>BIC: {{BIC}}</p>")));

        EmailService.EmailContent result = service.render("RECHNUNG", Map.of());

        assertThat(result).isNotNull();
        assertThat(result.subject()).isEqualTo("Rechnung – bitte überweisen Sie an Musterbank");
        assertThat(result.htmlBody()).contains("DE89 3704 0044 0532 0130 00")
                .contains("COBADEFFXXX");
    }

    @Test
    void callerKontextGewinntGegenueberFirmenstammdaten() {
        // Use-Case: Test oder Migrations-Skript will explizit andere Bankdaten
        // (z. B. Treuhandkonto) verwenden – das soll moeglich bleiben.
        given(firmeninformationService.getFirmeninformation()).willReturn(firma);
        given(repository.findByDokumentTyp("RECHNUNG"))
                .willReturn(Optional.of(aktiveVorlage(
                        "Test", "<p>IBAN: {{IBAN}}</p>")));

        Map<String, String> ctx = new HashMap<>();
        ctx.put("IBAN", "DE99 9999 9999 9999 9999 99");

        EmailService.EmailContent result = service.render("RECHNUNG", ctx);

        assertThat(result.htmlBody()).contains("DE99 9999 9999 9999 9999 99")
                .doesNotContain("DE89");
    }

    @Test
    void leereBankdatenWerdenAlsLeererStringSubstituiert() {
        // Frisch aufgesetzte Firma ohne hinterlegte Bankverbindung -> Platzhalter
        // darf nicht als "{{BANK}}" stehen bleiben (sonst landet das im Kunden-Mail).
        FirmeninformationDto leereFirma = new FirmeninformationDto();
        given(firmeninformationService.getFirmeninformation()).willReturn(leereFirma);
        given(repository.findByDokumentTyp("RECHNUNG"))
                .willReturn(Optional.of(aktiveVorlage(
                        "Test", "<p>Bank: {{BANK}}, IBAN: {{IBAN}}</p>")));

        EmailService.EmailContent result = service.render("RECHNUNG", Map.of());

        assertThat(result.htmlBody()).isEqualTo("<p>Bank: , IBAN: </p>");
    }

    private static final String BEWERTUNGS_BUTTON =
            "<table data-email-button=\"\" role=\"presentation\"><tbody><tr><td bgcolor=\"#500010\">"
                    + "<a href=\"{{REVIEW_URL}}\">Jetzt Bewertung abgeben</a></td></tr></tbody></table>";

    @Test
    void bewertungsButtonBekommtGoogleLinkAusFirmendaten() {
        firma.setGoogleBewertungsLink("https://g.page/r/beispiel/review");
        given(firmeninformationService.getFirmeninformation()).willReturn(firma);
        given(repository.findByDokumentTyp("RECHNUNG"))
                .willReturn(Optional.of(aktiveVorlage("Danke", "<p>Danke!</p>" + BEWERTUNGS_BUTTON)));

        EmailService.EmailContent result = service.render("RECHNUNG", Map.of());

        assertThat(result.htmlBody())
                .contains("href=\"https://g.page/r/beispiel/review\"")
                .contains("Jetzt Bewertung abgeben")
                .doesNotContain("{{REVIEW_URL}}");
    }

    @Test
    void bewertungsButtonFaelltOhneHinterlegtenLinkWeg() {
        // Ohne Google-Link wuerde der Button ins Leere fuehren – dann lieber gar keiner.
        given(firmeninformationService.getFirmeninformation()).willReturn(firma);
        given(repository.findByDokumentTyp("RECHNUNG"))
                .willReturn(Optional.of(aktiveVorlage("Danke", "<p>Danke!</p>" + BEWERTUNGS_BUTTON)));

        EmailService.EmailContent result = service.render("RECHNUNG", Map.of());

        assertThat(result.htmlBody()).isEqualTo("<p>Danke!</p>");
    }

    @Test
    void bewertungsLinkMitAnfuehrungszeichenSprengtDasAttributNicht() {
        firma.setGoogleBewertungsLink("https://g.page/r/x\"onmouseover=\"alert(1)");
        given(firmeninformationService.getFirmeninformation()).willReturn(firma);
        given(repository.findByDokumentTyp("RECHNUNG"))
                .willReturn(Optional.of(aktiveVorlage("Danke", BEWERTUNGS_BUTTON)));

        EmailService.EmailContent result = service.render("RECHNUNG", Map.of());

        assertThat(result.htmlBody())
                .contains("href=\"https://g.page/r/x%22onmouseover=%22alert(1)\"")
                .doesNotContain("onmouseover=\"");
    }

    @Test
    void firmenstammdatenFehlerBlocktVersandNicht() {
        // FirmeninformationService kann z. B. in eng gemockten Tests werfen –
        // der Versand muss trotzdem durchlaufen, sonst blockt ein DB-Glitch
        // den automatischen Mahnungs-Versand.
        given(firmeninformationService.getFirmeninformation())
                .willThrow(new RuntimeException("DB nicht erreichbar"));
        given(repository.findByDokumentTyp("RECHNUNG"))
                .willReturn(Optional.of(aktiveVorlage(
                        "Test", "<p>Body ohne Bank-Token</p>")));

        EmailService.EmailContent result = service.render("RECHNUNG", Map.of());

        assertThat(result).isNotNull();
        assertThat(result.htmlBody()).isEqualTo("<p>Body ohne Bank-Token</p>");
    }

    @Test
    void inaktiveVorlageGibtNullZurueck() {
        EmailTextTemplate t = aktiveVorlage("Test", "Body");
        t.setAktiv(false);
        given(repository.findByDokumentTyp("RECHNUNG")).willReturn(Optional.of(t));

        EmailService.EmailContent result = service.render("RECHNUNG", Map.of());

        assertThat(result).isNull();
    }

    @Test
    void nullKontextWirftNicht() {
        given(firmeninformationService.getFirmeninformation()).willReturn(firma);
        given(repository.findByDokumentTyp(anyString()))
                .willReturn(Optional.of(aktiveVorlage("S", "<p>{{BANK}}</p>")));

        EmailService.EmailContent result = service.render("RECHNUNG", null);

        assertThat(result.htmlBody()).contains("Musterbank");
    }
}

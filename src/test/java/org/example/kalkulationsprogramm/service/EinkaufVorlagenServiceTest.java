package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.domain.einkauf.Dokumentart;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.DokumentSoll;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto;
import org.example.kalkulationsprogramm.repository.EmailTextTemplateRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufVorlagenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EinkaufVorlagenServiceTest {
    @Mock EmailTextTemplateRepository repository;
    @InjectMocks EinkaufVorlagenService service;

    private EmailTextTemplate template(String type, String subject, String body) {
        EmailTextTemplate t = new EmailTextTemplate();
        t.setId(14L);
        t.setVersion(3L);
        t.setDokumentTyp(type);
        t.setName("Standard");
        t.setSubjectTemplate(subject);
        t.setHtmlBody(body);
        t.setAktiv(true);
        return t;
    }

    @Test
    void anfrageRenderedAnfragekontextUndEscapedLieferantAlsText() {
        var template = template("EINKAUF_ANFRAGE", "Anfrage {{ANFRAGENUMMER}}", "<p>{{LIEFERANTENNAME}}</p>");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_ANFRAGE",
                Map.of("ANFRAGENUMMER", "EA-12", "LIEFERANTENNAME", "<img src=x onerror=alert(1)>&"), List.of(), "RC-7");

        var rendered = service.rendern(14L, context);

        assertThat(rendered.subject()).isEqualTo("Anfrage EA-12");
        assertThat(rendered.htmlBody()).contains("&lt;img src=x onerror=alert(1)&gt;&amp;").doesNotContain("<img");
        assertThat(rendered.htmlBody()).contains("RC-7");
        assertThat(rendered.hash()).isNotBlank();
    }

    @Test
    void bestellungErlaubtBestellnummerUndAnfragenummer() {
        var template = template("EINKAUF_BESTELLUNG", "{{BESTELLNUMMER}} / {{ANFRAGENUMMER}}", "Bestellung");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_BESTELLUNG",
                Map.of("BESTELLNUMMER", "EB-8", "ANFRAGENUMMER", "EA-3"), List.of(), "RC-8");

        assertThat(service.rendern(14L, context).subject()).isEqualTo("EB-8 / EA-3");
    }

    @Test
    void bestellentwurfTokenIstInAnfrageUnzulaessig() {
        var template = template("EINKAUF_ANFRAGE", "{{BESTELLNUMMER}}", "Text");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_ANFRAGE", Map.of(), List.of(), "RC-9");

        assertThatThrownBy(() -> service.rendern(14L, context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BESTELLNUMMER");
    }

    @Test
    void positionslisteImBetreffIstNichtZulaessig() {
        var template = template("EINKAUF_ANFRAGE", "{{POSITIONEN}}", "Text");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_ANFRAGE", Map.of(), List.of(), "RC-10");

        assertThatThrownBy(() -> service.rendern(14L, context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Betreff");
    }

    @Test
    void unbekannterTokenWirdAbgelehnt() {
        var template = template("EINKAUF_ANFRAGE", "{{FREMDTOKEN}}", "Text");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_ANFRAGE", Map.of(), List.of(), "RC-11");

        assertThatThrownBy(() -> service.rendern(14L, context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("FREMDTOKEN");
    }

    @Test
    void kundenanfrageNummerKannNichtAlsEinkaufsnummerDurchrutschen() {
        var template = template("EINKAUF_ANFRAGE", "{{ANFRAGENUMMER}}", "Text");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_ANFRAGE",
                Map.of("KUNDENNUMMER", "K-77", "ANFRAGENUMMER", "EA-77"), List.of(), "RC-77");

        assertThatThrownBy(() -> service.rendern(14L, context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("KUNDENNUMMER");
    }

    @Test
    void direktbestellungBenotigtKeineAnfrageOderAngebotsnummer() {
        var template = template("EINKAUF_DIREKTBESTELLUNG", "Bestellung {{BESTELLNUMMER}}", "Text");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_DIREKTBESTELLUNG",
                Map.of("BESTELLNUMMER", "EB-44"), List.of(), "RC-44");

        assertThat(service.rendern(14L, context).subject()).isEqualTo("Bestellung EB-44");
    }

    @Test
    void fehlenderWertEinesVerwendetenTokensWirdAbgelehnt() {
        var template = template("EINKAUF_ANFRAGE", "Anfrage {{ANFRAGENUMMER}}", "Text");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_ANFRAGE", Map.of(), List.of(), "RC-45");

        assertThatThrownBy(() -> service.rendern(14L, context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ANFRAGENUMMER");
    }

    @Test
    void vorlagenversionUndInhaltAendernVorschauHash() {
        var template = template("EINKAUF_ANFRAGE", "Anfrage {{ANFRAGENUMMER}}", "Text");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_ANFRAGE",
                Map.of("ANFRAGENUMMER", "EA-46"), List.of(), "RC-46");
        String first = service.rendern(14L, context).hash();
        template.setVersion(4L);

        assertThat(service.rendern(14L, context).hash()).isNotEqualTo(first);
    }

    @Test
    void bestaetigungsnachfrageErlaubtBestellnummerAusVorhandenemEntwurf() {
        var template = template("EINKAUF_BESTAETIGUNG_NACHFRAGE", "Bestellung {{BESTELLNUMMER}}", "Text");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_BESTAETIGUNG_NACHFRAGE",
                Map.of("BESTELLNUMMER", "EB-90"), List.of(), "RC-90");

        assertThat(service.rendern(14L, context).subject()).isEqualTo("Bestellung EB-90");
    }

    @Test
    void vorschauOhneRueckmeldecodeWirdNichtErstellt() {
        var template = template("EINKAUF_ANFRAGE", "Anfrage {{ANFRAGENUMMER}}", "Text");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_ANFRAGE",
                Map.of("ANFRAGENUMMER", "EA-91"), List.of(), " ");

        assertThatThrownBy(() -> service.rendern(14L, context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Zuordnungscode");
    }

    @Test
    void zeugnislisteWirdAlsEscapeteStrukturUndNichtAlsSkalarGerendert() {
        var template = template("EINKAUF_ZEUGNIS_NACHFORDERUNG", "Bitte Zeugnis", "{{ZEUGNISSE}}");
        given(repository.findById(14L)).willReturn(Optional.of(template));
        PositionSnapshot position = new PositionSnapshot(Positionsart.ARTIKEL, 2L, null, null, null,
                "<img src=x onerror=alert(1)>", "S235", "40x40", null, null, null, null, null, null,
                List.of(new DokumentSoll(Dokumentart.ZEUGNIS_3_1, "EN 10204", "v1", true)), List.of());
        var context = new EinkaufVorlagenDto.VorlagenKontext("EINKAUF_ZEUGNIS_NACHFORDERUNG",
                Map.of(), List.of(position), "RC-92");

        var rendered = service.rendern(14L, context);

        assertThat(rendered.htmlBody()).contains("&lt;img src=x onerror=alert(1)&gt;: ZEUGNIS_3_1 — EN 10204")
                .doesNotContain("<img");
    }

    @Test
    void entwurfPrueftDieselbenTokensUndStrukturiertenListenOhneSpeichern() {
        var result = service.entwurfVorschau(" einkauf_anfrage ", "Anfrage {{ANFRAGENUMMER}}",
                "<p>{{ANREDE}} {{LIEFERANTENNAME}}</p>{{POSITIONEN}}{{ZEUGNISSE}}<script>alert(1)</script>");
        assertThat(result.subject()).isEqualTo("Anfrage PA-2026-00001");
        assertThat(result.htmlBody()).contains("Beispielprofil", "EN 10204", "VORSCHAU-KEIN-VERSAND").doesNotContain("<script>");
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void entwurfWeistFremdeTokensUndBetreffListenSchonVorSpeichernAb() {
        assertThatThrownBy(() -> service.entwurfVorschau("EINKAUF_ANFRAGE", "{{PA_NUMMER}}", "Text"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PA_NUMMER");
        assertThatThrownBy(() -> service.entwurfVorschau("EINKAUF_ANFRAGE", "{{POSITIONEN}}", "Text"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Betreff");
        assertThatThrownBy(() -> service.entwurfVorschau(null, "Betreff", "Text"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

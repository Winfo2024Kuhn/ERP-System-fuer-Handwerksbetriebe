package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Inhalt der Code-Mail. Der eigentliche SMTP-Versand läuft im
 * {@link FreigabeCodeIntegrationTest} gegen einen echten Testserver.
 */
class FreigabeCodeMailServiceTest
{
    private static DokumentFreigabe angebot(String art, String nummer)
    {
        DokumentFreigabe f = new DokumentFreigabe();
        f.setDokumentArt(art);
        f.setDokumentNummer(nummer);
        return f;
    }

    @Test
    void firmentelefon_nurWennGepflegt()
    {
        FirmeninformationService firmen = mock(FirmeninformationService.class);
        FreigabeCodeMailService service = new FreigabeCodeMailService(mock(SystemSettingsService.class), firmen, mock(EmailSignatureService.class));

        when(firmen.getFirmeninformation()).thenReturn(null);
        assertThat(service.firmentelefon()).isNull();

        FirmeninformationDto dto = new FirmeninformationDto();
        when(firmen.getFirmeninformation()).thenReturn(dto);
        assertThat(service.firmentelefon()).isNull();

        dto.setTelefon("   ");
        assertThat(service.firmentelefon()).isNull();

        dto.setTelefon("  09369 / 23 23 ");
        assertThat(service.firmentelefon()).isEqualTo("09369 / 23 23");

        when(firmen.getFirmeninformation()).thenThrow(new IllegalStateException("DB weg"));
        assertThat(service.firmentelefon()).isNull();
    }

    @Test
    void betreff_nenntArtUndNummer()
    {
        assertThat(FreigabeCodeMailService.betreff(angebot("Angebot", "AN-2026-0001")))
                .isEqualTo("Ihr Bestätigungscode für Angebot AN-2026-0001");
    }

    @Test
    void betreff_ohneArtUndNummer_bleibtVerstaendlich()
    {
        assertThat(FreigabeCodeMailService.betreff(angebot(null, null)))
                .isEqualTo("Ihr Bestätigungscode für Ihr Dokument");
        assertThat(FreigabeCodeMailService.betreff(angebot("  ", "  ")))
                .isEqualTo("Ihr Bestätigungscode für Ihr Dokument");
    }

    @Test
    void html_zeigtCodeInDreiergruppenGueltigkeitUndTelefon()
    {
        String html = FreigabeCodeMailService.buildCodeMailHtml(angebot("Angebot", "AN-2026-0001"), "048213",
                LocalDateTime.of(2026, 10, 11, 14, 15), "09369 / 23 23");

        assertThat(html)
                .contains("048 213")
                .contains("Angebot AN-2026-0001")
                .contains("Der Code gilt 15 Minuten (bis 14:15 Uhr).")
                .contains("rufen Sie uns bitte an: <strong>09369 / 23 23</strong>")
                .contains("border-left:3px solid #500010");
    }

    @Test
    void html_ohneTelefonUndAblauf_lässtBeidesWeg()
    {
        String html = FreigabeCodeMailService.buildCodeMailHtml(angebot("Angebot", "AN-1"), "123456", null, null);

        assertThat(html)
                .contains("Der Code gilt 15 Minuten.")
                .contains("Dann melden Sie sich bitte bei uns.")
                .doesNotContain("bis ")
                .doesNotContain("rufen Sie uns");
    }

    @Test
    void html_unerwarteteCodelaenge_bleibtUngeteilt()
    {
        String html = FreigabeCodeMailService.buildCodeMailHtml(angebot("Angebot", "AN-1"), "1234", null, null);

        assertThat(html).contains(">1234<");
    }

    @Test
    void html_escaptDokumentdatenUndTelefon()
    {
        String html = FreigabeCodeMailService.buildCodeMailHtml(
                angebot("<script>alert(1)</script>", "\"AN&1\""), "123456", null, "<b>0800</b>");

        assertThat(html)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt; &quot;AN&amp;1&quot;")
                .contains("&lt;b&gt;0800&lt;/b&gt;");
    }

    @Test
    void mitSignatur_haengtSignaturAn_undSendetOhneWennSieNichtLaedt()
    {
        EmailSignatureService signaturen = mock(EmailSignatureService.class);
        FreigabeCodeMailService service = new FreigabeCodeMailService(mock(SystemSettingsService.class),
                mock(FirmeninformationService.class), signaturen);

        when(signaturen.appendSystemSignatureIfConfigured("<p>Code</p>")).thenReturn("<p>Code</p><p>Signatur</p>");
        assertThat(service.mitSignatur("<p>Code</p>")).isEqualTo("<p>Code</p><p>Signatur</p>");

        when(signaturen.appendSystemSignatureIfConfigured("<p>Code</p>")).thenThrow(new IllegalStateException("DB weg"));
        assertThat(service.mitSignatur("<p>Code</p>")).isEqualTo("<p>Code</p>");
    }
}

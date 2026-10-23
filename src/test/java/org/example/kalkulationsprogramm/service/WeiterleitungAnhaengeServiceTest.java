package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeiterleitungAnhaengeServiceTest {

    private static final Set<LieferantDokumentTyp> ALLE = Set.of(LieferantDokumentTyp.values());

    @Mock private EmailAttachmentProcessingService anhangDateien;
    @Mock private LieferantDokumentZugriffService zugriff;

    @TempDir Path ordner;

    private WeiterleitungAnhaengeService service;
    private Email original;

    @BeforeEach
    void setUp() {
        service = new WeiterleitungAnhaengeService(anhangDateien, zugriff);
        original = new Email();
        when(zugriff.istAnhangSichtbar(anyLong(), anySet())).thenReturn(true);
    }

    private EmailAttachment anhang(long id, String name, String inhalt) throws Exception {
        EmailAttachment a = new EmailAttachment();
        a.setId(id);
        a.setOriginalFilename(name);
        a.setMimeType("application/pdf");
        a.setStoredFilename(id + "_datei");
        original.addAttachment(a);
        if (inhalt != null) {
            Path datei = ordner.resolve(id + "_datei");
            Files.writeString(datei, inhalt);
            when(anhangDateien.resolveAttachmentPath(a)).thenReturn(datei);
        } else {
            when(anhangDateien.resolveAttachmentPath(a)).thenReturn(ordner.resolve("fehlt"));
        }
        return a;
    }

    @Test
    void ohneIds_keineAnhaenge() {
        assertThat(service.lade(original, null, ALLE, 100, 1000)).isEmpty();
        assertThat(service.lade(original, List.of(), ALLE, 100, 1000)).isEmpty();
    }

    @Test
    void laedtGewaehlteAnhaengeInReihenfolgeUndNurEinmal() throws Exception {
        anhang(1L, "Angebot Max Mustermann.pdf", "eins");
        anhang(2L, "Skizze.pdf", "zwei");
        anhang(3L, "logo.png", "drei");

        List<EmailService.Attachment> ergebnis = service.lade(original, Arrays.asList(2L, 1L, 2L, null), ALLE, 100, 1000);

        assertThat(ergebnis).extracting(EmailService.Attachment::filename)
                .containsExactly("Skizze.pdf", "Angebot Max Mustermann.pdf");
        assertThat(new String(ergebnis.get(0).data(), StandardCharsets.UTF_8)).isEqualTo("zwei");
        assertThat(ergebnis.get(0).mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void fremderAnhang_wirdAbgelehnt() throws Exception {
        anhang(1L, "a.pdf", "eins");

        assertThatThrownBy(() -> service.lade(original, List.of(1L, 99L), ALLE, 100, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ein Anhang gehört nicht zur weitergeleiteten E-Mail.");
    }

    @Test
    void gesperrterLieferantenBeleg_wirdAbgelehnt() throws Exception {
        anhang(1L, "Rechnung.pdf", "eins");
        when(zugriff.istAnhangSichtbar(eq(1L), any())).thenReturn(false);

        assertThatThrownBy(() -> service.lade(original, List.of(1L), Set.of(), 100, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ein Anhang gehört nicht zur weitergeleiteten E-Mail.");
    }

    @Test
    void fehlendeDatei_klareMeldung() throws Exception {
        anhang(1L, "weg.pdf", null);

        assertThatThrownBy(() -> service.lade(original, List.of(1L), ALLE, 100, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Der Anhang 'weg.pdf' ist nicht mehr vorhanden.");
    }

    @Test
    void zuGrosserAnhang_wirdAbgelehnt() throws Exception {
        anhang(1L, "gross.pdf", "0123456789");

        assertThatThrownBy(() -> service.lade(original, List.of(1L), ALLE, 5, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'gross.pdf' überschreitet");
    }

    @Test
    void dateinameOhnePfadUndOhneVerboteneZeichen() throws Exception {
        anhang(1L, "..\\..\\etc/pass:wd?.pdf", "x");
        anhang(2L, null, "y");
        anhang(3L, "ordner/", "z");

        List<EmailService.Attachment> ergebnis = service.lade(original, List.of(1L, 2L, 3L), ALLE, 100, 1000);

        assertThat(ergebnis).extracting(EmailService.Attachment::filename)
                .containsExactly("pass_wd_.pdf", "anhang", "anhang");
    }

    @Test
    void gesamtGroesseWirdVorDemLesenGeprueft() throws Exception {
        anhang(1L, "a.pdf", "123456");
        anhang(2L, "b.pdf", "123456");

        assertThatThrownBy(() -> service.lade(original, List.of(1L, 2L), ALLE, 100, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Die Anhänge überschreiten die maximal zulässige Gesamtgröße von 25 MB.");
    }
}

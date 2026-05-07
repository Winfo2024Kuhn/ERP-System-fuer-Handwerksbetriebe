package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.repository.EmailSignatureImageRepository;
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests fuer die System-Signatur-Logik (V256 / Auto-Mail-Versand).
 *
 * <p>Pure Mockito — kein Spring-Context, damit der Test schnell laeuft und
 * keine DB benoetigt. Die Repository-Methode {@code clearSystemDefaultExcept}
 * wird gegen Mocks verifiziert; ihre tatsaechliche Wirkung ist Sache eines
 * DataJpaTest, das den HQL-Query gegen die echte DB feuert.</p>
 */
class EmailSignatureServiceTest {

    private EmailSignatureRepository signatureRepository;
    private EmailSignatureImageRepository imageRepository;
    private FrontendUserProfileRepository profileRepository;
    private EmailSignatureService service;

    @BeforeEach
    void setUp() {
        signatureRepository = mock(EmailSignatureRepository.class);
        imageRepository = mock(EmailSignatureImageRepository.class);
        profileRepository = mock(FrontendUserProfileRepository.class);
        service = new EmailSignatureService(signatureRepository, imageRepository, profileRepository);
    }

    // --------------------- isPlatzhalter ---------------------

    @Test
    void isPlatzhalter_erkenntDoubleQuoteMarker() {
        EmailSignature sig = signaturMit("<div data-system-placeholder=\"1\">Hier eintragen</div>");
        assertThat(EmailSignatureService.isPlatzhalter(sig)).isTrue();
    }

    @Test
    void isPlatzhalter_erkenntSingleQuoteMarker() {
        EmailSignature sig = signaturMit("<div data-system-placeholder='1'>Hier eintragen</div>");
        assertThat(EmailSignatureService.isPlatzhalter(sig)).isTrue();
    }

    @Test
    void isPlatzhalter_befuellteSignaturIstKeinPlatzhalter() {
        EmailSignature sig = signaturMit("<p>Mit freundlichen Gruessen<br>Max Mustermann</p>");
        assertThat(EmailSignatureService.isPlatzhalter(sig)).isFalse();
    }

    @Test
    void isPlatzhalter_nullHtmlGiltAlsPlatzhalter() {
        EmailSignature sig = new EmailSignature();
        sig.setHtml(null);
        assertThat(EmailSignatureService.isPlatzhalter(sig)).isTrue();
    }

    @Test
    void isPlatzhalter_nullSignaturGiltAlsPlatzhalter() {
        assertThat(EmailSignatureService.isPlatzhalter(null)).isTrue();
    }

    // --------------------- getSystemDefaultSignature ---------------------

    @Test
    void getSystemDefaultSignature_filtertPlatzhalterRaus() {
        EmailSignature platzhalter = signaturMit("<div data-system-placeholder=\"1\">Bitte eintragen</div>");
        platzhalter.setSystemDefault(true);
        when(signatureRepository.findFirstByIsSystemDefaultTrue()).thenReturn(Optional.of(platzhalter));

        Optional<EmailSignature> result = service.getSystemDefaultSignature();

        // Platzhalter wird unterdrueckt — keine Signatur wird zurueckgegeben,
        // damit Auto-Mails ohne Aufforderungstext rausgehen.
        assertThat(result).isEmpty();
    }

    @Test
    void getSystemDefaultSignature_liefertBefuellteSignatur() {
        EmailSignature befuellt = signaturMit("<p>Mit freundlichen Gruessen<br>Bauschlosserei</p>");
        befuellt.setSystemDefault(true);
        when(signatureRepository.findFirstByIsSystemDefaultTrue()).thenReturn(Optional.of(befuellt));

        Optional<EmailSignature> result = service.getSystemDefaultSignature();

        assertThat(result).isPresent();
        assertThat(result.get().getHtml()).contains("Bauschlosserei");
    }

    @Test
    void getSystemDefaultSignature_leerWennKeineGesetzt() {
        when(signatureRepository.findFirstByIsSystemDefaultTrue()).thenReturn(Optional.empty());

        assertThat(service.getSystemDefaultSignature()).isEmpty();
    }

    // --------------------- setSystemDefault ---------------------

    @Test
    void setSystemDefault_setztFlagUndLoeschtAndere() {
        EmailSignature target = signaturMit("<p>Neue Sig</p>");
        target.setId(42L);
        target.setSystemDefault(false);
        when(signatureRepository.findById(42L)).thenReturn(Optional.of(target));
        when(signatureRepository.save(any(EmailSignature.class))).thenAnswer(inv -> inv.getArgument(0));

        EmailSignature result = service.setSystemDefault(42L);

        assertThat(result.isSystemDefault()).isTrue();
        verify(signatureRepository).clearSystemDefaultExcept(42L);
        verify(signatureRepository).save(target);
    }

    @Test
    void setSystemDefault_idempotentWennSchonGesetzt() {
        EmailSignature alreadyDefault = signaturMit("<p>Aktiv</p>");
        alreadyDefault.setId(7L);
        alreadyDefault.setSystemDefault(true);
        when(signatureRepository.findById(7L)).thenReturn(Optional.of(alreadyDefault));

        EmailSignature result = service.setSystemDefault(7L);

        assertThat(result.isSystemDefault()).isTrue();
        // clearSystemDefaultExcept laeuft trotzdem, falls in der Zwischenzeit
        // ein anderer Datensatz das Flag versehentlich auch traegt.
        verify(signatureRepository).clearSystemDefaultExcept(7L);
        // Aber: kein erneutes save, weil Wert sich nicht aendert.
        verify(signatureRepository, never()).save(any(EmailSignature.class));
    }

    @Test
    void setSystemDefault_wirftBeiUnbekannterId() {
        when(signatureRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setSystemDefault(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
        verify(signatureRepository, never()).clearSystemDefaultExcept(any());
    }

    // --------------------- appendSystemSignatureIfConfigured ---------------------

    @Test
    void appendSystemSignatureIfConfigured_haengtSignaturAn() {
        EmailSignature befuellt = signaturMit("<p>--<br>Bauschlosserei</p>");
        befuellt.setId(1L);
        befuellt.setSystemDefault(true);
        when(signatureRepository.findFirstByIsSystemDefaultTrue()).thenReturn(Optional.of(befuellt));
        when(imageRepository.findBySignatureIdOrderBySortOrderAsc(1L)).thenReturn(java.util.List.of());

        String result = service.appendSystemSignatureIfConfigured("<p>Mahnung-Body</p>");

        assertThat(result).contains("Mahnung-Body");
        assertThat(result).contains("Bauschlosserei");
        // Die Signatur wird in einen Wrapper mit class="email-signature" gepackt,
        // damit die Idempotenz-Heuristik in ensureSignaturePresentOnce greift.
        assertThat(result).contains("email-signature");
    }

    @Test
    void appendSystemSignatureIfConfigured_idempotentBeiZweitemAufruf() {
        EmailSignature befuellt = signaturMit("<p>--<br>Bauschlosserei</p>");
        befuellt.setId(1L);
        befuellt.setSystemDefault(true);
        when(signatureRepository.findFirstByIsSystemDefaultTrue()).thenReturn(Optional.of(befuellt));
        when(imageRepository.findBySignatureIdOrderBySortOrderAsc(1L)).thenReturn(java.util.List.of());

        String einmal = service.appendSystemSignatureIfConfigured("<p>Body</p>");
        String zweimal = service.appendSystemSignatureIfConfigured(einmal);

        // Wichtig: beim zweiten Aufruf darf die Signatur NICHT doppelt angefuegt
        // werden — sonst landet sie zweimal in der Mail, wenn der Versender
        // den Body durch mehrere Renderer schickt.
        int erstesAuftreten = zweimal.indexOf("Bauschlosserei");
        int letztesAuftreten = zweimal.lastIndexOf("Bauschlosserei");
        assertThat(erstesAuftreten).isEqualTo(letztesAuftreten);
    }

    @Test
    void appendSystemSignatureIfConfigured_laesstBodyUnberuehrtWennPlatzhalter() {
        EmailSignature platzhalter = signaturMit("<div data-system-placeholder=\"1\">Bitte eintragen</div>");
        platzhalter.setSystemDefault(true);
        when(signatureRepository.findFirstByIsSystemDefaultTrue()).thenReturn(Optional.of(platzhalter));

        String body = "<p>Mahnung-Body</p>";
        String result = service.appendSystemSignatureIfConfigured(body);

        // Auto-Mails sollen lieber ohne Signatur rausgehen, als den
        // Platzhalter-Aufforderungstext an Kunden zu senden.
        assertThat(result).isEqualTo(body);
        verify(imageRepository, never()).findBySignatureIdOrderBySortOrderAsc(any());
    }

    @Test
    void appendSystemSignatureIfConfigured_laesstBodyUnberuehrtWennKeineSysSig() {
        when(signatureRepository.findFirstByIsSystemDefaultTrue()).thenReturn(Optional.empty());

        String body = "<p>AB-Body</p>";
        String result = service.appendSystemSignatureIfConfigured(body);

        assertThat(result).isEqualTo(body);
        verify(imageRepository, never()).findBySignatureIdOrderBySortOrderAsc(any());
        verify(signatureRepository, times(1)).findFirstByIsSystemDefaultTrue();
        // Sicherheit: keine ungewollten Schreibzugriffe.
        verify(signatureRepository, never()).save(any());
        verify(signatureRepository, never()).clearSystemDefaultExcept(any());
    }

    // --------------------- helpers ---------------------

    private static EmailSignature signaturMit(String html) {
        EmailSignature sig = new EmailSignature();
        sig.setName("Test-Signatur");
        sig.setHtml(html);
        return sig;
    }
}

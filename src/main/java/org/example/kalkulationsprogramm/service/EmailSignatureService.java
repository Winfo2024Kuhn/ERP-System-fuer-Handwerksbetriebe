package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.EmailSignatureImage;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.EmailSignatureImageRepository;
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSignatureService {

    private final EmailSignatureRepository signatureRepository;
    private final EmailSignatureImageRepository imageRepository;
    private final FrontendUserProfileRepository frontendUserProfileRepository;

    private static final Pattern SIGNATURE_CLASS_PATTERN = Pattern.compile(
            "class\\s*=\\s*(\\\"[^\\\"]*email-signature[^\\\"]*\\\"|'[^']*email-signature[^']*')",
            Pattern.CASE_INSENSITIVE);

    @Value("${file.image-upload-dir}")
    private String imageUploadDir;

    @Transactional(readOnly = true)
    public List<EmailSignature> list() {
        List<EmailSignature> signatures = signatureRepository.findAllByOrderByUpdatedAtDesc();
        signatures.forEach(sig -> Optional.ofNullable(sig.getImages()).ifPresent(List::size));
        return signatures;
    }

    @Transactional(readOnly = true)
    public Optional<EmailSignature> getDefaultForFrontendUser(Long profileId) {
        return getDefaultForFrontendUser(profileId, null);
    }

    /**
     * Liefert die Signatur, die fuer automatisch versendete E-Mails (Auto-AB,
     * Mahnverfahren, ...) angehaengt wird. Genau eine Zeile in
     * {@code email_signature} traegt {@code is_system_default = 1}; die
     * Konsistenz wird ueber {@link #setSystemDefault(Long)} sichergestellt.
     *
     * <p>Solange der Seed-Platzhalter aus V256 unveraendert ist (Marker
     * {@code data-system-placeholder="1"}), liefert die Methode
     * {@link Optional#empty()} — wir wollen keinen Aufforderungstext an
     * echte Empfaenger versenden. Sobald der Inhaber den Inhalt im UI
     * austauscht, faellt der Marker raus und die Signatur wird genutzt.</p>
     */
    @Transactional(readOnly = true)
    public Optional<EmailSignature> getSystemDefaultSignature() {
        return signatureRepository.findFirstByIsSystemDefaultTrue()
                .filter(sig -> !isPlatzhalter(sig))
                .map(sig -> {
                    Optional.ofNullable(sig.getImages()).ifPresent(List::size);
                    return sig;
                });
    }

    /**
     * Erkennt die unveraenderte Seed-Signatur aus V256 anhand des
     * {@code data-system-placeholder="1"}-Markers. Wird der HTML-Inhalt im
     * UI ersetzt, verschwindet der Marker und die Signatur gilt als
     * "befuellt".
     */
    public static boolean isPlatzhalter(EmailSignature sig) {
        return isPlatzhalterHtml(sig == null ? null : sig.getHtml());
    }

    /** Wie {@link #isPlatzhalter(EmailSignature)}, nur auf dem HTML. */
    public static boolean isPlatzhalterHtml(String html) {
        if (html == null) return true;
        return html.contains("data-system-placeholder=\"1\"")
                || html.contains("data-system-placeholder='1'");
    }

    /**
     * Haengt die System-Signatur an einen vorgerenderten HTML-Mail-Body an —
     * idempotent: ist die Signatur schon im Body, wird nichts dupliziert. Wenn
     * keine System-Signatur konfiguriert oder der Seed-Platzhalter noch
     * unveraendert ist, wird der Body unveraendert zurueckgegeben.
     *
     * <p>Wird von {@link AutoMahnVersandService} und
     * {@link AutoAuftragsbestaetigungVersandService} genutzt, damit alle
     * automatisch versendeten Mails einheitlich die im UI hinterlegte
     * System-Signatur tragen.</p>
     */
    @Transactional(readOnly = true)
    public String appendSystemSignatureIfConfigured(String htmlBody) {
        return getSystemDefaultSignature()
                .map(sig -> ensureSignaturePresentOnce(htmlBody, sig, null))
                .orElse(htmlBody);
    }

    /**
     * Setzt die angegebene Signatur als System-Default fuer automatische
     * E-Mails. Loescht die Markierung von allen anderen Zeilen, damit hoechstens
     * eine Signatur das Flag traegt.
     */
    @Transactional
    public EmailSignature setSystemDefault(Long signatureId) {
        EmailSignature sig = signatureRepository.findById(signatureId)
                .orElseThrow(() -> new IllegalArgumentException("Signatur nicht gefunden: " + signatureId));
        signatureRepository.clearSystemDefaultExcept(signatureId);
        if (!sig.isSystemDefault()) {
            sig.setSystemDefault(true);
            sig = signatureRepository.save(sig);
        }
        return sig;
    }

    /**
     * Signatur für eine Mail, die gerade verschickt wird. Benutzer bekommen immer ihre eigene
     * Standard-Signatur – egal, welchen Benutzer der Client mitschickt. Nur Admins dürfen mit der
     * Standard-Signatur eines anderen Benutzers versenden.
     */
    @Transactional(readOnly = true)
    public Optional<EmailSignature> getVersandSignatur(Long frontendUserId, String frontendUserDisplayName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (istAdmin(authentication)) {
            return getDefaultForFrontendUser(frontendUserId, frontendUserDisplayName);
        }
        return angemeldeteProfilId(authentication).flatMap(id -> getDefaultForFrontendUser(id, null));
    }

    /** Angemeldet und mit der Rolle {@code ADMIN}. */
    public static boolean istAdmin(Authentication authentication) {
        return istAngemeldet(authentication) && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** Benutzerprofil der Anmeldung – nie aus Angaben des Clients. */
    @Transactional(readOnly = true)
    public Optional<Long> angemeldeteProfilId(Authentication authentication) {
        if (!istAngemeldet(authentication)) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof FrontendUserPrincipal principal) {
            return Optional.ofNullable(principal.getId());
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return frontendUserProfileRepository.findByUsernameIgnoreCase(name.trim()).map(FrontendUserProfile::getId);
    }

    private static boolean istAngemeldet(Authentication authentication) {
        return authentication != null && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.isAuthenticated();
    }

    @Transactional(readOnly = true)
    public Optional<EmailSignature> getDefaultForFrontendUser(Long profileId, String frontendUserDisplayName) {
        Optional<FrontendUserProfile> profileOpt = Optional.empty();
        if (profileId != null) {
            profileOpt = frontendUserProfileRepository.findById(profileId);
        }
        if (profileOpt.isEmpty() && frontendUserDisplayName != null) {
            String normalized = frontendUserDisplayName.trim();
            if (!normalized.isEmpty()) {
                profileOpt = frontendUserProfileRepository.findByDisplayNameIgnoreCase(normalized);
            }
        }
        return profileOpt
                .map(FrontendUserProfile::getDefaultSignature)
                .map(sig -> {
                    Optional.ofNullable(sig.getImages()).ifPresent(List::size);
                    return sig;
                });
    }

    public Optional<Path> resolveImagePath(EmailSignatureImage img) {
        if (img == null) return Optional.empty();
        Path baseDir = Path.of(imageUploadDir).toAbsolutePath().normalize().resolve("signatures").resolve(String.valueOf(img.getSignature().getId()));
        Path p = baseDir.resolve(img.getStoredFilename());
        return Files.exists(p) ? Optional.of(p) : Optional.empty();
    }

    /** Bild einer Signatur; leer, wenn es das Bild nicht gibt oder es zu einer anderen Signatur gehört. */
    @Transactional(readOnly = true)
    public Optional<EmailSignatureImage> findImage(Long signatureId, Long imageId) {
        if (signatureId == null || imageId == null) return Optional.empty();
        return imageRepository.findById(imageId)
                .filter(img -> img.getSignature() != null && Objects.equals(img.getSignature().getId(), signatureId));
    }

    public Optional<EmailSignature> findById(Long id) {
        return signatureRepository.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        EmailSignature sig = signatureRepository.findById(id).orElseThrow();
        List<EmailSignatureImage> imgs = imageRepository.findBySignatureIdOrderBySortOrderAsc(id);
        for (EmailSignatureImage img : imgs) {
            try { resolveImagePath(img).ifPresent(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }); } catch (Exception ignored) {}
            imageRepository.delete(img);
        }
        signatureRepository.delete(sig);
    }

    @Transactional
    public void deleteImage(Long signatureId, Long imageId) {
        EmailSignatureImage img = imageRepository.findById(imageId).orElseThrow();
        if (img.getSignature() == null || !Objects.equals(img.getSignature().getId(), signatureId)) {
            throw new IllegalArgumentException("Image does not belong to signature");
        }
        try { resolveImagePath(img).ifPresent(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }); } catch (Exception ignored) {}
        imageRepository.delete(img);
    }

    public String renderSignatureHtmlForPreview(EmailSignature sig, String userName) {
        String html = applyVariables(sig.getHtml(), userName);
        // cid:-Verweise in Vorschaulinks umschreiben
        List<EmailSignatureImage> imgs = imageRepository.findBySignatureIdOrderBySortOrderAsc(sig.getId());
        String rewritten = org.example.kalkulationsprogramm.util.InlineAttachmentUtil.rewriteCidSources(
                html,
                imgs,
                img -> true,
                EmailSignatureImage::getCid,
                img -> "/api/email/signatures/" + sig.getId() + "/images/" + img.getId()
        );
        return ensureSignatureWrapper(rewritten, sig);
    }

    public String renderSignatureHtmlForEmail(EmailSignature sig, String userName) {
        return ensureSignatureWrapper(applyVariables(sig.getHtml(), userName), sig);
    }

    public String renderSignatureContent(EmailSignature sig, String userName) {
        return applyVariables(sig.getHtml(), userName);
    }

    public String ensureSignaturePresentOnce(String html, EmailSignature signature, String userName) {
        String base = html != null ? html : "";
        String signatureHtml = renderSignatureHtmlForEmail(signature, userName);
        if (signatureHtml.isBlank()) {
            return base;
        }
        String signatureContent = Optional.ofNullable(renderSignatureContent(signature, userName))
                .map(String::trim)
                .orElse("");
        String cleaned = stripTrailingSignatureVariant(base, signatureHtml.trim());
        if (!signatureContent.isEmpty()) {
            cleaned = stripTrailingSignatureVariant(cleaned, signatureContent);
        }
        if (!containsSignatureMarker(cleaned)) {
            cleaned = cleaned + signatureHtml;
        }
        return cleaned;
    }

    public boolean containsSignatureMarker(String html) {
        if (html == null) {
            return false;
        }
        return SIGNATURE_CLASS_PATTERN.matcher(html).find();
    }

    public Map<String, java.io.File> buildInlineCidFileMap(EmailSignature sig) {
        Map<String, java.io.File> map = new LinkedHashMap<>();
        String html = Optional.ofNullable(sig.getHtml()).orElse("");
        String hLower = html.toLowerCase(Locale.ROOT);
        List<EmailSignatureImage> imgs = imageRepository.findBySignatureIdOrderBySortOrderAsc(sig.getId());
        for (EmailSignatureImage img : imgs) {
            String cid = Optional.ofNullable(img.getCid()).orElse("");
            if (!cid.isBlank() && hLower.contains(("cid:" + cid).toLowerCase(Locale.ROOT))) {
                resolveImagePath(img).ifPresent(p -> map.put(img.getCid(), p.toFile()));
            }
        }
        return map;
    }

    private String applyVariables(String html, String userName) {
        if (html == null) return "";
        String out = html;
        if (userName != null) {
            out = out.replace("{user}", userName).replace("{USER}", userName);
        }
        return out;
    }

    private String ensureSignatureWrapper(String html, EmailSignature sig) {
        String content = html != null ? html.trim() : "";
        if (content.isEmpty()) {
            return "";
        }
        if (SIGNATURE_CLASS_PATTERN.matcher(content).find()) {
            return content;
        }
        String idAttr = sig != null && sig.getId() != null ? " data-signature-id=\"" + sig.getId() + "\"" : "";
        return "<div class=\"email-signature\"" + idAttr + ">" + content + "</div>";
    }

    private String stripTrailingSignatureVariant(String html, String variant) {
        if (html == null || variant == null || variant.isEmpty()) {
            return html != null ? html : "";
        }
        String working = html;
        String trimmedVariant = variant;
        while (true) {
            String trimmedWorking = rtrim(working);
            if (trimmedWorking.endsWith(trimmedVariant)) {
                working = trimmedWorking.substring(0, trimmedWorking.length() - trimmedVariant.length());
            } else {
                break;
            }
        }
        return working;
    }

    private String rtrim(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}

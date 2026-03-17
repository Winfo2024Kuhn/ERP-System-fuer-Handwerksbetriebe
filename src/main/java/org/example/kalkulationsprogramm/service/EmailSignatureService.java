package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.EmailSignatureImage;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.EmailSignatureImageRepository;
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    @Transactional
    public EmailSignature saveOrUpdate(EmailSignature sig) {
        EmailSignature target;
        if (sig.getId() != null) {
            target = signatureRepository.findById(sig.getId()).orElseThrow();
            target.setName(sig.getName());
            target.setHtml(sig.getHtml());
        } else {
            target = sig;
        }
        EmailSignature saved = signatureRepository.save(target);
        saved.setDefaultSignature(sig.isDefaultSignature());
        return saved;
    }

    @Transactional
    public EmailSignatureImage addImage(Long signatureId, MultipartFile file) throws IOException {
        EmailSignature sig = signatureRepository.findById(signatureId).orElseThrow();
        Path baseDir = Path.of(imageUploadDir).toAbsolutePath().normalize().resolve("signatures").resolve(String.valueOf(signatureId));
        Files.createDirectories(baseDir);
        String original = Path.of(Objects.requireNonNullElse(file.getOriginalFilename(), "image")).getFileName().toString();
        String stored = UUID.randomUUID() + "_" + original;
        Path dst = baseDir.resolve(stored).normalize();
        if (!dst.startsWith(baseDir)) {
            throw new SecurityException("Ungültiger Dateipfad: Verzeichnistraversal erkannt");
        }
        file.transferTo(dst);
        EmailSignatureImage img = new EmailSignatureImage();
        img.setSignature(sig);
        img.setCid("sig-" + UUID.randomUUID());
        img.setOriginalFilename(original);
        img.setStoredFilename(stored);
        img.setContentType(Objects.requireNonNullElse(file.getContentType(), Files.probeContentType(dst)));
        img.setSizeBytes(Files.size(dst));
        img.setSortOrder(Optional.ofNullable(sig.getImages()).map(List::size).orElse(0));
        img = imageRepository.save(img);
        sig.getImages().add(img);
        signatureRepository.save(sig);
        return img;
    }

    public Optional<Path> resolveImagePath(EmailSignatureImage img) {
        if (img == null) return Optional.empty();
        Path baseDir = Path.of(imageUploadDir).toAbsolutePath().normalize().resolve("signatures").resolve(String.valueOf(img.getSignature().getId()));
        Path p = baseDir.resolve(img.getStoredFilename());
        return Files.exists(p) ? Optional.of(p) : Optional.empty();
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

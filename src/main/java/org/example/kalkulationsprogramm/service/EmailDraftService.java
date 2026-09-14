package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.example.kalkulationsprogramm.domain.EmailDraft;
import org.example.kalkulationsprogramm.domain.EmailDraftAttachment;
import org.example.kalkulationsprogramm.dto.Email.EmailDraftDto;
import org.example.kalkulationsprogramm.repository.EmailDraftRepository;
import org.example.kalkulationsprogramm.repository.EmailDraftAttachmentRepository;
import java.util.Map;
import java.util.stream.Collectors;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailDraftService {
    // Same raw attachment budget as the composer (20 MB MIME message minus 1 MiB text).
    public static final long MAX_ATTACHMENT_BYTES = (long) ((20_000_000 - 1024 * 1024) / 1.353);
    private static final Pattern DRAFT_IMAGE_PATH = Pattern.compile("/api/(?:email/signatures/[0-9]++/images|emails/[0-9]++/attachments)/[0-9]++");
    private static final String SIGNATURE_BASE = "https://draft-signature.invalid";
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "msi", "scr", "ps1", "vbs", "js", "jar", "sh", "html", "htm", "svg");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "txt", "csv", "xml", "json", "rtf", "eml", "msg", "ics", "vcf",
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff", "heic", "heif",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
            "zip", "7z", "rar", "gz", "tar", "dwg", "dxf", "dwt", "dgn", "ifc", "stp", "step", "igs", "iges", "stl", "obj", "3dm", "skp", "tcd", "sza");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "text/plain", "text/csv", "text/xml", "application/xml", "application/json",
            "application/rtf", "text/rtf", "message/rfc822", "application/vnd.ms-outlook", "text/calendar", "text/vcard",
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/tiff", "image/heic", "image/heif",
            "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet", "application/vnd.oasis.opendocument.presentation",
            "application/zip", "application/x-zip-compressed", "application/x-7z-compressed", "application/vnd.rar", "application/x-rar-compressed", "application/gzip", "application/x-gzip", "application/x-tar",
            "application/acad", "application/x-acad", "application/x-autocad", "application/dwg", "application/x-dwg", "image/vnd.dwg", "image/x-dwg", "image/vnd.dxf", "image/x-dxf", "application/dxf", "application/x-dxf",
            "application/step", "model/step", "model/iges", "model/stl", "model/obj", "application/x-step", "application/x-iges");
    private final EmailDraftRepository repository;
    private final EmailDraftAttachmentRepository attachmentRepository;

    @Transactional(readOnly = true)
    public List<EmailDraftDto> list() {
        List<EmailDraft> drafts = repository.findAllByOrderByUpdatedAtDesc();
        if (drafts.isEmpty()) return List.of();
        Map<Long, List<EmailDraftDto.Attachment>> metadata = attachmentRepository
                .findMetadata(drafts.stream().map(EmailDraft::getId).toList()).stream().collect(Collectors.groupingBy(
                        EmailDraftAttachmentRepository.Metadata::getDraftId,
                        Collectors.mapping(this::toAttachmentDto, Collectors.toList())));
        return drafts.stream().map(draft -> toDto(draft, metadata.getOrDefault(draft.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public long count() { return repository.count(); }

    @Transactional(readOnly = true)
    public EmailDraftDto get(Long id) {
        EmailDraft draft = requireDraft(id);
        return toDto(draft, attachmentRepository.findMetadata(List.of(id)).stream().map(this::toAttachmentDto).toList());
    }

    public EmailDraftDto save(Long id, EmailDraftDto dto, List<MultipartFile> files) {
        validate(dto);
        // Read and validate every file before mutating the existing draft.
        List<EmailDraftAttachment> attachments = files == null ? null : readAttachments(files);
        EmailDraft draft = id == null ? new EmailDraft() : requireDraft(id);
        draft.setRecipient(dto.recipient());
        draft.setCc(dto.cc());
        draft.setSubject(dto.subject());
        draft.setBody(sanitizeDraftBody(dto.body()));
        draft.setFromAddress(dto.fromAddress());
        draft.setReplyEmailId(dto.replyEmailId());
        draft.setProjektId(dto.projektId());
        draft.setAnfrageId(dto.anfrageId());
        if (dto.geschaeftsdokument() != null) draft.setGeschaeftsdokument(dto.geschaeftsdokument());
        if (attachments != null) {
            draft.getAttachments().clear();
            attachments.forEach(attachment -> attachment.setDraft(draft));
            draft.getAttachments().addAll(attachments);
        }
        return toDto(repository.save(draft));
    }

    public void delete(Long id) { repository.delete(requireDraft(id)); }

    @Transactional(readOnly = true)
    public void validateForSending(Long draftId, Long replyEmailId) {
        if (draftId == null) return;
        EmailDraft draft = requireDraft(draftId);
        if (!java.util.Objects.equals(draft.getReplyEmailId(), replyEmailId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Entwurf gehört zu einer anderen Antwort. Bitte erneut öffnen.");
    }

    /** SMTP cannot be rolled back. Keep its successful completion independent of later archiving. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void deleteAfterSuccessfulSend(Long draftId) {
        if (draftId == null) return;
        requirePositive(draftId);
        repository.findById(draftId).ifPresent(repository::delete);
        repository.flush();
    }

    @Transactional(readOnly = true)
    public Download download(Long draftId, Long attachmentId) {
        requirePositive(attachmentId);
        requirePositive(draftId);
        EmailDraftAttachment attachment = attachmentRepository.findByIdAndDraftId(attachmentId, draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anhang nicht gefunden."));
        return new Download(attachment.getFilename(), attachment.getContentType(), attachment.getData());
    }

    public record Download(String filename, String contentType, byte[] data) { }

    private EmailDraft requireDraft(Long id) {
        requirePositive(id);
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Entwurf nicht gefunden."));
    }

    private void requirePositive(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Kennnummer.");
    }

    private void validate(EmailDraftDto dto) {
        if (dto == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entwurf fehlt.");
        checkLength(dto.recipient(), 10_000);
        checkLength(dto.cc(), 10_000);
        checkLength(dto.subject(), 10_000);
        checkLength(dto.body(), 1_000_000);
        checkLength(dto.fromAddress(), 255);
        for (Long id : new Long[] {dto.replyEmailId(), dto.projektId(), dto.anfrageId()}) {
            if (id != null) requirePositive(id);
        }
        if (dto.projektId() != null && dto.anfrageId() != null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte nur ein Projekt oder eine Anfrage verknüpfen.");
    }

    private void checkLength(String value, int max) {
        if (value != null && value.length() > max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ein Textfeld ist zu lang.");
    }

    private List<EmailDraftAttachment> readAttachments(List<MultipartFile> files) {
        if (files.size() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Höchstens 100 Anhänge sind erlaubt.");
        long total = 0;
        List<EmailDraftAttachment> result = new ArrayList<>();
        for (MultipartFile file : files) {
            total += file.getSize();
            if (total > MAX_ATTACHMENT_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Die Anhänge sind zusammen zu groß.");
            String name;
            try {
                String original = file.getOriginalFilename();
                if (original == null || original.isBlank()) throw new IllegalArgumentException();
                name = Path.of(original.replace('\\', '/')).getFileName().toString()
                        .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
            } catch (RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Dateiname.");
            }
            if (name.isBlank() || name.length() > 255 || name.equals(".") || name.equals(".."))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Dateiname.");
            String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (BLOCKED_EXTENSIONS.contains(extension) || !ALLOWED_EXTENSIONS.contains(extension))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dieser Dateityp ist als Anhang nicht erlaubt.");
            EmailDraftAttachment attachment = new EmailDraftAttachment();
            attachment.setFilename(name);
            String type = file.getContentType();
            if (type == null || type.isBlank()) type = "application/octet-stream";
            type = type.toLowerCase(Locale.ROOT);
            if (!type.equals("application/octet-stream") && !ALLOWED_CONTENT_TYPES.contains(type))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dieser Dateityp ist als Anhang nicht erlaubt.");
            attachment.setContentType(type);
            try {
                byte[] data = file.getBytes();
                // Renaming a program to .txt must not bypass the extension checks.
                boolean executable = data.length >= 2 && ((data[0] == 'M' && data[1] == 'Z') || (data[0] == '#' && data[1] == '!'));
                executable |= data.length >= 4 && data[0] == 0x7f && data[1] == 'E' && data[2] == 'L' && data[3] == 'F';
                if (executable) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ausführbare Dateien sind als Anhang nicht erlaubt.");
                attachment.setData(data);
                attachment.setSize(data.length);
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anhang konnte nicht gelesen werden.");
            }
            result.add(attachment);
        }
        return result;
    }

    private String sanitizeDraftBody(String html) {
        if (html == null) return null;
        // Jsoup's protocol validation drops relative images without a base URI.
        // Preserve only known signature/quoted-email image routes through the existing sanitizer.
        Document original = Jsoup.parseBodyFragment(html);
        original.outputSettings().prettyPrint(false);
        for (Element image : original.select("img[src]")) {
            String source = image.attr("src");
            if (DRAFT_IMAGE_PATH.matcher(source).matches()) image.attr("src", SIGNATURE_BASE + source);
        }
        Document clean = Jsoup.parseBodyFragment(EmailHtmlSanitizer.sanitizeDetailHtml(original.body().html()));
        clean.outputSettings().prettyPrint(false);
        for (Element image : clean.select("img[src]")) {
            String source = image.attr("src");
            if (source.startsWith(SIGNATURE_BASE) && DRAFT_IMAGE_PATH.matcher(source.substring(SIGNATURE_BASE.length())).matches())
                image.attr("src", source.substring(SIGNATURE_BASE.length()));
        }
        return clean.body().html();
    }

    private EmailDraftDto toDto(EmailDraft draft) {
        return toDto(draft, draft.getAttachments().stream().map(file -> new EmailDraftDto.Attachment(file.getId(),
                file.getFilename(), file.getContentType(), file.getSize())).toList());
    }

    private EmailDraftDto.Attachment toAttachmentDto(EmailDraftAttachmentRepository.Metadata metadata) {
        return new EmailDraftDto.Attachment(metadata.getId(), metadata.getFilename(), metadata.getContentType(), metadata.getSize());
    }

    private EmailDraftDto toDto(EmailDraft draft, List<EmailDraftDto.Attachment> attachments) {
        return new EmailDraftDto(draft.getId(), draft.getRecipient(), draft.getCc(), draft.getSubject(),
                draft.getBody(), draft.getFromAddress(), draft.getReplyEmailId(), draft.getProjektId(),
                draft.getAnfrageId(), draft.isGeschaeftsdokument(), draft.getCreatedAt(), draft.getUpdatedAt(), attachments);
    }
}

package org.example.kalkulationsprogramm.service.mail.liste;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailOrdner;
import org.example.kalkulationsprogramm.domain.EmailPostfachZuordnung;
import org.example.kalkulationsprogramm.dto.Email.UnifiedEmailDto;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachRefDto;
import org.example.kalkulationsprogramm.service.EmailThreadService;
import org.example.kalkulationsprogramm.service.GelesenStand;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.example.kalkulationsprogramm.util.EmailVorschauText;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Baut das {@link UnifiedEmailDto} für Mail-Listen: Ordnerlisten, Suche und die gemeinsamen Teile
 * der Detailansicht (Zuordnung, Postfach-Schilder, Verlauf, Ordner, Anhang-Metadaten).
 *
 * <p>Listen-DTOs sind leicht: kein HTML, Vorschau statt ganzem Text, Anhänge nur als Metadaten.
 * Kunden-Abgleich und Antwort-Postfach bleiben der Detailansicht vorbehalten (pro Zeile wären das
 * eigene Abfragen).</p>
 */
@Component
@RequiredArgsConstructor
public class EmailListenDtoMapper {

    /** Länge der Vorschau in der Mail-Liste (Zeichen, ohne zitierten Verlauf). */
    static final int VORSCHAU_LAENGE = 200;

    private final EmailThreadService emailThreadService;

    /** Listen-DTO; {@code gelesen} sagt, was der angemeldete Benutzer gelesen hat. */
    public UnifiedEmailDto listenDto(Email email, GelesenStand gelesen) {
        UnifiedEmailDto dto = new UnifiedEmailDto();
        dto.setId(email.getId());
        dto.setFromAddress(email.getFromAddress());
        dto.setRecipient(email.getRecipient());
        dto.setSubject(email.getSubject());
        dto.setCc(email.getCc());
        dto.setBody(vorschau(email));
        dto.setSentAt(email.getSentAt());
        dto.setRead(gelesen.istGelesen(email));
        dto.setStarred(email.isStarred());
        dto.setDirection(email.getDirection() != null ? email.getDirection().name() : null);
        dto.setZuordnungTyp(email.getZuordnungTyp() != null ? email.getZuordnungTyp().name() : null);
        dto.setSpamScore(email.getSpamScore());
        zuordnungsInfo(email, dto);
        postfachSchilder(email, dto);
        dto.setFolder(EmailOrdner.von(email).schluessel());
        threadInfo(email, dto);
        zustellung(email, dto);
        dto.setHasAttachments(email.getAttachments() != null && !email.getAttachments().isEmpty());
        if (dto.isHasAttachments()) {
            dto.setAttachments(anhaenge(email));
        }
        return dto;
    }

    /**
     * Vorschau: erst den zitierten Verlauf entfernen, dann kürzen – sonst bleibt ein halber
     * Zitatkopf („Am Do., 17. Sept. 20…“) in der Liste stehen.
     */
    private static String vorschau(Email email) {
        String klartext = email.getBody();
        if ((klartext == null || klartext.isBlank()) && email.getHtmlBody() != null) {
            klartext = EmailHtmlSanitizer.htmlToPlainText(email.getHtmlBody());
        }
        return EmailVorschauText.vorschau(klartext, VORSCHAU_LAENGE);
    }

    /** Zuordnung (Projekt, Anfrage, Lieferant) – das E-Mail-Center verlinkt darauf. */
    public void zuordnungsInfo(Email email, UnifiedEmailDto dto) {
        if (email.getProjekt() != null) {
            dto.setProjektId(email.getProjekt().getId());
            dto.setProjektName(email.getProjekt().getBauvorhaben());
            dto.setProjektAuftragsnummer(email.getProjekt().getAuftragsnummer());
        }
        if (email.getAnfrage() != null) {
            dto.setAnfrageId(email.getAnfrage().getId());
            dto.setAnfrageName(email.getAnfrage().getBauvorhaben());
        }
        if (email.getLieferant() != null) {
            dto.setLieferantId(email.getLieferant().getId());
            dto.setLieferantName(email.getLieferant().getLieferantenname());
        }
    }

    /** Postfach-Schilder aus den gebündelt geladenen Zuordnungen – keine Abfrage pro Zeile. */
    public void postfachSchilder(Email email, UnifiedEmailDto dto) {
        if (email.getPostfachZuordnungen() != null) {
            dto.setPostfaecher(email.getPostfachZuordnungen().stream()
                    .map(EmailPostfachZuordnung::getPostfach)
                    .filter(java.util.Objects::nonNull)
                    .map(PostfachRefDto::von)
                    .toList());
        }
    }

    /** Verlaufs-Felder: alle Mitglieder eines Verlaufs liefern dieselben Werte. */
    public void threadInfo(Email email, UnifiedEmailDto dto) {
        if (email.getParentEmail() != null) {
            dto.setParentEmailId(email.getParentEmail().getId());
        }
        EmailThreadService.ThreadKennzahlen kennzahlen = emailThreadService.kennzahlenFuer(email);
        dto.setThreadRootId(kennzahlen.rootId());
        dto.setReplyCount(Math.max(0, kennzahlen.anzahl() - 1));
        dto.setThreadLastActivityAt(kennzahlen.letzteAktivitaet());
    }

    /**
     * Zustellstatus: betrifft besonders den Funnel-Pfad – dort tippen Kunden ihre Adresse selbst,
     * entsprechend häufig sind Vertipper.
     */
    public void zustellung(Email email, UnifiedEmailDto dto) {
        dto.setZustellStatus(email.getZustellStatus() != null ? email.getZustellStatus().name() : null);
        dto.setZustellFehler(email.getZustellFehler());
    }

    /** Anhang-Metadaten ohne CID-Umschreibung; leer, wenn die Mail keine hat. */
    public List<UnifiedEmailDto.AttachmentDto> anhaenge(Email email) {
        if (email.getAttachments() == null) {
            return List.of();
        }
        return email.getAttachments().stream().map(att -> {
            UnifiedEmailDto.AttachmentDto attDto = new UnifiedEmailDto.AttachmentDto();
            attDto.setId(att.getId());
            attDto.setOriginalFilename(att.getOriginalFilename());
            attDto.setMimeType(att.getMimeType());
            attDto.setFileSize(att.getSizeBytes());
            attDto.setContentId(att.getContentId());
            attDto.setInline(Boolean.TRUE.equals(att.getInlineAttachment()));
            return attDto;
        }).collect(java.util.stream.Collectors.toList());
    }
}

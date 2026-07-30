package org.example.kalkulationsprogramm.dto.ProjektEmail;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.EmailDirection;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjektEmailDto {
    private Long id;
    private EmailDirection direction;
    private String from;
    private String to;
    private String subject;
    private LocalDateTime sentAt;
    private String bodyHtml;
    private List<ProjektEmailFileDto> attachments;
    private String benutzer;
    private Long frontendUserId;

    // Felder für E-Mail-Versand
    private String sender;
    private String body;
    private List<String> recipients;
    private List<String> cc;

    // Explizite Zuordnung beim Antworten
    private Long projektId;
    private Long anfrageId;
    private Long lieferantId;

    // Thread-Unterstützung
    private Long parentEmailId;
    private int replyCount;

    /**
     * Zustell-Ergebnis der Ausgangsmail ("OFFEN" / "UNZUSTELLBAR"). Nur bei
     * {@code direction == OUT} aussagekraeftig. Der EmailsTab blendet bei
     * "UNZUSTELLBAR" eine Warnung ein — sonst sieht eine nie angekommene Mail
     * genauso aus wie eine erfolgreich versendete.
     */
    private String zustellStatus;

    /** Klartext-Grund der Ablehnung, z.B. "unknown user". */
    private String zustellFehler;
}

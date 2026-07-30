package org.example.kalkulationsprogramm.dto.Kunde;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.EmailDirection;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KundeKommunikationDto {
    private Long id;
    private Long referenzId;
    private String referenzTyp;
    private String referenzName;
    private String subject;
    private String absender;
    private String empfaenger;
    private LocalDateTime zeitpunkt;
    private EmailDirection direction;
    private String snippet;
    private String body;
    private java.util.List<KundeEmailAttachmentDto> attachments;

    // Thread-Unterstützung
    private Long parentEmailId;
    private int replyCount;

    /**
     * Zustell-Ergebnis der Ausgangsmail ("OFFEN" / "UNZUSTELLBAR"). Nur bei
     * {@code direction == OUT} aussagekraeftig. Das Frontend blendet bei
     * "UNZUSTELLBAR" eine Warnung ein — sonst haette der Nutzer keinen
     * Hinweis darauf, dass die Mail nie ankam.
     */
    private String zustellStatus;

    /** Klartext-Grund der Ablehnung, z.B. "unknown user". */
    private String zustellFehler;
}

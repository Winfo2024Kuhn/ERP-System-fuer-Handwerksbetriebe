package org.example.kalkulationsprogramm.dto.Freigabe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Audit-Trail einer akzeptierten digitalen Freigabe — wird im Frontend als Detail-Popover
 * unter dem "Angenommen"-Badge angezeigt. Enthält die rechtlich relevanten Beweisdaten
 * (Wer, Wann, Wo, Hash). Im Streitfall kann hieraus eine Annahmebescheinigung als PDF
 * generiert werden.
 *
 * <p>Der Hash ist ein SHA-256 über Original-Hash + Akzeptanzdaten und damit unveränderbar.
 * Das Frontend zeigt ihn gekürzt mit Copy-Button für den vollständigen Wert.</p>
 */
@Getter
@Builder
@AllArgsConstructor
public class FreigabeAuditDto
{
    private String status;
    private String dokumentArt;
    private String dokumentNummer;
    private LocalDateTime erstelltAm;
    private LocalDateTime ablaufDatum;
    private LocalDateTime akzeptiertAm;
    private String akzeptiertEmail;
    private String akzeptiertIp;
    private String akzeptiertUserAgent;
    private String hashOriginal;
    private String hashAcceptance;
}

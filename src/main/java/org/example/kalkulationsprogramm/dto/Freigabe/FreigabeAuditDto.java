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
    /**
     * Vor- und Nachname der konkret klickenden Person — Teil der Beweissicherung
     * für die digitale Auftragsannahme. Bei Firmenkunden die vertretungsberechtigte
     * Person. Für Altbestand (Akzeptanzen vor V317) {@code null}.
     */
    private String unterzeichnerVorname;
    private String unterzeichnerNachname;
    private String unterzeichnerName;
    private String hashOriginal;
    private String hashAcceptance;
    /** Zeitpunkt, zu dem der E-Mail-Code bei der Annahme stimmte (seit V415, sonst {@code null}). */
    private LocalDateTime codeBestaetigtAm;
    /** Adresse, an die der bestätigte Code ging. */
    private String codeBestaetigtEmail;
    /**
     * Rechtsnachweis (seit V423): {@code true} = Kunde verlangt Beginn vor Ablauf der
     * Widerrufsfrist, {@code false} = nicht verlangt, {@code null} = ältere Annahme.
     */
    private Boolean vorzeitigerBeginn;
    private String widerrufStand;
    private String datenschutzStand;
    private String bestaetigungText;
    private String knopfText;
}

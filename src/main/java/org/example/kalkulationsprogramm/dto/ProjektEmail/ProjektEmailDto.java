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

    /** Entwurf, der nach bestätigtem SMTP-Versand serverseitig gelöscht wird. */
    private Long draftId;

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

    /**
     * Markiert diese Mail als Versand eines Ausgangsgeschäftsdokuments
     * (Rechnung, Angebot, Auftragsbestätigung, Mahnung).
     *
     * <p>Setzt der Dokument-Editor. Nur solche Mails gehen über das separat
     * konfigurierbare Postfach für Geschäftsdokumente raus; freier
     * Schriftverkehr bleibt auf dem Standard-Postfach. Das Kennzeichen kommt
     * bewusst vom Editor statt aus einer Ableitung im Backend: Der Anhang wird
     * als gewöhnliche Datei hochgeladen, dem Backend fehlt also jeder
     * verlässliche Hinweis auf die Dokumentart.</p>
     */
    private boolean geschaeftsdokument;

    /** Absender-Postfach einer neuen Mail ("Senden von"). Bei Antworten und Weiterleitungen ignoriert. */
    private Long postfachId;

    /**
     * Weiterleitung dieser Mail: Der Absender ist dann fest das Postfach, in dem sie
     * ankam – {@link #postfachId} wird ignoriert.
     */
    private Long weitergeleitetVonEmailId;

    /**
     * Weiterleitung: Anhänge der Original-Mail, die mitgehen sollen. Der Server liest sie
     * selbst aus der Original-Mail; ohne {@link #weitergeleitetVonEmailId} wirkungslos.
     */
    private List<Long> weiterleitungsAnhangIds;

    /** Sammel-Mail: jeder Empfänger bekommt eine eigene Mail und sieht nur sich selbst. */
    private boolean einzelversand;

    /**
     * Versand eines Angebots aus dem Dokument-Editor: Nach erfolgreichem Versand werden die
     * offenen Online-Links anderer Angebote desselben Vorgangs zurückgezogen (siehe
     * {@code FreigabeAbloesungService}). Der Editor setzt das Feld nur, wenn der Anwender
     * die Rückfrage dazu bestätigt hat.
     */
    private Long freigabenAbloesenFuerDokumentId;
}

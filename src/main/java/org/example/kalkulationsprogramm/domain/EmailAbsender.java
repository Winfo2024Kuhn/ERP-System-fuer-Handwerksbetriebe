package org.example.kalkulationsprogramm.domain;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * E-Mail-Postfach des Betriebs (in der Oberfläche „Postfach“).
 *
 * <p>Historisch nur eine Absender-Adresse, die einzelnen
 * {@link FrontendUserProfile}-Einträgen zugewiesen wird. Mit eigenem Zugang
 * (Benutzername, Passwort, Server) wird daraus ein echtes Postfach: Das ERP ruft es
 * ab und verschickt darüber. Ohne vollständigen Zugang bleibt es eine reine
 * Absender-Adresse, die über das Hauptpostfach versendet wird – wie bisher.</p>
 *
 * <p>Genau ein Postfach ist {@link #hauptpostfach} (z. B. info@), höchstens eins
 * verschickt {@link #fuerGeschaeftsdokumente Rechnungen und Mahnungen}.</p>
 *
 * <p>Wer ein Postfach im E-Mail-Center sieht, regeln {@link #sichtbarFuerAlle} und die
 * Freigaben für Abteilungen und Benutzer (ausgewertet im {@code PostfachSichtbarkeitService}).
 * Das Hauptpostfach sieht immer jeder.</p>
 */
@Getter
@Setter
@Entity
@Table(
        name = "email_absender",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_email_absender_adresse", columnNames = "email_adresse")
        }
)
public class EmailAbsender {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_adresse", nullable = false, length = 255)
    private String emailAdresse;

    @Column(name = "anzeigename", length = 255)
    private String anzeigename;

    @Column(name = "aktiv", nullable = false)
    private boolean aktiv = true;

    @Column(name = "sortierung", nullable = false)
    private int sortierung = 0;

    /** Login am Mail-Server; leer = {@link #emailAdresse}. */
    @Column(name = "benutzername", length = 255)
    private String benutzername;

    /** Nur verschlüsselt (MailSecretService), nie im Klartext und nie ausgeliefert. */
    @JsonIgnore
    @Column(name = "passwort_verschluesselt", columnDefinition = "TEXT")
    private String passwortVerschluesselt;

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "imap_host", length = 255)
    private String imapHost;

    @Column(name = "imap_port")
    private Integer imapPort;

    @Column(name = "hauptpostfach", nullable = false)
    private boolean hauptpostfach = false;

    @Column(name = "fuer_geschaeftsdokumente", nullable = false)
    private boolean fuerGeschaeftsdokumente = false;

    @Column(name = "letzter_abruf_am")
    private LocalDateTime letzterAbrufAm;

    /** Letzter Abruf-Fehler in Klartext; {@code null} = letzter Abruf hat geklappt. */
    @Column(name = "letzter_abruf_fehler", length = 500)
    private String letzterAbrufFehler;

    /**
     * {@code true} (Standard): jeder im Betrieb sieht das Postfach. {@code false}: nur die
     * freigegebenen Abteilungen und Benutzer, der Inhaber und Admins.
     */
    @Column(name = "sichtbar_fuer_alle", nullable = false)
    private boolean sichtbarFuerAlle = true;

    /**
     * Postfach läuft aus (z. B. die alte T-Online-Adresse): Mails kommen weiter an und sind
     * sichtbar, aber Antworten gehen über das Hauptpostfach und für neue Mails ist es nicht
     * mehr wählbar. Nie beim Haupt- oder Rechnungs-Postfach.
     */
    @Column(name = "laeuft_aus", nullable = false)
    private boolean laeuftAus = false;

    /**
     * Freigegebene Abteilungen. Bleibt bei „für alle sichtbar“ gespeichert (wirkt dann nicht),
     * damit ein späteres Umschalten die Auswahl nicht verliert. Nie als JSON ausliefern.
     */
    @JsonIgnore
    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(name = "email_absender_abteilung",
            joinColumns = @JoinColumn(name = "postfach_id"),
            inverseJoinColumns = @JoinColumn(name = "abteilung_id"))
    private Set<Abteilung> sichtbarFuerAbteilungen = new LinkedHashSet<>();

    /** Einzeln freigegebene Benutzer; sonst wie {@link #sichtbarFuerAbteilungen}. */
    @JsonIgnore
    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(name = "email_absender_benutzer",
            joinColumns = @JoinColumn(name = "postfach_id"),
            inverseJoinColumns = @JoinColumn(name = "frontend_user_profile_id"))
    private Set<FrontendUserProfile> sichtbarFuerBenutzer = new LinkedHashSet<>();

    /** Login für SMTP/IMAP: eigener Benutzername oder – wie meist bei Hetzner – die Adresse selbst. */
    public String effektiverBenutzername() {
        return benutzername != null && !benutzername.isBlank() ? benutzername.trim() : emailAdresse;
    }
}

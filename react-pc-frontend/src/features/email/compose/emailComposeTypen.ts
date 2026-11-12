import type { ProjektDetail } from '../../../types';
import type { EmailAttachment } from '../emailCenterModel';
import type { PostfachKurz } from '../postfach';
import type { EmailComposeKopfDaten } from './emailComposeAnzeige';

export interface EmailComposeFormProps {
    onClose: () => void;
    /** Registers a save guard for parent navigation; false keeps the editor open. */
    onBeforeLeave?: (save: (() => Promise<boolean>) | null) => void;
    // For projects
    projektId?: number;
    projekt?: ProjektDetail;
    // For offers (Anfragen)
    anfrageId?: number;
    anfrage?: {
        bauvorhaben: string;
        kundenName?: string;
        kundenEmails?: string[];
        kundenAnrede?: string;
        kundenAnsprechpartner?: string;
    };
    /** Customer ID for saving new email addresses */
    kundeId?: number;
    initialRecipient?: string;
    /** CC-Empfänger, z. B. bei "Allen antworten" */
    initialCc?: string[];
    initialSubject?: string;
    initialBody?: string;
    /** Zitat-Block für Antworten – wird NACH der Signatur eingefügt (richtige Reihenfolge: Text → Signatur → Zitat) */
    replyQuote?: string;
    /** ID der Email, auf die geantwortet wird – nutzt /{replyEmailId}/reply statt /send */
    replyEmailId?: number;
    /**
     * Weiterleitung: ID der Original-Mail. Das Backend schickt dann fest über
     * deren Antwort-Postfach – eine Absender-Auswahl gibt es nicht.
     */
    weitergeleitetVonEmailId?: number;
    /**
     * Weiterleitung: Anhänge der Original-Mail, sofern der Aufrufer sie schon geladen hat.
     * Fehlen sie (z. B. beim Wiederöffnen eines Weiterleitungs-Entwurfs), holt das Formular
     * die Original-Mail selbst. Eingebettete Bilder werden nicht angeboten.
     */
    weiterleitungsAnhaenge?: EmailAttachment[];
    /**
     * Fester Absender bei Antwort/Weiterleitung (`antwortPostfach` der Original-Mail),
     * sofern der Aufrufer ihn schon kennt. Fehlt er, holt das Formular ihn selbst.
     */
    antwortPostfach?: PostfachKurz | null;
    /** Pre-attached files (e.g. generated PDF from DocumentEditor) */
    initialAttachments?: File[];
    /**
     * Markiert den Versand als Ausgangsgeschäftsdokument (Rechnung, Angebot,
     * Auftragsbestätigung). Setzt der Dokument-Editor.
     *
     * <p>Ist ein eigenes Postfach für Geschäftsdokumente eingerichtet, geht die
     * Mail darüber raus und der Absender ist fest vorgegeben – eine frei
     * gewählte Adresse würde nicht zum versendenden Postfach passen und beim
     * Empfänger an SPF/DKIM scheitern.</p>
     */
    geschaeftsdokument?: boolean;
    onSuccess?: () => void;
    variant?: 'default' | 'modal';
    /**
     * `ohne`: kein eigener Kopf mit X. Für Dialoge, die selbst einen Kopf mit Schließen-Knopf
     * haben (Verlauf im E-Mail-Reiter) – sonst stünden zwei X übereinander.
     */
    kopf?: 'eigen' | 'ohne';
    /** Beschriftung des Sekundärknopfs unten (sichert den Entwurf, dann `onClose`). Standard: „Schließen“. */
    schliessenText?: string;
    /**
     * Bei `kopf="ohne"`: meldet dem fremden Kopf den aktuellen Betreff und ob Verlassen gerade
     * gesperrt ist (mit Grund, z. B. „Wird gesendet…“). `null` beim Abbauen des Formulars.
     */
    onKopfDaten?: (daten: EmailComposeKopfDaten | null) => void;
    /** Existing draft ID to resume editing */
    draftId?: number;
    /**
     * Erlaubt es, die Zuordnung zu Projekt/Anfrage im Formular zu wählen und zu ändern
     * (E-Mail-Center). Aus einer Projekt- oder Anfrage-Seite heraus steht der Vorgang
     * bereits fest – dort bleibt das Feld aus.
     */
    zuordnungWaehlbar?: boolean;
    /**
     * Wird nach dem Klick auf „E-Mail senden“ und vor dem eigentlichen Versand
     * aufgerufen (z. B. Rechnung buchen). `weiter: false` bricht ab, ohne zu senden –
     * die Fehlermeldung zeigt der Aufrufer selbst. `hinweisBeiSendefehler` wird an
     * die Meldung gehängt, falls der Versand danach scheitert.
     */
    vorDemSenden?: () => Promise<VorDemSendenErgebnis>;
    /**
     * Sperrt Vorschau und Download der automatisch angehängten Dateien und zeigt
     * stattdessen diesen Hinweis (z. B. ungebuchte Rechnung: sonst ließe sie sich
     * am Buchen vorbei herunterladen).
     */
    anhangVorschauGesperrt?: string;
    /**
     * `false`: kein automatischer Entwurf. Nötig, wenn beim Senden noch etwas
     * passieren muss (Buchen, alte Online-Links ablösen) – ein später aus der
     * Entwurfsliste verschickter Entwurf würde das sonst überspringen.
     */
    entwurfSpeichern?: boolean;
    /** Angebotsversand: nach dem Senden offene Online-Links anderer Angebote zurückziehen. */
    freigabenAbloesenFuerDokumentId?: number;
    /**
     * Die automatisch angehängten Dateien sind Pflicht (z. B. die Rechnung, die beim Senden
     * gebucht wird). Fehlt eine, ist „Senden“ gesperrt und dieser Hinweis erscheint.
     */
    pflichtanhangHinweis?: string;
    /** Text der Erfolgsmeldung nach dem Senden. Standard: „E-Mail wurde gesendet.“ */
    erfolgsMeldung?: string;
}

export interface VorDemSendenErgebnis {
    weiter: boolean;
    hinweisBeiSendefehler?: string;
}

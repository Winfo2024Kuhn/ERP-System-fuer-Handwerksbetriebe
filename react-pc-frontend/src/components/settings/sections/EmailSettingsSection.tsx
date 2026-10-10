import { PostfachSettings } from './postfach/PostfachSettings';

/**
 * Einstellungen → E-Mail.
 *
 * <p>Früher standen hier zwei feste Kästen („Ihr Postfach“ und „Postfach für
 * Rechnungen und Mahnungen“) plus getrennte SMTP-/IMAP-Server. Seit es beliebig
 * viele Postfächer gibt, sind das alles normale Postfächer: das Hauptpostfach
 * trägt den Haken „Hauptpostfach“, das Rechnungs-Postfach den Haken „Für
 * Rechnungen &amp; Mahnungen“, und die Server-Angaben stehen je Postfach.</p>
 */
export function EmailSettingsSection({ onSaved }: { onSaved?: () => void }) {
    return (
        <div className="space-y-6">
            <PostfachSettings onSaved={onSaved} />
        </div>
    );
}

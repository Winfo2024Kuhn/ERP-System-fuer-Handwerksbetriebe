import { useCallback, useEffect, useRef, useState, type RefObject } from 'react';
import { useToast } from '../../../components/ui/toast';
import { sanitizeHtml } from '../../../lib/htmlSanitizer';
import { aktuellesFrontendProfil } from '../../../lib/frontendProfil';
import { formuliereMitKi, ladeStandardSignatur, setzeEditorHtml, type KiStil } from '../mailEditorHtml';

const KI_FEHLER = 'Formulierung fehlgeschlagen. Bitte erneut versuchen.';

interface NachrichtOptionen {
    editorRef: RefObject<HTMLDivElement | null>;
    initialBody: string;
    /** Zitat für Antworten – kommt nach der Signatur (Text → Signatur → Zitat). */
    replyQuote?: string;
    initialDraftId?: number;
    /** Projekt/Anfrage als Zusammenhang für die KI. */
    vorgangName: string;
    markDirty: () => void;
    setError: (meldung: string | null) => void;
}

/**
 * Inhalt des Nachrichtenfelds: Startinhalt mit Signatur (und Zitat bei
 * Antworten), Wiederherstellen eines Entwurfs, KI-Formulierung. Das Feld selbst
 * ist ungesteuert; `body` spiegelt es für Entwurf und Versand.
 */
export function useNachricht({
    editorRef, initialBody, replyQuote, initialDraftId, vorgangName, markDirty, setError,
}: NachrichtOptionen) {
    // Bringt der Startinhalt seine Signatur schon mit (z. B. aus dem Dokument-Editor),
    // steht der bereinigte Text sofort fest; sonst ergänzt der Mount-Effekt die Signatur.
    const startTextMitSignatur = !initialDraftId && !replyQuote && !!initialBody && /email-signature/i.test(initialBody);
    const [body, setBody] = useState<string>(() => (startTextMitSignatur ? sanitizeHtml(initialBody) : initialBody || ''));
    const [signature, setSignature] = useState('');
    const [beautifying, setBeautifying] = useState(false);
    const toast = useToast();

    const loadSignature = useCallback(() =>
        ladeStandardSignatur(aktuellesFrontendProfil()?.id).then(sig => {
            if (sig) setSignature(sig);
            return sig;
        }), []);

    // Startinhalt – nur einmal beim Öffnen.
    useEffect(() => {
        if (initialDraftId) return;
        if (replyQuote) {
            // Antwort: Schreibbereich → Signatur → Zitat, Cursor an den Anfang.
            loadSignature().then(sig => {
                setBody(setzeEditorHtml(editorRef.current, `<p><br></p>${sig}<br>${replyQuote}`));
                if (!editorRef.current) return;
                const range = document.createRange();
                range.setStart(editorRef.current, 0);
                range.collapse(true);
                const auswahl = window.getSelection();
                auswahl?.removeAllRanges();
                auswahl?.addRange(range);
            });
        } else if (!initialBody) {
            loadSignature().then(sig => setBody(setzeEditorHtml(editorRef.current, `<p><br></p>${sig}`)));
        } else if (/email-signature/i.test(initialBody)) {
            // Signatur steckt schon drin (z. B. Entwurf); body steht seit dem ersten Rendern fest.
            setzeEditorHtml(editorRef.current, initialBody);
        } else {
            loadSignature().then(sig => setBody(setzeEditorHtml(editorRef.current, `${initialBody}${sig}`)));
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Ein geladener Entwurf kommt ins Feld, sobald es nach der Ladeanzeige wieder da ist.
    // Nur das Wiederherstellen darf den Inhalt ersetzen; Tastendrücke behalten ihren Cursor.
    const entwurfOffen = useRef(false);
    useEffect(() => {
        if (!entwurfOffen.current || !editorRef.current) return;
        entwurfOffen.current = false;
        setzeEditorHtml(editorRef.current, body);
    });

    const entwurfEinsetzen = (html: string) => {
        entwurfOffen.current = true;
        setBody(sanitizeHtml(html));
    };

    const beimTippen = () => {
        markDirty();
        setBody(editorRef.current?.innerHTML || '');
    };

    // KI im gewählten Stil: nur der eigene Text, Signatur und Zitat bleiben stehen.
    const formuliere = async (stil: KiStil) => {
        setBeautifying(true);
        setError(null);
        try {
            const ergebnis = await formuliereMitKi(editorRef.current?.innerHTML || body, {
                kontext: vorgangName ? `Projekt/Anfrage: ${vorgangName}` : null,
                ersatzSignaturHtml: signature,
                stil,
            });
            if (ergebnis.status === 'kein-text') return setError('Kein Text zum Umformulieren gefunden.');
            if (ergebnis.status === 'kein-vorschlag') return setError('Keine alternative Formulierung erhalten.');
            if (ergebnis.status === 'abgelehnt') return setError(ergebnis.meldung);
            markDirty();
            setBody(setzeEditorHtml(editorRef.current, ergebnis.html));
        } catch (err) {
            console.error('KI-Verschönerung fehlgeschlagen:', err);
            setError(KI_FEHLER);
            toast.error(KI_FEHLER);
        } finally {
            setBeautifying(false);
        }
    };

    /** Aktueller Inhalt des Felds (Versand, KI-Prüfung), sonst der zuletzt gespiegelte. */
    const aktuellesHtml = () => editorRef.current?.innerHTML || body;

    return { body, beautifying, beimTippen, formuliere, aktuellesHtml, entwurfEinsetzen };
}

export type Nachricht = ReturnType<typeof useNachricht>;

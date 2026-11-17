import { useCallback, useMemo } from 'react';
import { useConfirm } from '../ui/confirm-dialog';

interface UseKonfliktMeldungResult {
    /**
     * Prueft die Response eines Speicher-Requests auf einen Versionskonflikt
     * (HTTP 409, siehe RestExceptionHandler.handleOptimisticLockingFailure).
     * Liegt einer vor, zeigt sie die Neu-laden-Meldung an und liefert `true`
     * zurueck -- unabhaengig davon, ob der Nutzer "Neu laden" oder
     * "Abbrechen" waehlt. Bei jedem anderen Status liefert sie sofort
     * `false`, ohne den Antwort-Body zu lesen (der bleibt damit fuer
     * anderen Fehler-Handling-Code des Aufrufers verfuegbar).
     */
    pruefeAntwort: (res: Response, optionen?: KonfliktOptionen) => Promise<boolean>;
}

interface KonfliktOptionen {
    /**
     * Die Meldung des Servers (`message` im JSON) zeigen, wenn es eine gibt. Für
     * Konflikte mit eigenem Grund, z. B. „Dokument wurde auf einen älteren Stand
     * zurückgesetzt“ (Dokumenteditor). Ohne Meldung gilt der eigene Text.
     */
    serverMeldung?: boolean;
}

async function leseServerMeldung(res: Response): Promise<string | null> {
    try {
        const body: unknown = await res.json();
        const message = body && typeof body === 'object' ? (body as { message?: unknown }).message : null;
        return typeof message === 'string' && message.trim() ? message : null;
    } catch {
        return null;
    }
}

/**
 * Faengt den 409-Statuscode (optimistisches Sperren) beim Speichern ab und
 * zeigt die Neu-laden-Meldung ueber die Pflicht-Komponente useConfirm() aus
 * src/components/ui/confirm-dialog.tsx an -- kein eigenes Modal.
 *
 * @param bezeichnung Bezeichnung des Datensatzes fuer den Meldungstext,
 *   z.B. "Dokument", "Projekt". Default: "Dokument".
 */
export function useKonfliktMeldung(bezeichnung: string = 'Dokument'): UseKonfliktMeldungResult {
    const confirm = useConfirm();

    const pruefeAntwort = useCallback(
        async (res: Response, optionen: KonfliktOptionen = {}): Promise<boolean> => {
            if (res.status !== 409) {
                return false;
            }

            // Der Server liefert bei 409 zwar denselben Sachverhalt in
            // ApiError.message (siehe RestExceptionHandler.
            // handleOptimisticLockingFailure), aber die eigene, mit der
            // Bezeichnung konkretisierte Formulierung ist IMMER vorhanden und
            // genauer -- das Backend-Feld wird darum nur auf Wunsch (serverMeldung) gelesen.
            // (Fund aus dem Review, Task 8a: die vorherige Fassung parste den
            // Antwort-Body trotzdem und haengte body?.message sowie einen
            // dritten, generischen Text als "Fallback" an -- toter Code, denn
            // eigeneMeldung ist ein Template-String mit `bezeichnung` und
            // damit nie leer, der Fallback konnte also nie greifen.)
            const eigeneMeldung = `Jemand anders hat dieses ${bezeichnung} gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.`;
            const meldungstext = (optionen.serverMeldung ? await leseServerMeldung(res) : null) ?? eigeneMeldung;

            const neuLaden = await confirm({
                title: 'Nicht gespeichert',
                message: meldungstext,
                confirmLabel: 'Neu laden',
                cancelLabel: 'Abbrechen',
                // 'fehlschlag' (Task 8c, vorher 'info' seit Task 8a): gefuellte
                // Primaeraktionen sind im Design-System rose, nicht amber --
                // amber ist Warn-Icons/Badges vorbehalten (siehe
                // "Ungespeicherte Aenderungen" in Modals.tsx: amber-Icon, aber
                // rose-Knopf). 'info' lieferte zwar den rose-Knopf, aber auch
                // ein freundliches blaues Fragezeichen (HelpCircle in
                // sky-100/sky-600) -- fuer eine Meldung, die einen bereits
                // eingetretenen Fehlschlag benennt ("Ihre Aenderungen wurden
                // nicht uebernommen"), die falsche Semantik (Design-Review
                // Abschnitt 7-2/8-1). 'fehlschlag' liefert genau den
                // amber-Icon+rose-Knopf-Verbund, den der Review vorgeschlagen
                // hat, ohne die anderen 5 'info'-Aufrufer im Projekt
                // anzufassen (Urlaubsantraege, ProjektEditor,
                // BestellungEditor, KostenpositionenView,
                // LieferantReklamationenTab).
                variant: 'fehlschlag',
            });

            if (neuLaden) {
                window.location.reload();
            }

            return true;
        },
        [bezeichnung, confirm]
    );

    return useMemo(() => ({ pruefeAntwort }), [pruefeAntwort]);
}

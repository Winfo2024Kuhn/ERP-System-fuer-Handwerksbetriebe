import { useCallback, useMemo } from 'react';
import { useConfirm } from '../ui/confirm-dialog';

interface ApiErrorBody {
    message?: string;
}

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
    pruefeAntwort: (res: Response) => Promise<boolean>;
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
        async (res: Response): Promise<boolean> => {
            if (res.status !== 409) {
                return false;
            }

            // Antwort-Body defensiv parsen: der Server liefert bei 409 zwar
            // denselben Text in ApiError.message, aber die eigene, mit der
            // Bezeichnung konkretisierte Formulierung ist genauer und hat
            // Vorrang -- das Backend-Feld dient nur als Fallback. Ein
            // fehlender oder ungueltiger JSON-Body (Textkoerper) darf dabei
            // nicht zum Absturz fuehren.
            const body = (await res.json().catch(() => null)) as ApiErrorBody | null;
            const eigeneMeldung = `Jemand anders hat dieses ${bezeichnung} gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.`;
            const meldungstext =
                eigeneMeldung ||
                body?.message ||
                'Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.';

            const neuLaden = await confirm({
                title: 'Nicht gespeichert',
                message: meldungstext,
                confirmLabel: 'Neu laden',
                cancelLabel: 'Abbrechen',
                variant: 'warning',
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

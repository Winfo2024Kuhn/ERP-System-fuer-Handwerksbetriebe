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

            // Der Server liefert bei 409 zwar denselben Sachverhalt in
            // ApiError.message (siehe RestExceptionHandler.
            // handleOptimisticLockingFailure), aber die eigene, mit der
            // Bezeichnung konkretisierte Formulierung ist IMMER vorhanden und
            // genauer -- das Backend-Feld wird darum bewusst NICHT gelesen.
            // (Fund aus dem Review, Task 8a: die vorherige Fassung parste den
            // Antwort-Body trotzdem und haengte body?.message sowie einen
            // dritten, generischen Text als "Fallback" an -- toter Code, denn
            // eigeneMeldung ist ein Template-String mit `bezeichnung` und
            // damit nie leer, der Fallback konnte also nie greifen.)
            const meldungstext = `Jemand anders hat dieses ${bezeichnung} gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.`;

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

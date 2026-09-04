import { CheckCircle2 } from 'lucide-react';

/**
 * Ruhige Vollbild-Seite, KEIN Modal -- wird anstelle des Editors gerendert,
 * wenn der X-Button-Ablauf (speichern -> Sperre freigeben -> Tab schliessen,
 * siehe document-editor/index.tsx) den Tab nicht selbst schliessen konnte.
 *
 * Hintergrund (Issue #82): auf manchen Systemen (z.B. macOS Safari) blockiert
 * der Browser `window.close()`, wenn das Skript den Tab nicht zweifelsfrei
 * selbst geoeffnet hat. Der Nutzer haette sonst keine Rueckmeldung, ob
 * Speichern und Freigeben ueberhaupt geklappt haben -- diese Seite bestaetigt
 * beides und macht den Tab bewusst ohne weitere Aktion "fertig zum Wegklicken".
 *
 * Bewusst keine eigene Aktion/kein Knopf: der Editor hat zu diesem Zeitpunkt
 * schon gespeichert und die Sperre freigegeben, es gibt nichts mehr zu tun
 * ausser den Tab von Hand zu schliessen.
 */
export function TabSchliessenHinweis() {
    return (
        <div className="fixed inset-0 z-50 bg-slate-50 flex flex-col items-center justify-center gap-4 px-6 text-center">
            <div className="p-4 bg-rose-100 rounded-full">
                <CheckCircle2 className="w-8 h-8 text-rose-600" aria-hidden="true" />
            </div>
            <p role="status" className="max-w-sm text-base text-slate-700 text-balance">
                Dokument gespeichert und freigegeben — Sie können diesen Tab jetzt schließen.
            </p>
        </div>
    );
}

export default TabSchliessenHinweis;

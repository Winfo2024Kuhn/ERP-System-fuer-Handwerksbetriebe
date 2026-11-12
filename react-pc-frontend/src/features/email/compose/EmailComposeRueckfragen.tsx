import { AlertTriangle, Link2, Loader2, Mail, Save, Send, X } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import type { EmailZuordnung } from '../../../components/EmailZuordnungSearchModal';
import type { AdresseSpeichern, AdressZiel } from './useAdresseSpeichern';

interface ZuordnungWarnungProps {
    onAuswaehlen: () => void;
    onTrotzdemSenden: () => void;
    onAbbrechen: () => void;
}

/** Rückfrage vor dem Senden: E-Mail ohne Projekt/Anfrage verschicken? */
export function ZuordnungWarnungDialog({ onAuswaehlen, onTrotzdemSenden, onAbbrechen }: ZuordnungWarnungProps) {
    return (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 backdrop-blur-sm" onClick={onAbbrechen}>
            <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="zuordnung-warnung-titel"
                className="mx-4 w-full max-w-md rounded-xl bg-white p-6 shadow-2xl"
                onClick={(event) => event.stopPropagation()}
            >
                <div className="mb-4 flex items-center gap-3">
                    <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-amber-100">
                        <AlertTriangle className="h-5 w-5 text-amber-600" />
                    </div>
                    <h3 id="zuordnung-warnung-titel" className="text-lg font-semibold text-slate-900">
                        Kein Projekt und keine Anfrage verknüpft
                    </h3>
                </div>
                <p className="mb-4 text-sm text-slate-600">
                    Diese E-Mail gehört bisher zu keinem Vorgang. Ohne Verknüpfung taucht sie später
                    weder beim Projekt noch bei der Anfrage auf. Soll sie trotzdem raus?
                </p>
                <div className="flex flex-col gap-2">
                    <Button onClick={onAuswaehlen} className="w-full justify-start bg-rose-600 text-white hover:bg-rose-700" size="sm">
                        <Link2 className="mr-2 h-4 w-4" />
                        Projekt oder Anfrage auswählen
                    </Button>
                    <Button variant="outline" onClick={onTrotzdemSenden} className="w-full justify-start border-slate-300 text-slate-700" size="sm">
                        <Send className="mr-2 h-4 w-4" />
                        Trotzdem senden
                    </Button>
                    <Button variant="ghost" onClick={onAbbrechen} className="w-full justify-start text-slate-500" size="sm">
                        <X className="mr-2 h-4 w-4" />
                        Abbrechen
                    </Button>
                </div>
            </div>
        </div>
    );
}

interface AdresseSpeichernDialogProps {
    stand: AdresseSpeichern;
    kundeId?: number;
    zuordnung: EmailZuordnung | null;
}

function SpeichernKnopf({ stand, ziel, text }: { stand: AdresseSpeichern; ziel: AdressZiel; text: string }) {
    return (
        <Button onClick={() => stand.speichern(ziel)} disabled={stand.speichert}
            className="w-full bg-rose-600 hover:bg-rose-700 text-white justify-start" size="sm">
            {stand.speichert ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Mail className="w-4 h-4 mr-2" />}
            {text}
        </Button>
    );
}

/** Nach dem Senden an eine neue Adresse: beim Kunden, Projekt oder der Anfrage speichern? */
export function AdresseSpeichernDialog({ stand, kundeId, zuordnung }: AdresseSpeichernDialogProps) {
    return (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 backdrop-blur-sm">
            {/* role/aria-modal: Ein umgebender Dialog erkennt die Rückfrage als oberste Ebene und schließt nicht auf Escape. */}
            <div role="dialog" aria-modal="true" aria-labelledby="email-adresse-speichern-titel" className="bg-white rounded-xl shadow-2xl max-w-md w-full mx-4 p-6" onClick={e => e.stopPropagation()}>
                <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 rounded-full bg-rose-100 flex items-center justify-center">
                        <Save className="w-5 h-5 text-rose-600" />
                    </div>
                    <div>
                        <h3 id="email-adresse-speichern-titel" className="text-lg font-semibold text-slate-900">E-Mail-Adresse speichern?</h3>
                        <p className="text-sm text-slate-500">
                            <span className="font-medium text-slate-700">{stand.adresse}</span> ist noch nicht gespeichert.
                        </p>
                    </div>
                </div>
                <p className="text-sm text-slate-600 mb-4">Möchten Sie diese E-Mail-Adresse für zukünftige Verwendung speichern?</p>
                <div className="flex flex-col gap-2">
                    {kundeId && <SpeichernKnopf stand={stand} ziel="kunde" text="Beim Kunden speichern" />}
                    {zuordnung?.typ === 'PROJEKT' && <SpeichernKnopf stand={stand} ziel="projekt" text="Als Projekt-E-Mail speichern" />}
                    {zuordnung?.typ === 'ANFRAGE' && <SpeichernKnopf stand={stand} ziel="anfrage" text="Als Anfrage-E-Mail speichern" />}
                    <Button variant="outline" onClick={stand.ueberspringen} disabled={stand.speichert}
                        className="w-full border-slate-300 text-slate-700 justify-start" size="sm">
                        <X className="w-4 h-4 mr-2" />
                        Nicht speichern
                    </Button>
                </div>
            </div>
        </div>
    );
}

import { useId } from 'react';
import { FileText, Globe } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../ui/dialog';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';

interface OnlineAnnahmeDialogProps {
    isOpen: boolean;
    /** „Angebot“ oder „Nachtragsangebot“ – erscheint im Text. */
    dokumentBezeichnung: string;
    onClose: () => void;
    /** `true` = mit Online-Link verschicken, `false` = nur das PDF. */
    onWahl: (mitOnlineAnnahme: boolean) => void;
}

/**
 * Fragt vor dem Versand eines Angebots, ob der Kunde es online annehmen können soll.
 * „Ja“ hängt einen Link zum Annehmen an die Mail (danach folgt die Frage nach der
 * Gültigkeit), „Nein“ verschickt nur das PDF.
 */
export function OnlineAnnahmeDialog({ isOpen, dokumentBezeichnung, onClose, onWahl }: OnlineAnnahmeDialogProps) {
    const titelId = useId();
    const wahlKlasse = 'flex w-full items-start gap-3 rounded-xl border px-4 py-3 text-left transition-colors '
        + 'focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500';
    return (
        <Dialog open={isOpen} onOpenChange={(offen) => { if (!offen) onClose(); }} className="w-full max-w-md" aria-labelledby={titelId}>
            <DialogContent>
                <DialogHeader className="pr-8">
                    <div className="flex items-center gap-2">
                        <DialogTitle id={titelId}>Soll der Kunde das {dokumentBezeichnung} online annehmen können?</DialogTitle>
                        <InfoButton titel="Online annehmen">
                            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                <p>
                                    Bei „Ja“ steht in der E-Mail ein Link. Darüber sieht der Kunde das {dokumentBezeichnung}
                                    {' '}im Internet und kann es mit einem Bestätigungscode verbindlich annehmen.
                                    Danach legt das Programm die Auftragsbestätigung automatisch an.
                                </p>
                            </InfoAbschnitt>
                            <InfoAbschnitt titel="Was bewirkt das?">
                                <p>
                                    Bei „Nein“ bekommt der Kunde nur das PDF. Er sagt Ihnen dann wie gewohnt per Mail
                                    oder Telefon zu. Das PDF ist in beiden Fällen dasselbe.
                                </p>
                            </InfoAbschnitt>
                        </InfoButton>
                    </div>
                    <DialogDescription>
                        Sie können das bei jedem Versand neu entscheiden.
                    </DialogDescription>
                </DialogHeader>
                <div className="space-y-2">
                    <button
                        type="button"
                        onClick={() => onWahl(true)}
                        className={`${wahlKlasse} border-rose-200 bg-rose-50/60 hover:bg-rose-50 hover:border-rose-300`}
                    >
                        <Globe className="mt-0.5 h-5 w-5 flex-shrink-0 text-rose-600" aria-hidden="true" />
                        <span>
                            <span className="block text-sm font-semibold text-slate-900">Ja, mit Link zum Annehmen</span>
                            <span className="block text-xs text-slate-500">Der Kunde kann online mit einem Klick zusagen.</span>
                        </span>
                    </button>
                    <button
                        type="button"
                        onClick={() => onWahl(false)}
                        className={`${wahlKlasse} border-slate-200 bg-white hover:bg-slate-50 hover:border-slate-300`}
                    >
                        <FileText className="mt-0.5 h-5 w-5 flex-shrink-0 text-slate-500" aria-hidden="true" />
                        <span>
                            <span className="block text-sm font-semibold text-slate-900">Nein, nur das PDF schicken</span>
                            <span className="block text-xs text-slate-500">Ohne Link – der Kunde sagt Ihnen direkt Bescheid.</span>
                        </span>
                    </button>
                </div>
            </DialogContent>
        </Dialog>
    );
}

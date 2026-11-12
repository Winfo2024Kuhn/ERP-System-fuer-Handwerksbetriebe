import { useId, useState, type FormEvent } from 'react';
import { Button } from '../../../components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { InfoAbschnitt, InfoButton } from '../../../components/ui/info-button';
import { Input } from '../../../components/ui/input';
import { Label } from '../../../components/ui/label';
import { linkText, normalisiereLinkAdresse } from './mailLink';
import type { MailLinkDialogStand } from './useMailLinks';

interface MailLinkDialogProps {
    stand: MailLinkDialogStand;
    onUebernehmen: (text: string, href: string) => void;
    onSchliessen: () => void;
}

/**
 * Dialog „Link einfügen“ bzw. „Link ändern“: angezeigter Text frei wählbar,
 * die Adresse liegt dahinter. Nur Web- und E-Mail-Adressen; ohne Schema wird
 * `https://` ergänzt. Wird nur gerendert, solange er offen ist.
 */
export function MailLinkDialog({ stand, onUebernehmen, onSchliessen }: MailLinkDialogProps) {
    const id = useId();
    const [text, setText] = useState(stand.text);
    const [adresse, setAdresse] = useState(stand.href);
    const [fehler, setFehler] = useState<string | null>(null);
    const neu = stand.modus === 'neu';
    const titel = neu ? 'Link einfügen' : 'Link ändern';
    const adresseFehlt = !adresse.trim();

    const absenden = (event: FormEvent) => {
        event.preventDefault();
        const pruefung = normalisiereLinkAdresse(adresse);
        if (!pruefung.ok) {
            setFehler(pruefung.meldung);
            return;
        }
        onUebernehmen(linkText(text, pruefung.href), pruefung.href);
    };

    return (
        <Dialog open onOpenChange={(offen) => { if (!offen) onSchliessen(); }} aria-labelledby={`${id}-titel`} className="w-full max-w-md">
            <DialogContent>
                <DialogHeader>
                    <DialogTitle id={`${id}-titel`} className="text-slate-900">{titel}</DialogTitle>
                </DialogHeader>
                <form onSubmit={absenden} className="flex flex-col gap-4" noValidate>
                    <div className="space-y-2">
                        <Label htmlFor={`${id}-text`}>Angezeigter Text</Label>
                        <Input
                            id={`${id}-text`}
                            value={text}
                            onChange={(event) => setText(event.target.value)}
                            placeholder="z. B. Hier klicken"
                            autoFocus={!stand.text}
                        />
                        <p className="text-xs text-slate-500">Leer lassen, dann steht die Adresse selbst in der E-Mail.</p>
                    </div>
                    <div className="space-y-2">
                        <div className="flex items-center gap-1">
                            <Label htmlFor={`${id}-adresse`}>Link-Adresse</Label>
                            <InfoButton titel="Link-Adresse">
                                <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                    <p>
                                        In der E-Mail steht nur der angezeigte Text, die Adresse liegt unsichtbar dahinter.
                                        Beim Klick öffnet der Empfänger die Adresse in seinem Browser.
                                    </p>
                                </InfoAbschnitt>
                                <InfoAbschnitt titel="Was bewirkt das?">
                                    <p>
                                        Jeder, der die E-Mail hat, kann den Link öffnen. Wer eine OneDrive-Datei sehen oder
                                        bearbeiten darf, legen Sie in OneDrive bei der Freigabe fest, nicht hier.
                                    </p>
                                </InfoAbschnitt>
                            </InfoButton>
                        </div>
                        <Input
                            id={`${id}-adresse`}
                            value={adresse}
                            onChange={(event) => { setAdresse(event.target.value); setFehler(null); }}
                            placeholder="https://… z. B. OneDrive-Freigabelink"
                            inputMode="url"
                            autoComplete="off"
                            spellCheck={false}
                            autoFocus={!!stand.text}
                            aria-invalid={fehler ? true : undefined}
                            aria-describedby={fehler ? `${id}-fehler` : undefined}
                            className={fehler ? 'border-red-300 focus-visible:ring-red-500' : undefined}
                        />
                        {fehler && <p id={`${id}-fehler`} role="alert" className="text-sm text-red-700">{fehler}</p>}
                    </div>
                    <DialogFooter className="gap-2">
                        <Button type="button" variant="outline" onClick={onSchliessen}>Abbrechen</Button>
                        {/* Gesperrte Knöpfe zeigen keinen Tooltip – darum hängt er an der Hülle. */}
                        <span className="inline-flex" title={adresseFehlt ? 'Bitte zuerst eine Link-Adresse eintragen.' : undefined}>
                            <Button type="submit" disabled={adresseFehlt} className="w-full">
                                {neu ? 'Link einfügen' : 'Übernehmen'}
                            </Button>
                        </span>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}

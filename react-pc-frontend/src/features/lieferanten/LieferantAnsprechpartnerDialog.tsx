import { useId, useState, type ReactNode } from 'react';
import { Loader2 } from 'lucide-react';
import { PhoneInput } from '../../components/PhoneInput';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { ANREDE_AUSWAHL } from '../../lib/briefanrede';
import { cn } from '../../lib/utils';
import {
    entwurfAus,
    leererEntwurf,
    MAX_LAENGE,
    pruefeEntwurf,
    type AnsprechpartnerEntwurf,
    type LieferantAnsprechpartner,
} from './ansprechpartnerEntwurf';

const ANREDE_OPTIONEN = [{ value: '', label: '(keine)' }, ...ANREDE_AUSWAHL];

interface Props {
    offen: boolean;
    /** `null` = neuen Ansprechpartner anlegen. */
    ansprechpartner: LieferantAnsprechpartner | null;
    speichert: boolean;
    /** Liefert true, wenn gespeichert wurde; dann schließt der Dialog. */
    onSpeichern: (entwurf: AnsprechpartnerEntwurf) => Promise<boolean>;
    onSchliessen: () => void;
}

/**
 * Dialog „Ansprechpartner hinzufügen“ bzw. „… bearbeiten“. Speichern bleibt
 * gesperrt (mit sichtbarem Grund), bis der Nachname da ist und die E-Mail passt.
 * Den Formularstand setzt der Aufrufer per `key` zurück, wenn eine andere Person öffnet.
 */
export function LieferantAnsprechpartnerDialog({ offen, ansprechpartner, speichert, onSpeichern, onSchliessen }: Props) {
    const id = useId();
    const [entwurf, setEntwurf] = useState<AnsprechpartnerEntwurf>(() => ansprechpartner ? entwurfAus(ansprechpartner) : leererEntwurf());
    const [emailVerlassen, setEmailVerlassen] = useState(false);
    const fehler = pruefeEntwurf(entwurf);
    const emailFehler = emailVerlassen && fehler?.feld === 'email' ? fehler.meldung : null;
    const setze = (teil: Partial<AnsprechpartnerEntwurf>) => setEntwurf(vorher => ({ ...vorher, ...teil }));

    // Während des Speicherns schließen weder Abbrechen noch X noch Escape den Dialog.
    const schliessen = (auf: boolean) => { if (!auf && !speichert) onSchliessen(); };
    const speichern = async () => {
        if (fehler || speichert) return;
        if (await onSpeichern(entwurf)) onSchliessen();
    };

    return (
        <Dialog open={offen} onOpenChange={schliessen} aria-labelledby={`${id}-titel`}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle id={`${id}-titel`}>
                        {ansprechpartner ? 'Ansprechpartner bearbeiten' : 'Ansprechpartner hinzufügen'}
                    </DialogTitle>
                </DialogHeader>
                <form
                    id={`${id}-form`}
                    className="grid gap-4 py-4 overflow-y-auto -mx-1 px-1 flex-1 min-h-0"
                    onSubmit={(e) => { e.preventDefault(); void speichern(); }}
                >
                    <div className="grid grid-cols-3 gap-4">
                        <Feld id={`${id}-anrede`} label="Anrede">
                            <Select id={`${id}-anrede`} value={entwurf.anrede} onChange={anrede => setze({ anrede })} options={ANREDE_OPTIONEN} placeholder="(keine)" />
                        </Feld>
                        <Feld id={`${id}-vorname`} label="Vorname">
                            <Input id={`${id}-vorname`} className="h-10" value={entwurf.vorname} maxLength={MAX_LAENGE.vorname} onChange={e => setze({ vorname: e.target.value })} />
                        </Feld>
                        <Feld id={`${id}-nachname`} label="Nachname *">
                            <Input id={`${id}-nachname`} className="h-10" value={entwurf.nachname} required maxLength={MAX_LAENGE.nachname} onChange={e => setze({ nachname: e.target.value })} autoFocus={!ansprechpartner} />
                        </Feld>
                    </div>
                    <Feld id={`${id}-aufgabe`} label="Aufgabe">
                        <Input id={`${id}-aufgabe`} value={entwurf.aufgabe} maxLength={MAX_LAENGE.aufgabe} placeholder="z. B. Außendienst, Innendienst, Buchhaltung" onChange={e => setze({ aufgabe: e.target.value })} />
                    </Feld>
                    <div className="grid grid-cols-2 gap-4">
                        <Feld id={`${id}-telefon`} label="Telefon">
                            <PhoneInput id={`${id}-telefon`} value={entwurf.telefon} maxLength={MAX_LAENGE.telefon} onChange={telefon => setze({ telefon })} variant="festnetz" />
                        </Feld>
                        <Feld id={`${id}-handy`} label="Handy">
                            <PhoneInput id={`${id}-handy`} value={entwurf.mobiltelefon} maxLength={MAX_LAENGE.mobiltelefon} onChange={mobiltelefon => setze({ mobiltelefon })} variant="mobil" />
                        </Feld>
                    </div>
                    <Feld id={`${id}-email`} label="E-Mail">
                        <Input
                            id={`${id}-email`}
                            type="email"
                            value={entwurf.email}
                            maxLength={MAX_LAENGE.email}
                            placeholder="name@firma.de"
                            onChange={e => setze({ email: e.target.value })}
                            onBlur={() => setEmailVerlassen(true)}
                            aria-invalid={emailFehler ? true : undefined}
                            aria-describedby={emailFehler ? `${id}-email-fehler` : undefined}
                            className={cn(emailFehler && 'border-red-500 focus:ring-red-500')}
                        />
                        {emailFehler && <p id={`${id}-email-fehler`} className="text-sm text-red-600">{emailFehler}</p>}
                    </Feld>
                    <Feld id={`${id}-notiz`} label="Notiz">
                        <textarea
                            id={`${id}-notiz`}
                            value={entwurf.notiz}
                            maxLength={MAX_LAENGE.notiz}
                            rows={3}
                            onChange={e => setze({ notiz: e.target.value })}
                            className="w-full resize-y border border-slate-200 rounded px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </Feld>
                </form>
                <DialogFooter className="items-center">
                    {fehler && <p id={`${id}-grund`} className="mr-auto text-sm text-slate-600">{fehler.meldung}</p>}
                    <Button type="button" variant="outline" disabled={speichert} onClick={() => schliessen(false)}>Abbrechen</Button>
                    <Button
                        type="submit"
                        form={`${id}-form`}
                        disabled={Boolean(fehler) || speichert}
                        aria-describedby={fehler ? `${id}-grund` : undefined}
                        title={fehler?.meldung}
                    >
                        {speichert && <Loader2 aria-hidden="true" className="w-4 h-4 mr-2 motion-safe:animate-spin" />}
                        {speichert ? 'Speichert …' : 'Speichern'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

function Feld({ id, label, children }: { id: string; label: string; children: ReactNode }) {
    return (
        <div className="space-y-1.5 min-w-0">
            <Label htmlFor={id}>{label}</Label>
            {children}
        </div>
    );
}

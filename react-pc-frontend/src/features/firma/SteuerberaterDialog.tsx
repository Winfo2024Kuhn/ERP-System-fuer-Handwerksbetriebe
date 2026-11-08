import { useId } from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { DatePicker } from '../../components/ui/datepicker';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { AnsprechpartnerEditor } from './AnsprechpartnerEditor';
import { WeitereEmailsEditor } from './WeitereEmailsEditor';
import { kanzleiHakenGreiftNicht } from './steuerberaterEntwurf';
import type { SteuerberaterEntwurf } from './firmaTypen';
import type { useSteuerberaterKontakte } from './useSteuerberaterKontakte';

interface Props {
    kontakte: ReturnType<typeof useSteuerberaterKontakte>;
}

/** Dialog „Neuer Steuerberater“ bzw. „Steuerberater bearbeiten“. */
export function SteuerberaterDialog({ kontakte }: Props) {
    const { entwurf, setEntwurf, speichert } = kontakte;
    const id = useId();
    const setze = (teil: SteuerberaterEntwurf) => setEntwurf(prev => ({ ...prev, ...teil }));
    // Während des Speicherns schließt weder Abbrechen noch X noch Escape den Dialog.
    const schliessen = (offen: boolean) => { if (!speichert) kontakte.setDialogOffen(offen); };
    return (
        <Dialog open={kontakte.dialogOffen} onOpenChange={schliessen} aria-labelledby={`${id}-titel`}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle id={`${id}-titel`}>
                        {entwurf?.id ? 'Steuerberater bearbeiten' : 'Neuer Steuerberater'}
                    </DialogTitle>
                </DialogHeader>
                <div className="grid gap-4 py-4 overflow-y-auto pr-1 flex-1 min-h-0">
                    <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label htmlFor={`${id}-name`}>Name</Label>
                            <Input
                                id={`${id}-name`}
                                value={entwurf?.name || ''}
                                onChange={e => setze({ name: e.target.value })}
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor={`${id}-telefon`}>Telefon</Label>
                            <Input
                                id={`${id}-telefon`}
                                value={entwurf?.telefon || ''}
                                onChange={e => setze({ telefon: e.target.value })}
                            />
                        </div>
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor={`${id}-email`}>Haupt-E-Mail (für die Erkennung von BWA und Lohnabrechnungen) *</Label>
                        <Input
                            id={`${id}-email`}
                            value={entwurf?.email || ''}
                            onChange={e => setze({ email: e.target.value })}
                            placeholder="kanzlei@beispiel.de"
                        />
                    </div>
                    <WeitereEmailsEditor weitereEmails={entwurf?.weitereEmails || []} aktualisieren={setEntwurf} />
                    <AnsprechpartnerEditor ansprechpartnerListe={entwurf?.ansprechpartnerListe || []} aktualisieren={setEntwurf} />
                    <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label htmlFor={`${id}-ab`}>Gültig ab</Label>
                            <DatePicker
                                id={`${id}-ab`}
                                value={entwurf?.gueltigAb || ''}
                                onChange={v => setze({ gueltigAb: v })}
                                placeholder="Startdatum"
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor={`${id}-bis`}>Gültig bis</Label>
                            <DatePicker
                                id={`${id}-bis`}
                                value={entwurf?.gueltigBis || ''}
                                onChange={v => setze({ gueltigBis: v })}
                                placeholder="Enddatum (optional)"
                            />
                        </div>
                    </div>
                    <KanzleiErkennenHaken
                        id={`${id}-kanzlei`}
                        checked={entwurf?.autoProcessEmails !== false}
                        onChange={autoProcessEmails => setze({ autoProcessEmails })}
                        freemail={kanzleiHakenGreiftNicht(entwurf)}
                    />
                </div>
                <DialogFooter>
                    <Button variant="outline" disabled={speichert} onClick={() => schliessen(false)}>Abbrechen</Button>
                    <Button disabled={speichert} onClick={() => void kontakte.speichern()}>
                        {speichert && <Loader2 aria-hidden="true" className="w-4 h-4 mr-2 motion-safe:animate-spin" />}
                        {speichert ? 'Speichert …' : 'Speichern'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

interface HakenProps {
    id: string;
    checked: boolean;
    onChange: (checked: boolean) => void;
    /** Haupt-E-Mail bei gmx.de & Co.: Haken greift nicht, Hinweis zeigen. */
    freemail: boolean;
}

/** Haken „Auch andere E-Mail-Adressen der Kanzlei erkennen“ mit Erklärung. */
function KanzleiErkennenHaken({ id, checked, onChange, freemail }: HakenProps) {
    return (
        <div className="space-y-1">
            <div className="flex items-center gap-2">
                <input
                    type="checkbox"
                    id={id}
                    checked={checked}
                    onChange={e => onChange(e.target.checked)}
                    aria-describedby={freemail ? `${id}-freemail` : undefined}
                    className="rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                />
                <Label htmlFor={id}>Auch andere E-Mail-Adressen der Kanzlei erkennen</Label>
                <InfoButton titel="Andere E-Mail-Adressen der Kanzlei">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>
                            Kommt eine E-Mail von der Haupt-E-Mail oder einer der weiteren E-Mail-Adressen dieses
                            Steuerberaters, liest das Programm die PDF-Anhänge und legt BWA und Lohnabrechnungen unter
                            Steuerberater ab. Mit dem Haken gilt das auch für andere Adressen derselben Kanzlei, also
                            mit gleichem Teil nach dem @.
                        </p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>
                            Ohne Haken werden nur die Haupt-E-Mail und die weiteren E-Mail-Adressen ausgewertet.
                            Bereits abgelegte Unterlagen bleiben unverändert.
                        </p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Freie Anbieter wie gmx.de">
                        <p>
                            Bei Adressen von gmx.de, web.de, gmail.com, t-online.de und anderen freien Anbietern greift
                            der Haken nicht, damit keine fremden E-Mails als Steuerberater-Post gelten.
                        </p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>
            {freemail && (
                <p id={`${id}-freemail`} className="text-xs text-slate-600 pl-6">
                    Die Haupt-E-Mail liegt bei einem freien Anbieter. Dort gilt der Haken nicht, erkannt werden nur die eingetragenen Adressen.
                </p>
            )}
        </div>
    );
}

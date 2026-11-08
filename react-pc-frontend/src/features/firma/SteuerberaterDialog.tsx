import { Button } from '../../components/ui/button';
import { DatePicker } from '../../components/ui/datepicker';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { AnsprechpartnerEditor } from './AnsprechpartnerEditor';
import { WeitereEmailsEditor } from './WeitereEmailsEditor';
import type { SteuerberaterEntwurf } from './firmaTypen';
import type { useSteuerberaterKontakte } from './useSteuerberaterKontakte';

interface Props {
    kontakte: ReturnType<typeof useSteuerberaterKontakte>;
}

/** Dialog „Neuer Steuerberater“ bzw. „Steuerberater bearbeiten“. */
export function SteuerberaterDialog({ kontakte }: Props) {
    const { entwurf, setEntwurf } = kontakte;
    const setze = (teil: SteuerberaterEntwurf) => setEntwurf(prev => ({ ...prev, ...teil }));
    return (
        <Dialog open={kontakte.dialogOffen} onOpenChange={kontakte.setDialogOffen}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>
                        {entwurf?.id ? 'Steuerberater bearbeiten' : 'Neuer Steuerberater'}
                    </DialogTitle>
                </DialogHeader>
                <div className="grid gap-4 py-4 overflow-y-auto pr-1 flex-1 min-h-0">
                    <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label>Name</Label>
                            <Input
                                value={entwurf?.name || ''}
                                onChange={e => setze({ name: e.target.value })}
                            />
                        </div>
                        <div className="space-y-2">
                            <Label>Telefon</Label>
                            <Input
                                value={entwurf?.telefon || ''}
                                onChange={e => setze({ telefon: e.target.value })}
                            />
                        </div>
                    </div>
                    <div className="space-y-2">
                        <Label>Haupt-E-Mail (für BWA-Erkennung)</Label>
                        <Input
                            value={entwurf?.email || ''}
                            onChange={e => setze({ email: e.target.value })}
                            placeholder="kanzlei@beispiel.de"
                        />
                    </div>
                    <WeitereEmailsEditor weitereEmails={entwurf?.weitereEmails || []} aktualisieren={setEntwurf} />
                    <AnsprechpartnerEditor ansprechpartnerListe={entwurf?.ansprechpartnerListe || []} aktualisieren={setEntwurf} />
                    <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label>Gültig Ab *</Label>
                            <DatePicker
                                value={entwurf?.gueltigAb || ''}
                                onChange={v => setze({ gueltigAb: v })}
                                placeholder="Startdatum"
                            />
                        </div>
                        <div className="space-y-2">
                            <Label>Gültig Bis</Label>
                            <DatePicker
                                value={entwurf?.gueltigBis || ''}
                                onChange={v => setze({ gueltigBis: v })}
                                placeholder="Enddatum (optional)"
                            />
                        </div>
                    </div>
                    <div className="space-y-2">
                        <div className="flex items-center space-x-2">
                            <input
                                type="checkbox"
                                id="sb_auto"
                                aria-label="Automatische E-Mail Verarbeitung"
                                title="Automatische E-Mail Verarbeitung"
                                checked={entwurf?.autoProcessEmails !== false}
                                onChange={e => setze({ autoProcessEmails: e.target.checked })}
                                className="rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                            />
                            <Label htmlFor="sb_auto">Automatische E-Mail Verarbeitung (BWA)</Label>
                        </div>
                    </div>
                </div>
                <DialogFooter>
                    <Button variant="outline" onClick={() => kontakte.setDialogOffen(false)}>Abbrechen</Button>
                    <Button onClick={() => void kontakte.speichern()} className="bg-rose-600 text-white hover:bg-rose-700">Speichern</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

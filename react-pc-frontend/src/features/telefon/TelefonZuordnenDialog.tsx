import { useState } from 'react';
import { Loader2, Search, Truck, User, UserCheck } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { KundeSearchModal, type KundeSearchItem } from '../../components/KundeSearchModal';
import { LieferantSearchModal, type LieferantSuchErgebnis } from '../../components/LieferantSearchModal';
import { refreshNotifications } from '../../lib/notificationRefresh';
import { cn } from '../../lib/utils';
import { ordneZu } from './api';
import { anzeigeNummer } from './format';
import type { KontaktTyp, Sprachnachricht, TelefonAnruf, ZuordnenZiel } from './types';

/**
 * Anruf oder Anrufbeantworter-Nachricht einem Kunden oder Lieferanten
 * zuordnen.
 *
 * <p>Die Suche selbst erledigen die vorhandenen Suchfenster
 * ({@link KundeSearchModal}, {@link LieferantSearchModal}) – hier wird nur
 * gewählt, ob Kunde oder Lieferant, und ob die Nummer beim Kontakt gemerkt
 * werden soll. Gemerkte Nummern ordnen künftige Anrufe automatisch zu.</p>
 */

interface GewaehlterKontakt {
    typ: KontaktTyp;
    id: number;
    name: string;
    zusatz: string | null;
}

interface TelefonZuordnenDialogProps<T extends TelefonAnruf | Sprachnachricht> {
    offen: boolean;
    ziel: ZuordnenZiel;
    /** Nummer des Anrufers; leer = unterdrückt (dann gibt es nichts zu merken). */
    nummer: string;
    onSchliessen: () => void;
    onZugeordnet: (ergebnis: T) => void;
}

export function TelefonZuordnenDialog<T extends TelefonAnruf | Sprachnachricht>({
    offen, ziel, nummer, onSchliessen, onZugeordnet,
}: TelefonZuordnenDialogProps<T>) {
    const toast = useToast();
    const [typ, setTyp] = useState<KontaktTyp>('KUNDE');
    const [suchfensterOffen, setSuchfensterOffen] = useState(false);
    const [gewaehlt, setGewaehlt] = useState<GewaehlterKontakt | null>(null);
    const [nummerMerken, setNummerMerken] = useState(true);
    const [speichert, setSpeichert] = useState(false);
    const unterdrueckt = !nummer.trim();

    const wechsleTyp = (neu: KontaktTyp) => {
        if (neu === typ) return;
        setTyp(neu);
        setGewaehlt(null);
    };

    const waehleKunde = (kunde: KundeSearchItem) => {
        setGewaehlt({ typ: 'KUNDE', id: kunde.id, name: kunde.name, zusatz: [kunde.kundennummer, kunde.ort].filter(Boolean).join(' · ') || null });
    };

    const waehleLieferant = (lieferant: LieferantSuchErgebnis) => {
        setGewaehlt({ typ: 'LIEFERANT', id: lieferant.id, name: lieferant.lieferantenname, zusatz: lieferant.ort || null });
    };

    const zuordnen = async () => {
        if (!gewaehlt) return;
        setSpeichert(true);
        try {
            const ergebnis = await ordneZu<T>(ziel, {
                kundeId: gewaehlt.typ === 'KUNDE' ? gewaehlt.id : null,
                lieferantId: gewaehlt.typ === 'LIEFERANT' ? gewaehlt.id : null,
                nummerMerken: !unterdrueckt && nummerMerken,
            });
            toast.success(`${ziel.art === 'anruf' ? 'Anruf' : 'Nachricht'} ${gewaehlt.name} zugeordnet.`);
            refreshNotifications();
            onZugeordnet(ergebnis);
            onSchliessen();
        } catch (fehler) {
            toast.error(fehler instanceof Error ? fehler.message : 'Der Kontakt konnte nicht zugeordnet werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const SymbolTyp = typ === 'KUNDE' ? User : Truck;

    return (
        <Dialog open={offen} onOpenChange={(o) => { if (!o) onSchliessen(); }} className="w-full max-w-lg" aria-labelledby="telefon-zuordnen-titel">
            <DialogContent>
                <DialogHeader>
                    <DialogTitle id="telefon-zuordnen-titel" className="text-slate-900">
                        {ziel.art === 'anruf' ? 'Anruf zuordnen' : 'Nachricht zuordnen'}
                    </DialogTitle>
                    <DialogDescription>
                        {unterdrueckt ? 'Nummer unterdrückt' : <>Anrufer: <span className="font-medium tabular-nums text-slate-700">{anzeigeNummer(nummer)}</span></>}
                    </DialogDescription>
                </DialogHeader>

                {/* Umschalter Kunde | Lieferant */}
                <div role="radiogroup" aria-label="Art des Kontakts" className="inline-flex self-start rounded-lg border border-slate-200 bg-slate-50 p-1">
                    {(['KUNDE', 'LIEFERANT'] as const).map((option) => (
                        <button
                            key={option}
                            type="button"
                            role="radio"
                            aria-checked={typ === option}
                            onClick={() => wechsleTyp(option)}
                            className={cn(
                                'flex items-center gap-2 rounded-md px-4 py-1.5 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500',
                                typ === option ? 'bg-white text-rose-700 shadow-sm ring-1 ring-rose-200' : 'text-slate-600 hover:text-slate-900',
                            )}
                        >
                            {option === 'KUNDE' ? <User aria-hidden="true" className="h-4 w-4" /> : <Truck aria-hidden="true" className="h-4 w-4" />}
                            {option === 'KUNDE' ? 'Kunde' : 'Lieferant'}
                        </button>
                    ))}
                </div>

                {/* Gewählter Kontakt bzw. Suche öffnen */}
                {gewaehlt ? (
                    <div className="flex items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white text-rose-600">
                            <UserCheck aria-hidden="true" className="h-5 w-5" />
                        </div>
                        <div className="min-w-0 flex-1">
                            <p className="break-words font-semibold text-slate-900">{gewaehlt.name}</p>
                            {gewaehlt.zusatz && <p className="break-words text-sm text-slate-500">{gewaehlt.zusatz}</p>}
                        </div>
                        <Button type="button" variant="ghost" size="sm" onClick={() => setSuchfensterOffen(true)}>
                            Ändern
                        </Button>
                    </div>
                ) : (
                    <button
                        type="button"
                        onClick={() => setSuchfensterOffen(true)}
                        className="flex w-full items-center gap-3 rounded-lg border border-dashed border-slate-300 p-4 text-left text-slate-600 transition-colors hover:border-rose-300 hover:bg-rose-50 hover:text-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500"
                    >
                        <Search aria-hidden="true" className="h-5 w-5 shrink-0" />
                        <span className="flex-1 font-medium">{typ === 'KUNDE' ? 'Kunde suchen …' : 'Lieferant suchen …'}</span>
                        <SymbolTyp aria-hidden="true" className="h-5 w-5 shrink-0 text-slate-400" />
                    </button>
                )}

                {!unterdrueckt && (
                    <label className="flex cursor-pointer items-start gap-3">
                        <input
                            type="checkbox"
                            checked={nummerMerken}
                            onChange={(e) => setNummerMerken(e.target.checked)}
                            className="mt-0.5 h-4 w-4 shrink-0 accent-rose-600 focus:ring-2 focus:ring-rose-500"
                        />
                        <span>
                            <span className="block text-sm font-medium text-slate-900">Nummer beim Kontakt merken</span>
                            <span className="block text-xs text-slate-500">Künftige Anrufe von dieser Nummer werden dann automatisch zugeordnet.</span>
                        </span>
                    </label>
                )}

                <DialogFooter className="gap-2">
                    <Button type="button" variant="outline" onClick={onSchliessen} disabled={speichert}>
                        Abbrechen
                    </Button>
                    <Button
                        type="button"
                        onClick={zuordnen}
                        disabled={!gewaehlt || speichert}
                        title={gewaehlt ? undefined : 'Bitte zuerst einen Kontakt auswählen'}
                    >
                        {speichert && <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" />}
                        Zuordnen
                    </Button>
                </DialogFooter>
            </DialogContent>

            {typ === 'KUNDE' ? (
                <KundeSearchModal
                    isOpen={suchfensterOffen}
                    onClose={() => setSuchfensterOffen(false)}
                    onSelect={waehleKunde}
                    currentKundeId={gewaehlt?.typ === 'KUNDE' ? gewaehlt.id : undefined}
                />
            ) : (
                <LieferantSearchModal
                    isOpen={suchfensterOffen}
                    onClose={() => setSuchfensterOffen(false)}
                    onSelect={waehleLieferant}
                    currentLieferantId={gewaehlt?.typ === 'LIEFERANT' ? gewaehlt.id : undefined}
                />
            )}
        </Dialog>
    );
}

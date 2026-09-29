import { useEffect, useRef, useState } from 'react';
import { Calculator, Loader2, Search, Truck, User, UserCheck } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { KundeSearchModal, type KundeSearchItem } from '../../components/KundeSearchModal';
import { LieferantSearchModal, type LieferantSuchErgebnis } from '../../components/LieferantSearchModal';
import { refreshNotifications } from '../../lib/notificationRefresh';
import { cn } from '../../lib/utils';
import { ladeSteuerberaterAuswahl, ordneZu, zuordnenDaten } from './api';
import { anzeigeNummer, KONTAKTART_TEXT } from './format';
import type { KontaktKurz, KontaktTyp, Sprachnachricht, TelefonAnruf, ZuordnenZiel } from './types';

/**
 * Anruf oder Anrufbeantworter-Nachricht einem Kunden, Lieferanten oder
 * Steuerberater zuordnen.
 *
 * <p>Für Kunden und Lieferanten erledigen die vorhandenen Suchfenster
 * ({@link KundeSearchModal}, {@link LieferantSearchModal}) die Suche. Kanzleien
 * gibt es nur wenige – sie stehen direkt zur Auswahl im Dialog. Gemerkte
 * Nummern ordnen künftige Anrufe automatisch zu.</p>
 */

const ARTEN: { typ: KontaktTyp; symbol: typeof User }[] = [
    { typ: 'KUNDE', symbol: User },
    { typ: 'LIEFERANT', symbol: Truck },
    { typ: 'STEUERBERATER', symbol: Calculator },
];

type KanzleiListe = { status: 'laedt' } | { status: 'fertig'; kanzleien: KontaktKurz[] } | { status: 'fehler'; meldung: string };

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
    const [kanzleien, setKanzleien] = useState<KanzleiListe>({ status: 'laedt' });
    const kanzleienAngefragt = useRef(false);
    const eingehaengt = useRef(true);
    const unterdrueckt = !nummer.trim();
    // Bei Kanzleien kein „Nummer merken": gemerkte Nummern stehen sonst nirgends zum Ansehen oder Löschen.
    const merkenMoeglich = !unterdrueckt && typ !== 'STEUERBERATER';

    useEffect(() => {
        eingehaengt.current = true;
        return () => { eingehaengt.current = false; };
    }, []);

    // Kanzleien erst laden, wenn „Steuerberater" gewählt ist – und dann nur einmal.
    // Kein Abbruch beim Zurückwechseln: die Liste steht dann beim nächsten Mal bereit.
    const zeigtKanzleien = offen && typ === 'STEUERBERATER';
    useEffect(() => {
        if (!zeigtKanzleien || kanzleienAngefragt.current) return;
        kanzleienAngefragt.current = true;
        ladeSteuerberaterAuswahl()
            .then((liste) => {
                if (eingehaengt.current) setKanzleien({ status: 'fertig', kanzleien: liste });
            })
            .catch((fehler: unknown) => {
                if (!eingehaengt.current) return;
                const meldung = fehler instanceof Error ? fehler.message : 'Die Steuerberater konnten nicht geladen werden.';
                setKanzleien({ status: 'fehler', meldung });
                toast.error(meldung);
            });
    }, [zeigtKanzleien, toast]);

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
            const ergebnis = await ordneZu<T>(ziel, zuordnenDaten(gewaehlt.typ, gewaehlt.id, merkenMoeglich && nummerMerken));
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

    const SymbolTyp = ARTEN.find((art) => art.typ === typ)?.symbol ?? User;

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

                {/* Umschalter Kunde | Lieferant | Steuerberater */}
                <div role="radiogroup" aria-label="Art des Kontakts" className="inline-flex self-start rounded-lg border border-slate-200 bg-slate-50 p-1">
                    {ARTEN.map(({ typ: option, symbol: Symbol }) => (
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
                            <Symbol aria-hidden="true" className="h-4 w-4" />
                            {KONTAKTART_TEXT[option]}
                        </button>
                    ))}
                </div>

                {/* Kanzlei direkt wählen – es gibt nur wenige */}
                {typ === 'STEUERBERATER' ? (
                    <KanzleiAuswahl
                        liste={kanzleien}
                        gewaehltId={gewaehlt?.typ === 'STEUERBERATER' ? gewaehlt.id : null}
                        onWaehlen={(k) => setGewaehlt({ typ: 'STEUERBERATER', id: k.id, name: k.name, zusatz: null })}
                    />
                ) : gewaehlt ? (
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
                        <span className="flex-1 font-medium">{KONTAKTART_TEXT[typ]} suchen …</span>
                        <SymbolTyp aria-hidden="true" className="h-5 w-5 shrink-0 text-slate-400" />
                    </button>
                )}

                {!unterdrueckt && typ === 'STEUERBERATER' && (
                    <p className="text-xs text-slate-500">
                        Damit Anrufe der Kanzlei künftig automatisch erkannt werden, hinterlegen Administratoren die Nummer
                        unter Firma › Steuerberater bei der Kanzlei oder dem Ansprechpartner – Durchwahlen werden dann mit erkannt.
                    </p>
                )}
                {!unterdrueckt && typ !== 'STEUERBERATER' && (
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

            {typ === 'KUNDE' && (
                <KundeSearchModal
                    isOpen={suchfensterOffen}
                    onClose={() => setSuchfensterOffen(false)}
                    onSelect={waehleKunde}
                    currentKundeId={gewaehlt?.typ === 'KUNDE' ? gewaehlt.id : undefined}
                />
            )}
            {typ === 'LIEFERANT' && (
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

interface KanzleiAuswahlProps {
    liste: KanzleiListe;
    gewaehltId: number | null;
    onWaehlen: (kanzlei: KontaktKurz) => void;
}

/** Kanzleien als Auswahlliste – Laden, leer und Fehler klar unterscheidbar. */
function KanzleiAuswahl({ liste, gewaehltId, onWaehlen }: KanzleiAuswahlProps) {
    if (liste.status === 'laedt') {
        return (
            <div role="status" aria-label="Steuerberater werden geladen" className="space-y-2">
                <div className="h-11 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                <div className="h-11 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
            </div>
        );
    }
    if (liste.status === 'fehler') {
        return <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{liste.meldung}</p>;
    }
    if (liste.kanzleien.length === 0) {
        return (
            <p className="rounded-lg border border-dashed border-slate-300 p-4 text-sm text-slate-500">
                Noch kein Steuerberater angelegt. Administratoren legen Kanzleien unter Firma › Steuerberater an.
            </p>
        );
    }
    return (
        // -m-1 p-1: Platz für den Fokus-Ring, sonst schneidet die Scrollbox ihn ab.
        <div role="radiogroup" aria-label="Steuerberater wählen" className="-m-1 max-h-60 space-y-2 overflow-y-auto p-1">
            {liste.kanzleien.map((kanzlei) => {
                const gewaehlt = kanzlei.id === gewaehltId;
                return (
                    <button
                        key={kanzlei.id}
                        type="button"
                        role="radio"
                        aria-checked={gewaehlt}
                        onClick={() => onWaehlen(kanzlei)}
                        className={cn(
                            'flex w-full items-center gap-3 rounded-lg border p-3 text-left transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500',
                            gewaehlt ? 'border-rose-300 bg-rose-50 text-rose-800' : 'border-slate-200 bg-white text-slate-700 hover:border-rose-200 hover:bg-rose-50',
                        )}
                    >
                        {gewaehlt
                            ? <UserCheck aria-hidden="true" className="h-5 w-5 shrink-0 text-rose-600" />
                            : <Calculator aria-hidden="true" className="h-5 w-5 shrink-0 text-slate-400" />}
                        <span className="min-w-0 flex-1 break-words font-medium">{kanzlei.name}</span>
                    </button>
                );
            })}
        </div>
    );
}

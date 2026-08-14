import { useEffect, useState } from 'react';
import { X } from 'lucide-react';

import { ArtikelSuche } from './ArtikelSuche';
import { formatCurrency } from './formatCurrency';
import { preisHinweisKurz, type PreisHinweis } from './preisHinweis';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import type { Artikel } from '../../types';

export interface ArtikelAuswahl {
    artikelId: number;
    /** Innensicht — wird zum Block-title. Faellt auf Produktname + Abmessung zurueck. */
    titel: string;
    /** Kundentext — wird zur Block-description. Leer, wenn nicht gepflegt. */
    beschreibungHtml: string;
    menge: number;
    einheit: string;
    /** 0, wenn kein Preis ermittelbar war — der Bediener traegt ihn im Editor nach. */
    einzelpreis: number;
}

export interface ArtikelAuswahlDialogProps {
    offen: boolean;
    onSchliessen: () => void;
    onUebernehmen: (auswahl: ArtikelAuswahl[]) => void;
}

const zuAuswahl = (artikel: Artikel, menge: number): ArtikelAuswahl => ({
    artikelId: artikel.id,
    // Ohne gepflegte Kurzbeschreibung faellt der Titel auf Produktname plus
    // Abmessung zurueck. Er ist Innensicht und steht nicht im Kundendokument -
    // eine technische Bezeichnung ist hier also unschaedlich und besser als
    // eine namenlose Position im Editor.
    titel: artikel.kurzbeschreibung?.trim()
        || [artikel.produktname, artikel.abmessung].filter(Boolean).join(' '),
    beschreibungHtml: artikel.beschreibung ?? '',
    menge,
    einheit: artikel.positionsEinheit ?? 'Stk',
    // Kein ermittelbarer Preis wird zu 0. Der Bediener sieht die Null im Editor
    // und traegt den Wert nach - eine leere Zahl wuerde die Summenrechnung
    // durcheinanderbringen.
    einzelpreis: artikel.positionsEinzelpreis ?? 0,
});

export function ArtikelAuswahlDialog({ offen, onSchliessen, onUebernehmen }: ArtikelAuswahlDialogProps) {
    // artikelId -> Menge. Wer drin steht, ist ausgewaehlt.
    const [gewaehlt, setGewaehlt] = useState<Map<number, number>>(new Map());
    // Die vollen Artikeldaten der ausgewaehlten Zeilen. Ohne diese Kopie waere
    // die Auswahl weg, sobald der Bediener den Filter aendert und der Treffer
    // aus der Liste faellt.
    const [gemerkt, setGemerkt] = useState<Map<number, Artikel>>(new Map());

    useEffect(() => {
        if (!offen) return;
        const beiTaste = (e: KeyboardEvent) => { if (e.key === 'Escape') onSchliessen(); };
        window.addEventListener('keydown', beiTaste);
        return () => window.removeEventListener('keydown', beiTaste);
    }, [offen, onSchliessen]);

    // Beim Oeffnen mit leerer Auswahl starten - sonst schleppt das Fenster die
    // Auswahl des letzten Aufrufs mit. Bewusst waehrend des Renderns statt in
    // einem Effect: React unterstuetzt das Zuruecksetzen von State bei einer
    // Prop-Aenderung explizit auf diesem Weg (react.dev, "You Might Not Need
    // An Effect") - ein Effect wuerde hier nur einen unnoetigen zusaetzlichen
    // Render-Durchlauf verursachen und wird von eslint-plugin-react-hooks
    // (react-hooks/set-state-in-effect) zu Recht bemaengelt.
    const [vorherOffen, setVorherOffen] = useState(offen);
    if (offen !== vorherOffen) {
        setVorherOffen(offen);
        if (offen) {
            setGewaehlt(new Map());
            setGemerkt(new Map());
        }
    }

    if (!offen) return null;

    const umschalten = (artikel: Artikel) => {
        setGewaehlt((prev) => {
            const naechste = new Map(prev);
            if (naechste.has(artikel.id)) naechste.delete(artikel.id);
            else naechste.set(artikel.id, 1);
            return naechste;
        });
        setGemerkt((prev) => new Map(prev).set(artikel.id, artikel));
    };

    const setzeMenge = (artikelId: number, menge: number) =>
        setGewaehlt((prev) => new Map(prev).set(artikelId, menge));

    const uebernehmen = () => {
        const auswahl = [...gewaehlt.entries()]
            .map(([artikelId, menge]) => {
                const artikel = gemerkt.get(artikelId);
                return artikel ? zuAuswahl(artikel, menge) : null;
            })
            .filter((a): a is ArtikelAuswahl => a !== null);
        onUebernehmen(auswahl);
    };

    const zeilenAktion = (artikel: Artikel) => {
        const ausgewaehlt = gewaehlt.has(artikel.id);
        const hinweis = (artikel.preisHinweis ?? 'KEIN_PREIS') as PreisHinweis;
        return (
            <div className="flex items-center gap-3">
                <input
                    type="checkbox"
                    checked={ausgewaehlt}
                    onChange={() => umschalten(artikel)}
                    aria-label={`${artikel.produktname} auswählen`}
                    className="w-4 h-4 accent-rose-600"
                />
                <Input
                    type="number"
                    min={0}
                    step={0.01}
                    value={gewaehlt.get(artikel.id) ?? 1}
                    onChange={(e) => setzeMenge(artikel.id, Number(e.target.value))}
                    aria-label={`Menge für ${artikel.produktname}`}
                    className="w-20 h-8 text-sm"
                    disabled={!ausgewaehlt}
                />
                <span className="text-xs text-slate-500 w-8">{artikel.positionsEinheit}</span>
                {hinweis === 'OK' ? (
                    <span className="text-xs text-slate-500 tabular-nums">
                        {formatCurrency(artikel.positionsEinzelpreis)}
                    </span>
                ) : (
                    <span className="text-amber-700 bg-amber-50 rounded px-1.5 py-0.5 text-[10px]">
                        {preisHinweisKurz[hinweis]}
                    </span>
                )}
            </div>
        );
    };

    return (
        <div className="fixed inset-0 z-50 bg-white flex flex-col">
            <div className="flex items-start justify-between px-6 py-4 border-b border-slate-200">
                <div>
                    <h2 className="text-xl font-bold text-slate-900">Material auswählen</h2>
                    <p className="text-sm text-slate-500 mt-0.5">
                        Suche wie in der Materialverwaltung — Menge eintragen, übernehmen.
                    </p>
                </div>
                <button onClick={onSchliessen} className="p-2 hover:bg-slate-100 rounded-md" aria-label="Schließen">
                    <X className="w-5 h-5 text-slate-400" />
                </button>
            </div>

            <div className="flex-1 overflow-y-auto px-6 py-4">
                <ArtikelSuche urlSync={false} seitenGroesse={20} zeilenAktion={zeilenAktion} />
            </div>

            <div className="flex items-center justify-between px-6 py-4 border-t border-slate-200">
                <span className="text-sm text-slate-500">{gewaehlt.size} ausgewählt</span>
                <div className="flex gap-2">
                    <Button variant="outline" size="sm"
                            className="border-rose-300 text-rose-700 hover:bg-rose-50"
                            onClick={onSchliessen}>
                        Abbrechen
                    </Button>
                    <Button size="sm"
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                            onClick={uebernehmen}
                            disabled={gewaehlt.size === 0}>
                        Übernehmen
                    </Button>
                </div>
            </div>
        </div>
    );
}

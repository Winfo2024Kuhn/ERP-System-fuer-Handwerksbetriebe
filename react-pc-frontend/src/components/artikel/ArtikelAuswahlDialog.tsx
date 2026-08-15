import { useEffect, useState } from 'react';
import { X } from 'lucide-react';

import { ArtikelSuche } from './ArtikelSuche';
import { formatCurrency } from './formatCurrency';
import { baueKundentext, hatKundentext, kundentextFuerPosition } from './kundentext';
import { preisHinweisKurz, type PreisHinweis } from './preisHinweis';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { cn } from '../../lib/utils';
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
    // Der Kundentext entscheidet, was auf dem Angebot steht. Waere er leer,
    // druckt der Notnagel in RechnungPdfService:881-889 ersatzweise den Titel —
    // also die Innensicht, die den Kunden nichts angeht. Deshalb wird er
    // notfalls aus den Stammdaten gebaut (siehe kundentext.ts).
    beschreibungHtml: kundentextFuerPosition(artikel),
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
    // Was im Mengenfeld steht, solange der Bediener tippt — auch dann, wenn es
    // (noch) keine gueltige Zahl ist. Ohne diesen Zwischenschritt liesse sich
    // das Feld gar nicht leeren: Die Anzeige kaeme direkt aus `gewaehlt`, der
    // letzte gueltige Wert spraenge sofort zurueck und aus "12" wuerde beim
    // Korrigieren "112".
    const [mengenText, setMengenText] = useState<Map<number, string>>(new Map());

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
            setMengenText(new Map());
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
        // Eine frisch angehakte Zeile faengt bei 1 an, auch wenn vorher schon
        // etwas im Feld stand.
        setMengenText((prev) => {
            const naechste = new Map(prev);
            naechste.delete(artikel.id);
            return naechste;
        });
        setGemerkt((prev) => new Map(prev).set(artikel.id, artikel));
    };

    /**
     * Uebernimmt die Eingabe des Mengenfelds. In die Auswahl wandert nur eine
     * echte Zahl groesser 0; alles andere laesst den letzten gueltigen Wert
     * stehen.
     *
     * Das ist kein Schoenheitsfehler: Ein geleertes Feld liefert
     * `Number('') === 0`, und eine Position mit Menge 0 haette eine Zeilensumme
     * von 0 — eine negative Menge sogar eine negative Zeilensumme im Angebot.
     * Das `min` am Feld ist nur ein Browser-Hinweis und haelt davon nichts auf.
     */
    const setzeMenge = (artikelId: number, eingabe: string) => {
        setMengenText((prev) => new Map(prev).set(artikelId, eingabe));
        const wert = Number(eingabe);
        if (!Number.isFinite(wert) || wert <= 0) return;
        setGewaehlt((prev) => new Map(prev).set(artikelId, wert));
    };

    /** Zeigt das Feld einer gewaehlten Zeile gerade keine brauchbare Menge? */
    const mengeUngueltig = (artikelId: number): boolean => {
        const eingabe = mengenText.get(artikelId);
        if (eingabe === undefined) return false;
        const wert = Number(eingabe);
        return !Number.isFinite(wert) || wert <= 0;
    };

    const mengenLuecke = [...gewaehlt.keys()].some(mengeUngueltig);

    const uebernehmen = () => {
        // Zweiter Riegel neben setzeMenge: Der Knopf ist in diesem Fall zwar
        // gesperrt, aber eine Position mit Menge 0 im Angebot waere teuer genug,
        // um sie nicht von einem disabled-Attribut allein abhaengig zu machen.
        if (mengenLuecke) return;
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
        // Fehlender Kundentext gehoert VOR das Uebernehmen, nicht danach: Er ist
        // das Einzige, was der Kunde spaeter liest. Der Hinweis steht im selben
        // Stil wie die Preis-Hinweise, damit die Zeile nicht zwei Sprachen spricht.
        const kundentextFehlt = !hatKundentext(artikel.beschreibung);
        const ersatztext = kundentextFehlt ? baueKundentext(artikel) : '';
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
                    min={0.01}
                    step={0.01}
                    value={mengenText.get(artikel.id) ?? String(gewaehlt.get(artikel.id) ?? 1)}
                    onChange={(e) => setzeMenge(artikel.id, e.target.value)}
                    aria-label={`Menge für ${artikel.produktname}`}
                    aria-invalid={ausgewaehlt && mengeUngueltig(artikel.id)}
                    className={cn(
                        'w-20 h-8 text-sm',
                        ausgewaehlt && mengeUngueltig(artikel.id) && 'border-amber-400 bg-amber-50',
                    )}
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
                {kundentextFehlt && (
                    <span
                        className="text-amber-700 bg-amber-50 rounded px-1.5 py-0.5 text-[10px]"
                        title={ersatztext
                            ? `Beim Übernehmen wird daraus: „${ersatztext.replace(/<[^>]*>/g, '')}“`
                            : 'Lässt sich aus den Stammdaten nicht erzeugen — bitte im Editor selbst schreiben.'}
                    >
                        kein Kundentext
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
                <div className="flex items-center gap-3">
                    {mengenLuecke && (
                        <span className="text-xs text-amber-700">
                            Bitte überall eine Menge größer 0 eintragen.
                        </span>
                    )}
                    <Button variant="outline" size="sm"
                            className="border-rose-300 text-rose-700 hover:bg-rose-50"
                            onClick={onSchliessen}>
                        Abbrechen
                    </Button>
                    <Button size="sm"
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                            onClick={uebernehmen}
                            disabled={gewaehlt.size === 0 || mengenLuecke}>
                        Übernehmen
                    </Button>
                </div>
            </div>
        </div>
    );
}

import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from 'react';
import {
    Crop, FlipHorizontal, FlipVertical, RotateCcw, RotateCw, RefreshCw, X,
} from 'lucide-react';
import { Button } from '../ui/button';
import {
    MAX_BREITE_UPLOAD,
    STANDARD_BEARBEITUNG,
    zeichne,
    type Bildbearbeitung,
    type Zuschnitt,
} from './bildbearbeitung';

export interface BildEditorModalProps {
    offen: boolean;
    bildUrl: string;
    startBearbeitung?: Bildbearbeitung;
    onAbbrechen: () => void;
    onUebernehmen: (bearbeitung: Bildbearbeitung) => void;
}

const VERHAELTNISSE = [
    { name: 'Frei', wert: null },
    { name: '16:9', wert: 16 / 9 },
    { name: '4:3', wert: 4 / 3 },
    { name: '1:1', wert: 1 },
] as const;

/**
 * Bildbearbeitung fuer Beitragsbilder. Zuschneiden, drehen, spiegeln,
 * Helligkeit und Kontrast.
 *
 * Gibt beim Uebernehmen die BESCHREIBUNG der Bearbeitung zurueck, nicht das
 * fertige Bild. Der Assistent rendert daraus zwei Groessen: 1600 px fuer die
 * Website und 1024 px fuer die KI. Das Original im ERP wird nie veraendert.
 */
export function BildEditorModal({
    offen, bildUrl, startBearbeitung, onAbbrechen, onUebernehmen,
}: BildEditorModalProps) {
    const [bearbeitung, setBearbeitung] = useState<Bildbearbeitung>(
        startBearbeitung ?? STANDARD_BEARBEITUNG);
    const [verhaeltnis, setVerhaeltnis] = useState<number | null>(null);
    const [bild, setBild] = useState<HTMLImageElement | null>(null);
    const canvasRef = useRef<HTMLCanvasElement | null>(null);

    // Beim Oeffnen den mitgegebenen Stand uebernehmen.
    useEffect(() => {
        // Bewusst synchron im Effekt: "bearbeitung" ist veraenderlicher
        // Editier-Zustand des Nutzers und kein aus den Props ableitbarer Wert,
        // darum wird er hier beim Oeffnen bzw. bei neuem startBearbeitung
        // gezielt zurueckgesetzt statt abgeleitet.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        if (offen) setBearbeitung(startBearbeitung ?? STANDARD_BEARBEITUNG);
    }, [offen, startBearbeitung]);

    // Original einmal laden. crossOrigin ist nicht noetig, die Bilder kommen
    // vom eigenen Host ueber /api/dokumente beziehungsweise /api/beitraege.
    useEffect(() => {
        if (!offen) return;
        const img = new Image();
        img.onload = () => setBild(img);
        img.src = bildUrl;
        return () => { img.onload = null; };
    }, [offen, bildUrl]);

    // Bei jeder Aenderung neu zeichnen, immer vom Original aus.
    useEffect(() => {
        const ctx = canvasRef.current?.getContext('2d');
        if (ctx && bild) zeichne(ctx, bild, bearbeitung, MAX_BREITE_UPLOAD);
    }, [bild, bearbeitung]);

    const aendere = useCallback((teil: Partial<Bildbearbeitung>) => {
        setBearbeitung(vorher => ({ ...vorher, ...teil }));
    }, []);

    const drehe = (richtung: 1 | -1) => {
        const neu = (((bearbeitung.drehung + richtung * 90) % 360) + 360) % 360;
        aendere({ drehung: neu as Bildbearbeitung['drehung'] });
    };

    /**
     * Legt einen mittigen Zuschnitt im gewaehlten Seitenverhaeltnis an.
     * Ein Ziehrahmen waere komfortabler, ein mittiger Vorschlag deckt aber
     * den haeufigen Fall ab, ohne Maus-Mathematik in jsdom testen zu muessen.
     */
    const setzeVerhaeltnis = (wert: number | null) => {
        setVerhaeltnis(wert);
        if (wert === null || !bild) {
            aendere({ zuschnitt: null });
            return;
        }
        const istVerhaeltnis = bild.width / bild.height;
        let breite = bild.width;
        let hoehe = bild.height;
        if (istVerhaeltnis > wert) {
            breite = Math.round(bild.height * wert);
        } else {
            hoehe = Math.round(bild.width / wert);
        }
        const zuschnitt: Zuschnitt = {
            x: Math.round((bild.width - breite) / 2),
            y: Math.round((bild.height - hoehe) / 2),
            breite,
            hoehe,
        };
        aendere({ zuschnitt });
    };

    const setzeZurueck = () => {
        setBearbeitung(STANDARD_BEARBEITUNG);
        setVerhaeltnis(null);
    };

    if (!offen) return null;

    return (
        <div className="fixed inset-0 bg-black/60 z-[70] flex items-center justify-center p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden">
                <div className="flex items-center justify-between p-4 border-b border-slate-200">
                    <div className="flex items-center gap-2">
                        <Crop className="w-5 h-5 text-rose-600" />
                        <h2 className="text-lg font-bold text-slate-900">Bild bearbeiten</h2>
                    </div>
                    <button
                        type="button"
                        onClick={onAbbrechen}
                        aria-label="Schließen"
                        title="Schließen"
                        className="p-1.5 hover:bg-slate-100 rounded-full"
                    >
                        <X className="w-5 h-5 text-slate-500" />
                    </button>
                </div>

                <div className="flex-1 overflow-y-auto p-4 bg-slate-50 flex items-center justify-center min-h-[240px]">
                    <canvas ref={canvasRef} className="max-w-full max-h-[50vh] object-contain shadow" />
                </div>

                <div className="p-4 border-t border-slate-200 space-y-4">
                    <div className="flex flex-wrap items-center gap-2">
                        <Werkzeug label="Nach links drehen" onClick={() => drehe(-1)}>
                            <RotateCcw className="w-4 h-4" />
                        </Werkzeug>
                        <Werkzeug label="Nach rechts drehen" onClick={() => drehe(1)}>
                            <RotateCw className="w-4 h-4" />
                        </Werkzeug>
                        <Werkzeug
                            label="Waagerecht spiegeln"
                            aktiv={bearbeitung.spiegelnX}
                            onClick={() => aendere({ spiegelnX: !bearbeitung.spiegelnX })}
                        >
                            <FlipHorizontal className="w-4 h-4" />
                        </Werkzeug>
                        <Werkzeug
                            label="Senkrecht spiegeln"
                            aktiv={bearbeitung.spiegelnY}
                            onClick={() => aendere({ spiegelnY: !bearbeitung.spiegelnY })}
                        >
                            <FlipVertical className="w-4 h-4" />
                        </Werkzeug>

                        <span className="w-px h-6 bg-slate-300 mx-1" />

                        <span className="text-sm text-slate-500">Ausschnitt</span>
                        {VERHAELTNISSE.map(v => (
                            <button
                                key={v.name}
                                type="button"
                                onClick={() => setzeVerhaeltnis(v.wert)}
                                className={`px-2.5 py-1.5 text-sm rounded-lg border transition-colors
                                    ${verhaeltnis === v.wert
                                        ? 'bg-rose-600 text-white border-rose-600'
                                        : 'border-slate-200 text-slate-600 hover:bg-slate-100'}`}
                            >
                                {v.name}
                            </button>
                        ))}
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <Regler
                            label="Helligkeit"
                            wert={bearbeitung.helligkeit}
                            onChange={wert => aendere({ helligkeit: wert })}
                        />
                        <Regler
                            label="Kontrast"
                            wert={bearbeitung.kontrast}
                            onChange={wert => aendere({ kontrast: wert })}
                        />
                    </div>

                    <div className="flex justify-between gap-2">
                        <Button size="sm" variant="ghost" onClick={setzeZurueck}>
                            <RefreshCw className="w-4 h-4" />
                            Zurücksetzen
                        </Button>
                        <div className="flex gap-2">
                            <Button size="sm" variant="outline" onClick={onAbbrechen}>
                                Abbrechen
                            </Button>
                            <Button size="sm" onClick={() => onUebernehmen(bearbeitung)}>
                                Übernehmen
                            </Button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

function Werkzeug({ label, aktiv, onClick, children }: {
    label: string; aktiv?: boolean; onClick: () => void; children: ReactNode;
}) {
    return (
        <button
            type="button"
            aria-label={label}
            title={label}
            onClick={onClick}
            className={`p-2 rounded-lg border transition-colors
                ${aktiv
                    ? 'bg-rose-100 text-rose-700 border-rose-200'
                    : 'border-slate-200 text-slate-600 hover:bg-slate-100'}`}
        >
            {children}
        </button>
    );
}

function Regler({ label, wert, onChange }: {
    label: string; wert: number; onChange: (wert: number) => void;
}) {
    const id = useId();
    return (
        <div>
            <div className="flex items-center justify-between text-sm text-slate-600 mb-1">
                <label htmlFor={id}>{label}</label>
                <span className="text-slate-400">{wert}%</span>
            </div>
            <input
                id={id}
                type="range"
                min={50}
                max={150}
                value={wert}
                onChange={e => onChange(Number(e.target.value))}
                className="w-full accent-rose-600"
            />
        </div>
    );
}

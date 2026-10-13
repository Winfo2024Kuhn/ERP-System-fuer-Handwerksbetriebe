import type { CSSProperties } from 'react';
import { cn } from '../../lib/utils';
import { mmZuPt, normMasse, rechteckInPt, type AnschriftStatus, type Briefform, type RechteckMm } from './normMasse';

interface NormOverlayProps {
    form: Briefform;
    /** Aktueller Zoom – Linien und Beschriftungen bleiben auf dem Bildschirm gleich groß. */
    zoom: number;
    /** Färbt die Anschriftzone: grün, wenn die Anschrift passt, sonst gelb. */
    status: AnschriftStatus;
    /** Hilfslinien zeigen (Fenster, Zonen, Infoblock, Falz- und Lochmarken). */
    linien?: boolean;
    /** Umschlag-Ansicht: alles außer dem Fenster abdunkeln. */
    umschlag?: boolean;
}

const lage = (r: RechteckMm): CSSProperties => {
    const pt = rechteckInPt(r);
    return { left: pt.x, top: pt.y, width: pt.width, height: pt.height };
};

/**
 * Norm-Hilfen nach DIN 5008 über der A4-Seite. Liegt im selben pt-Koordinaten-
 * system wie die Bausteine, ist aber nur Anzeige: nicht klickbar, nicht im PDF.
 */
export function NormOverlay({ form, zoom, status, linien = true, umschlag = false }: NormOverlayProps) {
    const m = normMasse(form);
    const strich = 1 / zoom;
    const schrift: CSSProperties = { fontSize: 9 / zoom, lineHeight: 1.2, paddingTop: 1 / zoom, paddingBottom: 1 / zoom, paddingLeft: 3 / zoom, paddingRight: 3 / zoom };
    const passt = status === 'passt';
    const zoneFarbe = status === 'keine-anschrift'
        ? 'bg-slate-500/5'
        : passt ? 'bg-emerald-500/10' : 'bg-amber-400/20';

    return (
        <div aria-hidden="true" data-testid="norm-overlay" className="pointer-events-none absolute inset-0" style={{ zIndex: 900 }}>
            {umschlag && (
                // Großer Schatten um das Fenster = abgedunkelter Umschlag; die Seite schneidet ihn ab.
                <div className="absolute rounded-[2px]" style={{ ...lage(m.fenster), boxShadow: '0 0 0 2000px rgba(15, 23, 42, 0.78)' }} />
            )}

            {linien && (
                <>
                    {/* Infoblock */}
                    <div className="absolute border-dashed border-slate-300" style={{ ...lage(m.infoblock), borderWidth: strich }}>
                        <span className="absolute left-0 whitespace-nowrap font-medium text-slate-400" style={{ ...schrift, bottom: '100%', paddingLeft: 0 }}>Infoblock</span>
                    </div>

                    {/* Zusatz- und Vermerkzone */}
                    <div className="absolute border-b border-dotted border-slate-400" style={{ ...lage(m.zusatzzone), borderBottomWidth: strich }}>
                        <span className="absolute bottom-0 right-0 whitespace-nowrap text-slate-400" style={schrift}>Rücksendeangabe · Vermerke</span>
                    </div>

                    {/* Anschriftzone */}
                    <div data-testid="norm-anschriftzone" className={cn('absolute', zoneFarbe)} style={lage(m.anschriftzone)}>
                        <span className={cn('absolute bottom-0 right-0 font-medium', passt ? 'text-emerald-700' : 'text-slate-500')} style={schrift}>
                            Anschrift
                        </span>
                    </div>

                    {/* Sichtfenster des Umschlags */}
                    <div className="absolute rounded-[2px] border-dashed border-slate-500" style={{ ...lage(m.fenster), borderWidth: strich }}>
                        {/* Rechts oben im Fenster: die Anschrift steht links, so bleibt sie lesbar. */}
                        <span className="absolute right-0 top-0 whitespace-nowrap font-semibold text-slate-600" style={schrift}>
                            Fenster (DIN 5008 Form {form})
                        </span>
                    </div>

                    {/* Falz- und Lochmarken am linken Rand */}
                    {m.falzmarken.map(y => (
                        <Marke key={y} y={mmZuPt(y)} laenge={mmZuPt(6)} strich={strich} text="Falz" schrift={schrift} />
                    ))}
                    <Marke y={mmZuPt(m.lochmarke)} laenge={mmZuPt(10)} strich={strich} text="Loch" schrift={schrift} />
                </>
            )}
        </div>
    );
}

function Marke({ y, laenge, strich, text, schrift }: { y: number; laenge: number; strich: number; text: string; schrift: CSSProperties }) {
    return (
        <div className="absolute left-0" style={{ top: y }}>
            <div className="bg-slate-500" style={{ width: laenge, height: strich }} />
            <span className="absolute whitespace-nowrap text-slate-400" style={{ ...schrift, left: laenge, top: 0, transform: 'translateY(-50%)' }}>{text}</span>
        </div>
    );
}

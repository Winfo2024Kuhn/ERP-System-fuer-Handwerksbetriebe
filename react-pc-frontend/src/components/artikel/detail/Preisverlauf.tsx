import { useMemo } from "react";
import { Minus, TrendingDown, TrendingUp } from "lucide-react";
import type { ArtikelPreisstand } from "../../../types";
import { formatCurrency, formatDate } from "./format";

/**
 * Preisverlauf als Liniendiagramm je Lieferant, ergaenzt um eine Tabelle.
 *
 * Die Tabelle ist nicht nur Beiwerk: Ein Diagramm allein ist fuer Screenreader
 * nicht lesbar, und die genauen Werte sind ohnehin oft die eigentliche Frage.
 */
export function Preisverlauf({ staende }: { staende: ArtikelPreisstand[] }) {
    const mitDatum = useMemo(
        () => staende.filter((s) => s.preisDatum && s.preis !== undefined && s.preis !== null),
        [staende],
    );

    const serien = useMemo(() => {
        const map = new Map<string, ArtikelPreisstand[]>();
        for (const s of mitDatum) {
            const key = s.lieferantName ?? "Ohne Lieferant";
            if (!map.has(key)) map.set(key, []);
            map.get(key)!.push(s);
        }
        for (const liste of map.values()) {
            liste.sort((a, b) => new Date(a.preisDatum!).getTime() - new Date(b.preisDatum!).getTime());
        }
        return [...map.entries()];
    }, [mitDatum]);

    if (mitDatum.length === 0) {
        return (
            <p className="text-sm text-slate-500 py-6 text-center">
                Noch keine Preisänderungen erfasst. Sobald ein Preis geändert wird,
                erscheint hier der Verlauf.
            </p>
        );
    }

    // Achsenbereiche
    const zeiten = mitDatum.map((s) => new Date(s.preisDatum!).getTime());
    const preise = mitDatum.map((s) => s.preis!);
    const tMin = Math.min(...zeiten);
    const tMax = Math.max(...zeiten);
    const pMin = Math.min(...preise);
    const pMax = Math.max(...preise);
    // Etwas Luft nach oben und unten, damit Punkte nicht am Rand kleben.
    const pSpanne = pMax - pMin || Math.max(pMax * 0.1, 1);
    const yMin = Math.max(0, pMin - pSpanne * 0.15);
    const yMax = pMax + pSpanne * 0.15;

    // padL traegt die Preisbeschriftung der Achse. Sie ist bei Kleinstpreisen
    // deutlich breiter ("0,0043 €" statt "0,00 €"), deshalb der Zuschlag -
    // sonst laeuft die Beschriftung links aus dem Diagramm heraus.
    const B = 720, H = 240, padL = 84, padR = 16, padT = 16, padB = 36;
    const x = (t: number) => tMax === tMin
        ? padL + (B - padL - padR) / 2
        : padL + ((t - tMin) / (tMax - tMin)) * (B - padL - padR);
    const y = (p: number) => padT + (1 - (p - yMin) / (yMax - yMin || 1)) * (H - padT - padB);

    // Kontraststarke, gut unterscheidbare Farben (nicht rot/gruen als Paar).
    const farben = ["#dc2626", "#0f766e", "#7c3aed", "#b45309", "#1d4ed8", "#be185d"];

    return (
        <div>
            <div className="overflow-x-auto">
                {/* Auf breiten Monitoren nicht beliebig mitwachsen: Sonst werden
                    Beschriftung und Punkte doppelt so gross wie der Text daneben. */}
                <svg viewBox={`0 0 ${B} ${H}`} className="w-full max-w-5xl min-w-[560px] h-auto"
                    role="img"
                    aria-label={`Preisverlauf von ${formatCurrency(pMin)} bis ${formatCurrency(pMax)} über ${serien.length} Lieferanten`}>
                    {/* Waagerechte Hilfslinien mit Preisbeschriftung */}
                    {[0, 0.25, 0.5, 0.75, 1].map((anteil) => {
                        const wert = yMin + (yMax - yMin) * (1 - anteil);
                        const py = padT + anteil * (H - padT - padB);
                        return (
                            <g key={anteil}>
                                <line x1={padL} y1={py} x2={B - padR} y2={py} stroke="#e2e8f0" strokeWidth="1" />
                                <text x={padL - 8} y={py + 4} textAnchor="end"
                                    className="fill-slate-500" style={{ fontSize: 11 }}>
                                    {/* Bei Kleinstpreisen stuenden hier sonst fuenfmal
                                        "0,00 EUR" untereinander - die Achse waere blind. */}
                                    {formatCurrency(wert)}
                                </text>
                            </g>
                        );
                    })}

                    {/* Zeitachse */}
                    <line x1={padL} y1={H - padB} x2={B - padR} y2={H - padB} stroke="#94a3b8" strokeWidth="1" />
                    <text x={padL} y={H - padB + 18} className="fill-slate-500" style={{ fontSize: 11 }}>
                        {new Date(tMin).toLocaleDateString("de-DE")}
                    </text>
                    <text x={B - padR} y={H - padB + 18} textAnchor="end"
                        className="fill-slate-500" style={{ fontSize: 11 }}>
                        {new Date(tMax).toLocaleDateString("de-DE")}
                    </text>

                    {/* Eine Linie je Lieferant */}
                    {serien.map(([name, punkte], i) => {
                        const farbe = farben[i % farben.length];
                        const d = punkte
                            .map((p, idx) => `${idx === 0 ? "M" : "L"} ${x(new Date(p.preisDatum!).getTime())} ${y(p.preis!)}`)
                            .join(" ");
                        return (
                            <g key={name}>
                                {punkte.length > 1 && (
                                    <path d={d} fill="none" stroke={farbe} strokeWidth="2"
                                        strokeLinejoin="round" strokeLinecap="round" />
                                )}
                                {punkte.map((p) => (
                                    <circle key={p.preisId}
                                        cx={x(new Date(p.preisDatum!).getTime())}
                                        cy={y(p.preis!)}
                                        r={p.aktuell ? 5 : 3.5}
                                        fill={p.aktuell ? farbe : "#ffffff"}
                                        stroke={farbe} strokeWidth="2">
                                        <title>
                                            {name}: {formatCurrency(p.preis)} am {formatDate(p.preisDatum)}
                                            {p.aktuell ? " (gilt aktuell)" : ""}
                                        </title>
                                    </circle>
                                ))}
                            </g>
                        );
                    })}
                </svg>
            </div>

            {/* Legende */}
            <ul className="flex flex-wrap gap-x-5 gap-y-2 mt-3">
                {serien.map(([name], i) => (
                    <li key={name} className="flex items-center gap-2 text-sm text-slate-700">
                        <span className="inline-block w-4 h-1 rounded-full"
                            style={{ backgroundColor: farben[i % farben.length] }} aria-hidden="true" />
                        {name}
                    </li>
                ))}
            </ul>

            {/* Werte-Tabelle als barrierefreie Entsprechung des Diagramms */}
            <div className="overflow-x-auto mt-5">
                <table className="w-full text-sm">
                    <caption className="sr-only">Alle Preisstände mit Datum, Lieferant und Herkunft</caption>
                    <thead>
                        <tr className="text-left text-slate-500 border-b border-slate-200">
                            <th scope="col" className="py-2 pr-4 font-medium">Datum</th>
                            <th scope="col" className="py-2 pr-4 font-medium">Lieferant</th>
                            <th scope="col" className="py-2 pr-4 font-medium text-right">Preis</th>
                            <th scope="col" className="py-2 pr-4 font-medium text-right">Änderung</th>
                            <th scope="col" className="py-2 pr-4 font-medium">Herkunft</th>
                            <th scope="col" className="py-2 font-medium">Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        {staende.map((s, idx) => {
                            // Vorheriger Stand desselben Lieferanten fuer die Veraenderung.
                            const vorher = staende
                                .slice(idx + 1)
                                .find((v) => v.lieferantId === s.lieferantId && v.preis !== undefined);
                            const diff = vorher?.preis !== undefined && s.preis !== undefined
                                ? s.preis - vorher.preis
                                : undefined;
                            return (
                                <tr key={s.preisId} className="border-b border-slate-100 last:border-0">
                                    <td className="py-2.5 pr-4 text-slate-600">{formatDate(s.preisDatum)}</td>
                                    <td className="py-2.5 pr-4 text-slate-900">{s.lieferantName ?? "—"}</td>
                                    <td className="py-2.5 pr-4 text-right tabular-nums font-medium text-slate-900">
                                        {formatCurrency(s.preis)}
                                    </td>
                                    <td className="py-2.5 pr-4 text-right tabular-nums">
                                        {diff === undefined ? (
                                            <span className="text-slate-400">—</span>
                                        ) : diff > 0 ? (
                                            <span className="inline-flex items-center gap-1 text-amber-700">
                                                <TrendingUp className="w-3.5 h-3.5" aria-hidden="true" />
                                                +{formatCurrency(diff)}
                                            </span>
                                        ) : diff < 0 ? (
                                            <span className="inline-flex items-center gap-1 text-emerald-700">
                                                <TrendingDown className="w-3.5 h-3.5" aria-hidden="true" />
                                                {formatCurrency(diff)}
                                            </span>
                                        ) : (
                                            <span className="inline-flex items-center gap-1 text-slate-500">
                                                <Minus className="w-3.5 h-3.5" aria-hidden="true" />
                                                unverändert
                                            </span>
                                        )}
                                    </td>
                                    <td className="py-2.5 pr-4 text-slate-500">{s.quelle?.anzeigename ?? "—"}</td>
                                    <td className="py-2.5 text-slate-600">
                                        {s.aktuell
                                            ? <span className="text-xs font-semibold text-slate-900">gilt aktuell</span>
                                            : <span className="text-xs text-slate-400">abgelöst</span>}
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

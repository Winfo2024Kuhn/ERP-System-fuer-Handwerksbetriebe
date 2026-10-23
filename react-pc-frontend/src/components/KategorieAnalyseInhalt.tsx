import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { BarChart3, CheckCircle2, Clock, FolderOpen, Info, RefreshCw, TrendingUp } from 'lucide-react';
import { Chart, registerables } from 'chart.js';
import { Button } from './ui/button';
import { Card } from './ui/card';
import { cn } from '../lib/utils';
import { leseFehlermeldung } from '../lib/apiFehler';
import type { ProduktkategorieAnalyse } from '../types';

Chart.register(...registerables);

/** Zahlen deutsch mit fester Nachkommazahl, z. B. „10,91“. */
const fest = (wert: number, stellen = 2) =>
    new Intl.NumberFormat('de-DE', { minimumFractionDigits: stellen, maximumFractionDigits: stellen }).format(wert);

// Design-System: rose für Projekte und das Modell, slate für Hilfslinien, amber nur für Ausreißer (Warnung).
const ROSE = 'rgba(225, 29, 72, 1)';
const ROSE_FLAECHE = 'rgba(225, 29, 72, 0.7)';
const ROSE_BAND_1 = 'rgba(225, 29, 72, 0.12)';
const ROSE_BAND_2 = 'rgba(225, 29, 72, 0.06)';
const ROSE_DUNKEL = '#be123c';
const SLATE = '#64748b';
const SLATE_RASTER = 'rgba(148, 163, 184, 0.15)';
const AMBER = 'rgba(245, 158, 11, 1)';
const AMBER_FLAECHE = 'rgba(245, 158, 11, 0.7)';

export interface KategorieAnalyseInhaltProps {
    kategorieId: number;
    /** Abschlussjahr, leer = alle Jahre. */
    jahr: string;
    className?: string;
}

/**
 * Zeit-/Mengen-Analyse einer Kategorie (Ordner): Kennzahlen aus der Regression,
 * Streudiagramm, Arbeitsgänge und die Projekte, die in die Rechnung eingehen.
 * Wird direkt in eine Seite eingebettet (Reiter „Auswertung“) und im
 * Analyse-Fenster des Angebots-Editors genutzt.
 */
export function KategorieAnalyseInhalt({ kategorieId, jahr, className }: KategorieAnalyseInhaltProps) {
    const [daten, setDaten] = useState<ProduktkategorieAnalyse | null>(null);
    const [ohneAusreisser, setOhneAusreisser] = useState(false);
    const [laedt, setLaedt] = useState(true);
    const [fehler, setFehler] = useState<string | null>(null);
    /** Fachlicher Hinweis des Servers (400), z. B. gemischte Einheiten – kein Fehler, kein „Erneut versuchen“. */
    const [hinweis, setHinweis] = useState<string | null>(null);
    const canvasRef = useRef<HTMLCanvasElement | null>(null);
    const chartRef = useRef<Chart | null>(null);

    const laden = useCallback(async () => {
        setLaedt(true);
        setFehler(null);
        setHinweis(null);
        try {
            const params = new URLSearchParams();
            if (jahr) params.set('jahr', jahr);
            if (ohneAusreisser) params.set('ohneAusreisser', 'true');
            const qs = params.toString();
            const res = await fetch(`/api/produktkategorien/${kategorieId}/analyse${qs ? `?${qs}` : ''}`);
            if (res.status === 400) {
                setHinweis(await leseFehlermeldung(res, 'Für diesen Ordner lässt sich keine Zeitanalyse rechnen.'));
                setDaten(null);
                return;
            }
            if (!res.ok) throw new Error(await leseFehlermeldung(res, 'Die Analyse konnte nicht geladen werden.'));
            setDaten(await res.json());
        } catch (err) {
            // TypeError = keine Verbindung; sonst steht die Meldung des Servers im Fehler.
            setFehler(err instanceof Error && !(err instanceof TypeError) ? err.message : 'Die Analyse konnte nicht geladen werden.');
            setDaten(null);
        } finally {
            setLaedt(false);
        }
    }, [kategorieId, jahr, ohneAusreisser]);

    useEffect(() => { void laden(); }, [laden]);

    useEffect(() => {
        if (!daten || !canvasRef.current) return;
        const ctx = canvasRef.current.getContext('2d');
        if (!ctx) return;
        chartRef.current?.destroy();

        // Das Modell kommt direkt aus der Backend-Regression (OLS): zeit = fixzeit + steigung * menge.
        // Eine eigene Steigung (Summe der Arbeitsgang-Durchschnitte) wäre zu flach und passte weder zur
        // Genauigkeit (R²) noch zu den Bändern – siehe KategorieAnalyseInhalt.test.tsx.
        const steigung = daten.steigung || 0;
        const fixzeit = Math.max(0, daten.fixzeit);
        const mitMenge = daten.projekte.filter((p) => p.masseinheit > 0);
        const normale = mitMenge.filter((p) => !p.ausreisser);
        const ausreisser = mitMenge.filter((p) => p.ausreisser);
        const maxMenge = Math.max(0, ...mitMenge.map((p) => p.masseinheit));
        const linienEnde = maxMenge > 0 ? maxMenge : 1;
        const maxZeit = Math.max(0, ...mitMenge.map((p) => p.zeitGesamt), fixzeit, fixzeit + steigung * linienEnde);
        const einheit = daten.verrechnungseinheit;
        const sigma = daten.residualStdAbweichung || 0;

        const band = (faktor: number) => {
            const oben: { x: number; y: number }[] = [];
            const unten: { x: number; y: number }[] = [];
            for (let i = 0; i <= 20; i++) {
                const x = (linienEnde / 20) * i;
                const y = fixzeit + steigung * x;
                oben.push({ x, y: y + faktor * sigma });
                unten.push({ x, y: Math.max(0, y - faktor * sigma) });
            }
            return [...oben, ...unten.reverse()];
        };

        chartRef.current = new Chart(ctx, {
            type: 'scatter',
            data: {
                datasets: [
                    ...(sigma > 0 ? [
                        { label: 'Üblicher Bereich (weit)', type: 'line' as const, data: band(2), backgroundColor: ROSE_BAND_2, borderColor: 'transparent', pointRadius: 0, fill: true },
                        { label: 'Üblicher Bereich', type: 'line' as const, data: band(1), backgroundColor: ROSE_BAND_1, borderColor: 'transparent', pointRadius: 0, fill: true },
                    ] : []),
                    { label: 'Projekte', data: normale.map((p) => ({ x: p.masseinheit, y: p.zeitGesamt })), backgroundColor: ROSE_FLAECHE, borderColor: ROSE, pointRadius: 7, pointHoverRadius: 10 },
                    ...(ausreisser.length > 0 ? [{
                        label: 'Ausreißer', data: ausreisser.map((p) => ({ x: p.masseinheit, y: p.zeitGesamt })),
                        backgroundColor: AMBER_FLAECHE, borderColor: AMBER, pointRadius: 9, pointHoverRadius: 12, pointStyle: 'triangle' as const,
                    }] : []),
                    { label: 'Grundzeit', type: 'line' as const, data: [{ x: 0, y: fixzeit }, { x: linienEnde, y: fixzeit }], borderColor: SLATE, borderWidth: 2, borderDash: [5, 5], pointRadius: 0, fill: false },
                    { label: 'Richtwert', type: 'line' as const, data: [{ x: 0, y: fixzeit }, { x: linienEnde, y: fixzeit + steigung * linienEnde }], borderColor: ROSE_DUNKEL, borderWidth: 2, pointRadius: 0, fill: false },
                ],
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: { beginAtZero: true, suggestedMax: linienEnde * 1.1, title: { display: true, text: einheit }, grid: { color: SLATE_RASTER } },
                    y: { beginAtZero: true, suggestedMax: maxZeit * 1.1, title: { display: true, text: 'Stunden gesamt' }, grid: { color: SLATE_RASTER } },
                },
                plugins: {
                    legend: { display: true, position: 'bottom' },
                    tooltip: {
                        callbacks: {
                            label(context) {
                                const punkt = context.raw as { x: number; y: number };
                                const name = context.dataset.label || '';
                                if (name === 'Projekte' || name === 'Ausreißer') {
                                    const projekt = (name === 'Ausreißer' ? ausreisser : normale)[context.dataIndex];
                                    if (projekt) {
                                        return `${projekt.auftragsnummer} – ${projekt.kunde}: ${fest(punkt.x, 1)} ${einheit}, ${fest(punkt.y)} Std.${name === 'Ausreißer' ? ' (Ausreißer)' : ''}`;
                                    }
                                }
                                return `${fest(punkt.x, 1)}, ${fest(punkt.y)}`;
                            },
                        },
                    },
                },
            },
        });
        return () => { chartRef.current?.destroy(); chartRef.current = null; };
    }, [daten]);

    const arbeitsgaenge = daten?.arbeitsgangAnalysen ?? [];
    // Summe der direkt je Einheit gebuchten Zeit – reine Aufschlüsselung, NICHT die Modell-Steigung.
    const arbeitsgangSumme = arbeitsgaenge.reduce((summe, a) => summe + a.durchschnittStundenProEinheit, 0);
    const steigung = daten?.steigung ?? 0;
    const fixzeit = daten ? Math.max(0, daten.fixzeit) : 0;
    const rQ = daten?.rQuadrat ?? 0;
    const qualitaet = rQ >= 0.7 ? 'Zuverlässig' : rQ >= 0.4 ? 'Grobe Schätzung' : 'Wenig Erfahrungswerte';
    const qualitaetFarbe = rQ >= 0.7 ? 'text-emerald-700 bg-emerald-50 border-emerald-200'
        : rQ >= 0.4 ? 'text-amber-700 bg-amber-50 border-amber-200' : 'text-slate-700 bg-slate-50 border-slate-200';

    return (
        <section className={cn('space-y-5', className)} aria-label="Zeit- und Mengenanalyse" aria-busy={laedt}>
            <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
                <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer select-none">
                    <input
                        type="checkbox"
                        checked={ohneAusreisser}
                        onChange={(e) => setOhneAusreisser(e.target.checked)}
                        className="h-4 w-4 rounded border-slate-300 accent-rose-600 focus-visible:ring-2 focus-visible:ring-rose-500"
                    />
                    Ausreißer aus der Rechnung nehmen
                </label>
                <span className="text-xs text-slate-500 flex items-center gap-1">
                    <Info className="w-3.5 h-3.5" aria-hidden="true" />
                    Nur abgeschlossene Projekte mit erfassten Zeiten
                </span>
            </div>

            {laedt && (
                <div role="status" className="space-y-4" aria-label="Analyse wird geladen">
                    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                        {[0, 1, 2, 3].map((i) => <div key={i} className="h-24 rounded-lg bg-slate-100 motion-safe:animate-pulse" />)}
                    </div>
                    <div className="h-72 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                    <span className="sr-only">Analyse wird geladen …</span>
                </div>
            )}

            {hinweis && !laedt && (
                <p role="status" className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                    <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    <span>{hinweis}</span>
                </p>
            )}

            {fehler && !laedt && (
                <Card className="p-4 border-rose-200 bg-rose-50 flex flex-wrap items-center justify-between gap-3">
                    <p className="text-sm text-rose-800">{fehler}</p>
                    <Button size="sm" variant="outline" onClick={() => void laden()}>
                        <RefreshCw className="w-4 h-4" aria-hidden="true" /> Erneut versuchen
                    </Button>
                </Card>
            )}

            {daten && !laedt && daten.projekte.length === 0 && (
                <Card className="p-10 text-center border-dashed border-slate-200">
                    <BarChart3 className="w-10 h-10 mx-auto mb-3 text-rose-200" aria-hidden="true" />
                    <p className="text-slate-700 font-medium">Noch keine Projekte für die Zeitanalyse</p>
                    <p className="text-slate-500 text-sm mt-1">
                        Sobald ein Projekt mit Menge und erfassten Zeiten abgeschlossen ist, erscheinen hier Diagramm und Richtwerte.
                    </p>
                </Card>
            )}

            {daten && !laedt && daten.projekte.length > 0 && (
                <>
                    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                        <Kennzahl icon={<FolderOpen className="w-4 h-4 text-slate-600" aria-hidden="true" />} titel="Projekte mit Menge"
                            wert={String(daten.datenpunkte)} zusatz="gehen in die Rechnung ein" />
                        <Kennzahl icon={<Clock className="w-4 h-4 text-rose-600" aria-hidden="true" />} titel="Grundzeit je Auftrag"
                            wert={fest(fixzeit)} zusatz="Std. – fällt immer an, egal wie groß" />
                        <Kennzahl icon={<TrendingUp className="w-4 h-4 text-rose-600" aria-hidden="true" />} titel={`Zeit je ${daten.verrechnungseinheit}`}
                            wert={fest(steigung)} zusatz="Std. für jede weitere Einheit" />
                        <Kennzahl icon={<CheckCircle2 className="w-4 h-4 text-rose-600" aria-hidden="true" />} titel="Verlässlichkeit"
                            wert={`${fest(rQ * 100, 0)} %`}
                            zusatz={<span className={cn('inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium border', qualitaetFarbe)}>{qualitaet}</span>} />
                    </div>

                    {daten.residualStdAbweichung > 0 && (
                        <p className="flex items-center gap-2 px-4 py-2.5 bg-slate-50 rounded-lg border border-slate-200 text-sm text-slate-600">
                            <Info className="w-4 h-4 text-slate-400 flex-shrink-0" aria-hidden="true" />
                            <span>
                                Typische Abweichung vom Richtwert:
                                <span className="font-semibold text-slate-800 mx-1">± {fest(daten.residualStdAbweichung, 1)} Std.</span>
                                – etwa zwei von drei Projekten liegen in diesem Bereich.
                            </span>
                        </p>
                    )}

                    <div className="grid grid-cols-1 xl:grid-cols-5 gap-5">
                        <Card className="p-5 border-slate-200 shadow-sm xl:col-span-3">
                            <h4 className="text-sm font-semibold text-slate-700 mb-4">Stunden nach Menge</h4>
                            <div className="h-72"><canvas ref={canvasRef} aria-label="Diagramm: Stunden je Projekt nach Menge" role="img" /></div>
                        </Card>
                        <Card className="p-5 border-slate-200 shadow-sm xl:col-span-2">
                            <h4 className="text-sm font-semibold text-slate-700 mb-4">Arbeitsgänge</h4>
                            {arbeitsgaenge.length === 0 ? (
                                <p className="text-sm text-slate-500 text-center py-8">Für diese Projekte wurden keine Arbeitsgänge gebucht.</p>
                            ) : (
                                <div className="space-y-3">
                                    <div className="flex items-center justify-between text-xs font-medium text-slate-500 pb-1 border-b border-slate-100">
                                        <span>Arbeitsgang</span>
                                        <span>Std. je {daten.verrechnungseinheit}</span>
                                    </div>
                                    {arbeitsgaenge.map((a) => (
                                        <div key={a.arbeitsgangId} className="flex items-center justify-between gap-2">
                                            <span className="text-sm text-slate-700 truncate">{a.arbeitsgangBeschreibung}</span>
                                            <span className="text-sm font-semibold text-rose-700 tabular-nums flex-shrink-0">{fest(a.durchschnittStundenProEinheit)}</span>
                                        </div>
                                    ))}
                                    <Zeile titel="Summe gebucht je Einheit" wert={fest(arbeitsgangSumme)} trenner />
                                    <Zeile titel="Zeit je Einheit (Richtwert)" wert={fest(steigung)} />
                                    <Zeile titel="Grundzeit je Auftrag" wert={`${fest(fixzeit)} Std.`} />
                                </div>
                            )}
                        </Card>
                    </div>

                    <Card className="border-slate-200 shadow-sm overflow-hidden">
                        <div className="px-5 py-3 border-b border-slate-100 bg-slate-50">
                            <h4 className="text-sm font-semibold text-slate-700">Projekte in der Rechnung ({daten.projekte.length})</h4>
                        </div>
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-slate-100 text-left text-xs font-semibold text-slate-500">
                                        <th className="px-5 py-2.5">Auftrag</th>
                                        <th className="px-5 py-2.5">Kunde</th>
                                        <th className="px-5 py-2.5 text-right">{daten.verrechnungseinheit}</th>
                                        <th className="px-5 py-2.5 text-right">Stunden</th>
                                        <th className="px-5 py-2.5 text-right">Std. je Einheit</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {daten.projekte.map((p) => (
                                        <tr key={p.id} className="border-b border-slate-50 last:border-0">
                                            <td className="px-5 py-2.5 font-medium text-slate-800">
                                                {p.auftragsnummer}
                                                {p.ausreisser && (
                                                    <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-xs bg-amber-50 text-amber-700 border border-amber-200">Ausreißer</span>
                                                )}
                                            </td>
                                            <td className="px-5 py-2.5 text-slate-600">{p.kunde || '—'}</td>
                                            <td className="px-5 py-2.5 text-right tabular-nums text-slate-700">{fest(p.masseinheit, 1)}</td>
                                            <td className="px-5 py-2.5 text-right tabular-nums text-slate-700">{fest(p.zeitGesamt)}</td>
                                            <td className="px-5 py-2.5 text-right tabular-nums font-medium text-rose-700">
                                                {p.masseinheit > 0 ? fest(p.zeitGesamt / p.masseinheit) : '—'}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </Card>
                </>
            )}
        </section>
    );
}

function Kennzahl({ icon, titel, wert, zusatz }: { icon: ReactNode; titel: string; wert: string; zusatz: ReactNode }) {
    return (
        <Card className="p-4 border-slate-200 shadow-sm">
            <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center flex-shrink-0">{icon}</div>
                <div className="min-w-0">
                    <p className="text-xs text-slate-500">{titel}</p>
                    <p className="text-2xl font-bold text-slate-900 tabular-nums">{wert}</p>
                    <div className="text-xs text-slate-500">{zusatz}</div>
                </div>
            </div>
        </Card>
    );
}

function Zeile({ titel, wert, trenner }: { titel: string; wert: string; trenner?: boolean }) {
    return (
        <div className={cn('flex items-center justify-between gap-2', trenner && 'pt-2 border-t border-slate-100')}>
            <span className="text-sm font-semibold text-slate-800">{titel}</span>
            <span className="text-sm font-bold text-rose-700 tabular-nums">{wert}</span>
        </div>
    );
}

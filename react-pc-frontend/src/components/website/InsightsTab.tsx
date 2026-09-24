import { useCallback, useEffect, useState } from 'react';
import {
    CategoryScale,
    Chart as ChartJS,
    Filler,
    LineElement,
    LinearScale,
    PointElement,
    Tooltip,
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import { AlertTriangle, ArrowDown, ArrowUp, BarChart3, Loader2, Minus } from 'lucide-react';
import { ladeAnalyticsAktuell, ladeAnalyticsVerlauf } from './api';
import type { AnalyticsSnapshot, VerlaufPunkt } from './typen';

// Muss auf Modulebene stehen, sonst zeichnet die Linie nicht.
ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Filler);

const ZEITRAEUME = [7, 30, 90] as const;

/**
 * Zeigt, was auf der Firmen-Website los ist. Die Zahlen schickt die Website
 * einmal taeglich als Schnappschuss ans ERP; hier werden sie nur gelesen.
 */
export function InsightsTab() {
    const [schnappschuss, setSchnappschuss] = useState<AnalyticsSnapshot | null>(null);
    const [verlauf, setVerlauf] = useState<VerlaufPunkt[]>([]);
    const [tage, setTage] = useState<number>(30);
    const [laedt, setLaedt] = useState(true);
    const [fehler, setFehler] = useState<string | null>(null);

    const laden = useCallback(async (zeitraum: number) => {
        setLaedt(true);
        setFehler(null);
        try {
            const [aktuell, punkte] = await Promise.all([
                ladeAnalyticsAktuell(),
                ladeAnalyticsVerlauf(zeitraum),
            ]);
            setSchnappschuss(aktuell);
            setVerlauf(punkte);
        } catch {
            setFehler('Die Zahlen konnten nicht geladen werden. Bitte später noch einmal versuchen.');
        } finally {
            setLaedt(false);
        }
    }, []);

    useEffect(() => { void laden(tage); }, [laden, tage]);

    if (laedt && !schnappschuss && !fehler) {
        return (
            <div className="flex items-center justify-center py-20 text-slate-400">
                <Loader2 className="w-6 h-6 animate-spin mr-2" />
                Zahlen werden geladen...
            </div>
        );
    }

    if (fehler) {
        return (
            <div className="flex items-start gap-3 p-4 bg-rose-50 border border-rose-200 rounded-lg text-rose-800">
                <AlertTriangle className="w-5 h-5 flex-shrink-0 mt-0.5" />
                <p>{fehler}</p>
            </div>
        );
    }

    // Zusaetzlich zu null (Backend antwortet 204) auch eine Antwort ohne
    // "totals" als "keine Zahlen" behandeln, statt weiter unten mit
    // undefined-Feldern zu rechnen.
    if (!schnappschuss || !schnappschuss.totals) {
        return (
            <div className="text-center py-20 text-slate-400">
                <BarChart3 className="w-10 h-10 mx-auto mb-3 opacity-30" />
                <p className="text-slate-600 font-medium">Noch keine Zahlen von der Website da.</p>
                <p className="mt-1">Die Website schickt ihre Zahlen einmal am Tag. Nach dem ersten Versand erscheinen sie hier.</p>
            </div>
        );
    }

    const unterschied = schnappschuss.visitorsToday - schnappschuss.visitorsYesterday;

    return (
        <div className="space-y-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <Kennzahl
                    titel="Besucher heute"
                    wert={schnappschuss.visitorsToday}
                    fussnote={vergleichstext(unterschied)}
                    richtung={unterschied}
                />
                <Kennzahl titel="Besucher insgesamt" wert={schnappschuss.totals.visitors} />
                <Kennzahl titel="Anfragen insgesamt" wert={schnappschuss.totals.submissions} />
                <Kennzahl titel="Anfragen je 100 Besucher" wert={schnappschuss.conversion} />
            </div>

            <div className="bg-white border border-slate-200 rounded-xl p-5">
                <div className="flex items-center justify-between mb-4">
                    <h2 className="font-semibold text-slate-900">Besucher im Zeitverlauf</h2>
                    <div className="flex gap-1">
                        {ZEITRAEUME.map(z => (
                            <button
                                key={z}
                                onClick={() => setTage(z)}
                                className={`px-3 py-1.5 text-sm rounded-lg border transition-colors cursor-pointer
                                    ${tage === z
                                        ? 'bg-rose-600 text-white border-rose-600'
                                        : 'border-slate-200 text-slate-600 hover:bg-slate-50'}`}
                            >
                                {z} Tage
                            </button>
                        ))}
                    </div>
                </div>
                {verlauf.length === 0 ? (
                    <p className="text-slate-400 py-8 text-center">Für diesen Zeitraum liegen noch keine Tage vor.</p>
                ) : (
                    <div className="h-64">
                        <Line
                            data={{
                                labels: verlauf.map(p => formatiereTag(p.snapshotDate)),
                                datasets: [{
                                    label: 'Besucher',
                                    data: verlauf.map(p => p.besucherAmTag),
                                    borderColor: '#dc2626',
                                    backgroundColor: 'rgba(220, 38, 38, 0.08)',
                                    fill: true,
                                    tension: 0.3,
                                    pointRadius: 2,
                                }],
                            }}
                            options={{
                                responsive: true,
                                maintainAspectRatio: false,
                                plugins: { legend: { display: false } },
                                scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
                            }}
                        />
                    </div>
                )}
            </div>

            <div className="bg-white border border-slate-200 rounded-xl p-5">
                <h2 className="font-semibold text-slate-900 mb-4">Weg der Besucher</h2>
                <Trichter schritte={schnappschuss.funnel} />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-4 gap-4">
                <Liste
                    titel="Meistbesuchte Seiten"
                    eintraege={schnappschuss.topPages.map(p => ({ name: p.path, anzahl: p.count }))}
                />
                <Liste
                    titel="Geräte"
                    eintraege={schnappschuss.devices.map(d => ({ name: d.device, anzahl: d.count }))}
                />
                <Liste
                    titel="Browser"
                    eintraege={schnappschuss.browsers.map(b => ({ name: b.browser, anzahl: b.count }))}
                />
                <Liste
                    titel="Städte"
                    eintraege={schnappschuss.cities.map(c => ({ name: c.city, anzahl: c.count }))}
                />
            </div>

            <p className="text-sm text-slate-400">
                Stand: {formatiereZeitpunkt(schnappschuss.generatedAt)}
            </p>
        </div>
    );
}

function vergleichstext(unterschied: number): string {
    if (unterschied === 0) return 'genauso viele wie gestern';
    if (unterschied > 0) return `${unterschied} mehr als gestern`;
    return `${Math.abs(unterschied)} weniger als gestern`;
}

function formatiereTag(iso: string): string {
    const [, monat, tag] = iso.split('-');
    return `${tag}.${monat}.`;
}

function formatiereZeitpunkt(iso: string): string {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' });
}

function Kennzahl({ titel, wert, fussnote, richtung }: {
    titel: string;
    wert: number;
    fussnote?: string;
    richtung?: number;
}) {
    const Pfeil = richtung === undefined || richtung === 0 ? Minus : richtung > 0 ? ArrowUp : ArrowDown;
    const farbe = richtung === undefined || richtung === 0
        ? 'text-slate-400'
        : richtung > 0 ? 'text-emerald-600' : 'text-rose-600';
    return (
        <div className="bg-white border border-slate-200 rounded-xl p-5">
            <p className="text-sm text-slate-500">{titel}</p>
            <p className="text-3xl font-bold text-slate-900 mt-1">{wert.toLocaleString('de-DE')}</p>
            {fussnote && (
                <p className={`text-sm mt-1 flex items-center gap-1 ${farbe}`}>
                    <Pfeil className="w-4 h-4" />
                    {fussnote}
                </p>
            )}
        </div>
    );
}

function Trichter({ schritte }: { schritte: { name: string; label: string; count: number }[] }) {
    if (schritte.length === 0) {
        return <p className="text-slate-400">Dazu liegen noch keine Zahlen vor.</p>;
    }
    const groesster = Math.max(...schritte.map(s => s.count), 1);
    return (
        <div className="space-y-2">
            {schritte.map(schritt => (
                <div key={schritt.name} className="flex items-center gap-3">
                    <span className="w-40 flex-shrink-0 text-sm text-slate-600 truncate">{schritt.label}</span>
                    <div className="flex-1 bg-slate-100 rounded-full h-6 overflow-hidden">
                        <div
                            className="bg-rose-500 h-full rounded-full"
                            style={{ width: `${Math.round((schritt.count / groesster) * 100)}%` }}
                        />
                    </div>
                    <span className="w-16 text-right text-sm font-medium text-slate-900">
                        {schritt.count.toLocaleString('de-DE')}
                    </span>
                </div>
            ))}
        </div>
    );
}

function Liste({ titel, eintraege }: { titel: string; eintraege: { name: string; anzahl: number }[] }) {
    return (
        <div className="bg-white border border-slate-200 rounded-xl p-5">
            <h3 className="font-semibold text-slate-900 mb-3">{titel}</h3>
            {eintraege.length === 0 ? (
                <p className="text-sm text-slate-400">Noch nichts erfasst.</p>
            ) : (
                <ul className="space-y-2">
                    {eintraege.slice(0, 8).map(e => (
                        <li key={e.name} className="flex items-center justify-between gap-2 text-sm">
                            <span className="text-slate-600 truncate" title={e.name}>{e.name}</span>
                            <span className="font-medium text-slate-900 flex-shrink-0">
                                {e.anzahl.toLocaleString('de-DE')}
                            </span>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}

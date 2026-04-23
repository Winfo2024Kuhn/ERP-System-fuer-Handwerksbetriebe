import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Briefcase, ChevronRight, Loader2, Package, Plus, Search, X } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { PageLayout } from '../components/layout/PageLayout';
import { ProjektSearchModal } from '../components/ProjektSearchModal';
import { useToast } from '../components/ui/toast';

interface OffeneBedarfsZeile {
    id: number;
    projektId?: number | null;
    projektName?: string | null;
    projektNummer?: string | null;
    kundenName?: string | null;
    menge?: number | null;
    stueckzahl?: number | null;
    einheit?: string | null;
    kilogramm?: number | null;
    gesamtKilogramm?: number | null;
}

interface ProjektGruppe {
    projektId: number;
    bauvorhaben: string;
    auftragsnummer: string | null;
    kunde: string | null;
    anzahlZeilen: number;
    summeKilogramm: number;
}

const ZEILEN_OHNE_PROJEKT_KEY = -1;

export default function BedarfUebersichtPage() {
    const navigate = useNavigate();
    const toast = useToast();
    const [zeilen, setZeilen] = useState<OffeneBedarfsZeile[]>([]);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState('');
    const [projektPickerOffen, setProjektPickerOffen] = useState(false);

    const ladeBedarfe = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/bestellungen/offen');
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            setZeilen(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error('Bedarfe konnten nicht geladen werden', err);
            toast.error('Bedarfe konnten nicht geladen werden.');
            setZeilen([]);
        } finally {
            setLoading(false);
        }
    }, [toast]);

    useEffect(() => {
        ladeBedarfe();
    }, [ladeBedarfe]);

    // Bedarfe nach Projekt zusammenfassen
    const gruppen = useMemo<ProjektGruppe[]>(() => {
        const map = new Map<number, ProjektGruppe>();
        for (const z of zeilen) {
            const projektId = z.projektId ?? ZEILEN_OHNE_PROJEKT_KEY;
            // Pro-Zeile-Gewicht; gesamtKilogramm wäre der Projekt-Total, der pro
            // Zeile dupliziert ist und hier zu N-fach Aufsummierung führen würde.
            const kg = Number(z.kilogramm) || 0;
            const exist = map.get(projektId);
            if (exist) {
                exist.anzahlZeilen += 1;
                exist.summeKilogramm += kg;
            } else {
                map.set(projektId, {
                    projektId,
                    bauvorhaben: z.projektName ?? 'Ohne Projektzuordnung',
                    auftragsnummer: z.projektNummer ?? null,
                    kunde: z.kundenName ?? null,
                    anzahlZeilen: 1,
                    summeKilogramm: kg,
                });
            }
        }
        return Array.from(map.values()).sort((a, b) =>
            a.bauvorhaben.localeCompare(b.bauvorhaben, 'de')
        );
    }, [zeilen]);

    const sichtbar = useMemo(() => {
        const q = filter.trim().toLowerCase();
        if (!q) return gruppen;
        return gruppen.filter(g => {
            const hay = [g.bauvorhaben, g.auftragsnummer ?? '', g.kunde ?? '']
                .join(' ')
                .toLowerCase();
            return hay.includes(q);
        });
    }, [gruppen, filter]);

    const summen = useMemo(() => ({
        projekte: sichtbar.length,
        zeilenSumme: sichtbar.reduce((s, g) => s + g.anzahlZeilen, 0),
        kgSumme: sichtbar.reduce((s, g) => s + g.summeKilogramm, 0),
    }), [sichtbar]);

    const oeffneProjekt = (projektId: number) => {
        if (projektId === ZEILEN_OHNE_PROJEKT_KEY) {
            toast.warning('Diese Zeilen sind keinem Projekt zugeordnet.');
            return;
        }
        navigate(`/bestellungen/bedarf/projekt/${projektId}`);
    };

    return (
        <PageLayout
            ribbonCategory="Einkauf"
            title="Bedarf je Projekt"
            subtitle="Welches Projekt braucht noch Material?"
            actions={
                <Button
                    className="bg-rose-600 text-white hover:bg-rose-700"
                    onClick={() => setProjektPickerOffen(true)}
                >
                    <Plus className="w-4 h-4" />
                    Bedarf für Projekt anlegen
                </Button>
            }
        >
            {/* Filterleiste */}
            <div className="bg-white p-6 rounded-2xl shadow-lg border border-slate-100">
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                    <div className="lg:col-span-2">
                        <Label htmlFor="bedarf-filter" className="text-sm font-medium text-gray-700">
                            Projekt suchen
                        </Label>
                        <div className="relative mt-1">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
                            <Input
                                id="bedarf-filter"
                                value={filter}
                                onChange={e => setFilter(e.target.value)}
                                placeholder="Bauvorhaben, Auftragsnummer oder Kunde…"
                                className="pl-9"
                            />
                            {filter && (
                                <button
                                    type="button"
                                    onClick={() => setFilter('')}
                                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-rose-600"
                                    title="Filter zurücksetzen"
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            )}
                        </div>
                    </div>
                    <div className="grid grid-cols-3 gap-4 lg:gap-6 items-end">
                        <KennzahlBlock label="Projekte" wert={summen.projekte.toString()} />
                        <KennzahlBlock label="Offene Zeilen" wert={summen.zeilenSumme.toString()} />
                        <KennzahlBlock
                            label="Stahlgewicht"
                            wert={`${summen.kgSumme.toLocaleString('de-DE', { maximumFractionDigits: 0 })} kg`}
                        />
                    </div>
                </div>
            </div>

            {/* Liste */}
            {loading ? (
                <div className="bg-white p-12 rounded-2xl text-center text-slate-500 border border-slate-100">
                    <Loader2 className="w-6 h-6 mx-auto mb-2 animate-spin text-rose-400" />
                    Bedarfe werden geladen…
                </div>
            ) : sichtbar.length === 0 ? (
                <EmptyState
                    isFiltered={filter.trim().length > 0}
                    hatGruppen={gruppen.length > 0}
                    onResetFilter={() => setFilter('')}
                    onAnlegen={() => setProjektPickerOffen(true)}
                />
            ) : (
                <div className="bg-white rounded-2xl shadow-lg overflow-hidden border border-slate-100">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm text-left">
                            <thead className="bg-slate-50 text-slate-500 font-medium border-b border-slate-200">
                                <tr>
                                    <th className="px-4 py-3">Projekt</th>
                                    <th className="px-4 py-3">Kunde</th>
                                    <th className="px-4 py-3 text-right">Offene Zeilen</th>
                                    <th className="px-4 py-3 text-right">Stahlgewicht</th>
                                    <th className="px-4 py-3 w-10" aria-label="Pfeil"></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {sichtbar.map(g => (
                                    <tr
                                        key={g.projektId}
                                        onClick={() => oeffneProjekt(g.projektId)}
                                        role="button"
                                        tabIndex={0}
                                        onKeyDown={e => {
                                            if (e.key === 'Enter' || e.key === ' ') {
                                                e.preventDefault();
                                                oeffneProjekt(g.projektId);
                                            }
                                        }}
                                        className="hover:bg-rose-50/50 cursor-pointer transition-colors group"
                                        title={`Bedarf von ${g.bauvorhaben} öffnen`}
                                    >
                                        <td className="px-4 py-3">
                                            <div className="flex items-start gap-3 min-w-0">
                                                <div className="w-9 h-9 rounded-lg bg-slate-100 group-hover:bg-rose-100 flex items-center justify-center flex-shrink-0 transition-colors">
                                                    <Briefcase className="w-4 h-4 text-slate-500 group-hover:text-rose-600" />
                                                </div>
                                                <div className="min-w-0">
                                                    <p className="font-medium text-slate-900 truncate">
                                                        {g.bauvorhaben}
                                                    </p>
                                                    {g.auftragsnummer && (
                                                        <p className="text-xs font-mono text-slate-500 mt-0.5">
                                                            {g.auftragsnummer}
                                                        </p>
                                                    )}
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-4 py-3 text-slate-600 truncate max-w-[280px]">
                                            {g.kunde ?? <span className="text-slate-400 italic">—</span>}
                                        </td>
                                        <td className="px-4 py-3 text-right">
                                            <span className="inline-flex items-center justify-center min-w-[36px] h-7 px-2.5 rounded-full bg-rose-100 text-rose-800 text-sm font-semibold tabular-nums">
                                                {g.anzahlZeilen}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-right text-slate-700 tabular-nums">
                                            {g.summeKilogramm > 0
                                                ? `${g.summeKilogramm.toLocaleString('de-DE', { maximumFractionDigits: 0 })} kg`
                                                : <span className="text-slate-400">—</span>}
                                        </td>
                                        <td className="px-4 py-3 text-right">
                                            <ChevronRight className="w-5 h-5 text-slate-300 group-hover:text-rose-400 transition-colors" />
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            <ProjektSearchModal
                isOpen={projektPickerOffen}
                onClose={() => setProjektPickerOffen(false)}
                onSelect={(p) => navigate(`/bestellungen/bedarf/projekt/${p.id}`)}
                nurOffene
            />
        </PageLayout>
    );
}

function KennzahlBlock({ label, wert }: { label: string; wert: string }) {
    return (
        <div>
            <p className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</p>
            <p className="text-lg font-semibold text-slate-900 tabular-nums whitespace-nowrap">{wert}</p>
        </div>
    );
}

function EmptyState({
    isFiltered,
    hatGruppen,
    onResetFilter,
    onAnlegen,
}: {
    isFiltered: boolean;
    hatGruppen: boolean;
    onResetFilter: () => void;
    onAnlegen: () => void;
}) {
    if (isFiltered && hatGruppen) {
        return (
            <div className="py-16 px-6 text-center border border-dashed border-slate-200 rounded-2xl bg-slate-50">
                <Search className="w-10 h-10 mx-auto text-slate-300 mb-3" />
                <h3 className="text-lg font-semibold text-slate-800">
                    Kein Projekt passt zu deiner Suche
                </h3>
                <p className="text-sm text-slate-500 mt-1">
                    Probier es mit einem anderen Begriff oder leg den Filter ab.
                </p>
                <Button variant="outline" className="mt-5" onClick={onResetFilter}>
                    Filter zurücksetzen
                </Button>
            </div>
        );
    }
    return (
        <div className="py-16 px-6 text-center border border-dashed border-slate-200 rounded-2xl bg-slate-50">
            <Package className="w-12 h-12 mx-auto text-rose-200 mb-3" />
            <h3 className="text-lg font-semibold text-slate-800">
                Aktuell ist nirgends Material offen.
            </h3>
            <p className="text-sm text-slate-500 mt-1 max-w-md mx-auto">
                Sobald du für ein Projekt einen Bedarf anlegst, taucht es hier auf.
            </p>
            <Button onClick={onAnlegen} className="mt-5 bg-rose-600 text-white hover:bg-rose-700">
                <Plus className="w-4 h-4" />
                Bedarf für Projekt anlegen
            </Button>
        </div>
    );
}

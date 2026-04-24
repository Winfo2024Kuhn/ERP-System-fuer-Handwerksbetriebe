import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
    ArrowLeft,
    Check,
    FileSpreadsheet,
    FileText,
    Filter,
    Loader2,
    Package,
    Pencil,
    Plus,
    Ruler,
    Scissors,
    ShoppingCart,
    Trash2,
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { PageLayout } from '../components/layout/PageLayout';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { HicadImportModal } from '../components/HicadImportModal';
import { MaterialbestellungModal, type EditPosition } from '../components/MaterialbestellungModal';

interface ProjektStamm {
    id: number;
    bauvorhaben?: string;
    auftragsnummer?: string;
    kunde?: string;
    excKlasse?: string | null;
}

interface BedarfsZeile {
    id: number;
    artikelId?: number | null;
    externeArtikelnummer?: string | null;
    produktname?: string | null;
    produkttext?: string | null;
    werkstoffName?: string | null;
    kategorieId?: number | null;
    kategorieName?: string | null;
    rootKategorieName?: string | null;
    stueckzahl?: number;
    menge?: number | string | null;
    einheit?: string | null;
    projektId?: number | null;
    projektName?: string | null;
    projektNummer?: string | null;
    kundenName?: string | null;
    lieferantId?: number | null;
    lieferantName?: string | null;
    bestellt?: boolean;
    bestelltAm?: string | null;
    exportiertAm?: string | null;
    kommentar?: string | null;
    kilogramm?: number | null;
    gesamtKilogramm?: number | null;
    fixmassMm?: number | null;
    schnittbildId?: number | null;
    schnittbildBildUrl?: string | null;
    schnittAchseBildUrl?: string | null;
    anschnittbildStegUrl?: string | null;
    anschnittbildFlanschUrl?: string | null;
    anschnittStegText?: string | null;
    anschnittFlanschText?: string | null;
    anschnittWinkelLinks?: number | null;
    anschnittWinkelRechts?: number | null;
    zeugnisAnforderung?: string | null;
    excKlasse?: string | null;
    freiePosition?: boolean;
}

const formatMenge = (z: BedarfsZeile): string => {
    const einheit = z.einheit ?? 'Stück';
    const menge = z.menge != null ? Number(z.menge) : z.stueckzahl ?? 0;
    return `${menge.toLocaleString('de-DE', { maximumFractionDigits: 2 })} ${einheit}`;
};

/** Numerische Gesamtmenge der Zeile (Stück oder Meter). */
const getGesamt = (z: BedarfsZeile): number => {
    const m = z.menge != null ? Number(z.menge) : (z.stueckzahl ?? 0);
    return Number.isFinite(m) ? m : 0;
};

interface MengenStand {
    vorhanden: number;
    bestellen: number;
}

type MengenMap = Record<number, MengenStand>;

type Filter = 'alle' | 'zu_bestellen' | 'vorhanden' | 'teilweise';

const STORAGE_PREFIX = 'bedarf-checkliste-v1-';

const formatKg = (val: number | null | undefined): string | null => {
    if (val == null || val <= 0) return null;
    return `${val.toLocaleString('de-DE', { maximumFractionDigits: 1 })} kg`;
};

export default function ProjektBedarfPage() {
    const { projektId } = useParams<{ projektId: string }>();
    const projektIdNum = projektId ? Number(projektId) : NaN;
    const toast = useToast();
    const confirm = useConfirm();

    const [projekt, setProjekt] = useState<ProjektStamm | null>(null);
    const [zeilen, setZeilen] = useState<BedarfsZeile[]>([]);
    const [loading, setLoading] = useState(true);
    const [hicadOffen, setHicadOffen] = useState(false);
    const [materialOffen, setMaterialOffen] = useState(false);
    const [editZeile, setEditZeile] = useState<EditPosition | null>(null);
    const [mengen, setMengen] = useState<MengenMap>({});
    const [filter, setFilter] = useState<Filter>('alle');

    // localStorage-Backup laden, sobald die Projekt-ID feststeht.
    useEffect(() => {
        if (!Number.isFinite(projektIdNum)) return;
        try {
            const raw = window.localStorage.getItem(STORAGE_PREFIX + projektIdNum);
            if (raw) setMengen(JSON.parse(raw));
        } catch {
            // ignore — beschaedigter Eintrag wird beim naechsten Save ueberschrieben
        }
    }, [projektIdNum]);

    // Default-Mengen fuer neue Zeilen ergaenzen (Bestellen = Gesamt, Vorhanden = 0).
    useEffect(() => {
        if (zeilen.length === 0) return;
        setMengen(prev => {
            const next: MengenMap = { ...prev };
            let changed = false;
            for (const z of zeilen) {
                if (next[z.id] == null) {
                    next[z.id] = { vorhanden: 0, bestellen: getGesamt(z) };
                    changed = true;
                }
            }
            return changed ? next : prev;
        });
    }, [zeilen]);

    // Persistieren ins localStorage (lazy — nur wenn Projekt-ID + Daten da).
    useEffect(() => {
        if (!Number.isFinite(projektIdNum)) return;
        if (Object.keys(mengen).length === 0) return;
        try {
            window.localStorage.setItem(
                STORAGE_PREFIX + projektIdNum,
                JSON.stringify(mengen),
            );
        } catch {
            // Quota-/Privacy-Mode-Fehler nicht eskalieren
        }
    }, [mengen, projektIdNum]);

    const setVorhanden = useCallback((id: number, neu: number, gesamt: number) => {
        const klar = Math.max(0, Math.min(gesamt, Math.floor(Number.isFinite(neu) ? neu : 0)));
        setMengen(prev => ({
            ...prev,
            [id]: { vorhanden: klar, bestellen: gesamt - klar },
        }));
    }, []);

    const setBestellen = useCallback((id: number, neu: number, gesamt: number) => {
        const klar = Math.max(0, Math.min(gesamt, Math.floor(Number.isFinite(neu) ? neu : 0)));
        setMengen(prev => ({
            ...prev,
            [id]: { vorhanden: gesamt - klar, bestellen: klar },
        }));
    }, []);

    // Projekt-Stammdaten laden (für Titel/Subtitle)
    useEffect(() => {
        if (!Number.isFinite(projektIdNum)) return;
        let cancelled = false;
        fetch(`/api/projekte/simple?size=500`)
            .then(res => (res.ok ? res.json() : []))
            .then((arr: ProjektStamm[]) => {
                if (cancelled) return;
                const p = Array.isArray(arr) ? arr.find(x => x.id === projektIdNum) : null;
                setProjekt(p ?? null);
            })
            .catch(() => {
                if (!cancelled) setProjekt(null);
            });
        return () => { cancelled = true; };
    }, [projektIdNum]);

    // Bedarfs-Zeilen laden
    const ladeZeilen = useCallback(async () => {
        if (!Number.isFinite(projektIdNum)) return;
        setLoading(true);
        try {
            const res = await fetch('/api/bestellungen/offen');
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const alle: BedarfsZeile[] = await res.json();
            const meine = Array.isArray(alle) ? alle.filter(z => z.projektId === projektIdNum) : [];
            setZeilen(meine);
        } catch (err) {
            console.error('Bedarfe konnten nicht geladen werden', err);
            toast.error('Bedarfe konnten nicht geladen werden.');
            setZeilen([]);
        } finally {
            setLoading(false);
        }
    }, [projektIdNum, toast]);

    useEffect(() => {
        ladeZeilen();
    }, [ladeZeilen]);

    const summen = useMemo(() => ({
        zeilenAnzahl: zeilen.length,
        offenAnzahl: zeilen.filter(z => !z.bestellt && !z.exportiertAm).length,
        bestelltAnzahl: zeilen.filter(z => z.bestellt || z.exportiertAm).length,
        // kilogramm = Pro-Zeile-Gewicht; gesamtKilogramm wäre der vom Backend
        // bereits aufsummierte Projekt-Total und würde hier doppelt zählen.
        kgSumme: zeilen.reduce((s, z) => s + (Number(z.kilogramm) || 0), 0),
    }), [zeilen]);

    /** Status pro Zeile aus den Mengen abgeleitet — komplett vorhanden / teilweise / komplett bestellen. */
    const zeilenStatus = useCallback((z: BedarfsZeile): 'vorhanden' | 'teilweise' | 'zu_bestellen' => {
        const gesamt = getGesamt(z);
        const m = mengen[z.id];
        const v = m?.vorhanden ?? 0;
        if (v >= gesamt && gesamt > 0) return 'vorhanden';
        if (v > 0) return 'teilweise';
        return 'zu_bestellen';
    }, [mengen]);

    const gefilterteZeilen = useMemo(() => {
        if (filter === 'alle') return zeilen;
        return zeilen.filter(z => {
            const s = zeilenStatus(z);
            if (filter === 'zu_bestellen') return s === 'zu_bestellen' || s === 'teilweise';
            if (filter === 'vorhanden') return s === 'vorhanden';
            if (filter === 'teilweise') return s === 'teilweise';
            return true;
        });
    }, [zeilen, filter, zeilenStatus]);

    /** Anzahl Positionen, die mind. ein Stueck bestellt werden muessen. */
    const zuBestellenAnzahl = useMemo(
        () => zeilen.filter(z => (mengen[z.id]?.bestellen ?? getGesamt(z)) > 0
            && !z.bestellt && !z.exportiertAm).length,
        [zeilen, mengen],
    );

    const subtitleParts: string[] = [];
    if (projekt?.kunde) subtitleParts.push(`Kunde: ${projekt.kunde}`);
    if (projekt?.auftragsnummer) subtitleParts.push(`Auftrag ${projekt.auftragsnummer}`);

    const handleZeileLoeschen = async (zeile: BedarfsZeile) => {
        if (zeile.exportiertAm) {
            toast.warning('Diese Zeile ist bereits exportiert und kann nicht gelöscht werden.');
            return;
        }
        const ok = await confirm({
            title: 'Material wirklich löschen?',
            message: `„${zeile.produktname ?? 'Material'}" wird aus der Bedarfs-Liste entfernt.`,
            confirmLabel: 'Löschen',
            variant: 'danger',
        });
        if (!ok) return;
        try {
            const res = await fetch(`/api/bestellungen/${zeile.id}/freitext`, { method: 'DELETE' });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            toast.success('Material gelöscht.');
            ladeZeilen();
        } catch (err) {
            console.error(err);
            toast.error('Löschen fehlgeschlagen.');
        }
    };

    const handleZeileBearbeiten = (zeile: BedarfsZeile) => {
        if (zeile.exportiertAm) {
            toast.warning('Diese Zeile wurde bereits versendet und ist gesperrt.');
            return;
        }
        setEditZeile({
            id: zeile.id,
            artikelId: zeile.artikelId ?? null,
            externeArtikelnummer: zeile.externeArtikelnummer ?? null,
            produktname: zeile.produktname ?? null,
            produkttext: zeile.produkttext ?? null,
            werkstoffName: zeile.werkstoffName ?? null,
            kategorieId: zeile.kategorieId ?? null,
            menge: zeile.menge ?? zeile.stueckzahl ?? null,
            einheit: zeile.einheit ?? null,
            fixmassMm: zeile.fixmassMm ?? null,
            schnittbildId: zeile.schnittbildId ?? null,
            schnittbildBildUrl: zeile.schnittbildBildUrl ?? null,
            schnittAchseBildUrl: zeile.schnittAchseBildUrl ?? null,
            anschnittWinkelLinks: zeile.anschnittWinkelLinks ?? null,
            anschnittWinkelRechts: zeile.anschnittWinkelRechts ?? null,
            zeugnisAnforderung: zeile.zeugnisAnforderung ?? null,
            kommentar: zeile.kommentar ?? null,
            projektId: zeile.projektId ?? projektIdNum,
            projektName: zeile.projektName ?? projekt?.bauvorhaben ?? null,
            projektNummer: zeile.projektNummer ?? projekt?.auftragsnummer ?? null,
            kundenName: zeile.kundenName ?? projekt?.kunde ?? null,
            excKlasse: zeile.excKlasse ?? projekt?.excKlasse ?? null,
            lieferantId: zeile.lieferantId ?? null,
            lieferantName: zeile.lieferantName ?? null,
            exportiertAm: zeile.exportiertAm ?? null,
        });
        setMaterialOffen(true);
    };

    const initialProjektFuerModal = projekt ? {
        id: projekt.id,
        bauvorhaben: projekt.bauvorhaben,
        auftragsnummer: projekt.auftragsnummer,
        kunde: projekt.kunde,
        excKlasse: projekt.excKlasse ?? null,
    } : null;

    return (
        <PageLayout
            ribbonCategory="Einkauf · Bedarf"
            title={projekt?.bauvorhaben ?? `Projekt #${projektId ?? '?'}`}
            subtitle={subtitleParts.length > 0 ? subtitleParts.join(' · ') : 'Material-Bedarf für dieses Projekt'}
            actions={
                <div className="flex items-center gap-2">
                    <Link to="/bestellungen/bedarf">
                        <Button variant="outline">
                            <ArrowLeft className="w-4 h-4" />
                            Zur Übersicht
                        </Button>
                    </Link>
                    <Button
                        variant="outline"
                        onClick={() => setHicadOffen(true)}
                        disabled={!projekt}
                    >
                        <FileSpreadsheet className="w-4 h-4" />
                        HiCAD-Import
                    </Button>
                    <Button
                        variant="outline"
                        onClick={() => {
                            if (!Number.isFinite(projektIdNum)) return;
                            window.open(
                                `/api/bestellungen/projekt/${projektIdNum}/bedarfsliste-pdf`,
                                '_blank',
                                'noopener',
                            );
                        }}
                        disabled={!projekt || zeilen.length === 0}
                        title={zeilen.length === 0
                            ? 'Keine Positionen zum Drucken'
                            : 'Druckbare Stückliste mit Checkboxen öffnen'}
                    >
                        <FileText className="w-4 h-4" />
                        Stückliste drucken
                    </Button>
                    <Button
                        className="bg-rose-600 text-white hover:bg-rose-700"
                        onClick={() => { setEditZeile(null); setMaterialOffen(true); }}
                    >
                        <Plus className="w-4 h-4" />
                        Material hinzufügen
                    </Button>
                </div>
            }
        >
            {/* Kennzahlen */}
            <div className="bg-white p-6 rounded-2xl shadow-lg border border-slate-100">
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <KennzahlBlock label="Zeilen gesamt" wert={summen.zeilenAnzahl.toString()} />
                    <KennzahlBlock label="Noch offen" wert={summen.offenAnzahl.toString()} highlight />
                    <KennzahlBlock label="Bestellt" wert={summen.bestelltAnzahl.toString()} />
                    <KennzahlBlock
                        label="Stahlgewicht"
                        wert={summen.kgSumme > 0
                            ? `${summen.kgSumme.toLocaleString('de-DE', { maximumFractionDigits: 0 })} kg`
                            : '—'}
                    />
                </div>
            </div>

            {/* Bedarfs-Liste */}
            {loading ? (
                <div className="bg-white p-12 rounded-2xl text-center text-slate-500 border border-slate-100">
                    <Loader2 className="w-6 h-6 mx-auto mb-2 animate-spin text-rose-400" />
                    Bedarfe werden geladen…
                </div>
            ) : zeilen.length === 0 ? (
                <EmptyState
                    onHicad={() => setHicadOffen(true)}
                    onManuell={() => { setEditZeile(null); setMaterialOffen(true); }}
                />
            ) : (
                <div className="bg-white rounded-2xl shadow-lg overflow-hidden border border-slate-100">
                    {/* Filter-Leiste */}
                    <div className="flex items-center justify-between gap-3 px-4 py-3 border-b border-slate-100 bg-slate-50/60">
                        <div className="flex items-center gap-2 text-sm">
                            <Filter className="w-4 h-4 text-slate-400" />
                            <span className="text-slate-500 font-medium">Anzeigen:</span>
                            <FilterChip aktiv={filter === 'alle'} onClick={() => setFilter('alle')}>
                                Alle ({zeilen.length})
                            </FilterChip>
                            <FilterChip aktiv={filter === 'zu_bestellen'} onClick={() => setFilter('zu_bestellen')}>
                                Zu bestellen ({zuBestellenAnzahl})
                            </FilterChip>
                            <FilterChip aktiv={filter === 'teilweise'} onClick={() => setFilter('teilweise')}>
                                Teilweise vorhanden
                            </FilterChip>
                            <FilterChip aktiv={filter === 'vorhanden'} onClick={() => setFilter('vorhanden')}>
                                Komplett vorhanden
                            </FilterChip>
                        </div>
                        <p className="text-xs text-slate-400 hidden md:block">
                            Eingaben werden lokal pro Projekt gespeichert
                        </p>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full text-sm text-left">
                            <thead className="bg-slate-50 text-slate-500 font-medium border-b border-slate-200">
                                <tr>
                                    <th className="px-4 py-3">Material</th>
                                    <th className="px-4 py-3">Werkstoff</th>
                                    <th className="px-4 py-3 text-right">Menge</th>
                                    <th className="px-4 py-3 text-right">Fixmaß</th>
                                    <th className="px-4 py-3 text-right">Gewicht</th>
                                    <th className="px-4 py-3 text-center w-28">
                                        <span className="inline-flex items-center gap-1 text-emerald-700">
                                            <Check className="w-3.5 h-3.5" /> Vorhanden
                                        </span>
                                    </th>
                                    <th className="px-4 py-3 text-center w-28">
                                        <span className="inline-flex items-center gap-1 text-rose-700">
                                            <ShoppingCart className="w-3.5 h-3.5" /> Bestellen
                                        </span>
                                    </th>
                                    <th className="px-4 py-3 w-28 text-right" aria-label="Aktionen"></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {gefilterteZeilen.length === 0 ? (
                                    <tr>
                                        <td colSpan={9} className="px-4 py-10 text-center text-slate-400 text-sm">
                                            Keine Positionen für diesen Filter.
                                        </td>
                                    </tr>
                                ) : null}
                                {gefilterteZeilen.map(z => {
                                    const gesperrt = !!z.exportiertAm;
                                    const gesamt = getGesamt(z);
                                    const m = mengen[z.id] ?? { vorhanden: 0, bestellen: gesamt };
                                    const inputDisabled = gesperrt || !!z.bestellt;
                                    return (
                                        <tr
                                            key={z.id}
                                            className="hover:bg-rose-50/40 transition-colors group"
                                        >
                                            <td className="px-4 py-3">
                                                <div className="flex items-start gap-3 min-w-0">
                                                    <div className="w-9 h-9 rounded-lg bg-slate-100 group-hover:bg-rose-100 flex items-center justify-center flex-shrink-0 transition-colors">
                                                        <Package className="w-4 h-4 text-slate-500 group-hover:text-rose-600" />
                                                    </div>
                                                    <div className="min-w-0">
                                                        <p className="font-medium text-slate-900 truncate">
                                                            {z.produktname ?? 'Unbenannt'}
                                                        </p>
                                                        {z.kategorieName && (
                                                            <p className="text-xs text-slate-500 mt-0.5">
                                                                {z.kategorieName}
                                                            </p>
                                                        )}
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-4 py-3 text-slate-600">
                                                {z.werkstoffName ?? <span className="text-slate-300">—</span>}
                                            </td>
                                            <td className="px-4 py-3 text-right text-slate-700 tabular-nums">
                                                {formatMenge(z)}
                                            </td>
                                            <td className="px-4 py-3 text-right text-slate-700 tabular-nums">
                                                {z.fixmassMm ? (
                                                    <div className="inline-flex flex-col items-end gap-1">
                                                        <span className="inline-flex items-center gap-1 text-xs">
                                                            <Ruler className="w-3 h-3 text-slate-400" />
                                                            {z.fixmassMm} mm
                                                        </span>
                                                        {/* HiCAD-Anschnittbilder (aus Excel übernommen) haben Vorrang vor dem Stamm-Schnittbild. */}
                                                        {(z.anschnittbildStegUrl || z.anschnittbildFlanschUrl) ? (
                                                            <div className="inline-flex flex-col items-end gap-1">
                                                                {(z.anschnittbildStegUrl || z.anschnittStegText) && (
                                                                    <HicadAnschnitt
                                                                        label="Steg"
                                                                        bildUrl={z.anschnittbildStegUrl}
                                                                        winkelText={z.anschnittStegText}
                                                                    />
                                                                )}
                                                                {(z.anschnittbildFlanschUrl || z.anschnittFlanschText) && (
                                                                    <HicadAnschnitt
                                                                        label="Flansch"
                                                                        bildUrl={z.anschnittbildFlanschUrl}
                                                                        winkelText={z.anschnittFlanschText}
                                                                    />
                                                                )}
                                                            </div>
                                                        ) : z.schnittbildId != null ? (
                                                            <div className="inline-flex items-center gap-1.5">
                                                                {z.schnittAchseBildUrl && (
                                                                    <img
                                                                        src={z.schnittAchseBildUrl}
                                                                        alt="Schnittachse"
                                                                        className="h-7 w-auto rounded border border-slate-200 bg-white object-contain mr-1"
                                                                    />
                                                                )}
                                                                <span className="text-[11px] font-medium text-rose-600 tabular-nums">
                                                                    {(z.anschnittWinkelLinks ?? 90)}°
                                                                </span>
                                                                {z.schnittbildBildUrl ? (
                                                                    <img
                                                                        src={z.schnittbildBildUrl}
                                                                        alt="Schnittbild"
                                                                        className="h-7 w-auto rounded border border-slate-200 bg-white object-contain"
                                                                    />
                                                                ) : (
                                                                    <Scissors className="w-3 h-3 text-rose-600" />
                                                                )}
                                                                <span className="text-[11px] font-medium text-rose-600 tabular-nums">
                                                                    {(z.anschnittWinkelRechts ?? 90)}°
                                                                </span>
                                                            </div>
                                                        ) : null}
                                                    </div>
                                                ) : (
                                                    <span className="text-slate-300">—</span>
                                                )}
                                            </td>
                                            <td className="px-4 py-3 text-right text-slate-700 tabular-nums">
                                                {formatKg(z.kilogramm) ?? <span className="text-slate-300">—</span>}
                                            </td>
                                            <td className="px-4 py-3 text-center">
                                                <MengenInput
                                                    value={m.vorhanden}
                                                    max={gesamt}
                                                    accent="emerald"
                                                    disabled={inputDisabled}
                                                    onChange={(v) => setVorhanden(z.id, v, gesamt)}
                                                    onMaxClick={() => setVorhanden(z.id, gesamt, gesamt)}
                                                />
                                            </td>
                                            <td className="px-4 py-3 text-center">
                                                <MengenInput
                                                    value={m.bestellen}
                                                    max={gesamt}
                                                    accent="rose"
                                                    disabled={inputDisabled}
                                                    onChange={(v) => setBestellen(z.id, v, gesamt)}
                                                    onMaxClick={() => setBestellen(z.id, gesamt, gesamt)}
                                                />
                                            </td>
                                            <td className="px-4 py-3 text-right">
                                                <div className="flex items-center justify-end gap-1">
                                                    <button
                                                        type="button"
                                                        onClick={() => handleZeileBearbeiten(z)}
                                                        disabled={gesperrt}
                                                        title={gesperrt ? 'Versendet — gesperrt' : 'Bearbeiten'}
                                                        className="p-2 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                                    >
                                                        <Pencil className="w-4 h-4" />
                                                    </button>
                                                    <button
                                                        type="button"
                                                        onClick={() => handleZeileLoeschen(z)}
                                                        disabled={gesperrt}
                                                        title={gesperrt ? 'Versendet — gesperrt' : 'Löschen'}
                                                        className="p-2 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>

                    {/* Footer-Bar: Sammelaktion zur Preisanfrage */}
                    <div className="flex items-center justify-between gap-3 px-4 py-3 border-t border-slate-100 bg-slate-50/60">
                        <div className="text-sm text-slate-600">
                            <span className="font-semibold text-rose-700 tabular-nums">
                                {zuBestellenAnzahl}
                            </span>{' '}
                            {zuBestellenAnzahl === 1 ? 'Position' : 'Positionen'} mit
                            offener Bestellmenge
                        </div>
                        <Button
                            className="bg-rose-600 text-white hover:bg-rose-700"
                            disabled={zuBestellenAnzahl === 0}
                            onClick={() => toast.info(
                                'Preisanfrage-Übergabe folgt im nächsten Schritt (Lieferantenauswahl-Modal).',
                            )}
                            title={zuBestellenAnzahl === 0
                                ? 'Keine Positionen mit offener Bestellmenge'
                                : 'Markierte Positionen in eine Preisanfrage übernehmen'}
                        >
                            <ShoppingCart className="w-4 h-4" />
                            → In Preisanfrage übernehmen
                        </Button>
                    </div>
                </div>
            )}

            {/* Modals */}
            {projekt && (
                <HicadImportModal
                    isOpen={hicadOffen}
                    onClose={() => setHicadOffen(false)}
                    onSuccess={() => { setHicadOffen(false); ladeZeilen(); }}
                    projekt={{
                        id: projekt.id,
                        bauvorhaben: projekt.bauvorhaben ?? `Projekt #${projekt.id}`,
                        auftragsnummer: projekt.auftragsnummer,
                    }}
                />
            )}
            <MaterialbestellungModal
                isOpen={materialOffen}
                onClose={() => { setMaterialOffen(false); setEditZeile(null); }}
                onSuccess={() => { setMaterialOffen(false); setEditZeile(null); ladeZeilen(); }}
                initialProjekt={initialProjektFuerModal}
                projektSperren={!editZeile}
                editPosition={editZeile}
            />
        </PageLayout>
    );
}

function HicadAnschnitt({
    label,
    bildUrl,
    winkelText,
}: {
    label: 'Steg' | 'Flansch';
    bildUrl?: string | null;
    winkelText?: string | null;
}) {
    // HiCAD-Zelle: "27.6° 27.6°" (Start/Ende des Zuschnitts) — trennen für Bild-flankierende Anzeige
    const teile = (winkelText ?? '').split(/\s+/).filter(Boolean);
    const links = teile[0] ?? null;
    const rechts = teile.length > 1 ? teile[teile.length - 1] : null;
    return (
        <div
            className="inline-flex items-center gap-1"
            title={`Anschnitt ${label}${winkelText ? ` · ${winkelText}` : ''}`}
        >
            {links && (
                <span className="text-[11px] font-medium text-rose-600 tabular-nums">{links}</span>
            )}
            {bildUrl && (
                <img
                    src={bildUrl}
                    alt={`Anschnitt ${label}`}
                    className="h-6 w-auto rounded border border-slate-200 bg-white object-contain"
                />
            )}
            {rechts && (
                <span className="text-[11px] font-medium text-rose-600 tabular-nums">{rechts}</span>
            )}
        </div>
    );
}

function KennzahlBlock({ label, wert, highlight = false }: { label: string; wert: string; highlight?: boolean }) {
    return (
        <div>
            <p className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</p>
            <p className={`text-lg font-semibold tabular-nums whitespace-nowrap ${highlight ? 'text-rose-700' : 'text-slate-900'}`}>
                {wert}
            </p>
        </div>
    );
}

function FilterChip({
    aktiv,
    onClick,
    children,
}: { aktiv: boolean; onClick: () => void; children: React.ReactNode }) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={`px-2.5 py-1 rounded-full text-xs font-medium transition-colors ${
                aktiv
                    ? 'bg-rose-600 text-white hover:bg-rose-700'
                    : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-100'
            }`}
        >
            {children}
        </button>
    );
}

/**
 * Number-Input mit Min=0/Max=gesamt, "Max"-Button setzt auf Vollwert.
 * Akzent (`emerald` / `rose`) gibt der Eingabe die richtige Farbe pro Spalte.
 */
function MengenInput({
    value,
    max,
    accent,
    disabled,
    onChange,
    onMaxClick,
}: {
    value: number;
    max: number;
    accent: 'emerald' | 'rose';
    disabled: boolean;
    onChange: (n: number) => void;
    onMaxClick: () => void;
}) {
    const accentRing = accent === 'emerald'
        ? 'focus:ring-emerald-300 focus:border-emerald-400'
        : 'focus:ring-rose-300 focus:border-rose-400';
    const accentText = accent === 'emerald' ? 'text-emerald-700' : 'text-rose-700';
    const istMax = value > 0 && value === max;
    return (
        <div className="inline-flex items-center gap-1">
            <input
                type="number"
                min={0}
                max={max}
                value={value}
                disabled={disabled}
                onChange={(e) => onChange(parseInt(e.target.value, 10))}
                onFocus={(e) => e.target.select()}
                className={`w-16 text-center tabular-nums text-sm font-medium ${accentText}
                    rounded-md border border-slate-200 bg-white px-2 py-1
                    focus:outline-none focus:ring-2 ${accentRing}
                    disabled:bg-slate-50 disabled:text-slate-400 disabled:cursor-not-allowed`}
            />
            <button
                type="button"
                onClick={onMaxClick}
                disabled={disabled || istMax}
                title={`Auf Maximum (${max}) setzen`}
                className={`text-[10px] px-1.5 py-1 rounded font-medium transition-colors
                    ${istMax
                        ? 'bg-slate-100 text-slate-300 cursor-default'
                        : `bg-slate-100 ${accentText} hover:bg-slate-200 cursor-pointer`}
                    disabled:opacity-30 disabled:cursor-not-allowed`}
            >
                MAX
            </button>
        </div>
    );
}


function EmptyState({ onHicad, onManuell }: { onHicad: () => void; onManuell: () => void }) {
    return (
        <div className="bg-white p-10 rounded-2xl shadow-lg border border-slate-100">
            <div className="text-center mb-8">
                <Package className="w-12 h-12 mx-auto text-rose-200 mb-3" />
                <h3 className="text-lg font-semibold text-slate-800">
                    Noch kein Material angelegt
                </h3>
                <p className="text-sm text-slate-500 mt-1 max-w-md mx-auto">
                    Leg den Bedarf entweder aus einer HiCAD-Sägeliste an oder gib das Material direkt ein.
                </p>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-2xl mx-auto">
                <button
                    type="button"
                    onClick={onHicad}
                    className="text-left p-6 rounded-xl border-2 border-dashed border-slate-200 hover:border-rose-300 hover:bg-rose-50/30 transition-colors group"
                >
                    <FileSpreadsheet className="w-8 h-8 text-slate-400 group-hover:text-rose-600 mb-3 transition-colors" />
                    <p className="font-semibold text-slate-900">HiCAD-Sägeliste importieren</p>
                    <p className="text-sm text-slate-500 mt-1">
                        Excel-Export aus HiCAD hochladen. Profile werden automatisch zusammengefasst.
                    </p>
                </button>
                <button
                    type="button"
                    onClick={onManuell}
                    className="text-left p-6 rounded-xl border-2 border-dashed border-slate-200 hover:border-rose-300 hover:bg-rose-50/30 transition-colors group"
                >
                    <Plus className="w-8 h-8 text-slate-400 group-hover:text-rose-600 mb-3 transition-colors" />
                    <p className="font-semibold text-slate-900">Material manuell hinzufügen</p>
                    <p className="text-sm text-slate-500 mt-1">
                        Einzelne Position direkt eingeben (Bezeichnung, Menge, Werkstoff, Fixmaß).
                    </p>
                </button>
            </div>
        </div>
    );
}

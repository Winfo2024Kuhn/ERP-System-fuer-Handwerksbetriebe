import { LadefehlerPanel } from '../components/ui/ladefehler-panel';
import { DirektbestellungDialog } from '../features/einkauf/components/DirektbestellungDialog';
import { ladeBedarfszeilen, nutztEchtesBackend, speichereWerkstatt, druckeBedarfsliste } from '../features/einkauf/originalBedarfApi';
import type { BedarfResponse } from '../features/einkauf/types';
import { DecimalInput } from '../components/ui/decimal-input';
import { formatDecimalInput, validateDecimalInput } from '../lib/numberInput';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams, useNavigate, useSearchParams } from 'react-router-dom';
import {
    ArrowLeft,
    Check,
    ChevronRight,
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
import { IdsLieferantenAuswahlModal } from '../components/IdsLieferantenAuswahlModal';
import { Plug } from 'lucide-react';
import type { IdsDraft } from '../types/ids';

interface ProjektStamm {
    id: number;
    bauvorhaben?: string;
    auftragsnummer?: string;
    kunde?: string;
    excKlasse?: string | null;
}

interface BedarfsZeile {
    bedarf?: BedarfResponse;
    vorhanden?: number;
    bestellen?: number;
    werkstattMaximum?: number;
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
    /** HiCAD-Positionsnummer(n), z. B. „1200“ oder „1100, 1102“. */
    positionsnummer?: string | null;
    mantelflaecheM2?: number | null;
    fixmassMm?: number | null;
    schnittbildId?: number | null;
    schnittbildBildUrl?: string | null;
    schnittAchseBildUrl?: string | null;
    anschnittbildStegUrl?: string | null;
    anschnittbildFlanschUrl?: string | null;
    anschnittStegText?: string | null;
    anschnittFlanschText?: string | null;
    anschnittWinkelLinks?: string | number | null;
    anschnittWinkelRechts?: string | number | null;
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

const formatMantelflaeche = (val: number | null | undefined): string | null => {
    if (val == null || val <= 0) return null;
    return `Mantelfläche ${val.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} m²`;
};

export default function ProjektBedarfPage() {
    const { projektId } = useParams<{ projektId: string }>();
    const projektIdNum = projektId ? Number(projektId) : NaN;
    const toast = useToast();
    const confirm = useConfirm();
    const navigate = useNavigate();
    const [direktOffen, setDirektOffen] = useState(false);

    const [projekt, setProjekt] = useState<ProjektStamm | null>(null);
    const [zeilen, setZeilen] = useState<BedarfsZeile[]>([]);
    const [loading, setLoading] = useState(true);
    const [ladefehler, setLadefehler] = useState<string | null>(null);
    const [hicadOffen, setHicadOffen] = useState(false);
    const [materialOffen, setMaterialOffen] = useState(false);
    const [editZeile, setEditZeile] = useState<EditPosition | null>(null);
    const [mengen, setMengen] = useState<MengenMap>({});
    const [filter, setFilter] = useState<Filter>('alle');
    const [idsAuswahlOffen, setIdsAuswahlOffen] = useState(false);
    const [werkstattSpeichert, setWerkstattSpeichert] = useState(false);
    const [ungueltigeMengen, setUngueltigeMengen] = useState<Record<string, boolean>>({});

    // localStorage-Backup laden, sobald die Projekt-ID feststeht.
    useEffect(() => {
        if (nutztEchtesBackend || !Number.isFinite(projektIdNum)) return;
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

    // Mock-Vorschau: Persistieren ins localStorage (lazy — nur wenn Projekt-ID + Daten da).
    useEffect(() => {
        if (nutztEchtesBackend || !Number.isFinite(projektIdNum)) return;
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
        const klar = Math.max(0, Math.min(gesamt, (Number.isFinite(neu) ? neu : 0)));
        setMengen(prev => ({
            ...prev,
            [id]: { vorhanden: klar, bestellen: gesamt - klar },
        }));
    }, []);

    const setBestellen = useCallback((id: number, neu: number, gesamt: number) => {
        const klar = Math.max(0, Math.min(gesamt, (Number.isFinite(neu) ? neu : 0)));
        setMengen(prev => ({
            ...prev,
            [id]: { vorhanden: gesamt - klar, bestellen: klar },
        }));
    }, []);

    // Projekt-Stammdaten laden (für Titel/Subtitle)
    useEffect(() => {
        if (!Number.isFinite(projektIdNum)) return;
        let cancelled = false;
        fetch(nutztEchtesBackend ? `/api/projekte/${projektIdNum}` : '/api/projekte/simple?size=500')
            .then(res => { if (!res.ok) throw new Error('Projekt konnte nicht geladen werden.'); return res.json(); })
            .then((data: ProjektStamm | ProjektStamm[]) => {
                const arr = Array.isArray(data) ? data : [data];
                if (cancelled) return;
                const p = Array.isArray(arr) ? arr.find(x => x.id === projektIdNum) : null;
                setProjekt(p ?? null);
            })
            .catch(() => {
                if (!cancelled) { setProjekt(null); toast.error('Projekt konnte nicht geladen werden.'); }
            });
        return () => { cancelled = true; };
    }, [projektIdNum, toast]);

    // Bedarfs-Zeilen laden
    const ladeZeilen = useCallback(async () => {
        if (!Number.isFinite(projektIdNum)) return;
        setLoading(true);
        setLadefehler(null);
        try {
            const alle: BedarfsZeile[] = await ladeBedarfszeilen(projektIdNum);
            const meine = Array.isArray(alle) ? alle.filter(z => z.projektId === projektIdNum) : [];
            setZeilen(meine);
            if (nutztEchtesBackend) setMengen(Object.fromEntries(meine.map(z => [z.id, {
                vorhanden: z.vorhanden ?? 0, bestellen: z.bestellen ?? 0,
            }])));
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Bedarfe konnten nicht geladen werden.';
            setLadefehler(message);
            toast.error(message);
        } finally {
            setLoading(false);
        }
    }, [projektIdNum, toast]);

    useEffect(() => {
        ladeZeilen();
    }, [ladeZeilen]);

    const speicherePruefung = async () => {
        setWerkstattSpeichert(true);
        try {
            await speichereWerkstatt(zeilen.filter((z): z is BedarfsZeile & { bedarf: BedarfResponse } => !!z.bedarf),
                Object.fromEntries(Object.entries(mengen).map(([id, m]) => [id, m.vorhanden])));
            await ladeZeilen();
            toast.success('Werkstattprüfung gespeichert.');
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Werkstattprüfung konnte nicht gespeichert werden.');
        } finally { setWerkstattSpeichert(false); }
    };

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
        const gesamt = z.werkstattMaximum ?? getGesamt(z);
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
        if (nutztEchtesBackend) { toast.info('Ein gespeicherter Einkaufsbedarf kann hier noch nicht gelöscht werden.'); return; }
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
            bedarf: zeile.bedarf,
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
            anschnittWinkelLinks: zeile.anschnittWinkelLinks == null ? null : Number(String(zeile.anschnittWinkelLinks).replace(',', '.')),
            anschnittWinkelRechts: zeile.anschnittWinkelRechts == null ? null : Number(String(zeile.anschnittWinkelRechts).replace(',', '.')),
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
                        onClick={() => setIdsAuswahlOffen(true)}
                        title="Beim Lieferanten direkt im Online-Shop bestellen (IDS-Connect)"
                    >
                        <Plug className="w-4 h-4" />
                        Im Lieferanten-Shop
                    </Button>
                    <Button
                        variant="outline"
                        onClick={() => {
                            if (!Number.isFinite(projektIdNum)) return;
                            if (nutztEchtesBackend) { void druckeBedarfsliste(zeilen.map(z => z.id)).catch(e => toast.error(e.message)); return; }
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
            ) : ladefehler ? (
                <LadefehlerPanel message={ladefehler} onRetry={() => void ladeZeilen()} />
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
                            {nutztEchtesBackend ? 'Werkstattprüfung anschließend speichern' : 'Eingaben werden lokal pro Projekt gespeichert'}
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
                                    const gesamt = z.werkstattMaximum ?? getGesamt(z);
                                    const m = mengen[z.id] ?? { vorhanden: 0, bestellen: gesamt };
                                    const inputDisabled = gesperrt || werkstattSpeichert;
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
                                                        {z.positionsnummer && (
                                                            <p className="text-xs font-mono text-slate-500 mt-0.5" title="Positionsnummer aus HiCAD">
                                                                Pos {z.positionsnummer}
                                                            </p>
                                                        )}
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
                                                {formatMantelflaeche(z.mantelflaecheM2) && (
                                                    <p className="text-[11px] text-slate-500 mt-0.5 whitespace-nowrap">
                                                        {formatMantelflaeche(z.mantelflaecheM2)}
                                                    </p>
                                                )}
                                            </td>
                                            <td className="px-4 py-3 text-center">
                                                <MengenInput
                                                    onValidityChange={valid => setUngueltigeMengen(prev => ({ ...prev, [`${z.id}-vorhanden`]: !valid }))}
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
                                                    onValidityChange={valid => setUngueltigeMengen(prev => ({ ...prev, [`${z.id}-bestellen`]: !valid }))}
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
                                                        disabled={gesperrt || nutztEchtesBackend}
                                                        title={nutztEchtesBackend ? 'Gespeicherten Bedarf hier noch nicht löschbar' : gesperrt ? 'Versendet — gesperrt' : 'Löschen'}
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

                    {nutztEchtesBackend && <div className="flex justify-end px-4 py-3 border-t border-slate-100">
                        <Button variant="outline" disabled={werkstattSpeichert || Object.values(ungueltigeMengen).some(Boolean)} onClick={speicherePruefung}>
                            {werkstattSpeichert && <Loader2 className="w-4 h-4 animate-spin" />} Werkstattprüfung speichern
                        </Button>
                    </div>}
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
                            disabled={zuBestellenAnzahl === 0 || Object.values(ungueltigeMengen).some(Boolean) || (nutztEchtesBackend && zeilen.some(z => (mengen[z.id]?.vorhanden ?? 0) !== (z.vorhanden ?? 0)))}
                            onClick={() => nutztEchtesBackend ? setDirektOffen(true) : toast.info('Preisanfrage-Übergabe folgt im nächsten Schritt (Lieferantenauswahl-Modal).')}
                            title={zuBestellenAnzahl === 0
                                ? 'Keine Positionen mit offener Bestellmenge'
                                : nutztEchtesBackend ? 'Werkstattprüfung zuerst speichern, dann Bestellung vorbereiten' : 'Markierte Positionen in eine Preisanfrage übernehmen'}
                        >
                            <ShoppingCart className="w-4 h-4" />
                            {nutztEchtesBackend ? 'Bestellung vorbereiten' : '→ In Preisanfrage übernehmen'}
                        </Button>
                    </div>
                </div>
            )}

            {/* Modals */}
            {projekt && (
                <HicadImportModal
                    isOpen={hicadOffen}
                    onClose={() => setHicadOffen(false)}
                    onSuccess={() => { setHicadOffen(false); void ladeZeilen(); }}
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
            {direktOffen && <DirektbestellungDialog onClose={() => setDirektOffen(false)}
                initialeTeilmengen={zeilen.filter(z => (mengen[z.id]?.bestellen ?? 0) > 0).map(z => ({ bedarfId: z.id, menge: mengen[z.id].bestellen }))}
                onCreated={id => { setDirektOffen(false); navigate(`/bestellungen/${id}`); }} />}
            {Number.isFinite(projektIdNum) && <GeparkteShopWarenkoerbe projektId={projektIdNum} />}
            <IdsLieferantenAuswahlModal
                isOpen={idsAuswahlOffen}
                onClose={() => setIdsAuswahlOffen(false)}
                projektId={Number.isFinite(projektIdNum) ? projektIdNum : undefined}
                projektName={projekt?.bauvorhaben ?? undefined}
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
    onValidityChange,
}: {
    value: number;
    max: number;
    accent: 'emerald' | 'rose';
    disabled: boolean;
    onChange: (n: number) => void;
    onMaxClick: () => void;
    onValidityChange: (valid: boolean) => void;
}) {
    const [draft, setDraft] = useState(formatDecimalInput(value));
    const [error, setError] = useState('');
    const [previousValue, setPreviousValue] = useState(value);
    if (previousValue !== value) { setPreviousValue(value); setDraft(formatDecimalInput(value)); setError(''); }
    const commit = () => {
        const result = validateDecimalInput(draft, { label: 'Menge', required: true, min: 0, max });
        if (!result.valid || result.value == null) { setError(result.valid ? 'Bitte eine Menge eingeben.' : result.message); return; }
        setError(''); onChange(result.value);
    };
    const accentRing = accent === 'emerald'
        ? 'focus:ring-emerald-300 focus:border-emerald-400'
        : 'focus:ring-rose-300 focus:border-rose-400';
    const accentText = accent === 'emerald' ? 'text-emerald-700' : 'text-rose-700';
    const istMax = value > 0 && value === max;
    return (
        <div className="inline-flex items-center gap-1">
            <DecimalInput
                min={0}
                max={max}
                value={draft}
                aria-label={accent === 'emerald' ? 'Vorhandene Menge' : 'Bestellmenge'}
                required
                error={error}
                disabled={disabled}
                onChange={value => {
                    setDraft(value);
                    const result = validateDecimalInput(value, { label: 'Menge', required: true, min: 0, max });
                    onValidityChange(result.valid && result.value != null);
                }}
                onBlur={commit}
                onKeyDown={e => { if (e.key === 'Enter') commit(); }}
                className={`w-16 text-center tabular-nums text-sm font-medium ${accentText}
                    rounded-md border border-slate-200 bg-white px-2 py-1
                    focus:outline-none focus:ring-2 ${accentRing}
                    disabled:bg-slate-50 disabled:text-slate-400 disabled:cursor-not-allowed`}
            />
            <button
                type="button"
                onClick={() => { onValidityChange(true); setError(''); setDraft(formatDecimalInput(max)); onMaxClick(); }}
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

type ShopWarenkorbZustand =
    | { art: 'laedt' }
    | { art: 'fehler'; meldung: string }
    | { art: 'fertig'; idsVerfuegbar: boolean; warenkoerbe: IdsDraft[] };

/**
 * Würth-/IDS-Warenkörbe, die aus diesem Projektbedarf heraus gefüllt wurden.
 * Sie bleiben hier geparkt, bis sie über „Bei Würth bestellen" abgeschickt werden.
 * Sichtbar nur, wenn die Shop-Anbindung eingerichtet ist oder schon Warenkörbe existieren.
 */
function GeparkteShopWarenkoerbe({ projektId }: { projektId: number }) {
    const toast = useToast();
    const [searchParams] = useSearchParams();
    const hervorgehoben = searchParams.get('warenkorb');
    const [zustand, setZustand] = useState<ShopWarenkorbZustand>({ art: 'laedt' });
    const [sichtbar, setSichtbar] = useState(false);
    const generation = useRef(0);
    const gemeldet = useRef<string | null>(null);
    // Einmal fertig geladen (Erfolg oder Fehler): Ab dann aktualisiert der Fokus still im Hintergrund.
    const geladen = useRef(false);
    // Läuft gerade ein sichtbares Laden, liefert es ohnehin den frischen Stand – Fokus dann auslassen.
    const laedtSichtbar = useRef(false);
    const ausblenden = useCallback(() => {
        setSichtbar(false);
        setZustand({ art: 'fertig', idsVerfuegbar: false, warenkoerbe: [] });
    }, []);

    /** „still“ = Fokus-Refresh: kein Skeleton, kein Toast, bisheriger Inhalt bleibt bei Fehlern stehen. */
    const laden = useCallback(async (still = false) => {
        if (still && (!geladen.current || laedtSichtbar.current)) return;
        const anfrage = ++generation.current;
        if (!still) {
            laedtSichtbar.current = true;
            setZustand(prev => (prev.art === 'fertig' ? prev : { art: 'laedt' }));
        }
        try {
            const lieferanten = await fetch('/api/ids/lieferanten')
                .then(res => (res.ok ? res.json() : []))
                .then((arr: unknown) => Array.isArray(arr) && arr.length > 0)
                .catch(() => false);
            try {
                const res = await fetch(`/api/ids/warenkoerbe?projektId=${encodeURIComponent(String(projektId))}`);
                if (anfrage !== generation.current) return;
                // Ohne Shop-Warenkorb-Schnittstelle (404) gibt es hier nichts zu zeigen – kein Fehler.
                if (res.status === 404) { ausblenden(); return; }
                const daten: unknown = res.ok ? await res.json().catch(() => null) : null;
                if (!Array.isArray(daten)) throw new Error('Geparkte Shop-Warenkörbe konnten nicht geladen werden.');
                if (anfrage !== generation.current) return;
                const warenkoerbe = (daten as IdsDraft[]).filter(w => w.projektId === projektId);
                setZustand({ art: 'fertig', idsVerfuegbar: lieferanten, warenkoerbe });
                setSichtbar(lieferanten || warenkoerbe.length > 0);
            } catch (err) {
                if (anfrage !== generation.current) return;
                // Ohne eingerichtete Shop-Anbindung gibt es hier nichts zu zeigen – kein Fehler für den Nutzer.
                if (!lieferanten) { ausblenden(); return; }
                // Fokus-Refresh: bisherigen Stand stehen lassen, beim nächsten Fokus erneut versuchen.
                if (still) return;
                const meldung = err instanceof Error ? err.message : 'Geparkte Shop-Warenkörbe konnten nicht geladen werden.';
                setSichtbar(true);
                setZustand({ art: 'fehler', meldung });
                toast.error(meldung);
            }
        } finally {
            if (anfrage === generation.current) {
                geladen.current = true;
                laedtSichtbar.current = false;
            }
        }
    }, [projektId, toast, ausblenden]);

    const verwerfen = useCallback(() => { generation.current++; laedtSichtbar.current = false; }, []);
    useEffect(() => {
        void laden();
        // Nach der Rückgabe im Shop-Tab zeigt die Seite beim Zurückwechseln still den neuen Stand.
        const beiFokus = () => { void laden(true); };
        window.addEventListener('focus', beiFokus);
        return () => { verwerfen(); window.removeEventListener('focus', beiFokus); };
    }, [laden, verwerfen]);

    useEffect(() => {
        if (!hervorgehoben || zustand.art !== 'fertig' || gemeldet.current === hervorgehoben) return;
        const warenkorb = zustand.warenkoerbe.find(w => w.id === hervorgehoben);
        if (!warenkorb) return;
        gemeldet.current = hervorgehoben;
        if (warenkorb.ordered) toast.success(`Warenkorb ${warenkorb.number} wurde bei Würth bestellt.`);
        else toast.success('Warenkorb am Projekt geparkt');
    }, [hervorgehoben, zustand, toast]);

    if (!sichtbar) return null;

    return (
        <section aria-labelledby="geparkte-warenkoerbe-titel" className="bg-white rounded-2xl shadow-lg border border-slate-100 overflow-hidden">
            <div className="flex items-center gap-3 px-6 py-4 border-b border-slate-100">
                <div className="w-9 h-9 rounded-lg bg-rose-100 flex items-center justify-center">
                    <ShoppingCart className="w-4 h-4 text-rose-600" />
                </div>
                <div>
                    <h2 id="geparkte-warenkoerbe-titel" className="text-base font-semibold text-slate-900">Geparkte Shop-Warenkörbe</h2>
                    <p className="text-sm text-slate-500">Im Lieferanten-Shop zusammengestellt. Bestellt wird erst, wenn du den Warenkorb abschickst.</p>
                </div>
            </div>
            {zustand.art === 'laedt' ? (
                <div role="status" className="m-6 h-14 rounded-lg bg-slate-100 motion-safe:animate-pulse">
                    <span className="sr-only">Shop-Warenkörbe werden geladen…</span>
                </div>
            ) : zustand.art === 'fehler' ? (
                <div className="p-6"><LadefehlerPanel message={zustand.meldung} onRetry={() => void laden()} /></div>
            ) : zustand.warenkoerbe.length === 0 ? (
                <p className="px-6 py-5 text-sm text-slate-500">
                    Noch kein Warenkorb geparkt. Über „Im Lieferanten-Shop“ Artikel zusammenstellen und den Warenkorb an die Anwendung zurückgeben – er landet dann hier.
                </p>
            ) : (
                <ul className="divide-y divide-slate-100">
                    {zustand.warenkoerbe.map(w => {
                        const aktiv = w.id === hervorgehoben;
                        return (
                            <li key={w.id}>
                                <Link
                                    to={`/bestellungen/ids/${encodeURIComponent(w.id)}`}
                                    aria-current={aktiv ? 'true' : undefined}
                                    className={`flex items-center justify-between gap-4 px-6 py-4 transition-colors hover:bg-rose-50 focus-visible:outline-rose-600 ${aktiv ? 'bg-rose-50 ring-2 ring-inset ring-rose-300' : ''}`}
                                >
                                    <span>
                                        <span className="block font-semibold text-slate-900">Würth · {w.number}</span>
                                        <span className="text-sm text-slate-500">{w.items.length} {w.items.length === 1 ? 'Position' : 'Positionen'}</span>
                                    </span>
                                    <span className="flex items-center gap-3">
                                        <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${w.ordered ? 'bg-slate-100 text-slate-700' : 'bg-rose-100 text-rose-700'}`}>
                                            {w.ordered ? 'Bestellt' : 'Noch nicht bestellt'}
                                        </span>
                                        <ChevronRight className="w-4 h-4 text-slate-400" aria-hidden="true" />
                                    </span>
                                </Link>
                            </li>
                        );
                    })}
                </ul>
            )}
        </section>
    );
}

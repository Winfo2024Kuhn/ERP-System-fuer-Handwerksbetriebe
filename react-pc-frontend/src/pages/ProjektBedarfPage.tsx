import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
    ArrowLeft,
    FileSpreadsheet,
    Loader2,
    Lock,
    Package,
    Pencil,
    Plus,
    Ruler,
    Scissors,
    Trash2,
    Truck,
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
                    >
                        <FileSpreadsheet className="w-4 h-4" />
                        HiCAD-Import
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
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm text-left">
                            <thead className="bg-slate-50 text-slate-500 font-medium border-b border-slate-200">
                                <tr>
                                    <th className="px-4 py-3">Material</th>
                                    <th className="px-4 py-3">Werkstoff</th>
                                    <th className="px-4 py-3 text-right">Menge</th>
                                    <th className="px-4 py-3 text-right">Fixmaß</th>
                                    <th className="px-4 py-3 text-right">Gewicht</th>
                                    <th className="px-4 py-3">Status</th>
                                    <th className="px-4 py-3 w-28 text-right" aria-label="Aktionen"></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {zeilen.map(z => {
                                    const gesperrt = !!z.exportiertAm;
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
                                                    <div className="inline-flex flex-col items-end gap-0.5">
                                                        <span className="inline-flex items-center gap-1 text-xs">
                                                            <Ruler className="w-3 h-3 text-slate-400" />
                                                            {z.fixmassMm} mm
                                                        </span>
                                                        {z.schnittbildId != null && (
                                                            <span className="inline-flex items-center gap-1 text-[11px] text-rose-600">
                                                                <Scissors className="w-3 h-3" />
                                                                {(z.anschnittWinkelLinks ?? 90)}° · {(z.anschnittWinkelRechts ?? 90)}°
                                                            </span>
                                                        )}
                                                    </div>
                                                ) : (
                                                    <span className="text-slate-300">—</span>
                                                )}
                                            </td>
                                            <td className="px-4 py-3 text-right text-slate-700 tabular-nums">
                                                {formatKg(z.kilogramm) ?? <span className="text-slate-300">—</span>}
                                            </td>
                                            <td className="px-4 py-3">
                                                <StatusPille zeile={z} />
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
                </div>
            )}

            {/* Modals */}
            <HicadImportModal
                isOpen={hicadOffen}
                onClose={() => setHicadOffen(false)}
                onSuccess={() => { setHicadOffen(false); ladeZeilen(); }}
            />
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

function StatusPille({ zeile }: { zeile: BedarfsZeile }) {
    if (zeile.exportiertAm) {
        return (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-blue-50 text-blue-700 text-xs font-medium">
                <Lock className="w-3 h-3" />
                Versendet
            </span>
        );
    }
    if (zeile.bestellt) {
        return (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-blue-50 text-blue-700 text-xs font-medium">
                <Truck className="w-3 h-3" />
                Bestellt
            </span>
        );
    }
    if (zeile.lieferantName) {
        return (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-amber-50 text-amber-700 text-xs font-medium">
                {zeile.lieferantName}
            </span>
        );
    }
    return (
        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 text-xs font-medium">
            Offen
        </span>
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

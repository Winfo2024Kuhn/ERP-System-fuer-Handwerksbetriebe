import { useCallback, useEffect, useMemo, useState } from "react";
import { X, Check, Trash2, Sparkles, AlertTriangle, Loader2, RefreshCw, Folder, ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { CategoryTreeModal } from "./CategoryTreeModal";
import { useToast } from "./ui/toast";

interface ArtikelVorschlagResponse {
    id: number;
    status: string;
    typ: string;
    erstelltAm: string;
    lieferantId?: number;
    lieferantName?: string;
    quelleDokumentId?: number;
    quelleDokumentBezeichnung?: string;
    externeArtikelnummer?: string;
    produktname?: string;
    produktlinie?: string;
    produkttext?: string;
    vorgeschlageneKategorieId?: number;
    vorgeschlageneKategoriePfad?: string;
    vorgeschlagenerWerkstoffId?: number;
    vorgeschlagenerWerkstoffName?: string;
    masse?: number;
    hoehe?: number;
    breite?: number;
    einzelpreis?: number;
    preiseinheit?: string;
    kiKonfidenz?: number;
    kiBegruendung?: string;
    konfliktArtikelId?: number;
    konfliktArtikelName?: string;
    trefferArtikelId?: number;
    trefferArtikelName?: string;
}

interface ArtikelVorschlaegeModalProps {
    onClose: () => void;
    onApproved?: () => void;
}

const TYP_LABEL: Record<string, string> = {
    NEU_ANLAGE: "Neu anlegen",
    MATCH_VORSCHLAG: "Match-Vorschlag",
    KONFLIKT_EXTERNE_NUMMER: "Nummer-Konflikt",
};

const TYP_COLOR: Record<string, string> = {
    NEU_ANLAGE: "bg-emerald-100 text-emerald-700 border-emerald-200",
    MATCH_VORSCHLAG: "bg-amber-100 text-amber-700 border-amber-200",
    KONFLIKT_EXTERNE_NUMMER: "bg-rose-100 text-rose-700 border-rose-200",
};

export function ArtikelVorschlaegeModal({ onClose, onApproved }: ArtikelVorschlaegeModalProps) {
    const toast = useToast();
    const [list, setList] = useState<ArtikelVorschlagResponse[]>([]);
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [werkstoffe, setWerkstoffe] = useState<{ id: number; name: string }[]>([]);
    const [showCategoryModal, setShowCategoryModal] = useState(false);
    const [form, setForm] = useState<Partial<ArtikelVorschlagResponse>>({});

    const selected = useMemo(() => list.find(v => v.id === selectedId) ?? null, [list, selectedId]);

    const loadList = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch("/api/artikel-vorschlaege?status=PENDING");
            if (!res.ok) throw new Error("Fehler beim Laden");
            const data: ArtikelVorschlagResponse[] = await res.json();
            setList(data);
            if (data.length > 0 && (selectedId === null || !data.find(d => d.id === selectedId))) {
                setSelectedId(data[0].id);
            } else if (data.length === 0) {
                setSelectedId(null);
            }
        } catch (err) {
            console.error(err);
            toast.error("Vorschläge konnten nicht geladen werden.");
        } finally {
            setLoading(false);
        }
    }, [selectedId, toast]);

    useEffect(() => { loadList(); }, []); // nur einmal beim Öffnen laden

    useEffect(() => {
        fetch("/api/artikel/werkstoffe/details")
            .then(res => res.ok ? res.json() : [])
            .then(data => setWerkstoffe(Array.isArray(data) ? data : []))
            .catch(() => setWerkstoffe([]));
    }, []);

    useEffect(() => {
        if (selected) {
            setForm({
                produktname: selected.produktname,
                produktlinie: selected.produktlinie,
                produkttext: selected.produkttext,
                externeArtikelnummer: selected.externeArtikelnummer,
                vorgeschlageneKategorieId: selected.vorgeschlageneKategorieId,
                vorgeschlageneKategoriePfad: selected.vorgeschlageneKategoriePfad,
                vorgeschlagenerWerkstoffId: selected.vorgeschlagenerWerkstoffId,
                vorgeschlagenerWerkstoffName: selected.vorgeschlagenerWerkstoffName,
                masse: selected.masse,
                hoehe: selected.hoehe,
                breite: selected.breite,
                einzelpreis: selected.einzelpreis,
                preiseinheit: selected.preiseinheit,
            });
        } else {
            setForm({});
        }
    }, [selected]);

    const updateField = <K extends keyof ArtikelVorschlagResponse>(key: K, value: ArtikelVorschlagResponse[K]) => {
        setForm(prev => ({ ...prev, [key]: value }));
    };

    const selectNext = (delta: 1 | -1) => {
        if (list.length === 0) return;
        const idx = list.findIndex(v => v.id === selectedId);
        const next = (idx + delta + list.length) % list.length;
        setSelectedId(list[next].id);
    };

    // Keyboard shortcuts: ↑/↓ navigieren, Enter = Freigeben, Entf = Ablehnen, Esc = schließen
    useEffect(() => {
        const handler = (e: KeyboardEvent) => {
            if (showCategoryModal) return;
            if (e.key === "Escape") { onClose(); }
            else if (e.key === "ArrowDown" || e.key === "j") { e.preventDefault(); selectNext(1); }
            else if (e.key === "ArrowUp" || e.key === "k") { e.preventDefault(); selectNext(-1); }
            else if ((e.key === "Enter" && (e.ctrlKey || e.metaKey)) && selected && !saving) { e.preventDefault(); handleApprove(); }
            else if (e.key === "Delete" && selected && !saving) { e.preventDefault(); handleReject(); }
        };
        window.addEventListener("keydown", handler);
        return () => window.removeEventListener("keydown", handler);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedId, list, saving, showCategoryModal]);

    const buildPayload = () => ({
        produktname: form.produktname ?? null,
        produktlinie: form.produktlinie ?? null,
        produkttext: form.produkttext ?? null,
        externeArtikelnummer: form.externeArtikelnummer ?? null,
        kategorieId: form.vorgeschlageneKategorieId ?? null,
        werkstoffId: form.vorgeschlagenerWerkstoffId ?? null,
        masse: form.masse ?? null,
        hoehe: form.hoehe ?? null,
        breite: form.breite ?? null,
        einzelpreis: form.einzelpreis ?? null,
        preiseinheit: form.preiseinheit ?? null,
    });

    const handleApprove = async () => {
        if (!selected) return;
        setSaving(true);
        try {
            const res = await fetch(`/api/artikel-vorschlaege/${selected.id}/approve`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(buildPayload()),
            });
            if (!res.ok) throw new Error("Freigabe fehlgeschlagen");
            toast.success("Artikel freigegeben und im Materialstamm angelegt.");
            const remaining = list.filter(v => v.id !== selected.id);
            setList(remaining);
            setSelectedId(remaining[0]?.id ?? null);
            onApproved?.();
        } catch (err) {
            console.error(err);
            toast.error("Freigabe fehlgeschlagen.");
        } finally {
            setSaving(false);
        }
    };

    const handleReject = async () => {
        if (!selected) return;
        setSaving(true);
        try {
            const res = await fetch(`/api/artikel-vorschlaege/${selected.id}/reject`, { method: "POST" });
            if (!res.ok) throw new Error("Ablehnen fehlgeschlagen");
            toast.info("Vorschlag abgelehnt.");
            const remaining = list.filter(v => v.id !== selected.id);
            setList(remaining);
            setSelectedId(remaining[0]?.id ?? null);
        } catch (err) {
            console.error(err);
            toast.error("Ablehnen fehlgeschlagen.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 bg-slate-900/70 z-40 flex items-stretch justify-stretch">
            <div className="bg-white w-full h-full flex flex-col" onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4 bg-gradient-to-r from-rose-50 to-white">
                    <div className="flex items-center gap-3">
                        <div className="p-2 rounded-full bg-rose-100 text-rose-600">
                            <Sparkles className="w-5 h-5" />
                        </div>
                        <div>
                            <h2 className="text-lg font-semibold text-slate-900">Vorgeschlagene neue Materialien</h2>
                            <p className="text-xs text-slate-500">
                                Von der KI aus Lieferantenrechnungen vorgeschlagen · {list.length} offen
                                <span className="ml-2 text-slate-400">
                                    Tastatur: ↑↓ navigieren · Strg+Enter = Freigeben · Entf = Ablehnen · Esc = Schließen
                                </span>
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button variant="outline" size="sm" onClick={loadList} disabled={loading}>
                            <RefreshCw className={`w-4 h-4 mr-2 ${loading ? "animate-spin" : ""}`} />
                            Neu laden
                        </Button>
                        <button onClick={onClose} className="p-2 text-slate-400 hover:text-slate-700 rounded-md hover:bg-slate-100">
                            <X className="w-5 h-5" />
                        </button>
                    </div>
                </div>

                {/* Content: Liste links, Detail rechts */}
                <div className="flex-1 flex overflow-hidden">
                    {/* Liste */}
                    <aside className="w-80 border-r border-slate-200 overflow-y-auto bg-slate-50/50">
                        {loading && list.length === 0 ? (
                            <div className="p-6 text-center text-slate-500">
                                <Loader2 className="w-5 h-5 animate-spin mx-auto mb-2" />
                                Lade Vorschläge…
                            </div>
                        ) : list.length === 0 ? (
                            <div className="p-6 text-center text-slate-500">
                                <Check className="w-8 h-8 mx-auto mb-2 text-emerald-400" />
                                <p className="font-medium text-slate-700">Alles erledigt!</p>
                                <p className="text-xs mt-1">Keine offenen Vorschläge.</p>
                            </div>
                        ) : (
                            <ul className="divide-y divide-slate-200">
                                {list.map(v => {
                                    const isSelected = v.id === selectedId;
                                    return (
                                        <li key={v.id}>
                                            <button
                                                type="button"
                                                onClick={() => setSelectedId(v.id)}
                                                className={`w-full text-left px-4 py-3 transition-colors ${isSelected ? "bg-white border-l-4 border-rose-500" : "hover:bg-white border-l-4 border-transparent"}`}
                                            >
                                                <div className="flex items-start justify-between gap-2 mb-1">
                                                    <p className={`text-sm truncate ${isSelected ? "font-semibold text-slate-900" : "font-medium text-slate-800"}`}>
                                                        {v.produktname || "(ohne Namen)"}
                                                    </p>
                                                    <span className={`text-[10px] px-1.5 py-0.5 rounded-full border shrink-0 ${TYP_COLOR[v.typ] ?? "bg-slate-100 text-slate-600 border-slate-200"}`}>
                                                        {TYP_LABEL[v.typ] ?? v.typ}
                                                    </span>
                                                </div>
                                                <p className="text-xs text-slate-500 truncate">
                                                    {v.lieferantName || "?"} · {v.externeArtikelnummer || "ohne Nr."}
                                                </p>
                                                {typeof v.kiKonfidenz === "number" && (
                                                    <div className="mt-2 flex items-center gap-2">
                                                        <div className="flex-1 h-1 rounded-full bg-slate-200 overflow-hidden">
                                                            <div
                                                                className={`h-full ${v.kiKonfidenz >= 0.85 ? "bg-emerald-500" : v.kiKonfidenz >= 0.6 ? "bg-amber-500" : "bg-rose-500"}`}
                                                                style={{ width: `${Math.round(v.kiKonfidenz * 100)}%` }}
                                                            />
                                                        </div>
                                                        <span className="text-[10px] text-slate-500 tabular-nums">
                                                            {Math.round(v.kiKonfidenz * 100)}%
                                                        </span>
                                                    </div>
                                                )}
                                            </button>
                                        </li>
                                    );
                                })}
                            </ul>
                        )}
                    </aside>

                    {/* Detail */}
                    <section className="flex-1 overflow-y-auto bg-white">
                        {!selected ? (
                            <div className="h-full flex items-center justify-center text-slate-400">
                                <div className="text-center">
                                    <Check className="w-12 h-12 mx-auto mb-2 text-emerald-300" />
                                    <p>Kein Vorschlag ausgewählt.</p>
                                </div>
                            </div>
                        ) : (
                            <div className="max-w-4xl mx-auto p-6 space-y-6">
                                {/* KI-Begründung */}
                                {selected.kiBegruendung && (
                                    <div className="bg-rose-50/70 border border-rose-100 rounded-xl p-4">
                                        <div className="flex items-start gap-3">
                                            <Sparkles className="w-5 h-5 text-rose-500 shrink-0 mt-0.5" />
                                            <div className="flex-1">
                                                <p className="text-xs font-semibold text-rose-700 uppercase tracking-wide mb-1">KI-Analyse</p>
                                                <p className="text-sm text-slate-700">{selected.kiBegruendung}</p>
                                                <p className="text-xs text-slate-500 mt-2">
                                                    Quelle: {selected.quelleDokumentBezeichnung || "Unbekannt"}
                                                    {selected.lieferantName && <> · Lieferant: <strong>{selected.lieferantName}</strong></>}
                                                </p>
                                            </div>
                                        </div>
                                    </div>
                                )}

                                {/* Konflikt-Hinweis */}
                                {selected.typ === "KONFLIKT_EXTERNE_NUMMER" && selected.konfliktArtikelName && (
                                    <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 flex items-start gap-3">
                                        <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
                                        <div className="text-sm text-amber-900">
                                            <p className="font-semibold">Nummer-Konflikt</p>
                                            <p>
                                                Die externe Nummer <code className="bg-amber-100 px-1 rounded">{selected.externeArtikelnummer}</code> zeigt bereits auf{" "}
                                                <strong>{selected.konfliktArtikelName}</strong> (ID {selected.konfliktArtikelId}).
                                                Bitte die richtige Zuordnung manuell klären, bevor freigegeben wird.
                                            </p>
                                        </div>
                                    </div>
                                )}

                                {/* Formular */}
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <div className="md:col-span-2">
                                        <Label>Produktname</Label>
                                        <Input
                                            value={form.produktname ?? ""}
                                            onChange={e => updateField("produktname", e.target.value)}
                                            autoFocus
                                        />
                                    </div>
                                    <div>
                                        <Label>Produktlinie</Label>
                                        <Input
                                            value={form.produktlinie ?? ""}
                                            onChange={e => updateField("produktlinie", e.target.value)}
                                        />
                                    </div>
                                    <div>
                                        <Label>Externe Artikelnummer</Label>
                                        <Input
                                            value={form.externeArtikelnummer ?? ""}
                                            onChange={e => updateField("externeArtikelnummer", e.target.value)}
                                        />
                                    </div>
                                    <div className="md:col-span-2">
                                        <Label>Beschreibung / Produkttext</Label>
                                        <textarea
                                            rows={2}
                                            className="flex w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                            value={form.produkttext ?? ""}
                                            onChange={e => updateField("produkttext", e.target.value)}
                                        />
                                    </div>

                                    <div>
                                        <Label>Kategorie</Label>
                                        <button
                                            type="button"
                                            onClick={() => setShowCategoryModal(true)}
                                            className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm items-center justify-between hover:border-rose-300"
                                        >
                                            <span className={form.vorgeschlageneKategoriePfad ? "text-slate-800 truncate" : "text-slate-400"}>
                                                {form.vorgeschlageneKategoriePfad || "Kategorie wählen…"}
                                            </span>
                                            <Folder className="w-4 h-4 text-slate-400" />
                                        </button>
                                    </div>

                                    <div>
                                        <Label>Werkstoff</Label>
                                        <select
                                            className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                            value={form.vorgeschlagenerWerkstoffId ?? 0}
                                            onChange={e => {
                                                const id = Number(e.target.value) || undefined;
                                                const name = werkstoffe.find(w => w.id === id)?.name;
                                                updateField("vorgeschlagenerWerkstoffId", id);
                                                updateField("vorgeschlagenerWerkstoffName", name);
                                            }}
                                        >
                                            <option value={0}>— keiner —</option>
                                            {werkstoffe.map(w => (
                                                <option key={w.id} value={w.id}>{w.name}</option>
                                            ))}
                                        </select>
                                    </div>

                                    <div>
                                        <Label>Höhe (mm)</Label>
                                        <Input
                                            type="number"
                                            value={form.hoehe ?? ""}
                                            onChange={e => updateField("hoehe", e.target.value ? parseInt(e.target.value, 10) : undefined)}
                                        />
                                    </div>
                                    <div>
                                        <Label>Breite (mm)</Label>
                                        <Input
                                            type="number"
                                            value={form.breite ?? ""}
                                            onChange={e => updateField("breite", e.target.value ? parseInt(e.target.value, 10) : undefined)}
                                        />
                                    </div>
                                    <div>
                                        <Label>Masse (kg/m)</Label>
                                        <Input
                                            type="number"
                                            step="0.001"
                                            value={form.masse ?? ""}
                                            onChange={e => updateField("masse", e.target.value ? parseFloat(e.target.value) : undefined)}
                                        />
                                    </div>
                                    <div>
                                        <Label>Einzelpreis (€)</Label>
                                        <Input
                                            type="number"
                                            step="0.01"
                                            value={form.einzelpreis ?? ""}
                                            onChange={e => updateField("einzelpreis", e.target.value ? parseFloat(e.target.value) : undefined)}
                                        />
                                    </div>
                                    <div>
                                        <Label>Preiseinheit</Label>
                                        <Input
                                            value={form.preiseinheit ?? ""}
                                            onChange={e => updateField("preiseinheit", e.target.value)}
                                            placeholder="z.B. 1, 100, kg"
                                        />
                                    </div>
                                </div>
                            </div>
                        )}
                    </section>
                </div>

                {/* Footer-Actions */}
                {selected && (
                    <div className="border-t border-slate-200 bg-slate-50 px-6 py-3 flex items-center justify-between gap-3">
                        <div className="flex items-center gap-2">
                            <Button variant="outline" size="sm" onClick={() => selectNext(-1)} disabled={list.length < 2}>
                                <ChevronLeft className="w-4 h-4 mr-1" /> Vorheriger
                            </Button>
                            <Button variant="outline" size="sm" onClick={() => selectNext(1)} disabled={list.length < 2}>
                                Nächster <ChevronRight className="w-4 h-4 ml-1" />
                            </Button>
                        </div>
                        <div className="flex items-center gap-2">
                            <Button
                                variant="outline"
                                size="sm"
                                className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                onClick={handleReject}
                                disabled={saving}
                            >
                                <Trash2 className="w-4 h-4 mr-2" />
                                Ablehnen
                            </Button>
                            <Button
                                size="sm"
                                className="bg-emerald-600 hover:bg-emerald-700 text-white"
                                onClick={handleApprove}
                                disabled={saving}
                            >
                                {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Check className="w-4 h-4 mr-2" />}
                                Freigeben & Anlegen
                            </Button>
                        </div>
                    </div>
                )}

                {showCategoryModal && (
                    <CategoryTreeModal
                        onSelect={(id, name) => {
                            updateField("vorgeschlageneKategorieId", id);
                            updateField("vorgeschlageneKategoriePfad", name);
                            setShowCategoryModal(false);
                        }}
                        onClose={() => setShowCategoryModal(false)}
                    />
                )}
            </div>
        </div>
    );
}

import { useCallback, useEffect, useRef, useState } from 'react';
import { Loader2, Plus, Ruler, Scissors, Trash2, UploadCloud, X } from 'lucide-react';
import { Button } from './ui/button';
import { useToast } from './ui/toast';
import { useConfirm } from './ui/confirm-dialog';
import { cn } from '../lib/utils';

interface SchnittAchse {
    id: number;
    bildUrl: string;
    kategorieId: number;
}

interface Schnittbild {
    id: number;
    bildUrlSchnittbild: string;
    schnittAchseId: number;
    schnittAchseBildUrl?: string;
    kategorieId?: number;
}

interface Props {
    /** Artikel-Kategorie (Warengruppe) — id aus /api/kategorien. */
    kategorie: { id: number; bezeichnung: string };
    onClose: () => void;
}

/**
 * Admin-Modal zur Pflege von Achsen + Schnittbildern einer Kategorie.
 * Bilder per Drag & Drop oder Klick hochladen.
 */
export const KategorieSchnittbilderModal: React.FC<Props> = ({ kategorie, onClose }) => {
    const toast = useToast();
    const confirm = useConfirm();

    const [achsen, setAchsen] = useState<SchnittAchse[]>([]);
    const [schnittbilderByAchse, setSchnittbilderByAchse] = useState<Record<number, Schnittbild[]>>({});
    const [loading, setLoading] = useState(true);
    const [uploading, setUploading] = useState(false);

    const ladeAchsen = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/schnitt-achsen?kategorieId=${kategorie.id}`);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data: SchnittAchse[] = await res.json();
            setAchsen(data);

            const perAchse = await Promise.all(
                data.map(async (a) => {
                    const r = await fetch(`/api/schnittbilder?schnittAchseId=${a.id}`);
                    const list: Schnittbild[] = r.ok ? await r.json() : [];
                    return [a.id, list] as const;
                }),
            );
            const map: Record<number, Schnittbild[]> = {};
            for (const [id, list] of perAchse) map[id] = list;
            setSchnittbilderByAchse(map);
        } catch (err) {
            console.error(err);
            toast.error('Schnittbilder konnten nicht geladen werden.');
        } finally {
            setLoading(false);
        }
    }, [kategorie.id, toast]);

    useEffect(() => {
        ladeAchsen();
    }, [ladeAchsen]);

    const uploadBild = useCallback(async (datei: File): Promise<string | null> => {
        const formData = new FormData();
        formData.append('datei', datei);
        setUploading(true);
        try {
            const res = await fetch('/api/schnittbilder/upload', {
                method: 'POST',
                body: formData,
            });
            if (!res.ok) {
                const payload = await res.json().catch(() => ({}));
                toast.error(payload?.error || 'Upload fehlgeschlagen.');
                return null;
            }
            const data: { url: string } = await res.json();
            return data.url;
        } catch (err) {
            console.error(err);
            toast.error('Upload fehlgeschlagen.');
            return null;
        } finally {
            setUploading(false);
        }
    }, [toast]);

    const handleAchseUpload = async (datei: File) => {
        const url = await uploadBild(datei);
        if (!url) return;
        try {
            const res = await fetch('/api/schnitt-achsen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ bildUrl: url, kategorieId: kategorie.id }),
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            toast.success('Achse hinzugefügt.');
            ladeAchsen();
        } catch (err) {
            console.error(err);
            toast.error('Achse konnte nicht angelegt werden.');
        }
    };

    const handleSchnittUpload = async (achseId: number, datei: File) => {
        const url = await uploadBild(datei);
        if (!url) return;
        try {
            const res = await fetch('/api/schnittbilder', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ bildUrlSchnittbild: url, schnittAchseId: achseId }),
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            toast.success('Schnittbild hinzugefügt.');
            ladeAchsen();
        } catch (err) {
            console.error(err);
            toast.error('Schnittbild konnte nicht angelegt werden.');
        }
    };

    const handleAchseLoeschen = async (achse: SchnittAchse) => {
        const ok = await confirm({
            title: 'Achse löschen?',
            message: 'Alle zugehörigen Schnittbilder werden ebenfalls entfernt. Positionen, die diese Schnittbilder referenzieren, verlieren die Referenz.',
            confirmLabel: 'Löschen',
            variant: 'danger',
        });
        if (!ok) return;
        try {
            const res = await fetch(`/api/schnitt-achsen/${achse.id}`, { method: 'DELETE' });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            toast.success('Achse gelöscht.');
            ladeAchsen();
        } catch (err) {
            console.error(err);
            toast.error('Löschen fehlgeschlagen.');
        }
    };

    const handleSchnittLoeschen = async (sb: Schnittbild) => {
        const ok = await confirm({
            title: 'Schnittbild löschen?',
            message: 'Positionen, die dieses Schnittbild referenzieren, verlieren die Referenz.',
            confirmLabel: 'Löschen',
            variant: 'danger',
        });
        if (!ok) return;
        try {
            const res = await fetch(`/api/schnittbilder/${sb.id}`, { method: 'DELETE' });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            toast.success('Schnittbild gelöscht.');
            ladeAchsen();
        } catch (err) {
            console.error(err);
            toast.error('Löschen fehlgeschlagen.');
        }
    };

    return (
        <div
            className="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center p-4"
            onClick={onClose}
        >
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl flex flex-col max-h-[92vh]"
                onClick={(e) => e.stopPropagation()}
                role="dialog"
                aria-modal="true"
                aria-labelledby="schnittbilder-modal-title"
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-gradient-to-r from-rose-50 to-white rounded-t-2xl">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-rose-100 flex items-center justify-center">
                            <Scissors className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500 font-medium">
                                Schnittbilder verwalten
                            </p>
                            <h2 id="schnittbilder-modal-title" className="text-lg font-bold text-slate-900 leading-tight">
                                {kategorie.bezeichnung}
                            </h2>
                        </div>
                    </div>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onClose}
                        className="text-slate-500 hover:text-slate-700 hover:bg-slate-100"
                        aria-label="Schließen"
                    >
                        <X className="w-5 h-5" />
                    </Button>
                </div>

                {/* Body */}
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                    {/* Neue Achse per Drag & Drop */}
                    <div>
                        <p className="text-xs uppercase tracking-wide text-slate-500 font-semibold mb-2">
                            Neue Achse hinzufügen
                        </p>
                        <BildDropZone
                            onFile={handleAchseUpload}
                            disabled={uploading}
                            label="Achsen-Bild hier ablegen oder klicken"
                            hint="Pro Kategorie sind mehrere Achsen möglich (z.B. starke / schwache Achse). PNG, JPG, GIF oder WebP."
                            size="lg"
                        />
                    </div>

                    {/* Achsen-Liste */}
                    {loading ? (
                        <div className="text-center text-slate-500 py-8">
                            <Loader2 className="w-6 h-6 mx-auto mb-2 animate-spin text-rose-400" />
                            Lädt…
                        </div>
                    ) : achsen.length === 0 ? (
                        <div className="text-center text-slate-500 py-10 border-2 border-dashed border-slate-200 rounded-xl">
                            <Ruler className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                            <p className="font-medium text-slate-700">Noch keine Achsen angelegt.</p>
                            <p className="text-sm mt-1">Leg oben die erste Achse für diese Kategorie an.</p>
                        </div>
                    ) : (
                        <div className="space-y-4">
                            {achsen.map((a) => {
                                const list = schnittbilderByAchse[a.id] ?? [];
                                return (
                                    <div
                                        key={a.id}
                                        className="border border-slate-200 rounded-xl overflow-hidden"
                                    >
                                        {/* Achsen-Kopf */}
                                        <div className="flex items-center justify-between gap-3 p-4 bg-slate-50 border-b border-slate-200">
                                            <div className="flex items-center gap-3 min-w-0">
                                                <img
                                                    src={a.bildUrl}
                                                    alt={`Achse ${a.id}`}
                                                    className="w-14 h-14 object-contain bg-white border border-slate-200 rounded-lg"
                                                    onError={(e) => {
                                                        (e.currentTarget as HTMLImageElement).style.opacity = '0.3';
                                                    }}
                                                />
                                                <div className="min-w-0">
                                                    <p className="font-semibold text-slate-900">Achse #{a.id}</p>
                                                    <p className="text-xs text-slate-500 truncate">
                                                        {list.length} Schnittbild{list.length === 1 ? '' : 'er'}
                                                    </p>
                                                </div>
                                            </div>
                                            <button
                                                type="button"
                                                onClick={() => handleAchseLoeschen(a)}
                                                title="Achse löschen"
                                                aria-label="Achse löschen"
                                                className="p-2 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer"
                                            >
                                                <Trash2 className="w-4 h-4" />
                                            </button>
                                        </div>

                                        {/* Schnittbilder der Achse */}
                                        <div className="p-4 space-y-3">
                                            {list.length > 0 && (
                                                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                                                    {list.map((sb) => (
                                                        <div
                                                            key={sb.id}
                                                            className="relative group border border-slate-200 rounded-lg p-2 bg-white hover:border-rose-300 transition-colors"
                                                        >
                                                            <img
                                                                src={sb.bildUrlSchnittbild}
                                                                alt={`Schnittbild ${sb.id}`}
                                                                className="w-full h-20 object-contain"
                                                                onError={(e) => {
                                                                    (e.currentTarget as HTMLImageElement).style.opacity = '0.3';
                                                                }}
                                                            />
                                                            <button
                                                                type="button"
                                                                onClick={() => handleSchnittLoeschen(sb)}
                                                                title="Schnittbild löschen"
                                                                aria-label="Schnittbild löschen"
                                                                className="absolute top-1 right-1 p-1.5 rounded-md bg-white/90 text-slate-400 hover:text-rose-600 hover:bg-rose-50 shadow-sm opacity-0 group-hover:opacity-100 focus:opacity-100 transition-opacity cursor-pointer"
                                                            >
                                                                <Trash2 className="w-3.5 h-3.5" />
                                                            </button>
                                                        </div>
                                                    ))}
                                                </div>
                                            )}

                                            {/* Drop-Zone für neues Schnittbild */}
                                            <BildDropZone
                                                onFile={(f) => handleSchnittUpload(a.id, f)}
                                                disabled={uploading}
                                                label="Schnittbild hinzufügen"
                                                size="sm"
                                            />
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="px-6 py-3 border-t border-slate-100 flex justify-end">
                    <Button variant="outline" onClick={onClose}>
                        Schließen
                    </Button>
                </div>
            </div>
        </div>
    );
};

// ========= Bild-DropZone =========

interface BildDropZoneProps {
    onFile: (file: File) => void;
    disabled?: boolean;
    label: string;
    hint?: string;
    size?: 'sm' | 'lg';
}

const BildDropZone: React.FC<BildDropZoneProps> = ({ onFile, disabled = false, label, hint, size = 'lg' }) => {
    const [dragActive, setDragActive] = useState(false);
    const inputRef = useRef<HTMLInputElement | null>(null);

    const handleFile = (datei: File | undefined | null) => {
        if (!datei) return;
        if (!datei.type.startsWith('image/')) {
            // Backend prüft noch mal, aber sofortiges Feedback hier
            alert('Bitte nur Bilddateien ablegen (PNG, JPG, GIF, WebP).');
            return;
        }
        onFile(datei);
    };

    return (
        <label
            onDragEnter={(e) => { e.preventDefault(); if (!disabled) setDragActive(true); }}
            onDragOver={(e) => { e.preventDefault(); if (!disabled) setDragActive(true); }}
            onDragLeave={(e) => { e.preventDefault(); setDragActive(false); }}
            onDrop={(e) => {
                e.preventDefault();
                setDragActive(false);
                if (disabled) return;
                const datei = e.dataTransfer.files?.[0];
                handleFile(datei);
            }}
            className={cn(
                'flex flex-col items-center justify-center gap-2 w-full border-2 border-dashed rounded-xl text-center transition-colors select-none',
                size === 'lg' ? 'p-6' : 'p-3',
                disabled
                    ? 'border-slate-200 bg-slate-50 text-slate-400 cursor-not-allowed'
                    : dragActive
                        ? 'border-rose-500 bg-rose-50 text-rose-700 cursor-copy'
                        : 'border-rose-200 bg-white text-slate-600 hover:border-rose-400 hover:bg-rose-50/40 cursor-pointer',
            )}
        >
            <input
                ref={inputRef}
                type="file"
                accept="image/*"
                className="sr-only"
                disabled={disabled}
                onChange={(e) => {
                    const datei = e.target.files?.[0];
                    handleFile(datei);
                    // Damit dieselbe Datei erneut hochgeladen werden kann
                    if (inputRef.current) inputRef.current.value = '';
                }}
            />
            <div
                className={cn(
                    'rounded-full flex items-center justify-center',
                    size === 'lg' ? 'w-12 h-12' : 'w-8 h-8',
                    dragActive ? 'bg-rose-100 text-rose-700' : 'bg-slate-100 text-slate-500',
                )}
            >
                <UploadCloud className={cn(size === 'lg' ? 'w-6 h-6' : 'w-4 h-4')} />
            </div>
            <div>
                <p className={cn('font-medium', size === 'lg' ? 'text-sm' : 'text-xs')}>{label}</p>
                {hint && size === 'lg' && (
                    <p className="text-xs text-slate-500 mt-1">{hint}</p>
                )}
            </div>
            {size === 'lg' && (
                <span className="inline-flex items-center gap-1 text-xs text-rose-600 font-medium mt-1">
                    <Plus className="w-3 h-3" />
                    Datei auswählen
                </span>
            )}
        </label>
    );
};

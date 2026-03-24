import { useEffect, useState } from "react";
import { AlertTriangle, Calendar, FileText, User, CheckCircle, Trash, Plus } from "lucide-react";
import { Card } from "./ui/card";
import { Button } from "./ui/button";
import { ImageViewer } from "./ui/image-viewer";
import { CreateReklamationModal } from "./CreateReklamationModal";
import { useToast } from './ui/toast';
import { useConfirm } from './ui/confirm-dialog';
import type { LieferantReklamation } from '../types';
import { prependUniqueById } from '../lib/optimisticUploads';


interface LieferantReklamationenTabProps {
    lieferantId: number;
}

export function LieferantReklamationenTab({ lieferantId }: LieferantReklamationenTabProps) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [reklamationen, setReklamationen] = useState<LieferantReklamation[]>([]);
    const [loading, setLoading] = useState(true);

    // Image Viewer State
    const [reklamationBildViewer, setReklamationBildViewer] = useState<{ images: { url: string; name?: string }[]; startIndex: number } | null>(null);

    // Create Modal State
    const [createModalOpen, setCreateModalOpen] = useState(false);

    useEffect(() => {
        loadData();
    }, [lieferantId]);

    const loadData = async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/reklamationen/lieferant/${lieferantId}`);
            if (res.ok) {
                const data = await res.json();
                setReklamationen(data);
            }
        } catch (err) {
            console.error(err);
        }
        setLoading(false);
    };

    const handleImageClick = (rek: LieferantReklamation, clickedIndex: number) => {
        const images = rek.bilder.map(b => ({ url: b.url, name: b.originalDateiname }));
        setReklamationBildViewer({ images, startIndex: clickedIndex });
    };

    const handleComplete = async (id: number) => {
        if (!await confirmDialog({ title: "Reklamation abschließen", message: "Möchten Sie diese Reklamation wirklich abschließen?", variant: "info", confirmLabel: "Abschließen" })) return;

        try {
            const res = await fetch(`/api/reklamationen/${id}/status?status=ABGESCHLOSSEN`, {
                method: 'PATCH'
            });

            if (res.ok) {
                loadData(); // Reload to update status and order
            } else {
                toast.error("Fehler beim Aktualisieren des Status.");
            }
        } catch (err) {
            console.error(err);
            toast.error("Ein Fehler ist aufgetreten.");
        }
    };

    const handleDelete = async (id: number) => {
        if (!await confirmDialog({ title: "Reklamation löschen", message: "Möchten Sie diese Reklamation wirklich löschen? Dies kann nicht rückgängig gemacht werden.", variant: "danger", confirmLabel: "Löschen" })) return;

        try {
            const res = await fetch(`/api/reklamationen/${id}`, {
                method: 'DELETE'
            });

            if (res.ok) {
                loadData();
            } else {
                toast.error("Fehler beim Löschen der Reklamation.");
            }
        } catch (err) {
            console.error(err);
            toast.error("Ein Fehler ist aufgetreten.");
        }
    };

    if (loading) {
        return <div className="p-8 text-center text-slate-500">Lade Reklamationen...</div>;
    }



    return (
        <div className="space-y-4 p-4">
            {/* Header / Create Button */}
            <div className="flex justify-between items-center bg-slate-50 p-4 rounded-lg border border-slate-200">
                <div>
                    <h3 className="text-lg font-medium text-slate-900">Reklamationsübersicht</h3>
                    <p className="text-sm text-slate-500">Verwalten Sie hier alle offenen und abgeschlossenen Reklamationen.</p>
                </div>
                <Button onClick={() => setCreateModalOpen(true)} className="bg-rose-600 hover:bg-rose-700">
                    <Plus className="w-4 h-4 mr-2" />
                    Neue Reklamation
                </Button>
            </div>

            {reklamationen.length === 0 ? (
                <div className="p-12 text-center text-slate-500 border-2 border-dashed border-slate-200 rounded-xl bg-slate-50">
                    <AlertTriangle className="w-12 h-12 mx-auto mb-4 text-slate-300" />
                    <h3 className="text-lg font-medium text-slate-900 mb-2">Keine Reklamationen</h3>
                    <p>Für diesen Lieferanten liegen keine Reklamationen vor.</p>
                </div>
            ) : (
                reklamationen.map(rek => (
                    <Card key={rek.id} className="p-6 border-slate-200 relative overflow-hidden group">
                        {/* Status Stripe */}
                        <div className={`absolute left-0 top-0 bottom-0 w-1 ${rek.status === 'ABGESCHLOSSEN' ? 'bg-slate-300' : 'bg-rose-500'}`} />

                        {/* Delete Button - Top Right */}
                        <div className="absolute top-4 right-4 opacity-0 group-hover:opacity-100 transition-opacity">
                            <Button
                                variant="ghost"
                                size="sm"
                                className="text-slate-400 hover:text-red-600 hover:bg-red-50 h-8 w-8 p-0 rounded-full"
                                onClick={() => handleDelete(rek.id)}
                                title="Reklamation löschen"
                            >
                                <Trash className="w-4 h-4" />
                            </Button>
                        </div>

                        <div className="flex flex-col md:flex-row gap-6">
                            {/* Left: Info */}
                            <div className="flex-1 space-y-4 pl-2">
                                <div className="flex items-start justify-between">
                                    <div>
                                        <div className="flex items-center gap-3 mb-1">
                                            <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${rek.status === 'OFFEN' ? 'bg-red-100 text-red-700' :
                                                rek.status === 'ABGESCHLOSSEN' ? 'bg-slate-100 text-slate-600' :
                                                    'bg-blue-100 text-blue-700'
                                                }`}>
                                                {rek.status}
                                            </span>
                                            <span className="text-sm text-slate-500 flex items-center gap-1">
                                                <Calendar className="w-3 h-3" />
                                                {new Date(rek.erstelltAm).toLocaleDateString()} {new Date(rek.erstelltAm).toLocaleTimeString()}
                                            </span>
                                        </div>
                                        <h3 className="font-semibold text-slate-900 text-lg">
                                            Reklamation #{rek.id}
                                        </h3>
                                    </div>

                                    {rek.status !== 'ABGESCHLOSSEN' && (
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => handleComplete(rek.id)}
                                            className="text-slate-600 hover:text-green-600 hover:bg-green-50 border-slate-200 hover:border-green-200 mr-8"
                                        >
                                            <CheckCircle className="w-4 h-4 mr-2" />
                                            Abschließen
                                        </Button>
                                    )}
                                </div>

                                <p className="text-slate-700 whitespace-pre-wrap bg-slate-50 p-3 rounded-lg border border-slate-100">
                                    {rek.beschreibung}
                                </p>

                                <div className="flex flex-wrap gap-4 text-sm text-slate-600">
                                    <div className="flex items-center gap-1.5">
                                        <User className="w-4 h-4 text-slate-400" />
                                        Erstellt von: <span className="font-medium text-slate-900">{rek.erstellerName}</span>
                                    </div>
                                    {(rek.lieferscheinNummer || rek.lieferscheinDateiname) && (
                                        <div className="flex items-center gap-1.5">
                                            <FileText className="w-4 h-4 text-slate-400" />
                                            Lieferschein:
                                            {rek.lieferscheinId ? (
                                                <a
                                                    href={`/api/dokumente/${encodeURIComponent(rek.lieferscheinDateiname || '')}`}
                                                    target="_blank"
                                                    rel="noreferrer"
                                                    className="font-medium text-rose-600 hover:underline"
                                                >
                                                    {rek.lieferscheinNummer || rek.lieferscheinDateiname}
                                                </a>
                                            ) : (
                                                <span className="font-medium text-slate-900">{rek.lieferscheinNummer || rek.lieferscheinDateiname}</span>
                                            )}
                                        </div>
                                    )}
                                </div>
                            </div>

                            {/* Right: Images */}
                            {rek.bilder && rek.bilder.length > 0 && (
                                <div className="w-full md:w-1/3">
                                    <h4 className="text-sm font-medium text-slate-900 mb-3">Bilder ({rek.bilder.length})</h4>
                                    <div className="grid grid-cols-2 gap-2">
                                        {rek.bilder.map((img, idx) => (
                                            <div
                                                key={img.id}
                                                onClick={() => handleImageClick(rek, idx)}
                                                className="block relative aspect-square rounded-lg overflow-hidden border border-slate-200 hover:border-rose-300 transition-colors group cursor-pointer"
                                            >
                                                <img src={img.url} alt="Reklamationsbild" className="w-full h-full object-cover" />
                                                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors flex items-center justify-center opacity-0 group-hover:opacity-100">
                                                    <div className="bg-white/90 rounded-full p-2 shadow-sm">
                                                        <FileText className="w-4 h-4 text-slate-700" />
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    </Card>
                ))
            )}

            <ImageViewer
                src={reklamationBildViewer ? reklamationBildViewer.images[reklamationBildViewer.startIndex]?.url : null}
                onClose={() => setReklamationBildViewer(null)}
                alt="Reklamationsbild"
                images={reklamationBildViewer?.images}
                startIndex={reklamationBildViewer?.startIndex}
            />

            <CreateReklamationModal
                isOpen={createModalOpen}
                onClose={() => setCreateModalOpen(false)}
                lieferantId={lieferantId}
                onSuccess={(reklamation) => {
                    setReklamationen(prev => prependUniqueById(prev, [reklamation]));
                    void loadData();
                }}
            />
        </div>
    );
}

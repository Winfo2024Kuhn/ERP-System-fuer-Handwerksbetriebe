import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Calendar, User, FileText, Upload, Loader2 } from 'lucide-react';
import { ImageViewer } from '../components/ui/image-viewer';

interface Reklamation {
    id: number;
    erstelltAm: string;
    beschreibung: string;
    status: string;
    erstellerName: string;
    lieferscheinNummer?: string;
    lieferscheinDateiname?: string;
    bilder: {
        id: number;
        url: string;
        originalDateiname: string;
    }[];
}

export function LieferantReklamationDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const [reklamation, setReklamation] = useState<Reklamation | null>(null);
    const [loading, setLoading] = useState(true);
    const [uploading, setUploading] = useState(false);
    const [viewerImage, setViewerImage] = useState<string | null>(null);

    useEffect(() => {
        loadData();
    }, [id]);

    const loadData = async () => {
        try {
            const res = await fetch(`/api/reklamationen/${id}`);
            if (res.ok) {
                const data = await res.json();
                setReklamation(data);
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    };

    const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        if (!e.target.files || e.target.files.length === 0) return;

        setUploading(true);
        try {
            const files = Array.from(e.target.files);
            for (const file of files) {
                const formData = new FormData();
                formData.append("datei", file);

                await fetch(`/api/reklamationen/${id}/bilder`, {
                    method: "POST",
                    body: formData
                });
            }
            loadData(); // Refresh to see new images
        } catch (error) {
            console.error(error);
            alert("Fehler beim Hochladen.");
        } finally {
            setUploading(false);
        }
    };

    if (loading) return <div className="p-8 text-center text-slate-500">Lade Details...</div>;
    if (!reklamation) return <div className="p-8 text-center text-slate-500">Reklamation nicht gefunden.</div>;

    return (
        <div className="min-h-screen bg-white pb-20 font-sans">
            {/* Header */}
            <div className="bg-white px-4 py-3 border-b border-slate-200 sticky top-0 z-10 flex items-center gap-3">
                <button
                    onClick={() => navigate(-1)}
                    className="p-2 -ml-2 text-slate-600 hover:bg-slate-100 rounded-full"
                >
                    <ArrowLeft className="w-6 h-6" />
                </button>
                <div className="flex-1">
                    <div className="flex items-center gap-2 mb-0.5">
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wide ${reklamation.status === 'OFFEN' ? 'bg-rose-100 text-rose-700' :
                            reklamation.status === 'ABGESCHLOSSEN' ? 'bg-slate-100 text-slate-600' :
                                'bg-blue-100 text-blue-700'
                            }`}>
                            {reklamation.status}
                        </span>
                        <span className="text-xs text-slate-400">#{reklamation.id}</span>
                    </div>
                </div>
            </div>

            <div className="p-4 space-y-6">
                {/* Description */}
                <div>
                    <h3 className="text-sm font-medium text-slate-500 mb-2 uppercase tracking-wide">Beschreibung</h3>
                    <p className="text-slate-900 leading-relaxed whitespace-pre-wrap">
                        {reklamation.beschreibung}
                    </p>
                </div>

                {/* Info Grid */}
                <div className="grid grid-cols-2 gap-4">
                    <div className="p-3 bg-slate-50 rounded-lg">
                        <div className="flex items-center gap-2 text-slate-500 mb-1">
                            <User className="w-4 h-4" />
                            <span className="text-xs font-medium uppercase">Ersteller</span>
                        </div>
                        <p className="text-sm font-medium text-slate-900">{reklamation.erstellerName}</p>
                    </div>
                    <div className="p-3 bg-slate-50 rounded-lg">
                        <div className="flex items-center gap-2 text-slate-500 mb-1">
                            <Calendar className="w-4 h-4" />
                            <span className="text-xs font-medium uppercase">Datum</span>
                        </div>
                        <p className="text-sm font-medium text-slate-900">
                            {new Date(reklamation.erstelltAm).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })} {new Date(reklamation.erstelltAm).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}
                        </p>
                    </div>
                </div>

                {/* Lieferschein */}
                {(reklamation.lieferscheinNummer || reklamation.lieferscheinDateiname) && (
                    <div className="p-3 border border-slate-200 rounded-lg flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center">
                                <FileText className="w-5 h-5 text-rose-600" />
                            </div>
                            <div>
                                <p className="text-xs text-slate-500 uppercase font-medium">Lieferschein</p>
                                <p className="text-sm font-medium text-slate-900 truncate max-w-[200px]">
                                    {reklamation.lieferscheinNummer || reklamation.lieferscheinDateiname}
                                </p>
                            </div>
                        </div>
                    </div>
                )}

                {/* Images */}
                <div>
                    <div className="flex items-center justify-between mb-3">
                        <h3 className="text-sm font-medium text-slate-500 uppercase tracking-wide">Bilder ({reklamation.bilder.length})</h3>
                    </div>

                    <div className="grid grid-cols-3 gap-2 mb-4">
                        {reklamation.bilder.map(img => (
                            <div
                                key={img.id}
                                onClick={() => setViewerImage(img.url)}
                                className="aspect-square bg-slate-100 rounded-lg overflow-hidden border border-slate-200 relative group cursor-pointer"
                            >
                                <img src={img.url} alt="Reklamation" className="w-full h-full object-cover" />
                            </div>
                        ))}
                    </div>

                    <label className="w-full flex items-center justify-center gap-2 p-4 border-2 border-dashed border-rose-200 bg-rose-50/50 rounded-xl cursor-pointer hover:bg-rose-50 transition-colors">
                        {uploading ? (
                            <Loader2 className="w-5 h-5 text-rose-600 animate-spin" />
                        ) : (
                            <Upload className="w-5 h-5 text-rose-600" />
                        )}
                        <span className="text-sm font-medium text-rose-700">
                            {uploading ? "Wird hochgeladen..." : "Foto hinzufügen"}
                        </span>
                        <input
                            type="file"
                            accept="image/*"
                            capture="environment"
                            multiple
                            className="hidden"
                            onChange={handleFileUpload}
                            disabled={uploading}
                        />
                    </label>
                </div>
            </div>
            <ImageViewer
                src={viewerImage}
                alt="Reklamationsbild"
                onClose={() => setViewerImage(null)}
            />
        </div>
    );
}

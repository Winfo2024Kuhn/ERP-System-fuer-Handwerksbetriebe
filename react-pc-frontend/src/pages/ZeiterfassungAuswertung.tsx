import { useState } from 'react';
import { BarChart3, Loader2, Search, X, FileText } from 'lucide-react';
import { Button } from '../components/ui/button';
import { ProjectSelectModal } from '../components/ProjectSelectModal';

export default function ZeiterfassungAuswertung() {
    const [selectedProjekt, setSelectedProjekt] = useState<{ id: number; bauvorhaben: string } | null>(null);
    const [showProjectModal, setShowProjectModal] = useState(false);

    const [auswertung, setAuswertung] = useState<any>(null);
    const [loading, setLoading] = useState(false);

    const loadAuswertung = async () => {
        if (!selectedProjekt) return;
        setLoading(true);
        try {
            const res = await fetch(`/api/zeitverwaltung/auswertung/projekt/${selectedProjekt.id}`);
            const data = await res.json();
            if (data && typeof data === 'object') {
                data.taetigkeiten = Array.isArray(data.taetigkeiten) ? data.taetigkeiten : [];
                setAuswertung(data);
            } else {
                setAuswertung(null);
            }
        } catch (err) {
            console.error('Fehler beim Laden der Auswertung:', err);
            setAuswertung(null);
        }
        setLoading(false);
    };

    return (
        <div className="p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                        Berichte
                    </p>
                    <h1 className="text-3xl font-bold text-slate-900">
                        PROJEKTAUSWERTUNG
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Zeitauswertung nach Tätigkeiten pro Projekt
                    </p>
                </div>
            </div>

            <div className="space-y-6">
                <div className="flex gap-4 items-end bg-white p-4 rounded-lg border border-slate-200 shadow-sm">
                    <div className="flex-1">
                        <label className="block text-sm font-medium text-slate-700 mb-1">Projekt</label>
                        <div className="relative">
                            <div
                                className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white cursor-pointer hover:border-rose-400 transition-colors flex items-center justify-between"
                                onClick={() => setShowProjectModal(true)}
                            >
                                <span className={!selectedProjekt ? "text-slate-500" : "font-medium text-slate-900"}>
                                    {selectedProjekt ? selectedProjekt.bauvorhaben : "Projekt wählen..."}
                                </span>
                                <Search className="w-4 h-4 text-slate-400" />
                            </div>
                            {selectedProjekt && (
                                <button
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        setSelectedProjekt(null);
                                        setAuswertung(null);
                                    }}
                                    className="absolute right-8 top-2.5 text-slate-400 hover:text-rose-600 transition-colors"
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            )}
                        </div>
                    </div>
                    {auswertung && (
                        <div className="flex items-center gap-2">
                            <Button 
                                variant="outline" 
                                onClick={() => window.open(`/api/zeitverwaltung/auswertung/projekt/${selectedProjekt?.id}/pdf`, '_blank')}
                                disabled={!auswertung.anzahlBuchungen || auswertung.anzahlBuchungen === 0}
                                title={(!auswertung.anzahlBuchungen || auswertung.anzahlBuchungen === 0) ? "Keine Buchungen vorhanden" : "PDF exportieren"}
                            >
                                <FileText className="w-4 h-4 mr-2" /> PDF Export
                            </Button>
                        </div>
                    )}
                    <Button onClick={loadAuswertung} className="bg-rose-600 hover:bg-rose-700 text-white" disabled={!selectedProjekt || loading}>
                        {loading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <BarChart3 className="w-4 h-4 mr-2" />}
                        Auswertung laden
                    </Button>
                </div>

                {loading ? (
                    <div className="flex justify-center py-12">
                        <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                    </div>
                ) : auswertung ? (
                    <div className="bg-white rounded-lg border border-slate-200 shadow-sm">
                        <div className="p-4 border-b border-slate-200 bg-slate-50">
                            <h3 className="font-bold text-lg text-slate-900">Ergebnisse für: {selectedProjekt?.bauvorhaben}</h3>
                            <p className="text-slate-500 text-sm">
                                Gesamt: <span className="font-medium text-rose-600">{typeof auswertung.gesamtStunden === 'number' ? auswertung.gesamtStunden.toFixed(2) : '0.00'}h</span> ({auswertung.anzahlBuchungen} Buchungen)
                            </p>
                        </div>
                        <div className="divide-y divide-slate-100">
                            {auswertung.taetigkeiten?.length > 0 ? (
                                auswertung.taetigkeiten.map((t: any, i: number) => (
                                    <div key={i} className="p-4">
                                        <div className="flex justify-between items-center mb-2">
                                            <h4 className="font-semibold text-slate-800">{t.arbeitsgang || 'Ohne Tätigkeit'}</h4>
                                            <div className="text-sm font-bold text-slate-700">
                                                {typeof t.gesamtStunden === 'number' ? t.gesamtStunden.toFixed(2) : '0.00'}h
                                                <span className="text-slate-400 font-normal ml-1">({t.anzahlBuchungen})</span>
                                            </div>
                                        </div>

                                        {/* Detail Tabelle */}
                                        <table className="w-full text-xs text-left text-slate-600">
                                            <thead className="bg-slate-50 border-b border-slate-100">
                                                <tr>
                                                    <th className="p-2 w-32">Mitarbeiter</th>
                                                    <th className="p-2 w-24">Datum</th>
                                                    <th className="p-2 w-24">Zeit</th>
                                                    <th className="p-2 w-24 text-right">Dauer</th>
                                                    <th className="p-2">Bemerkung</th>
                                                </tr>
                                            </thead>
                                            <tbody className="divide-y divide-slate-100">
                                                {t.buchungen?.map((b: any) => (
                                                    <tr key={b.id} className="hover:bg-slate-50">
                                                        <td className="p-2">{b.mitarbeiterName}</td>
                                                        <td className="p-2">{b.startDateTime ? new Date(b.startDateTime).toLocaleDateString() : '-'}</td>
                                                        <td className="p-2">
                                                            {b.startZeit} - {b.endeZeit}
                                                        </td>
                                                        <td className="p-2 text-right">
                                                            {b.dauerMinuten ? (b.dauerMinuten / 60).toFixed(2) + 'h' : '-'}
                                                        </td>
                                                        <td className="p-2 italic text-slate-500">{b.notiz}</td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                ))
                            ) : (
                                <div className="p-8 text-center text-slate-500 italic">
                                    Keine Buchungen für diesen Zeitraum gefunden.
                                </div>
                            )}
                        </div>
                    </div>
                ) : (
                    <div className="text-center py-16 bg-slate-50 rounded-lg border border-dashed border-slate-300">
                        <BarChart3 className="w-12 h-12 text-slate-300 mx-auto mb-3" />
                        <p className="text-slate-500 font-medium">Wähle ein Projekt und klicke auf "Auswertung laden"</p>
                    </div>
                )}
            </div>

            {showProjectModal && (
                <ProjectSelectModal
                    onSelect={(p) => {
                        setSelectedProjekt(p);
                        setShowProjectModal(false);
                        // Optional: Auto-load when selected? For now manual button as per existing UI
                    }}
                    onClose={() => setShowProjectModal(false)}
                />
            )}
        </div>
    );
}

import { useState, useEffect } from 'react';
import { ArrowLeft, FileText, Download, Calendar, Euro } from 'lucide-react';
import { Button } from '../ui/button';
import { Card } from '../ui/card';

interface ZuordnungDto {
    id: number;
    projektId?: number;
    projektName?: string;
    kostenstelleId: number;
    kostenstelleName: string;
    betrag: number;
    prozentanteil: number | null;
    beschreibung: string;
    zugeordnetAm: string;
    
    // Extra Info
    lieferantName?: string;
    bestellnummer?: string;
    dokumentDatum?: string;
    geschaeftsdokumentId?: number;
}

interface Kostenstelle {
    id: number;
    bezeichnung: string;
    typ: string;
    beschreibung: string;
}

interface KostenstelleDetailViewProps {
    kostenstelle: Kostenstelle;
    onBack: () => void;
}

export function KostenstelleDetailView({ kostenstelle, onBack }: KostenstelleDetailViewProps) {
    const [assignments, setAssignments] = useState<ZuordnungDto[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [kostenstelle.id]);

    const loadData = async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/bestellungen-uebersicht/zuordnungen/kostenstelle/${kostenstelle.id}`);
            if (res.ok) {
                setAssignments(await res.json());
            }
        } catch (e) {
            console.error(e);
        } finally {
            setLoading(false);
        }
    };

    const totalAmount = assignments.reduce((sum, a) => sum + (a.betrag || 0), 0);

    const formatCurrency = (val: number) => new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val);
    const formatDate = (val?: string) => val ? new Date(val).toLocaleDateString('de-DE') : '-';

    const handleViewPdf = (docId?: number) => {
        if (!docId) return;
        window.open(`/api/bestellungen-uebersicht/document/${docId}`, '_blank');
    };

    return (
        <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
            {/* Header */}
            <div className="flex items-center gap-4">
                <Button variant="ghost" size="sm" onClick={onBack} className="-ml-2">
                    <ArrowLeft className="w-5 h-5 mr-1" />
                    Zurück
                </Button>
                <div>
                    <h2 className="text-2xl font-bold text-slate-900">{kostenstelle.bezeichnung}</h2>
                    <p className="text-slate-500">Kostenstellen-Details & Zuordnungen</p>
                </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <Card className="p-6 bg-slate-50 border-slate-200">
                    <p className="text-sm font-medium text-slate-500 uppercase tracking-wide mb-1">Gesamtkosten</p>
                    <p className="text-3xl font-bold text-slate-900">{formatCurrency(totalAmount)}</p>
                </Card>
                <Card className="p-6 bg-slate-50 border-slate-200">
                    <p className="text-sm font-medium text-slate-500 uppercase tracking-wide mb-1">Anzahl Zuweisungen</p>
                    <p className="text-3xl font-bold text-slate-900">{assignments.length}</p>
                </Card>
            </div>

            {/* Liste */}
            <Card className="overflow-hidden">
                <div className="p-4 border-b bg-slate-50 flex justify-between items-center">
                    <h3 className="font-semibold text-slate-900">Zugeordnete Rechnungen & Kosten</h3>
                </div>
                
                {loading ? (
                    <div className="p-8 text-center text-slate-500">Laden...</div>
                ) : assignments.length === 0 ? (
                    <div className="p-8 text-center text-slate-500">Keine Zuordnungen vorhanden.</div>
                ) : (
                    <div className="divide-y divide-slate-100">
                        {assignments.map((a) => (
                            <div key={a.id} className="p-4 hover:bg-slate-50 flex flex-col md:flex-row gap-4 justify-between items-start md:items-center">
                                <div className="flex-1">
                                    <div className="flex items-center gap-2 mb-1">
                                        <FileText className="w-4 h-4 text-slate-400" />
                                        <span className="font-medium text-slate-900">
                                            {a.lieferantName || 'Unbekannter Lieferant'}
                                        </span>
                                        {a.bestellnummer && (
                                            <span className="text-xs bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded">
                                                {a.bestellnummer}
                                            </span>
                                        )}
                                    </div>
                                    <p className="text-sm text-slate-600 mb-1">{a.beschreibung}</p>
                                    <div className="flex gap-3 text-xs text-slate-400">
                                        <span className="flex items-center">
                                            <Calendar className="w-3 h-3 mr-1" />
                                            {formatDate(a.dokumentDatum)}
                                        </span>
                                        <span className="flex items-center">
                                            <Euro className="w-3 h-3 mr-1" />
                                            Anteil: {a.prozentanteil ? `${a.prozentanteil.toFixed(1)}%` : 'Pausschal'}
                                        </span>
                                    </div>
                                </div>
                                <div className="flex items-center gap-4">
                                    <div className="text-right">
                                        <span className="block font-bold text-slate-900 text-lg">
                                            {formatCurrency(a.betrag)}
                                        </span>
                                        <span className="text-xs text-slate-400">Netto</span>
                                    </div>
                                    {a.geschaeftsdokumentId && (
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => handleViewPdf(a.geschaeftsdokumentId)}
                                            className="text-slate-600 hover:text-rose-600"
                                            title="PDF öffnen"
                                        >
                                            <Download className="w-4 h-4" />
                                        </Button>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </Card>
        </div>
    );
}

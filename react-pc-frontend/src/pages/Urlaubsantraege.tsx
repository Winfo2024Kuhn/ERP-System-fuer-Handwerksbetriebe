import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select-custom';
import { Check, X, Calendar, User, FileText, Plane, Stethoscope, Clock } from 'lucide-react';
import { useConfirm } from '../components/ui/confirm-dialog';

interface Urlaubsantrag {
    id: number;
    mitarbeiterName: string;
    mitarbeiter: {
        id: number;
        vorname: string;
        nachname: string;
        jahresUrlaub: number;
    };
    vonDatum: string;
    bisDatum: string;
    bemerkung: string | null;
    status: 'OFFEN' | 'GENEHMIGT' | 'ABGELEHNT' | 'STORNIERT';
    erstellDatum: string;
    typ: 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ZEITAUSGLEICH';
}

export default function Urlaubsantraege() {
    const confirmDialog = useConfirm();
    const [searchParams, setSearchParams] = useSearchParams();
    const [antraege, setAntraege] = useState<Urlaubsantrag[]>([]);
    const [statusFilter, setStatusFilter] = useState<string>(() => {
        return searchParams.get('status') || 'OFFEN';
    });
    const [loading, setLoading] = useState(false);

    // Deep-link: read status from URL param
    useEffect(() => {
        const statusParam = searchParams.get('status');
        if (statusParam) {
            setStatusFilter(statusParam);
            setSearchParams({}, { replace: true });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [searchParams]);

    useEffect(() => {
        loadAntraege();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [statusFilter]);

    const loadAntraege = async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/urlaub/antraege?status=${statusFilter}`);
            if (res.ok) {
                const data = await res.json();
                setAntraege(data);
            }
        } catch (error) {
            console.error("Error loading requests", error);
        } finally {
            setLoading(false);
        }
    };

    const handleApprove = async (id: number) => {
        if (!await confirmDialog({ title: 'Genehmigen', message: 'Urlaubsantrag genehmigen?', variant: 'info', confirmLabel: 'Genehmigen' })) return;
        try {
            const res = await fetch(`/api/urlaub/antraege/${id}/approve`, { method: 'PUT' });
            if (res.ok) {
                loadAntraege();
            }
        } catch (error) {
            console.error("Error approving request", error);
        }
    };

    const handleReject = async (id: number) => {
        if (!await confirmDialog({ title: 'Ablehnen', message: 'Urlaubsantrag ablehnen?', variant: 'danger', confirmLabel: 'Ablehnen' })) return;
        try {
            const res = await fetch(`/api/urlaub/antraege/${id}/reject`, { method: 'PUT' });
            if (res.ok) {
                loadAntraege();
            }
        } catch (error) {
            console.error("Error rejecting request", error);
        }
    };

    const getStatusBadge = (status: string) => {
        switch (status) {
            case 'OFFEN': return <span className="px-2 py-1 rounded bg-yellow-100 text-yellow-700 text-xs font-medium">Offen</span>;
            case 'GENEHMIGT': return <span className="px-2 py-1 rounded bg-green-100 text-green-700 text-xs font-medium">Genehmigt</span>;
            case 'ABGELEHNT': return <span className="px-2 py-1 rounded bg-red-100 text-red-700 text-xs font-medium">Abgelehnt</span>;
            default: return <span className="px-2 py-1 rounded bg-slate-100 text-slate-700 text-xs font-medium">{status}</span>;
        }
    };

    return (
        <PageLayout>
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                        Urlaubsverwaltung
                    </p>
                    <h1 className="text-3xl font-bold text-slate-900 uppercase">
                        URLAUBSANTRÄGE
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Verwaltung und Genehmigung von Urlaubsanträgen
                    </p>
                </div>
                <div className="w-full md:w-64">
                    <Select
                        value={statusFilter}
                        onChange={(val) => setStatusFilter(val)}
                        options={[
                            { value: 'OFFEN', label: 'Offene Anträge' },
                            { value: 'GENEHMIGT', label: 'Genehmigte Anträge' },
                            { value: 'ABGELEHNT', label: 'Abgelehnte Anträge' },
                            { value: 'STORNIERT', label: 'Stornierte Anträge' }
                        ]}
                    />
                </div>
            </div>

            <div className="space-y-4">
                {antraege.length === 0 && !loading && (
                    <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg">
                        <p>Keine Anträge mit Status "{statusFilter}" gefunden.</p>
                    </div>
                )}

                {antraege.map((antrag) => (
                    <Card key={antrag.id} className="p-4 border-slate-200">
                        <div className="flex flex-col md:flex-row justify-between gap-4">
                            <div className="flex items-start gap-4">
                                <div className="w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center text-slate-600">
                                    <User className="w-5 h-5" />
                                </div>
                                <div>
                                    <div className="flex items-center gap-2 mb-1">
                                        {antrag.typ === 'KRANKHEIT' && (
                                            <span className="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium bg-red-100 text-red-800">
                                                <Stethoscope className="w-3 h-3" />
                                                Krankheit
                                            </span>
                                        )}
                                        {antrag.typ === 'URLAUB' && (
                                            <span className="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium bg-blue-100 text-blue-800">
                                                <Plane className="w-3 h-3" />
                                                Urlaub
                                            </span>
                                        )}
                                        {antrag.typ === 'FORTBILDUNG' && (
                                            <span className="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium bg-purple-100 text-purple-800">
                                                <FileText className="w-3 h-3" />
                                                Fortbildung
                                            </span>
                                        )}
                                        {antrag.typ === 'ZEITAUSGLEICH' && (
                                            <span className="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium bg-teal-100 text-teal-800">
                                                <Clock className="w-3 h-3" />
                                                Zeitausgleich
                                            </span>
                                        )}
                                    </div>
                                    <h3 className="text-lg font-bold text-slate-900">
                                        {antrag.mitarbeiter.nachname}, {antrag.mitarbeiter.vorname}
                                    </h3>
                                    <div className="flex items-center gap-4 mt-1 text-sm text-slate-500">
                                        <span className="flex items-center gap-1">
                                            <Calendar className="w-4 h-4" />
                                            {new Date(antrag.vonDatum).toLocaleDateString()} - {new Date(antrag.bisDatum).toLocaleDateString()}
                                        </span>
                                        {antrag.mitarbeiter.jahresUrlaub && (
                                            <span className="text-rose-600 font-medium">
                                                (Jahresurlaub: {antrag.mitarbeiter.jahresUrlaub} Tage)
                                            </span>
                                        )}
                                    </div>
                                    {antrag.bemerkung && (
                                        <div className="mt-2 p-2 bg-slate-50 rounded text-sm text-slate-600 flex gap-2">
                                            <FileText className="w-4 h-4 mt-0.5 text-slate-400" />
                                            {antrag.bemerkung}
                                        </div>
                                    )}
                                </div>
                            </div>

                            <div className="flex items-center gap-4">
                                <div>
                                    {getStatusBadge(antrag.status)}
                                </div>
                                {antrag.status === 'OFFEN' && (
                                    <div className="flex gap-2">
                                        <Button
                                            onClick={() => handleReject(antrag.id)}
                                            variant="outline"
                                            className="text-red-600 hover:bg-red-50 border-red-200"
                                            size="sm"
                                        >
                                            <X className="w-4 h-4 mr-1" /> Ablehnen
                                        </Button>
                                        <Button
                                            onClick={() => handleApprove(antrag.id)}
                                            className="bg-rose-600 text-white hover:bg-rose-700"
                                            size="sm"
                                        >
                                            <Check className="w-4 h-4 mr-1" /> Genehmigen
                                        </Button>
                                    </div>
                                )}
                            </div>
                        </div>
                    </Card>
                ))}
            </div>
        </PageLayout>
    );
}

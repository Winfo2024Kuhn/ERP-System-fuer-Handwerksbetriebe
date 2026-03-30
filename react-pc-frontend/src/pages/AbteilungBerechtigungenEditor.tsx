import { useState, useEffect } from 'react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Shield, Users, Save, Loader2, Check, Eye, FileText, Wallet } from 'lucide-react';

interface TypBerechtigung {
    typ: string;
    darfSehen: boolean;
    darfScannen: boolean;
}

interface AbteilungBerechtigung {
    abteilungId: number;
    abteilungName: string;
    berechtigungen: TypBerechtigung[];
    darfRechnungenGenehmigen: boolean;
    darfRechnungenSehen: boolean;
}

const DOKUMENT_TYP_LABELS: Record<string, string> = {
    'ANFRAGE': 'Anfrage',
    'AUFTRAGSBESTAETIGUNG': 'Auftragsbestätigung',
    'LIEFERSCHEIN': 'Lieferschein',
    'RECHNUNG': 'Rechnung'
};

export default function AbteilungBerechtigungenEditor() {
    const [berechtigungen, setBerechtigungen] = useState<AbteilungBerechtigung[]>([]);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState<number | null>(null);
    const [saveSuccess, setSaveSuccess] = useState<number | null>(null);

    const loadBerechtigungen = async () => {
        try {
            const res = await fetch('/api/abteilungen/berechtigungen');
            if (res.ok) {
                const data = await res.json();
                setBerechtigungen(data);
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err);
        }
        setLoading(false);
    };

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        loadBerechtigungen();
    }, []);

    const handleToggle = (abteilungId: number, typ: string, field: 'darfSehen' | 'darfScannen') => {
        setBerechtigungen(prev => prev.map(abt => {
            if (abt.abteilungId !== abteilungId) return abt;
            return {
                ...abt,
                berechtigungen: abt.berechtigungen.map(b => {
                    if (b.typ !== typ) return b;
                    const newValue = !b[field];
                    // Wenn darfScannen aktiviert wird, auch darfSehen aktivieren
                    if (field === 'darfScannen' && newValue) {
                        return { ...b, darfScannen: true, darfSehen: true };
                    }
                    // Wenn darfSehen deaktiviert wird, auch darfScannen deaktivieren
                    if (field === 'darfSehen' && !newValue) {
                        return { ...b, darfSehen: false, darfScannen: false };
                    }
                    return { ...b, [field]: newValue };
                })
            };
        }));
    };

    const handleToggleRechnungsFlag = (abteilungId: number, field: 'darfRechnungenGenehmigen' | 'darfRechnungenSehen') => {
        setBerechtigungen(prev => prev.map(abt => {
            if (abt.abteilungId !== abteilungId) return abt;
            return { ...abt, [field]: !abt[field] };
        }));
    };

    const handleSave = async (abteilung: AbteilungBerechtigung) => {
        setSaving(abteilung.abteilungId);
        try {
            const res = await fetch(`/api/abteilungen/${abteilung.abteilungId}/berechtigungen`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    berechtigungen: abteilung.berechtigungen,
                    darfRechnungenGenehmigen: abteilung.darfRechnungenGenehmigen,
                    darfRechnungenSehen: abteilung.darfRechnungenSehen
                })
            });
            if (res.ok) {
                setSaveSuccess(abteilung.abteilungId);
                setTimeout(() => setSaveSuccess(null), 2000);
            }
        } catch (err) {
            console.error('Fehler beim Speichern:', err);
        }
        setSaving(null);
    };

    if (loading) {
        return (
            <PageLayout>
                <div className="flex justify-center py-12">
                    <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                </div>
            </PageLayout>
        );
    }

    return (
        <PageLayout>
            {/* Header */}
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                        Administration
                    </p>
                    <h1 className="text-3xl font-bold text-slate-900">
                        LIEFERANTEN-DOKUMENTENRECHTE
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Berechtigungen für Dokumenttypen pro Abteilung
                    </p>
                </div>
            </div>

            {/* Info Card */}
            <Card className="mb-6 p-4 bg-blue-50 border-blue-200">
                <div className="flex items-start gap-3">
                    <Shield className="w-5 h-5 text-blue-600 mt-0.5" />
                    <div>
                        <p className="text-blue-900 font-medium">Berechtigungen</p>
                        <p className="text-blue-700 text-sm">
                            <strong>Sehen</strong>: Mitarbeiter kann diesen Dokumenttyp in der App sehen.<br />
                            <strong>Scannen</strong>: Mitarbeiter kann diesen Dokumenttyp hochladen. Setzt "Sehen" voraus.
                        </p>
                    </div>
                </div>
            </Card>

            {/* Berechtigungen per Abteilung */}
            <div className="space-y-6">
                {berechtigungen.map(abt => (
                    <Card key={abt.abteilungId} className="p-6">
                        <div className="flex items-center justify-between mb-4">
                            <div className="flex items-center gap-3">
                                <div className="w-10 h-10 bg-rose-100 rounded-lg flex items-center justify-center">
                                    <Users className="w-5 h-5 text-rose-600" />
                                </div>
                                <h2 className="text-xl font-bold text-slate-900">{abt.abteilungName}</h2>
                            </div>
                            <Button
                                onClick={() => handleSave(abt)}
                                disabled={saving === abt.abteilungId}
                                className="bg-rose-600 hover:bg-rose-700 text-white"
                            >
                                {saving === abt.abteilungId ? (
                                    <Loader2 className="w-4 h-4 animate-spin mr-2" />
                                ) : saveSuccess === abt.abteilungId ? (
                                    <Check className="w-4 h-4 mr-2" />
                                ) : (
                                    <Save className="w-4 h-4 mr-2" />
                                )}
                                {saveSuccess === abt.abteilungId ? 'Gespeichert' : 'Speichern'}
                            </Button>
                        </div>

                        {/* Matrix Table */}
                        <div className="overflow-x-auto">
                            <table className="w-full">
                                <thead>
                                    <tr className="border-b border-slate-200">
                                        <th className="text-left py-3 px-4 font-semibold text-slate-700">Dokumenttyp</th>
                                        <th className="text-center py-3 px-4 font-semibold text-slate-700 w-32">
                                            <div className="flex items-center justify-center gap-1">
                                                <Eye className="w-4 h-4" /> Sehen
                                            </div>
                                        </th>
                                        <th className="text-center py-3 px-4 font-semibold text-slate-700 w-32">
                                            <div className="flex items-center justify-center gap-1">
                                                <FileText className="w-4 h-4" /> Scannen
                                            </div>
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {abt.berechtigungen.map(b => (
                                        <tr key={b.typ} className="border-b border-slate-100 hover:bg-slate-50">
                                            <td className="py-3 px-4 font-medium text-slate-900">
                                                {DOKUMENT_TYP_LABELS[b.typ] || b.typ}
                                            </td>
                                            <td className="text-center py-3 px-4">
                                                <button
                                                    onClick={() => handleToggle(abt.abteilungId, b.typ, 'darfSehen')}
                                                    className={`w-6 h-6 rounded-md border-2 transition-colors ${
                                                        b.darfSehen
                                                            ? 'bg-green-500 border-green-500'
                                                            : 'bg-white border-slate-300 hover:border-slate-400'
                                                    }`}
                                                >
                                                    {b.darfSehen && <Check className="w-full h-full text-white p-0.5" />}
                                                </button>
                                            </td>
                                            <td className="text-center py-3 px-4">
                                                <button
                                                    onClick={() => handleToggle(abt.abteilungId, b.typ, 'darfScannen')}
                                                    className={`w-6 h-6 rounded-md border-2 transition-colors ${
                                                        b.darfScannen
                                                            ? 'bg-green-500 border-green-500'
                                                            : 'bg-white border-slate-300 hover:border-slate-400'
                                                    }`}
                                                >
                                                    {b.darfScannen && <Check className="w-full h-full text-white p-0.5" />}
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        {/* Offene Posten Berechtigungen */}
                        <div className="mt-6 pt-4 border-t border-slate-200">
                            <div className="flex items-center gap-2 mb-3">
                                <Wallet className="w-4 h-4 text-slate-600" />
                                <h3 className="font-semibold text-slate-700">Eingangsrechnungen (Offene Posten)</h3>
                            </div>
                            <div className="space-y-3">
                                <label className="flex items-center gap-3 cursor-pointer">
                                    <button
                                        onClick={() => handleToggleRechnungsFlag(abt.abteilungId, 'darfRechnungenGenehmigen')}
                                        className={`w-6 h-6 rounded-md border-2 transition-colors flex-shrink-0 ${
                                            abt.darfRechnungenGenehmigen
                                                ? 'bg-green-500 border-green-500'
                                                : 'bg-white border-slate-300 hover:border-slate-400'
                                        }`}
                                    >
                                        {abt.darfRechnungenGenehmigen && <Check className="w-full h-full text-white p-0.5" />}
                                    </button>
                                    <div>
                                        <span className="text-sm font-medium text-slate-900">Darf Rechnungen genehmigen</span>
                                        <p className="text-xs text-slate-500">Sieht alle Eingangsrechnungen und kann diese genehmigen</p>
                                    </div>
                                </label>
                                <label className="flex items-center gap-3 cursor-pointer">
                                    <button
                                        onClick={() => handleToggleRechnungsFlag(abt.abteilungId, 'darfRechnungenSehen')}
                                        className={`w-6 h-6 rounded-md border-2 transition-colors flex-shrink-0 ${
                                            abt.darfRechnungenSehen
                                                ? 'bg-green-500 border-green-500'
                                                : 'bg-white border-slate-300 hover:border-slate-400'
                                        }`}
                                    >
                                        {abt.darfRechnungenSehen && <Check className="w-full h-full text-white p-0.5" />}
                                    </button>
                                    <div>
                                        <span className="text-sm font-medium text-slate-900">Darf genehmigte Rechnungen sehen</span>
                                        <p className="text-xs text-slate-500">Sieht nur bereits genehmigte Eingangsrechnungen (Buchhaltung)</p>
                                    </div>
                                </label>
                            </div>
                        </div>
                    </Card>
                ))}

                {berechtigungen.length === 0 && (
                    <Card className="p-12 text-center">
                        <Users className="w-12 h-12 mx-auto text-slate-300 mb-4" />
                        <p className="text-slate-500">Keine Abteilungen vorhanden.</p>
                        <p className="text-slate-400 text-sm mt-1">
                            Erstellen Sie zuerst Abteilungen im Mitarbeiter-Editor.
                        </p>
                    </Card>
                )}
            </div>
        </PageLayout>
    );
}

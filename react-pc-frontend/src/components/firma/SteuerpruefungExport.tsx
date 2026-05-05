import { useState, useEffect, useCallback } from 'react';
import { Card } from '../ui/card';
import { Button } from '../ui/button';
import { Label } from '../ui/label';
import { DatePicker } from '../ui/datepicker';
import { Download, FileSpreadsheet, ShieldCheck, RefreshCw, AlertTriangle } from 'lucide-react';
import { useToast } from '../ui/toast';

/**
 * Steuerprüfungs-Export für Ausgangs-Geschäftsdokumente.
 * Erlaubt einem Steuerprüfer den Audit-Trail über einen Zeitraum als CSV
 * herunterzuladen (GoBD Z3-Datenträgerüberlassung).
 */
export function SteuerpruefungExport() {
    const toast = useToast();
    const heute = new Date();
    const jahresAnfang = new Date(heute.getFullYear(), 0, 1).toISOString().split('T')[0];
    const heuteIso = heute.toISOString().split('T')[0];

    const [von, setVon] = useState(jahresAnfang);
    const [bis, setBis] = useState(heuteIso);
    const [anzahl, setAnzahl] = useState<number | null>(null);
    const [loading, setLoading] = useState(false);
    const [downloading, setDownloading] = useState(false);

    const refreshAnzahl = useCallback(async () => {
        if (!von || !bis) return;
        setLoading(true);
        try {
            const params = new URLSearchParams({ von, bis });
            const res = await fetch(`/api/ausgangs-dokumente/audit/anzahl?${params.toString()}`);
            if (res.ok) {
                setAnzahl(await res.json());
            } else {
                setAnzahl(null);
            }
        } catch (e) {
            console.error(e);
            setAnzahl(null);
        } finally {
            setLoading(false);
        }
    }, [von, bis]);

    useEffect(() => {
        refreshAnzahl();
    }, [refreshAnzahl]);

    const handleDownload = async () => {
        if (!von || !bis) return;
        setDownloading(true);
        try {
            const params = new URLSearchParams({ von, bis });
            const res = await fetch(`/api/ausgangs-dokumente/audit/export?${params.toString()}`);
            if (!res.ok) {
                toast.error('Export fehlgeschlagen');
                return;
            }
            const blob = await res.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `audit_ausgangsdokumente_${von}_bis_${bis}.csv`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
            toast.success('CSV-Export heruntergeladen');
        } catch (e) {
            console.error(e);
            toast.error('Fehler beim Download');
        } finally {
            setDownloading(false);
        }
    };

    const setSchnellzeitraum = (jahr: number) => {
        setVon(`${jahr}-01-01`);
        setBis(`${jahr}-12-31`);
    };

    const aktuellesJahr = heute.getFullYear();

    return (
        <div className="space-y-6">
            <Card className="p-6">
                <div className="flex items-start gap-3 mb-6">
                    <div className="bg-rose-50 p-3 rounded-lg">
                        <ShieldCheck className="w-6 h-6 text-rose-600" />
                    </div>
                    <div>
                        <h3 className="text-lg font-semibold text-slate-900">
                            Audit-Trail für Steuerprüfung
                        </h3>
                        <p className="text-sm text-slate-500 mt-1">
                            GoBD-konformer Export aller Aktionen an Ausgangs-Geschäftsdokumenten
                            (Erstellt, Geändert, Gebucht, Versendet, Storniert, Gelöscht) inkl.
                            Begründung, Bearbeiter und Zeitstempel als CSV.
                        </p>
                    </div>
                </div>

                <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 mb-6 flex gap-2">
                    <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" />
                    <div className="text-xs text-amber-800">
                        <strong>Hinweis:</strong> Diese Datei enthält personenbezogene Daten
                        (Bearbeiter-IDs, IP-Adressen). Sie nur an autorisierte Prüfer weitergeben
                        und nach Abschluss der Prüfung sicher löschen.
                    </div>
                </div>

                <div className="space-y-4">
                    <div>
                        <Label className="text-sm font-medium text-slate-700 mb-2 block">Schnellauswahl</Label>
                        <div className="flex flex-wrap gap-2">
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setSchnellzeitraum(aktuellesJahr)}
                                className="border-slate-300"
                            >
                                {aktuellesJahr}
                            </Button>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setSchnellzeitraum(aktuellesJahr - 1)}
                                className="border-slate-300"
                            >
                                {aktuellesJahr - 1}
                            </Button>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setSchnellzeitraum(aktuellesJahr - 2)}
                                className="border-slate-300"
                            >
                                {aktuellesJahr - 2}
                            </Button>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <Label className="text-sm font-medium text-slate-700">Von</Label>
                            <DatePicker value={von} onChange={setVon} />
                        </div>
                        <div>
                            <Label className="text-sm font-medium text-slate-700">Bis</Label>
                            <DatePicker value={bis} onChange={setBis} />
                        </div>
                    </div>

                    <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <FileSpreadsheet className="w-5 h-5 text-slate-500" />
                            <div>
                                <p className="text-sm font-medium text-slate-900">
                                    {loading ? (
                                        <span className="flex items-center gap-2 text-slate-500">
                                            <RefreshCw className="w-3 h-3 animate-spin" />
                                            Zähle Einträge...
                                        </span>
                                    ) : anzahl !== null ? (
                                        <>{anzahl.toLocaleString('de-DE')} Audit-Einträge</>
                                    ) : (
                                        '–'
                                    )}
                                </p>
                                <p className="text-xs text-slate-500">im gewählten Zeitraum</p>
                            </div>
                        </div>
                        <Button
                            onClick={handleDownload}
                            disabled={downloading || !von || !bis || anzahl === 0}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {downloading ? (
                                <RefreshCw className="w-4 h-4 mr-2 animate-spin" />
                            ) : (
                                <Download className="w-4 h-4 mr-2" />
                            )}
                            CSV herunterladen
                        </Button>
                    </div>
                </div>
            </Card>

            <Card className="p-6">
                <h4 className="font-semibold text-slate-900 mb-3">Was ist im Export enthalten?</h4>
                <ul className="space-y-2 text-sm text-slate-600">
                    <li className="flex gap-2">
                        <span className="text-rose-600">•</span>
                        Zeitpunkt jeder Aktion (auf die Sekunde genau)
                    </li>
                    <li className="flex gap-2">
                        <span className="text-rose-600">•</span>
                        Aktion (Erstellt, Geändert, Gebucht, Versendet, Storniert, Gelöscht)
                    </li>
                    <li className="flex gap-2">
                        <span className="text-rose-600">•</span>
                        Dokumentnummer, Typ und Beträge (Netto/Brutto) zum Zeitpunkt der Aktion
                    </li>
                    <li className="flex gap-2">
                        <span className="text-rose-600">•</span>
                        Bearbeiter-ID und IP-Adresse
                    </li>
                    <li className="flex gap-2">
                        <span className="text-rose-600">•</span>
                        Pflicht-Begründung bei Löschung und Änderung
                    </li>
                    <li className="flex gap-2">
                        <span className="text-rose-600">•</span>
                        SHA-256-Fingerabdruck des Dokumentinhalts (zur Manipulationserkennung)
                    </li>
                </ul>
            </Card>
        </div>
    );
}

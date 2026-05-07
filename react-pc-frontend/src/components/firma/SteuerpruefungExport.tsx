import { useState, useEffect, useCallback } from 'react';
import { Card } from '../ui/card';
import { Button } from '../ui/button';
import { Label } from '../ui/label';
import { DatePicker } from '../ui/datepicker';
import {
    Download, FileSpreadsheet, ShieldCheck, RefreshCw, AlertTriangle,
    CheckCircle2, XCircle, Package, Link2,
} from 'lucide-react';
import { useToast } from '../ui/toast';

interface VerifyFehler {
    auditId: number;
    chainIndex: number | null;
    dokumentNummer: string;
    typ: string;
    beschreibung: string;
}

interface VerifyBericht {
    intakt: boolean;
    gesamtAnzahl: number;
    letzterChainIndex: number | null;
    letzterEntryHash: string | null;
    fehler: VerifyFehler[];
}

/**
 * Steuerprüfungs-Export für Ausgangs-Geschäftsdokumente.
 *
 * Drei Werkzeuge in einem Panel:
 *  1. CSV-Export des Audit-Logs (mit Hash-Kette).
 *  2. Z3-Paket (ZIP) für GoBD-Datenträgerüberlassung — komplett mit INFO.txt.
 *  3. Verifikation der Hash-Kette: zeigt sofort, ob jemand die Datenbank manipuliert hat.
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
    const [downloadingZip, setDownloadingZip] = useState(false);
    const [verifying, setVerifying] = useState(false);
    const [verifyBericht, setVerifyBericht] = useState<VerifyBericht | null>(null);

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

    const handleDownloadCsv = async () => {
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

    const handleDownloadZip = async () => {
        if (!von || !bis) return;
        setDownloadingZip(true);
        try {
            const params = new URLSearchParams({ von, bis });
            const res = await fetch(`/api/ausgangs-dokumente/audit/z3-paket?${params.toString()}`);
            if (!res.ok) {
                toast.error('Z3-Paket-Erstellung fehlgeschlagen');
                return;
            }
            const blob = await res.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `steuerpruefung_${von}_bis_${bis}.zip`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
            toast.success('Z3-Paket heruntergeladen');
        } catch (e) {
            console.error(e);
            toast.error('Fehler beim Download');
        } finally {
            setDownloadingZip(false);
        }
    };

    const handleVerify = async () => {
        setVerifying(true);
        setVerifyBericht(null);
        try {
            const res = await fetch('/api/ausgangs-dokumente/audit/verify');
            if (!res.ok) {
                toast.error('Verifikation fehlgeschlagen');
                return;
            }
            const bericht: VerifyBericht = await res.json();
            setVerifyBericht(bericht);
            if (bericht.intakt) {
                toast.success(`Hash-Kette intakt – ${bericht.gesamtAnzahl} Einträge geprüft`);
            } else {
                toast.error('Hash-Kette gebrochen! Details siehe Bericht.');
            }
        } catch (e) {
            console.error(e);
            toast.error('Fehler bei der Verifikation');
        } finally {
            setVerifying(false);
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
                            Begründung, Bearbeiter, Zeitstempel und manipulationssicherer Hash-Kette.
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

                    <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 flex items-center justify-between gap-3 flex-wrap">
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
                        <div className="flex flex-wrap gap-2">
                            <Button
                                onClick={handleDownloadCsv}
                                disabled={downloading || !von || !bis || anzahl === 0}
                                variant="outline"
                                className="border-slate-300"
                            >
                                {downloading ? (
                                    <RefreshCw className="w-4 h-4 mr-2 animate-spin" />
                                ) : (
                                    <Download className="w-4 h-4 mr-2" />
                                )}
                                Nur CSV
                            </Button>
                            <Button
                                onClick={handleDownloadZip}
                                disabled={downloadingZip || !von || !bis}
                                className="bg-rose-600 text-white hover:bg-rose-700"
                            >
                                {downloadingZip ? (
                                    <RefreshCw className="w-4 h-4 mr-2 animate-spin" />
                                ) : (
                                    <Package className="w-4 h-4 mr-2" />
                                )}
                                Z3-Prüfungs-Paket (ZIP)
                            </Button>
                        </div>
                    </div>
                </div>
            </Card>

            <Card className="p-6">
                <div className="flex items-start gap-3 mb-4">
                    <div className="bg-emerald-50 p-3 rounded-lg">
                        <Link2 className="w-6 h-6 text-emerald-600" />
                    </div>
                    <div className="flex-1">
                        <h3 className="text-lg font-semibold text-slate-900">
                            Hash-Kette verifizieren
                        </h3>
                        <p className="text-sm text-slate-500 mt-1">
                            Prüft, ob seit Inbetriebnahme jemand am Audit-Log oder den Buchungen
                            manipuliert hat. Manipulation bricht die Hash-Kette und wird sofort
                            angezeigt.
                        </p>
                    </div>
                    <Button
                        onClick={handleVerify}
                        disabled={verifying}
                        variant="outline"
                        className="border-emerald-300 text-emerald-700 hover:bg-emerald-50"
                    >
                        {verifying ? (
                            <RefreshCw className="w-4 h-4 mr-2 animate-spin" />
                        ) : (
                            <ShieldCheck className="w-4 h-4 mr-2" />
                        )}
                        Kette prüfen
                    </Button>
                </div>

                {verifyBericht && (
                    <div
                        className={`rounded-lg border p-4 ${
                            verifyBericht.intakt
                                ? 'bg-emerald-50 border-emerald-200'
                                : 'bg-rose-50 border-rose-200'
                        }`}
                    >
                        <div className="flex items-start gap-2">
                            {verifyBericht.intakt ? (
                                <CheckCircle2 className="w-5 h-5 text-emerald-600 flex-shrink-0 mt-0.5" />
                            ) : (
                                <XCircle className="w-5 h-5 text-rose-600 flex-shrink-0 mt-0.5" />
                            )}
                            <div className="flex-1">
                                <p
                                    className={`text-sm font-semibold ${
                                        verifyBericht.intakt ? 'text-emerald-800' : 'text-rose-800'
                                    }`}
                                >
                                    {verifyBericht.intakt
                                        ? `Hash-Kette intakt – ${verifyBericht.gesamtAnzahl.toLocaleString('de-DE')} Einträge geprüft`
                                        : 'Hash-Kette gebrochen – Manipulation nachweisbar'}
                                </p>
                                {verifyBericht.intakt && verifyBericht.letzterEntryHash && (
                                    <p className="text-xs text-emerald-700 mt-1 font-mono break-all">
                                        Kettenkopf (chain_index {verifyBericht.letzterChainIndex}):
                                        <br />
                                        {verifyBericht.letzterEntryHash}
                                    </p>
                                )}
                                {!verifyBericht.intakt && verifyBericht.fehler.length > 0 && (
                                    <ul className="text-xs text-rose-700 mt-2 space-y-1">
                                        {verifyBericht.fehler.map((f, i) => (
                                            <li key={i}>
                                                <strong>chain_index {f.chainIndex ?? '–'}</strong>{' '}
                                                (Dokument {f.dokumentNummer}): {f.beschreibung}
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>
                        </div>
                    </div>
                )}
            </Card>

            <Card className="p-6">
                <h4 className="font-semibold text-slate-900 mb-3">Was ist im Z3-Paket enthalten?</h4>
                <ul className="space-y-2 text-sm text-slate-600">
                    <li className="flex gap-2">
                        <span className="text-rose-600">•</span>
                        <span>
                            <strong>dokumente.csv</strong> – alle Ausgangsdokumente im Zeitraum
                            (Rechnungen, Angebote, Storno usw.) mit Beträgen und Status.
                        </span>
                    </li>
                    <li className="flex gap-2">
                        <span className="text-rose-600">•</span>
                        <span>
                            <strong>audit.csv</strong> – kompletter Audit-Trail inkl. Hash-Kette
                            (chain_index, previous_hash, entry_hash). Jede Zeile per SHA-256
                            verifizierbar.
                        </span>
                    </li>
                    <li className="flex gap-2">
                        <span className="text-rose-600">•</span>
                        <span>
                            <strong>INFO.txt</strong> – Anleitung für den Prüfer: was ist enthalten,
                            wie wird die Kette geprüft, Status zum Zeitpunkt des Exports.
                        </span>
                    </li>
                    <li className="flex gap-2">
                        <span className="text-rose-600">•</span>
                        <span>
                            <strong>manifest.sha256</strong> – SHA-256 jeder Datei. Zeigt, dass
                            das ZIP unterwegs nicht verändert wurde.
                        </span>
                    </li>
                </ul>
            </Card>
        </div>
    );
}

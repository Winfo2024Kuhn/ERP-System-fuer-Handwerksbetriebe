import { useState, useEffect } from 'react';
import { PdfCanvasViewer } from './ui/PdfCanvasViewer';
import { Button } from './ui/button';
import { RefreshCw, FileText, X, Download, Plus, Trash2, Percent, Euro, Save, Briefcase, Package } from 'lucide-react';
import { ProjectSelectModal } from './ProjectSelectModal';
import { KostenstelleSelectModal } from './KostenstelleSelectModal';
import { useToast } from './ui/toast';

// ========== Types ==========
interface GeschaeftsdatenDto {
    id: number;
    dokumentNummer: string | null;
    dokumentDatum: string | null;
    betragNetto: number | null;
    betragBrutto: number | null;
    mwstSatz: number | null;
    liefertermin: string | null;
    bestellnummer: string | null;
    lieferantId: number | null;
    lieferantName: string | null;
}

interface ProjektAnteil {
    projektId?: number;
    projektName?: string;
    kostenstelleId?: number;
    kostenstelleName?: string;
    betrag: number;
    prozentanteil: number;
    beschreibung: string;
}

// ========== Helpers ==========
const formatDate = (isoText: string | null): string => {
    if (!isoText) return '–';
    const date = new Date(isoText);
    return Number.isNaN(date.getTime()) ? '–' : date.toLocaleDateString('de-DE');
};

const formatEuro = (value: number | null): string => {
    if (value == null || !Number.isFinite(value)) return '–';
    return value.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};

// ========== Props ==========
export interface ZuordnungModalProps {
    geschaeftsdokumentId: number;
    dokumentNummer?: string | null;
    lieferantName?: string | null;
    pdfUrl?: string | null;
    onClose: () => void;
    onSuccess: () => void;
}

export function ZuordnungModal({ geschaeftsdokumentId, dokumentNummer, lieferantName, pdfUrl, onClose, onSuccess }: ZuordnungModalProps) {
    const toast = useToast();
    const [geschaeftsdaten, setGeschaeftsdaten] = useState<GeschaeftsdatenDto | null>(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [modus, setModus] = useState<'prozent' | 'absolut'>('prozent');
    const [anteile, setAnteile] = useState<ProjektAnteil[]>([]);
    const [showProjectModal, setShowProjectModal] = useState(false);
    const [showKostenstelleModal, setShowKostenstelleModal] = useState(false);

    // Geschäftsdaten + bestehende Zuordnungen laden
    useEffect(() => {
        setLoading(true);
        Promise.all([
            fetch(`/api/bestellungen-uebersicht/geschaeftsdaten/${geschaeftsdokumentId}`),
            fetch(`/api/bestellungen-uebersicht/zuordnungen/${geschaeftsdokumentId}`),
        ])
            .then(async ([gdRes, zuordRes]) => {
                let nettoFuerBerechnung = 0;
                if (gdRes.ok) {
                    const gd = await gdRes.json();
                    setGeschaeftsdaten(gd);
                    nettoFuerBerechnung = gd.betragNetto ?? 0;
                }
                if (zuordRes.ok) {
                    const zuordnungen = await zuordRes.json();
                    setAnteile(zuordnungen.map((z: { projektId?: number; projektName?: string; kostenstelleId?: number; kostenstelleName?: string; betrag?: number; prozentanteil?: number; beschreibung?: string }) => {
                        const pct = z.prozentanteil || 0;
                        // Betrag immer aus prozentanteil × betragNetto berechnen – vermeidet brutto-basierte Altdaten
                        const betrag = nettoFuerBerechnung > 0
                            ? Number((nettoFuerBerechnung * pct / 100).toFixed(2))
                            : (z.betrag || 0);
                        return {
                            projektId: z.projektId,
                            projektName: z.projektName,
                            kostenstelleId: z.kostenstelleId,
                            kostenstelleName: z.kostenstelleName,
                            betrag,
                            prozentanteil: pct,
                            beschreibung: z.beschreibung || '',
                        };
                    }));
                }
            })
            .catch(() => toast.error('Fehler beim Laden der Zuordnungsdaten'))
            .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [geschaeftsdokumentId]);

    const betragNetto = geschaeftsdaten?.betragNetto || 0;

    // Projekt hinzufügen (vom Modal aufgerufen)
    const addProjekt = (p: { id: number; bauvorhaben: string }) => {
        if (anteile.find(a => a.projektId === p.id)) {
            setShowProjectModal(false);
            return;
        }
        const defaultAnteil = anteile.length === 0 ? 100 : 0;
        setAnteile([...anteile, {
            projektId: p.id,
            projektName: p.bauvorhaben,
            prozentanteil: defaultAnteil,
            betrag: modus === 'prozent' ? (betragNetto * defaultAnteil / 100) : 0,
            beschreibung: ''
        }]);
        setShowProjectModal(false);
    };

    // Kostenstelle hinzufügen (vom Modal aufgerufen)
    const addKostenstelle = (k: { id: number; bezeichnung: string }) => {
        if (anteile.find(a => a.kostenstelleId === k.id)) {
            setShowKostenstelleModal(false);
            return;
        }
        const defaultAnteil = anteile.length === 0 ? 100 : 0;
        setAnteile([...anteile, {
            kostenstelleId: k.id,
            kostenstelleName: k.bezeichnung,
            prozentanteil: defaultAnteil,
            betrag: modus === 'prozent' ? (betragNetto * defaultAnteil / 100) : 0,
            beschreibung: ''
        }]);
        setShowKostenstelleModal(false);
    };

    // Anteil aktualisieren (nur für nicht-letzte Einträge)
    const updateAnteil = (idx: number, field: keyof ProjektAnteil, value: number | string) => {
        setAnteile(prev => prev.map((a, i) => {
            if (i !== idx) return a;
            const newA = { ...a, [field]: value };
            if (modus === 'prozent' && field === 'prozentanteil') {
                newA.betrag = Number(((betragNetto * Number(value)) / 100).toFixed(2));
            } else if (modus === 'absolut' && field === 'betrag') {
                newA.prozentanteil = betragNetto > 0 ? Number(((Number(value) / betragNetto) * 100).toFixed(2)) : 0;
            }
            return newA;
        }));
    };

    // Berechne die Werte für den letzten Eintrag (Differenz zu 100% bzw. Gesamtbetrag)
    const berechneLetzenAnteil = () => {
        if (anteile.length < 2) return null;
        const lastIdx = anteile.length - 1;

        // Summe aller Einträge AUSSER dem letzten
        const sumProzentOhneLetzen = anteile.slice(0, lastIdx).reduce((s, a) => s + a.prozentanteil, 0);
        const sumBetragOhneLetzen = anteile.slice(0, lastIdx).reduce((s, a) => s + a.betrag, 0);

        const restProzent = Number((100 - sumProzentOhneLetzen).toFixed(2));
        const restBetrag = Number((betragNetto - sumBetragOhneLetzen).toFixed(2));

        return { restProzent, restBetrag };
    };

    const letzterAnteilBerechnet = berechneLetzenAnteil();

    // Entfernen
    const removeAnteil = (idx: number) => {
        setAnteile(prev => prev.filter((_, i) => i !== idx));
    };

    // Berechne Summen – letzten Eintrag mit effektiv berechnetem Wert (nicht gespeichertem) addieren
    const sumBetrag = anteile.reduce((s, a, idx) => {
        const isLast = idx === anteile.length - 1 && anteile.length >= 2;
        return s + (isLast && letzterAnteilBerechnet ? letzterAnteilBerechnet.restBetrag : a.betrag);
    }, 0);
    const sumProzent = anteile.reduce((s, a, idx) => {
        const isLast = idx === anteile.length - 1 && anteile.length >= 2;
        return s + (isLast && letzterAnteilBerechnet ? letzterAnteilBerechnet.restProzent : a.prozentanteil);
    }, 0);
    const rest = Number((betragNetto - sumBetrag).toFixed(2));

    // Speichern - mit berechneten Werten für den letzten Eintrag
    const speichern = async () => {
        if (anteile.length === 0) return;
        setSaving(true);
        try {
            // Für den letzten Eintrag die berechneten Werte verwenden
            const anteileZumSpeichern = anteile.map((a, idx) => {
                const isLast = idx === anteile.length - 1 && anteile.length >= 2;
                if (isLast && letzterAnteilBerechnet) {
                    return {
                        projektId: a.projektId,
                        kostenstelleId: a.kostenstelleId,
                        betrag: letzterAnteilBerechnet.restBetrag,
                        prozentanteil: letzterAnteilBerechnet.restProzent,
                        beschreibung: a.beschreibung
                    };
                }
                return {
                    projektId: a.projektId,
                    kostenstelleId: a.kostenstelleId,
                    betrag: a.betrag,
                    prozentanteil: a.prozentanteil,
                    beschreibung: a.beschreibung
                };
            });

            const res = await fetch('/api/bestellungen-uebersicht/zuordnen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    geschaeftsdokumentId,
                    frontendUserProfileId: (() => {
                        try {
                            const stored = localStorage.getItem('frontendUserSelection');
                            if (stored) {
                                const parsed = JSON.parse(stored);
                                return parsed.id || null;
                            }
                        } catch { /* ignore */ }
                        return null;
                    })(),
                    projektAnteile: anteileZumSpeichern
                })
            });
            if (res.ok) {
                onSuccess();
                onClose();
            } else {
                toast.error('Fehler beim Speichern');
            }
        } catch {
            toast.error('Netzwerkfehler');
        } finally {
            setSaving(false);
        }
    };

    // ESC Handler
    useEffect(() => {
        const handleEsc = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleEsc);
        return () => window.removeEventListener('keydown', handleEsc);
    }, [onClose]);

    // Display values: prefer loaded geschaeftsdaten, fallback to props
    const displayDokNr = geschaeftsdaten?.dokumentNummer || dokumentNummer || '–';
    const displayLieferant = geschaeftsdaten?.lieferantName || lieferantName;
    const displayPdfUrl = pdfUrl;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm">
            <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-6xl mx-4 max-h-[90vh] overflow-hidden flex flex-col" onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-rose-50">
                    <div>
                        <h3 className="text-lg font-semibold text-slate-900">
                            Rechnung {displayDokNr}
                        </h3>
                        <p className="text-sm text-slate-500">{displayLieferant}</p>
                    </div>
                    <button onClick={onClose} className="p-2 rounded-lg hover:bg-rose-100 transition">
                        <X className="w-5 h-5 text-slate-500" />
                    </button>
                </div>

                {/* Content - Split Layout */}
                <div className="flex-1 overflow-hidden flex">
                    {/* Left: PDF Preview */}
                    <div className="w-1/2 border-r border-slate-200 bg-slate-100 flex flex-col">
                        <div className="px-4 py-2 bg-slate-200 border-b border-slate-300 flex items-center justify-between">
                            <span className="text-sm font-medium text-slate-700">PDF-Vorschau</span>
                            {displayPdfUrl && (
                                <a
                                    href={displayPdfUrl}
                                    download
                                    className="text-xs px-2 py-1 bg-white rounded border border-slate-300 text-slate-600 hover:bg-slate-50 flex items-center gap-1"
                                >
                                    <Download className="w-3 h-3" />
                                    Download
                                </a>
                            )}
                        </div>
                        <div className="flex-1 overflow-auto p-4">
                            {displayPdfUrl ? (
                                <PdfCanvasViewer url={displayPdfUrl} className="w-full h-full min-h-[500px] rounded-lg overflow-y-auto overflow-x-hidden" />
                            ) : (
                                <div className="flex flex-col items-center justify-center h-full text-slate-400">
                                    <FileText className="w-12 h-12 mb-3" />
                                    <p>Keine PDF-Vorschau verfügbar</p>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Right: Assignment Form */}
                    <div className="w-1/2 overflow-auto p-6 space-y-6">
                        {loading ? (
                            <div className="flex items-center justify-center py-12">
                                <RefreshCw className="w-8 h-8 animate-spin text-slate-400" />
                            </div>
                        ) : (
                            <>
                                {/* Metadaten */}
                                <div className="bg-slate-50 rounded-xl p-4">
                                    <h4 className="text-sm font-semibold text-slate-700 mb-3">Rechnungsdaten</h4>
                                    <div className="grid grid-cols-2 gap-4 text-sm">
                                        <div>
                                            <span className="text-slate-500">Rechnungsnr.:</span>
                                            <p className="font-medium">{geschaeftsdaten?.dokumentNummer || '–'}</p>
                                        </div>
                                        <div>
                                            <span className="text-slate-500">Datum:</span>
                                            <p className="font-medium">{formatDate(geschaeftsdaten?.dokumentDatum || null)}</p>
                                        </div>
                                        <div>
                                            <span className="text-slate-500">Netto:</span>
                                            <p className="font-medium text-rose-600">{formatEuro(geschaeftsdaten?.betragNetto || null)} €</p>
                                        </div>
                                        <div>
                                            <span className="text-slate-500">Brutto:</span>
                                            <p className="font-medium">{formatEuro(geschaeftsdaten?.betragBrutto || null)} €</p>
                                        </div>
                                    </div>
                                </div>

                                {/* Modus-Toggle */}
                                <div className="flex items-center gap-4">
                                    <span className="text-sm font-medium text-slate-700">Verteilungsmodus:</span>
                                    <div className="flex bg-slate-100 rounded-lg p-1">
                                        <button
                                            onClick={() => setModus('prozent')}
                                            className={`flex items-center gap-1 px-3 py-1.5 rounded-md text-sm font-medium transition ${modus === 'prozent' ? 'bg-white shadow text-rose-600' : 'text-slate-500'}`}
                                        >
                                            <Percent className="w-4 h-4" />
                                            Prozentual
                                        </button>
                                        <button
                                            onClick={() => setModus('absolut')}
                                            className={`flex items-center gap-1 px-3 py-1.5 rounded-md text-sm font-medium transition ${modus === 'absolut' ? 'bg-white shadow text-rose-600' : 'text-slate-500'}`}
                                        >
                                            <Euro className="w-4 h-4" />
                                            Absolut
                                        </button>
                                    </div>
                                </div>

                                {/* Projekt-Suche & Kostenstelle Buttons */}
                                <div className="flex gap-2">
                                    <Button
                                        variant="outline"
                                        onClick={() => setShowProjectModal(true)}
                                        className="flex-1 justify-start gap-2 text-slate-600 hover:text-rose-700 hover:border-rose-300"
                                    >
                                        <Briefcase className="w-4 h-4" />
                                        Projekt hinzufügen...
                                    </Button>

                                    <Button
                                        variant="outline"
                                        onClick={() => setShowKostenstelleModal(true)}
                                        className="flex-1 justify-start gap-2 text-slate-600 hover:text-rose-700 hover:border-rose-300"
                                    >
                                        <Package className="w-4 h-4" />
                                        Kostenstelle...
                                    </Button>
                                </div>

                                {/* ProjectSelectModal */}
                                {showProjectModal && (
                                    <ProjectSelectModal
                                        onSelect={addProjekt}
                                        onClose={() => setShowProjectModal(false)}
                                    />
                                )}

                                {/* KostenstelleSelectModal */}
                                {showKostenstelleModal && (
                                    <KostenstelleSelectModal
                                        onSelect={addKostenstelle}
                                        onClose={() => setShowKostenstelleModal(false)}
                                    />
                                )}

                                {/* Zuordnungsliste */}
                                {anteile.length === 0 ? (
                                    <div className="text-center py-8 text-slate-400 border-2 border-dashed rounded-xl">
                                        <Plus className="w-8 h-8 mx-auto mb-2" />
                                        <p>Klicken Sie oben, um Projekte zuzuordnen</p>
                                    </div>
                                ) : (
                                    <div className="space-y-3">
                                        {anteile.map((a, idx) => {
                                            const isLast = idx === anteile.length - 1 && anteile.length >= 2;
                                            // Für den letzten Eintrag: berechnete Werte verwenden
                                            const displayProzent = isLast && letzterAnteilBerechnet ? letzterAnteilBerechnet.restProzent : a.prozentanteil;
                                            const displayBetrag = isLast && letzterAnteilBerechnet ? letzterAnteilBerechnet.restBetrag : a.betrag;

                                            return (
                                                <div key={idx} className={`rounded-xl p-4 border ${isLast ? 'bg-rose-50 border-rose-200' : 'bg-slate-50 border-slate-200'}`}>
                                                    <div className="flex items-start justify-between mb-3">
                                                        <div>
                                                            <p className="font-medium text-slate-900">
                                                                {a.projektName || a.kostenstelleName}
                                                            </p>
                                                            <div className="flex gap-1 mt-0.5">
                                                                {a.kostenstelleId && (
                                                                    <span className="text-xs bg-slate-200 text-slate-600 px-1.5 py-0.5 rounded">
                                                                        Kostenstelle
                                                                    </span>
                                                                )}
                                                                {isLast && (
                                                                    <span className="text-xs bg-rose-200 text-rose-700 px-1.5 py-0.5 rounded">
                                                                        Automatisch (Rest)
                                                                    </span>
                                                                )}
                                                            </div>
                                                        </div>
                                                        <button onClick={() => removeAnteil(idx)} className="text-slate-400 hover:text-red-500">
                                                            <Trash2 className="w-4 h-4" />
                                                        </button>
                                                    </div>
                                                    <div className="grid grid-cols-3 gap-3">
                                                        <div>
                                                            <label className="text-xs text-slate-500">{modus === 'prozent' ? 'Anteil %' : 'Betrag €'}</label>
                                                            {isLast ? (
                                                                <p className={`px-3 py-1.5 rounded-lg text-sm font-medium ${displayProzent < 0 || displayBetrag < 0 ? 'bg-red-100 text-red-700' : 'bg-rose-100 text-rose-700'}`}>
                                                                    {modus === 'prozent' ? displayProzent.toFixed(2) : formatEuro(displayBetrag)}
                                                                </p>
                                                            ) : (
                                                                <input
                                                                    type="number"
                                                                    value={modus === 'prozent' ? a.prozentanteil : a.betrag}
                                                                    onChange={e => updateAnteil(idx, modus === 'prozent' ? 'prozentanteil' : 'betrag', Number(e.target.value))}
                                                                    className="w-full px-3 py-1.5 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-rose-500"
                                                                />
                                                            )}
                                                        </div>
                                                        <div>
                                                            <label className="text-xs text-slate-500">= {modus === 'prozent' ? 'Betrag' : 'Prozent'}</label>
                                                            <p className={`px-3 py-1.5 rounded-lg text-sm font-medium ${isLast ? 'bg-rose-100' : 'bg-slate-100'}`}>
                                                                {modus === 'prozent' ? `${formatEuro(displayBetrag)} €` : `${displayProzent.toFixed(1)} %`}
                                                            </p>
                                                        </div>
                                                        <div>
                                                            <label className="text-xs text-slate-500">Beschreibung</label>
                                                            <input
                                                                type="text"
                                                                value={a.beschreibung}
                                                                onChange={e => updateAnteil(idx, 'beschreibung', e.target.value)}
                                                                placeholder="z.B. Dachlatten"
                                                                className="w-full px-3 py-1.5 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-rose-500"
                                                            />
                                                        </div>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                )}

                                {/* Zusammenfassung */}
                                {anteile.length > 0 && (
                                    <div className={`rounded-xl p-4 ${Math.abs(rest) < 0.01 ? 'bg-green-50 border border-green-200' : 'bg-amber-50 border border-amber-200'}`}>
                                        <div className="flex justify-between text-sm">
                                            <span>Zugeordnet:</span>
                                            <span className="font-medium">{formatEuro(sumBetrag)} € ({sumProzent.toFixed(1)} %)</span>
                                        </div>
                                        <div className="flex justify-between text-sm mt-1">
                                            <span>Gesamt:</span>
                                            <span className="font-medium">{formatEuro(geschaeftsdaten?.betragNetto || null)} €</span>
                                        </div>
                                        <div className="flex justify-between text-sm mt-1 font-semibold">
                                            <span>Rest:</span>
                                            <span className={Math.abs(rest) < 0.01 ? 'text-green-600' : 'text-amber-600'}>
                                                {formatEuro(rest)} €
                                            </span>
                                        </div>
                                    </div>
                                )}
                            </>
                        )}
                    </div>
                </div>

                {/* Footer */}
                <div className="flex justify-end gap-3 px-6 py-4 border-t border-slate-200 bg-slate-50">
                    <Button variant="outline" onClick={onClose}>Abbrechen</Button>
                    <Button
                        onClick={speichern}
                        disabled={saving || anteile.length === 0}
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                    >
                        {saving ? <RefreshCw className="w-4 h-4 animate-spin mr-2" /> : <Save className="w-4 h-4 mr-2" />}
                        Zuordnen & Abschließen
                    </Button>
                </div>
            </div>
        </div>
    );
}

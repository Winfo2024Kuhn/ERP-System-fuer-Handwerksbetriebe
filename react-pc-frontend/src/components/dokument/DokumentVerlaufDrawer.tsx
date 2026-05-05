import { useState, useEffect } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../ui/dialog';
import { History, FileText, Edit2, Lock, Send, RotateCcw, Trash2, CheckCircle2, RefreshCw, Copy } from 'lucide-react';
import { useToast } from '../ui/toast';

export interface DokumentAuditEintrag {
    id: number;
    aktion: string;
    dokumentId: number;
    dokumentNummer: string;
    typ: string;
    betragNetto: number | null;
    betragBrutto: number | null;
    gebucht: boolean;
    storniert: boolean;
    digitalAngenommen: boolean;
    inhaltHash: string | null;
    geaendertVon: string | null;
    geaendertVonId: number | null;
    geaendertAm: string;
    aenderungsgrund: string | null;
    ipAdresse: string | null;
}

interface DokumentVerlaufDrawerProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    dokumentId: number | undefined;
    dokumentNummer?: string;
}

const AKTION_META: Record<string, { label: string; icon: React.ComponentType<{className?: string}>; color: string }> = {
    ERSTELLT:           { label: 'Erstellt',           icon: FileText,    color: 'bg-slate-100 text-slate-700 border-slate-200' },
    GEAENDERT:          { label: 'Geändert',           icon: Edit2,       color: 'bg-amber-100 text-amber-700 border-amber-200' },
    GEBUCHT:            { label: 'Gebucht',            icon: Lock,        color: 'bg-blue-100 text-blue-700 border-blue-200' },
    VERSENDET:          { label: 'Versendet',          icon: Send,        color: 'bg-purple-100 text-purple-700 border-purple-200' },
    STORNIERT:          { label: 'Storniert',          icon: RotateCcw,   color: 'bg-orange-100 text-orange-700 border-orange-200' },
    GELOESCHT:          { label: 'Gelöscht',           icon: Trash2,      color: 'bg-red-100 text-red-700 border-red-200' },
    DIGITAL_ANGENOMMEN: { label: 'Digital angenommen', icon: CheckCircle2, color: 'bg-green-100 text-green-700 border-green-200' },
};

/**
 * Audit-Verlauf eines AusgangsGeschaeftsDokuments für die Steuerprüfung.
 * Zeigt alle protokollierten Aktionen mit Bearbeiter, Zeitstempel, Begründung und Inhaltsfingerabdruck.
 */
export function DokumentVerlaufDrawer({ open, onOpenChange, dokumentId, dokumentNummer }: DokumentVerlaufDrawerProps) {
    const toast = useToast();
    const [cache, setCache] = useState<Record<number, DokumentAuditEintrag[]>>({});

    useEffect(() => {
        if (!open || !dokumentId) return;
        if (cache[dokumentId]) return;
        let cancelled = false;
        fetch(`/api/ausgangs-dokumente/${dokumentId}/historie`)
            .then(r => r.ok ? r.json() : [])
            .then((data: DokumentAuditEintrag[]) => {
                if (!cancelled) setCache(prev => ({ ...prev, [dokumentId]: data }));
            })
            .catch(e => {
                console.error(e);
                if (!cancelled) {
                    setCache(prev => ({ ...prev, [dokumentId]: [] }));
                    toast.error('Verlauf konnte nicht geladen werden');
                }
            });
        return () => { cancelled = true; };
    }, [open, dokumentId, cache, toast]);

    const eintraege = dokumentId !== undefined ? cache[dokumentId] : undefined;
    const loading = open && dokumentId !== undefined && eintraege === undefined;

    const formatBetrag = (n: number | null) => n != null ? n.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' }) : '–';
    const formatDate = (s: string) => {
        try { return new Date(s).toLocaleString('de-DE'); } catch { return s; }
    };
    const copyHash = (hash: string) => {
        navigator.clipboard.writeText(hash);
        toast.success('Hash kopiert');
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <History className="w-5 h-5 text-rose-600" />
                        Verlauf {dokumentNummer && <span className="text-slate-500 font-normal">– {dokumentNummer}</span>}
                    </DialogTitle>
                </DialogHeader>

                <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs text-slate-600 mt-2">
                    Lückenlose Protokollierung aller Aktionen für die Steuerprüfung (GoBD § 146 AO).
                    Einträge können nicht geändert oder gelöscht werden.
                </div>

                {loading || !eintraege ? (
                    <div className="flex items-center justify-center py-12">
                        <RefreshCw className="w-6 h-6 animate-spin text-rose-600" />
                    </div>
                ) : eintraege.length === 0 ? (
                    <div className="text-center py-12 text-slate-400">
                        Keine Audit-Einträge vorhanden.
                        <p className="text-xs mt-2">
                            Hinweis: Für Dokumente, die vor Einführung der Audit-Tabelle erstellt wurden, fehlen historische Einträge.
                        </p>
                    </div>
                ) : (
                    <div className="space-y-3 mt-2">
                        {eintraege.map((e) => {
                            const meta = AKTION_META[e.aktion] || { label: e.aktion, icon: FileText, color: 'bg-slate-100 text-slate-700 border-slate-200' };
                            const Icon = meta.icon;
                            return (
                                <div key={e.id} className="border border-slate-200 rounded-lg p-3 bg-white">
                                    <div className="flex items-start justify-between gap-3">
                                        <div className="flex items-start gap-3">
                                            <span className={`inline-flex items-center gap-1 px-2 py-1 rounded border text-xs font-medium ${meta.color}`}>
                                                <Icon className="w-3 h-3" />
                                                {meta.label}
                                            </span>
                                            <div className="text-sm">
                                                <div className="font-medium text-slate-900">
                                                    {e.geaendertVon || 'System'}
                                                </div>
                                                <div className="text-xs text-slate-500">
                                                    {formatDate(e.geaendertAm)}
                                                </div>
                                            </div>
                                        </div>
                                        <div className="text-right text-xs">
                                            <div className="text-slate-700">{formatBetrag(e.betragBrutto)}</div>
                                            <div className="text-slate-400">{e.typ}</div>
                                        </div>
                                    </div>

                                    {e.aenderungsgrund && (
                                        <div className="mt-2 text-sm text-slate-700 bg-slate-50 rounded p-2 border border-slate-100">
                                            <span className="font-medium">Begründung: </span>
                                            {e.aenderungsgrund}
                                        </div>
                                    )}

                                    <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
                                        {e.ipAdresse && <span>IP: {e.ipAdresse}</span>}
                                        {e.inhaltHash && (
                                            <button
                                                type="button"
                                                onClick={() => copyHash(e.inhaltHash!)}
                                                className="inline-flex items-center gap-1 hover:text-rose-600 transition-colors"
                                                title="Vollständigen Hash kopieren"
                                            >
                                                <Copy className="w-3 h-3" />
                                                <span className="font-mono">SHA-256: {e.inhaltHash.substring(0, 12)}…</span>
                                            </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </DialogContent>
        </Dialog>
    );
}

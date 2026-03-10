import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { RefreshCw, FileText, CheckCircle, AlertTriangle, ShieldCheck, Clock, Wallet } from 'lucide-react';
import { useToast } from './ui/toast';

// API Types
interface Eingangsrechnung {
    id: number;
    dokumentId: number;
    lieferantId: number;
    lieferantName: string;
    dokumentNummer: string;
    dokumentDatum: string | null;
    zahlungsziel: string | null;
    betragNetto: number | null;
    betragBrutto: number | null;
    bezahlt: boolean;
    bezahltAm: string | null;
    bereitsGezahlt: boolean;
    dateiname: string;
    pdfUrl: string;
    ueberfaellig: boolean;
    genehmigt: boolean;
    darfGenehmigen: boolean; // Vom Backend: true wenn User aus Abteilung 3 (Büro)
    // Skonto-Felder
    skontoTage: number | null;
    skontoProzent: number | null;
    nettoTage: number | null;
    skontoFrist: string | null;
    skontoVerbleibendeTage: number | null;
    skontoAbgelaufen: boolean;
    tatsaechlichGezahlt: number | null;
    mitSkonto: boolean;
    referenzNummer?: string;
    typ?: string;
}

// Auth Token aus localStorage holen
const getAuthToken = (): string | null => {
    const stored = localStorage.getItem('frontendUserSelection');
    if (stored) {
        try {
            const parsed = JSON.parse(stored);
            return parsed.loginToken || null;
        } catch {
            return null;
        }
    }
    return null;
};


// Format date to German locale
const formatDate = (isoText: string | undefined | null): string => {
    if (!isoText) return '–';
    const date = new Date(isoText);
    return Number.isNaN(date.getTime()) ? '–' : date.toLocaleDateString('de-DE');
};

// Format euro amount
const formatEuro = (value: number | undefined | null): string => {
    if (value == null || !Number.isFinite(value)) return '–';
    return new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
};

interface EingangsrechnungenTabProps {
    onOpenPdf?: (url: string, title: string) => void;
}

export default function EingangsrechnungenTab({ onOpenPdf }: EingangsrechnungenTabProps) {
    const toast = useToast();
    const navigate = useNavigate();
    const [items, setItems] = useState<Eingangsrechnung[]>([]);
    const [loading, setLoading] = useState(true);
    const [showBezahlt, setShowBezahlt] = useState(false);

    // canApprove wird vom Backend bestimmt (über darfGenehmigen in den Items)
    const canApprove = useMemo(() => {
        // Wenn mindestens ein Item darfGenehmigen = true hat, darf der User genehmigen
        return items.some(item => item.darfGenehmigen);
    }, [items]);

    const loadData = useCallback(async () => {
        setLoading(true);
        try {
            const endpoint = showBezahlt ? '/api/offene-posten/eingang/alle' : '/api/offene-posten/eingang';
            const token = getAuthToken();
            const headers: HeadersInit = { 'Content-Type': 'application/json' };
            if (token) {
                headers['X-Auth-Token'] = token;
            }
            const res = await fetch(endpoint, { headers });
            if (res.ok) {
                const data = await res.json();
                setItems(data);
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err);
        } finally {
            setLoading(false);
        }
    }, [showBezahlt]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const toggleBezahlt = async (id: number, currentValue: boolean) => {
        try {
            const token = getAuthToken();
            const headers: HeadersInit = { 'Content-Type': 'application/json' };
            if (token) headers['X-Auth-Token'] = token;

            await fetch(`/api/offene-posten/eingang/${id}/bezahlt`, {
                method: 'PUT',
                headers,
                body: JSON.stringify({ bezahlt: !currentValue })
            });
            await loadData();
        } catch (err) {
            console.error('Fehler beim Aktualisieren:', err);
        }
    };

    const toggleGenehmigt = async (id: number, currentValue: boolean) => {
        if (!canApprove) return;
        try {
            const token = getAuthToken();
            const headers: HeadersInit = { 'Content-Type': 'application/json' };
            if (token) headers['X-Auth-Token'] = token;

            const res = await fetch(`/api/offene-posten/eingang/${id}/genehmigen`, {
                method: 'PATCH',
                headers,
                body: JSON.stringify({ genehmigt: !currentValue })
            });

            if (res.status === 403) {
                toast.error('Keine Berechtigung zum Genehmigen. Nur Mitarbeiter aus Abteilung Büro dürfen genehmigen.');
                return;
            }

            // Reload data from server to ensure consistency
            await loadData();
        } catch (err) {
            console.error('Fehler beim Genehmigen:', err);
            loadData(); // Reload on error
        }
    };

    // Backend filtert bereits nach Berechtigungen:
    // - Abteilung 3 (Büro) sieht alle
    // - Abteilung 2 (Buchhaltung) sieht nur genehmigte
    const visibleItems = items;

    const totalSum = useMemo(() => {
        return visibleItems
            .filter(i => !i.bezahlt)
            .reduce((sum, i) => sum + (i.betragBrutto || 0), 0);
    }, [visibleItems]);

    // Group invoices with their credit notes (Gutschriften)
    const groupedItems = useMemo(() => {
        const itemMap = new Map<string, Eingangsrechnung>();
        const childrenByParent = new Map<string, Eingangsrechnung[]>();
        const rootItems: Eingangsrechnung[] = [];

        // First pass: Index all items
        visibleItems.forEach(item => {
            if (item.dokumentNummer) {
                itemMap.set(item.dokumentNummer, item);
            }
        });

        // Second pass: Group items
        visibleItems.forEach(item => {
            if (item.typ === 'GUTSCHRIFT' && item.referenzNummer) {
                const list = childrenByParent.get(item.referenzNummer) || [];
                list.push(item);
                childrenByParent.set(item.referenzNummer, list);
            } else {
                rootItems.push(item);
            }
        });

        // Add orphans (credit notes without found parent) to root
        childrenByParent.forEach((children, parentId) => {
            if (!itemMap.has(parentId)) {
                rootItems.push(...children);
            }
        });

        return rootItems.map(item => ({
            item,
            children: (childrenByParent.get(item.dokumentNummer) || []).sort((a, b) => {
                const dateA = a.dokumentDatum ? new Date(a.dokumentDatum).getTime() : 0;
                const dateB = b.dokumentDatum ? new Date(b.dokumentDatum).getTime() : 0;
                return dateA - dateB;
            })
        }));
    }, [visibleItems]);

    const offeneCount = visibleItems.filter(i => !i.bezahlt).length;
    const overdueCount = visibleItems.filter(i => !i.bezahlt && i.ueberfaellig).length;
    const genehmigtCount = visibleItems.filter(i => !i.bezahlt && i.genehmigt).length;

    if (loading) {
        return (
            <Card className="p-8 text-center text-slate-500 border-0 shadow-sm rounded-xl">
                <RefreshCw className="w-6 h-6 mx-auto mb-2 animate-spin text-rose-400" />
                <p className="text-sm">Lade Eingangsrechnungen...</p>
            </Card>
        );
    }

    const renderRow = (item: Eingangsrechnung, isChild = false) => (
        <tr
            key={item.id}
            className={`align-top transition-colors ${item.bezahlt ? 'bg-green-50/60 opacity-60' : item.ueberfaellig ? 'bg-red-50/50 hover:bg-red-50' : 'bg-white hover:bg-slate-50/80'} ${isChild ? 'bg-slate-50/30' : ''}`}
        >
            {/* Lieferant */}
            <td className="px-4 py-3 text-sm">
                {!isChild ? (
                    item.lieferantId ? (
                        <button
                            onClick={() => navigate(`/lieferanten?lieferantId=${item.lieferantId}`)}
                            className="font-medium text-rose-600 hover:text-rose-800 hover:underline transition-colors"
                        >
                            {item.lieferantName || '–'}
                        </button>
                    ) : (
                        <span className="font-medium text-slate-900">{item.lieferantName || '–'}</span>
                    )
                ) : (
                    <div className="flex items-center gap-2 text-slate-500 pl-4">
                        <span className="text-xl leading-none">↳</span>
                        <span className="text-rose-600 font-medium">Gutschrift</span>
                    </div>
                )}
            </td>
            {/* Rechnungsnummer */}
            <td className="px-4 py-3 text-sm text-slate-900">
                <div className="flex items-center gap-2">
                    <span>{item.dokumentNummer || '–'}</span>
                    {item.bereitsGezahlt && !item.bezahlt && (
                        <span className="inline-flex items-center rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700">
                            Vorauskasse
                        </span>
                    )}
                    {item.ueberfaellig && !item.bezahlt && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700">
                            <AlertTriangle className="w-3 h-3" />
                            Überfällig
                        </span>
                    )}
                </div>
            </td>
            {/* Datum */}
            <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">
                {formatDate(item.dokumentDatum)}
            </td>
            {/* Fällig */}
            <td className="px-4 py-3 text-sm whitespace-nowrap">
                <span className={item.ueberfaellig && !item.bezahlt ? 'text-red-600 font-semibold' : 'text-slate-600'}>
                    {formatDate(item.zahlungsziel)}
                </span>
            </td>
            {/* Skonto */}
            <td className="px-4 py-3 text-center text-sm whitespace-nowrap">
                {item.skontoProzent != null && item.skontoTage != null ? (
                    <div className="flex flex-col items-center gap-1">
                        <span className="text-slate-700 font-medium">{item.skontoProzent}%</span>
                        {!item.bezahlt && (
                            item.skontoAbgelaufen ? (
                                <span className="inline-flex items-center rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
                                    abgelaufen
                                </span>
                            ) : item.skontoVerbleibendeTage != null ? (
                                <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${item.skontoVerbleibendeTage <= 2
                                    ? 'bg-red-100 text-red-700'
                                    : item.skontoVerbleibendeTage <= 5
                                        ? 'bg-amber-100 text-amber-700'
                                        : 'bg-green-100 text-green-700'
                                    }`}>
                                    noch {item.skontoVerbleibendeTage} {item.skontoVerbleibendeTage === 1 ? 'Tag' : 'Tage'}
                                </span>
                            ) : null
                        )}
                        {item.bezahlt && item.mitSkonto && (
                            <span className="inline-flex items-center rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700">
                                ✓ genutzt
                            </span>
                        )}
                    </div>
                ) : (
                    <span className="text-slate-400">–</span>
                )}
            </td>
            {/* Netto */}
            <td className="px-4 py-3 text-right text-sm text-slate-600 whitespace-nowrap">
                {item.betragNetto != null ? `${formatEuro(item.betragNetto)} €` : '–'}
            </td>
            {/* Brutto */}
            <td className="px-4 py-3 text-right text-sm font-semibold text-slate-900 whitespace-nowrap">
                {item.betragBrutto != null ? `${formatEuro(item.betragBrutto)} €` : '–'}
                {item.bezahlt && item.mitSkonto && item.tatsaechlichGezahlt != null && (
                    <div className="text-xs text-green-600 font-normal">
                        gezahlt: {formatEuro(item.tatsaechlichGezahlt)} €
                    </div>
                )}
            </td>

            {/* Dokument öffnen */}
            <td className="px-4 py-3 text-center">
                {item.pdfUrl ? (
                    <button
                        onClick={() => onOpenPdf?.(item.pdfUrl, item.dokumentNummer || 'Rechnung')}
                        className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-600 transition hover:border-rose-200 hover:text-rose-600"
                        title="Rechnung öffnen"
                    >
                        <FileText className="w-5 h-5" />
                    </button>
                ) : (
                    <span className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-dashed border-slate-200 text-slate-300">
                        <FileText className="w-5 h-5" />
                    </span>
                )}
            </td>

            {/* Genehmigt */}
            <td className="px-4 py-3 text-center">
                <div className="flex justify-center">
                    <label
                        className={`inline-flex items-center justify-center p-1.5 rounded-full transition-colors ${canApprove ? 'cursor-pointer hover:bg-slate-100' : 'cursor-not-allowed opacity-70'}`}
                        title={canApprove ? "Status ändern" : "Nur Büro/Berechtigte"}
                    >
                        <input
                            type="checkbox"
                            className="sr-only"
                            checked={item.genehmigt}
                            onChange={() => toggleGenehmigt(item.id, item.genehmigt)}
                            disabled={!canApprove}
                        />
                        {item.genehmigt ? (
                            <ShieldCheck className="w-5 h-5 text-emerald-600" />
                        ) : (
                            <ShieldCheck className="w-5 h-5 text-slate-300" />
                        )}
                    </label>
                </div>
            </td>

            {/* Bezahlt */}
            <td className="px-4 py-3 text-center">
                <label className="inline-flex items-center gap-2 text-sm font-medium cursor-pointer">
                    <input
                        type="checkbox"
                        checked={item.bezahlt}
                        onChange={() => toggleBezahlt(item.id, item.bezahlt)}
                        className="h-4 w-4 rounded border-slate-300 text-green-600 focus:ring-green-500"
                    />
                    <span className={item.bezahlt ? 'text-green-600' : 'text-slate-600'}>
                        {item.bezahlt ? 'Bezahlt' : 'Offen'}
                    </span>
                </label>
            </td>
        </tr>
    );

    return (
        <div className="space-y-4">
            {/* KPI Stats */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 animate-fadeInUp delay-1">
                <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                    <div className="flex items-center gap-2.5">
                        <div className="p-1.5 bg-amber-50 rounded-lg">
                            <Wallet className="w-4 h-4 text-amber-600" />
                        </div>
                        <div>
                            <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Offene Summe</p>
                            <p className="text-base font-bold text-slate-900">{formatEuro(totalSum)} €</p>
                        </div>
                    </div>
                </Card>
                <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                    <div className="flex items-center gap-2.5">
                        <div className="p-1.5 bg-slate-50 rounded-lg">
                            <FileText className="w-4 h-4 text-slate-500" />
                        </div>
                        <div>
                            <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Offen</p>
                            <p className="text-base font-bold text-slate-900">{offeneCount}</p>
                        </div>
                    </div>
                </Card>
                <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                    <div className="flex items-center gap-2.5">
                        <div className="p-1.5 bg-red-50 rounded-lg">
                            <Clock className="w-4 h-4 text-red-500" />
                        </div>
                        <div>
                            <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Überfällig</p>
                            <p className="text-base font-bold text-red-600">{overdueCount}</p>
                        </div>
                    </div>
                </Card>
                <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                    <div className="flex items-center gap-2.5">
                        <div className="p-1.5 bg-emerald-50 rounded-lg">
                            <ShieldCheck className="w-4 h-4 text-emerald-600" />
                        </div>
                        <div>
                            <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Genehmigt</p>
                            <p className="text-base font-bold text-emerald-700">{genehmigtCount}</p>
                        </div>
                    </div>
                </Card>
            </div>

            {/* Controls */}
            <div className="flex items-center justify-end gap-4 animate-fadeInUp delay-1">
                <label className="flex items-center gap-2 text-sm text-slate-600 cursor-pointer">
                    <input
                        type="checkbox"
                        checked={showBezahlt}
                        onChange={(e) => setShowBezahlt(e.target.checked)}
                        className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                    />
                    Bezahlte anzeigen
                </label>
                <Button variant="outline" size="sm" onClick={loadData}>
                    <RefreshCw className="w-4 h-4 mr-1" />
                    Aktualisieren
                </Button>
            </div>

            {/* Table */}
            <div className="animate-fadeInUp delay-2">
                {visibleItems.length === 0 ? (
                    <Card className="p-8 text-center border-0 shadow-sm rounded-xl">
                        <CheckCircle className="w-10 h-10 mx-auto mb-2 text-emerald-300" />
                        <p className="text-sm font-medium text-slate-600">Keine offenen Eingangsrechnungen</p>
                        <p className="text-xs mt-1 text-slate-400">Alle Lieferantenrechnungen wurden bezahlt oder sind ausgeblendet.</p>
                    </Card>
                ) : (
                    <Card className="overflow-hidden border-0 shadow-sm rounded-xl">
                        <div className="overflow-x-auto">
                            <table className="w-full">
                                <thead>
                                    <tr className="bg-slate-50 border-b border-slate-200">
                                        <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Lieferant</th>
                                        <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Rechnungsnr.</th>
                                        <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Datum</th>
                                        <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Fällig</th>
                                        <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">Skonto</th>
                                        <th className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">Netto</th>
                                        <th className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">Brutto</th>
                                        <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">Dokument</th>
                                        <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">Genehmigt</th>
                                        <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">Status</th>
                                    </tr>
                                </thead>

                                <tbody className="divide-y divide-slate-100">
                                    {groupedItems.map(({ item, children }) => (
                                        <React.Fragment key={item.id}>
                                            {renderRow(item)}
                                            {children.map(child => renderRow(child, true))}
                                        </React.Fragment>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </Card>
                )}
            </div>
        </div>
    );
}

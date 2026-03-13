import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { RefreshCw, FileText, AlertTriangle, X, Download, ExternalLink, Building2, ArrowUpRight, Clock, BadgeAlert, Wallet, Plus } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import type { OffenerPosten, Mahnstufe } from '../types';
import EingangsrechnungenTab from '../components/EingangsrechnungenTab';
import { AusgangsrechnungUploadModal } from '../components/AusgangsrechnungUploadModal';


// Mahnstufe labels
const MAHNSTUFE_LABELS: Record<Mahnstufe, string> = {
    ZAHLUNGSERINNERUNG: 'Zahlungserinnerung',
    ERSTE_MAHNUNG: '1. Mahnung',
    ZWEITE_MAHNUNG: '2. Mahnung'
};

// Format euro amount
const formatEuro = (value: number | undefined | null): string => {
    if (value == null || !Number.isFinite(value)) return '–';
    return new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
};

// Format date to German locale
const formatDate = (isoText: string | undefined | null): string => {
    if (!isoText) return '–';
    const date = new Date(isoText);
    return Number.isNaN(date.getTime()) ? '–' : date.toLocaleDateString('de-DE');
};

// Calculate days until due date and status
const beschreibeFaelligkeit = (isoText: string | undefined | null): {
    label: string;
    className: string;
    diffTage: number | null
} => {
    if (!isoText) return { label: '', className: '', diffTage: null };
    const datum = new Date(isoText);
    if (Number.isNaN(datum.getTime())) return { label: '', className: '', diffTage: null };

    const heute = new Date();
    heute.setHours(0, 0, 0, 0);
    datum.setHours(0, 0, 0, 0);

    const diffTage = Math.ceil((datum.getTime() - heute.getTime()) / (1000 * 60 * 60 * 24));

    if (diffTage > 0) {
        return { label: `noch ${diffTage} Tage`, className: 'text-slate-500', diffTage };
    }
    if (diffTage === 0) {
        return { label: 'Heute fällig', className: 'text-amber-600 font-semibold', diffTage };
    }
    return { label: `Überfällig seit ${Math.abs(diffTage)} Tagen`, className: 'text-red-600 font-semibold', diffTage };
};

// Group invoices with their reminders
interface InvoiceWithReminders {
    invoice: OffenerPosten;
    mahnungen: OffenerPosten[];
}

// Document Preview Modal Component
interface PreviewDoc {
    url: string;
    title: string;
}

function DocumentPreviewModal({ doc, onClose }: { doc: PreviewDoc; onClose: () => void }) {
    // Erkennung für PDF: .pdf Extension, /dokumente/ oder /attachments/ oder /pdf Endpoints
    const isPdf = doc.url.toLowerCase().includes('.pdf') ||
        doc.url.includes('/dokumente/') ||
        doc.url.includes('/attachments/') ||
        doc.url.endsWith('/pdf');


    useEffect(() => {
        const handleEsc = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleEsc);
        return () => window.removeEventListener('keydown', handleEsc);
    }, [onClose]);

    const handleDownload = () => {
        const link = document.createElement('a');
        link.href = doc.url;
        link.download = doc.title;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm"
        >
            <div
                className="relative bg-white rounded-2xl shadow-2xl w-full max-w-5xl mx-4 max-h-[90vh] overflow-hidden flex flex-col"
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
                    <h3 className="font-semibold text-slate-900 truncate">{doc.title}</h3>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={handleDownload}
                            className="text-slate-500 hover:text-slate-700"
                            title="Herunterladen"
                        >
                            <Download className="w-4 h-4" />
                        </Button>
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => window.open(doc.url, '_blank')}
                            className="text-slate-500 hover:text-slate-700"
                            title="In neuem Tab öffnen"
                        >
                            <ExternalLink className="w-4 h-4" />
                        </Button>
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={onClose}
                            className="text-slate-500 hover:text-slate-700"
                        >
                            <X className="w-5 h-5" />
                        </Button>
                    </div>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-auto bg-slate-100 min-h-[500px]">
                    {isPdf ? (
                        <iframe
                            src={doc.url}
                            className="w-full h-full min-h-[600px]"
                            title={doc.title}
                        />
                    ) : (
                        <div className="flex flex-col items-center justify-center py-12">
                            <FileText className="w-24 h-24 text-slate-300 mb-6" />
                            <p className="text-slate-600 text-lg font-medium">{doc.title}</p>
                            <p className="text-slate-400 mt-2">Vorschau nicht verfügbar</p>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={handleDownload}
                                className="mt-4"
                            >
                                <Download className="w-4 h-4 mr-2" />
                                Herunterladen
                            </Button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export default function OffenePostenEditor() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [items, setItems] = useState<OffenerPosten[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [previewDoc, setPreviewDoc] = useState<PreviewDoc | null>(null);
    const [showUploadModal, setShowUploadModal] = useState(false);
    const [activeTab, setActiveTab] = useState<'ausgang' | 'eingang'>(() => {
        const tabParam = searchParams.get('tab');
        return tabParam === 'eingang' ? 'eingang' : 'ausgang';
    });

    // Deep-link: read tab param from URL
    useEffect(() => {
        const tabParam = searchParams.get('tab');
        if (tabParam === 'eingang' || tabParam === 'ausgang') {
            setActiveTab(tabParam);
            setSearchParams({}, { replace: true });
        }
    }, [searchParams]);


    // Load open items
    const loadData = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const res = await fetch('/api/projekte/offene-posten');
            if (!res.ok) throw new Error('Laden fehlgeschlagen');
            const data: OffenerPosten[] = await res.json();
            setItems(data);
        } catch (err) {
            console.error('Offene Posten konnten nicht geladen werden:', err);
            setError('Fehler beim Laden der offenen Posten.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    // Group invoices with their reminders
    const groupedInvoices = useMemo((): InvoiceWithReminders[] => {
        const childrenByParent = new Map<number, OffenerPosten[]>();
        const invoices: OffenerPosten[] = [];

        items.forEach(doc => {
            if (doc.mahnung && doc.referenzDokumentId) {
                const list = childrenByParent.get(doc.referenzDokumentId) || [];
                list.push(doc);
                childrenByParent.set(doc.referenzDokumentId, list);
            } else {
                invoices.push(doc);
            }
        });

        // Sort invoices by date descending
        invoices.sort((a, b) => {
            const dateA = a.rechnungsdatum ? new Date(a.rechnungsdatum).getTime() : 0;
            const dateB = b.rechnungsdatum ? new Date(b.rechnungsdatum).getTime() : 0;
            return dateB - dateA;
        });

        return invoices.map(invoice => ({
            invoice,
            mahnungen: (childrenByParent.get(invoice.id) || []).sort((a, b) => {
                const dateA = a.uploadDatum ? new Date(a.uploadDatum).getTime() : 0;
                const dateB = b.uploadDatum ? new Date(b.uploadDatum).getTime() : 0;
                return dateA - dateB;
            })
        }));
    }, [items]);

    // Calculate total sum
    const totalSum = useMemo(() => {
        return groupedInvoices.reduce((sum, { invoice }) => {
            const betrag = invoice.rechnungsbetrag;
            return sum + (betrag != null && Number.isFinite(betrag) ? betrag : 0);
        }, 0);
    }, [groupedInvoices]);

    // KPI stats for Ausgangsrechnungen
    const kpiStats = useMemo(() => {
        const overdue = groupedInvoices.filter(({ invoice }) => {
            if (!invoice.faelligkeitsdatum) return false;
            const d = new Date(invoice.faelligkeitsdatum);
            const heute = new Date();
            heute.setHours(0, 0, 0, 0);
            d.setHours(0, 0, 0, 0);
            return d.getTime() < heute.getTime();
        }).length;
        const mitMahnung = groupedInvoices.filter(({ mahnungen }) => mahnungen.length > 0).length;
        const overdueSum = groupedInvoices
            .filter(({ invoice }) => {
                if (!invoice.faelligkeitsdatum) return false;
                const d = new Date(invoice.faelligkeitsdatum);
                const heute = new Date();
                heute.setHours(0, 0, 0, 0);
                d.setHours(0, 0, 0, 0);
                return d.getTime() < heute.getTime();
            })
            .reduce((sum, { invoice }) => sum + (invoice.rechnungsbetrag ?? 0), 0);
        return { overdue, mitMahnung, overdueSum };
    }, [groupedInvoices]);

    // Toggle bezahlt status
    const toggleBezahlt = async (id: number, currentValue: boolean) => {
        try {
            await fetch(`/api/projekte/dokumente/${id}/bezahlt?bezahlt=${!currentValue}`, { method: 'PATCH' });
            await loadData();
        } catch (err) {
            console.error('Status konnte nicht aktualisiert werden:', err);
        }
    };

    const navigate = useNavigate();

    // Render invoice row
    const renderInvoiceRow = (item: InvoiceWithReminders) => {
        const { invoice, mahnungen } = item;
        const faelligInfo = beschreibeFaelligkeit(invoice.faelligkeitsdatum);
        const tageBis = faelligInfo.diffTage;
        const tageBisText = tageBis != null ? (tageBis > 0 ? `+${tageBis}` : `${tageBis}`) : '–';
        const letzteMahnung = mahnungen.length > 0 ? mahnungen[mahnungen.length - 1] : null;

        return (
            <React.Fragment key={invoice.id}>
                {/* Main invoice row */}
                <tr className={`align-top transition-colors ${mahnungen.length > 0 ? 'border-l-4 border-amber-300 bg-white hover:bg-rose-50/30' : 'bg-white hover:bg-slate-50/80'}`}>
                    {/* Projekt */}
                    <td className="px-4 py-3 text-sm">
                        {invoice.projektId ? (
                            <button
                                onClick={() => navigate(`/projekte?projektId=${invoice.projektId}`)}
                                className="text-rose-600 hover:text-rose-800 hover:underline font-medium transition-colors"
                            >
                                {invoice.projektAuftragsnummer || '–'}
                            </button>
                        ) : (
                            <span className="text-slate-400">{invoice.projektAuftragsnummer || '–'}</span>
                        )}
                    </td>
                    {/* Kunde */}
                    <td className="px-4 py-3 text-sm text-slate-600">{invoice.projektKunde || '–'}</td>
                    {/* Rechnungsnummer */}
                    <td className="px-4 py-3 text-sm text-slate-900">
                        <div className="flex items-center gap-2">
                            <span>{invoice.rechnungsnummer || '–'}</span>
                            {letzteMahnung && (
                                <span className="inline-flex items-center rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-700">
                                    {MAHNSTUFE_LABELS[letzteMahnung.mahnstufe!] || 'Mahnung'}
                                </span>
                            )}
                        </div>
                    </td>
                    {/* Rechnungsdatum */}
                    <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">{formatDate(invoice.rechnungsdatum)}</td>
                    {/* Versandt */}
                    <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">{formatDate(invoice.emailVersandDatum)}</td>
                    {/* Fällig */}
                    <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">{formatDate(invoice.faelligkeitsdatum)}</td>
                    {/* Tage */}
                    <td className={`px-4 py-3 text-sm ${faelligInfo.className}`} title={faelligInfo.label}>
                        {tageBisText}
                    </td>
                    {/* Betrag */}
                    <td className="px-4 py-3 text-right text-sm font-semibold text-slate-900 whitespace-nowrap">
                        {invoice.rechnungsbetrag != null ? `${formatEuro(invoice.rechnungsbetrag)} €` : '–'}
                    </td>
                    {/* Dokument */}
                    <td className="px-4 py-3 text-center">
                        {invoice.url ? (
                            <button
                                onClick={() => {
                                    setPreviewDoc({ url: invoice.url!, title: invoice.rechnungsnummer || 'Rechnung' });
                                }}
                                className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-600 transition hover:border-rose-200 hover:text-rose-600"
                                title="Rechnung ansehen"
                            >
                                <FileText className="w-5 h-5" />
                            </button>
                        ) : (
                            <span className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-dashed border-slate-200 text-slate-300">
                                <FileText className="w-5 h-5" />
                            </span>
                        )}
                    </td>
                    {/* Bezahlt */}
                    <td className="px-4 py-3 text-center">
                        <label className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 cursor-pointer">
                            <input
                                type="checkbox"
                                checked={invoice.bezahlt}
                                onChange={() => toggleBezahlt(invoice.id, invoice.bezahlt)}
                                className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                            />
                            Bezahlt
                        </label>
                    </td>
                </tr>

                {/* Mahnung rows */}
                {mahnungen.map(mahnung => {
                    const mahnungInfo = beschreibeFaelligkeit(mahnung.faelligkeitsdatum);
                    const mahnungTageBis = mahnungInfo.diffTage;
                    const mahnungTageBisText = mahnungTageBis != null ? (mahnungTageBis > 0 ? `+${mahnungTageBis}` : `${mahnungTageBis}`) : '–';

                    return (
                        <tr key={mahnung.id} className="bg-amber-50 align-top border-l-4 border-amber-200 hover:bg-amber-100">
                            <td className="px-4 py-3 text-sm text-amber-700">
                                <div className="flex items-center gap-2">
                                    <span className="text-lg leading-none text-amber-500">↳</span>
                                    <span className="font-semibold">{MAHNSTUFE_LABELS[mahnung.mahnstufe!] || 'Mahnung'}</span>
                                </div>
                            </td>
                            <td className="px-4 py-3 text-sm text-slate-500">–</td>
                            <td className="px-4 py-3 text-sm text-slate-900">
                                <div className="flex items-center gap-2">
                                    <span>{mahnung.rechnungsnummer || '–'}</span>
                                </div>
                            </td>
                            <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">{formatDate(mahnung.rechnungsdatum)}</td>
                            <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">{formatDate(mahnung.emailVersandDatum)}</td>
                            <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">{formatDate(mahnung.faelligkeitsdatum)}</td>
                            <td className={`px-4 py-3 text-sm ${mahnungInfo.className}`} title={mahnungInfo.label}>
                                {mahnungTageBisText}
                            </td>
                            <td className="px-4 py-3 text-right text-sm font-semibold text-slate-900 whitespace-nowrap">
                                {mahnung.rechnungsbetrag != null ? `${formatEuro(mahnung.rechnungsbetrag)} €` : '–'}
                            </td>
                            <td className="px-4 py-3 text-center">
                                {mahnung.url ? (
                                    <button
                                        onClick={() => setPreviewDoc({ url: mahnung.url!, title: MAHNSTUFE_LABELS[mahnung.mahnstufe!] || 'Mahnung' })}
                                        className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-amber-200 bg-white text-amber-600 transition hover:border-amber-300 hover:text-amber-700"
                                        title="Mahnung öffnen"
                                    >
                                        <FileText className="w-5 h-5" />
                                    </button>
                                ) : (
                                    <span className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-dashed border-slate-200 text-slate-300">
                                        <FileText className="w-5 h-5" />
                                    </span>
                                )}
                            </td>
                            <td className="px-4 py-3 text-center text-sm text-slate-400">–</td>
                        </tr>
                    );
                })}
            </React.Fragment>
        );
    };

    return (
        <PageLayout
            ribbonCategory="Buchhaltung"
            title="Offene Posten"
            subtitle="Übersicht aller unbezahlten Rechnungen"
            actions={
                activeTab === 'ausgang' ? (
                    <div className="flex gap-2">
                        <Button variant="outline" size="sm" onClick={() => setShowUploadModal(true)}>
                            <Plus className="w-4 h-4 mr-1" />
                            Manuell erfassen
                        </Button>
                        <Button variant="outline" size="sm" onClick={loadData} disabled={loading}>
                            <RefreshCw className={`w-4 h-4 mr-1 ${loading ? 'animate-spin' : ''}`} />
                            Aktualisieren
                        </Button>
                    </div>
                ) : null
            }
        >
            {/* Tab Navigation */}
            <div className="animate-fadeInUp">
                <div className="flex gap-1 mb-5 border-b border-slate-200">
                    <button
                        onClick={() => setActiveTab('ausgang')}
                        className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${activeTab === 'ausgang'
                            ? 'border-rose-500 text-rose-600'
                            : 'border-transparent text-slate-500 hover:text-slate-900 hover:border-slate-300'
                            }`}
                    >
                        <ArrowUpRight className="w-4 h-4" />
                        Ausgangsrechnungen
                    </button>
                    <button
                        onClick={() => setActiveTab('eingang')}
                        className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${activeTab === 'eingang'
                            ? 'border-rose-500 text-rose-600'
                            : 'border-transparent text-slate-500 hover:text-slate-900 hover:border-slate-300'
                            }`}
                    >
                        <Building2 className="w-4 h-4" />
                        Eingangsrechnungen
                    </button>
                </div>
            </div>

            {/* Tab Content */}
            {activeTab === 'ausgang' ? (
                <>
                    {/* KPI Stats */}
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-5 animate-fadeInUp delay-1">
                        <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                            <div className="flex items-center gap-2.5">
                                <div className="p-1.5 bg-rose-50 rounded-lg">
                                    <Wallet className="w-4 h-4 text-rose-600" />
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
                                    <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Rechnungen</p>
                                    <p className="text-base font-bold text-slate-900">{groupedInvoices.length}</p>
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
                                    <p className="text-base font-bold text-red-600">{kpiStats.overdue}</p>
                                </div>
                            </div>
                        </Card>
                        <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                            <div className="flex items-center gap-2.5">
                                <div className="p-1.5 bg-amber-50 rounded-lg">
                                    <BadgeAlert className="w-4 h-4 text-amber-500" />
                                </div>
                                <div>
                                    <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Mit Mahnung</p>
                                    <p className="text-base font-bold text-amber-700">{kpiStats.mitMahnung}</p>
                                </div>
                            </div>
                        </Card>
                    </div>

                    {/* Content */}
                    <div className="animate-fadeInUp delay-2">
                        {loading ? (
                            <Card className="p-8 text-center text-slate-500 border-0 shadow-sm rounded-xl">
                                <RefreshCw className="w-6 h-6 mx-auto mb-2 animate-spin text-rose-400" />
                                <p className="text-sm">Lade offene Posten...</p>
                            </Card>
                        ) : error ? (
                            <Card className="p-8 text-center text-red-500 border-0 shadow-sm rounded-xl">
                                <AlertTriangle className="w-8 h-8 mx-auto mb-2 text-red-300" />
                                <p className="text-sm">{error}</p>
                            </Card>
                        ) : groupedInvoices.length === 0 ? (
                            <Card className="p-8 text-center border-0 shadow-sm rounded-xl">
                                <FileText className="w-10 h-10 mx-auto mb-2 text-slate-300" />
                                <p className="text-sm font-medium text-slate-600">Keine offenen Rechnungen vorhanden</p>
                                <p className="text-xs mt-1 text-slate-400">Alle Rechnungen wurden bezahlt.</p>
                            </Card>
                        ) : (
                            <Card className="overflow-hidden border-0 shadow-sm rounded-xl">
                                <div className="overflow-x-auto">
                                    <table className="w-full">
                                        <thead>
                                            <tr className="bg-slate-50 border-b border-slate-200">
                                                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Projekt</th>
                                                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Kunde</th>
                                                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Rechnungsnr.</th>
                                                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Datum</th>
                                                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Versandt</th>
                                                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Fällig</th>
                                                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Tage</th>
                                                <th className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">Betrag</th>
                                                <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">Dokument</th>
                                                <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">Status</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-slate-100">
                                            {groupedInvoices.map(renderInvoiceRow)}
                                        </tbody>
                                    </table>
                                </div>
                            </Card>
                        )}
                    </div>
                </>
            ) : (
                <EingangsrechnungenTab
                    onOpenPdf={(url, title) => setPreviewDoc({ url, title })}
                />
            )}

            {/* Document Preview Modal */}
            {previewDoc && (
                <DocumentPreviewModal doc={previewDoc} onClose={() => setPreviewDoc(null)} />
            )}

            {/* Upload Modal */}
            <AusgangsrechnungUploadModal
                isOpen={showUploadModal}
                onClose={() => setShowUploadModal(false)}
                onSuccess={() => {
                    setShowUploadModal(false);
                    loadData();
                }}
            />
        </PageLayout>
    );
}



import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { RefreshCw, FileText, AlertTriangle, Building2, ArrowUpRight, Clock, BadgeAlert, Wallet, CheckCircle2 } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import type { OffenerPosten, Mahnstufe } from '../types';
import EingangsrechnungenTab from '../components/EingangsrechnungenTab';
import DocumentPreviewModal, { type PreviewDoc } from '../components/DocumentPreviewModal';
import { scrolleMittigInDenBlick } from '../lib/scrollInDenBlick';
import { useHakenSpeichern } from '../hooks/useHakenSpeichern';
import { cn } from '../lib/utils';
import { InfoAbschnitt, InfoButton } from '../components/ui/info-button';
import { useToast } from '../components/ui/toast';
import { leseFehlermeldung } from '../lib/apiFehler';


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

/** Erklärt, was der Haken „Bezahlt“ am Projekt auslöst (AusgangsGeschaeftsDokumentService). */
function BezahltInfo() {
    return <InfoButton titel="Haken „Bezahlt“">
        <InfoAbschnitt titel="Was passiert im Hintergrund?">
            <p>Die Rechnung wird als bezahlt gespeichert und verschwindet aus den offenen Posten (über „Bezahlte anzeigen“ sehen Sie sie weiter). Danach prüft das Programm das ganze Projekt: Sind alle Rechnungen geschrieben und gebucht, ist keine mehr offen und deckt das bezahlte Geld den Auftragspreis, gilt das Projekt als bezahlt und wird automatisch beendet.</p>
        </InfoAbschnitt>
        <InfoAbschnitt titel="Was bewirkt das?">
            <p>Die Rechnung selbst (Betrag, PDF) ändert sich nicht, und der Kunde bekommt nichts geschickt. Haben Sie das Projekt von Hand beendet oder wieder geöffnet, bleibt es dabei.</p>
        </InfoAbschnitt>
    </InfoButton>;
}

/** Erklärt den Schalter „Bezahlte anzeigen“ und was das Herausnehmen des Hakens bewirkt. */
function BezahlteAnzeigenInfo() {
    return <InfoButton titel="Bezahlte anzeigen">
        <InfoAbschnitt titel="Was passiert im Hintergrund?">
            <p>Zeigt zusätzlich die Kundenrechnungen der letzten 12 Monate, die schon als bezahlt markiert sind. Stornierte Rechnungen erscheinen hier nicht.</p>
        </InfoAbschnitt>
        <InfoAbschnitt titel="Was bewirkt das?">
            <p>Nehmen Sie den Haken „Bezahlt“ heraus, ist die Rechnung wieder offen: Sie steht wieder in den offenen Posten, kann wieder gemahnt werden, und ein Projekt, das dadurch nicht mehr voll bezahlt ist, gilt wieder als offen. Die Rechnung selbst und ihre Nummer bleiben unverändert.</p>
        </InfoAbschnitt>
    </InfoButton>;
}

/**
 * Was der Kunde noch zahlen muss: der offene Betrag nach Gutschriften. Kennt der
 * Server ihn nicht (ältere Antwort, Mahnung), gilt der Rechnungsbetrag.
 */
function offenerBetragVon(posten: OffenerPosten): number | undefined {
    const betrag = posten.offenerBetrag ?? posten.rechnungsbetrag;
    return betrag != null && Number.isFinite(betrag) ? betrag : undefined;
}

// Group invoices with their reminders
interface InvoiceWithReminders {
    invoice: OffenerPosten;
    mahnungen: OffenerPosten[];
}

/** Ordnet Mahnungen ihrer Rechnung zu; Rechnungen neueste zuerst, Mahnungen in Versandreihenfolge. */
function gruppiereRechnungen(items: OffenerPosten[]): InvoiceWithReminders[] {
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
}

export default function OffenePostenEditor() {
    const toast = useToast();
    const [searchParams, setSearchParams] = useSearchParams();
    const [items, setItems] = useState<OffenerPosten[]>([]);
    // Bezahlte Kundenrechnungen (letzte 12 Monate) – nur geladen, solange „Bezahlte anzeigen“ an ist.
    const [showBezahlt, setShowBezahlt] = useState(false);
    const [bezahlteItems, setBezahlteItems] = useState<OffenerPosten[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [previewDoc, setPreviewDoc] = useState<PreviewDoc | null>(null);
    const [activeTab, setActiveTab] = useState<'ausgang' | 'eingang'>(() => {
        const tabParam = searchParams.get('tab');
        return tabParam === 'eingang' ? 'eingang' : 'ausgang';
    });
    const [fokusId, setFokusId] = useState<number | null>(() => {
        const v = searchParams.get('dokumentId');
        return v ? Number(v) : null;
    });

    // Deep-link: tab + optionalen dokumentId aus URL übernehmen.
    // dokumentId wird nach Verarbeitung wieder entfernt, damit Reload nicht erneut springt.
    useEffect(() => {
        const tabParam = searchParams.get('tab');
        const dokParam = searchParams.get('dokumentId');
        if (tabParam || dokParam) {
            if (tabParam === 'eingang' || tabParam === 'ausgang') setActiveTab(tabParam);
            if (dokParam) setFokusId(Number(dokParam));
            setSearchParams({}, { replace: true });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [searchParams]);

    // Scroll + Highlight, sobald die geladene Liste die fokussierte ID enthält.
    useEffect(() => {
        if (fokusId == null) return;
        if (!items.some(i => i.id === fokusId)) return;
        const t = window.setTimeout(() => {
            const el = document.querySelector(`[data-dokument-id="${fokusId}"]`) as HTMLElement | null;
            if (el) {
                scrolleMittigInDenBlick(el);
                el.classList.add('ring-2', 'ring-rose-400');
                window.setTimeout(() => {
                    el.classList.remove('ring-2', 'ring-rose-400');
                    setFokusId(null);
                }, 2400);
            } else {
                setFokusId(null);
            }
        }, 80);
        return () => window.clearTimeout(t);
    }, [fokusId, items]);


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

    // Bezahlte Rechnungen laden. Fehler zeigt ein Toast; die offenen Posten bleiben davon unberührt.
    const loadBezahlte = useCallback(async () => {
        try {
            const res = await fetch('/api/projekte/offene-posten/bezahlt');
            if (!res.ok) {
                toast.error(await leseFehlermeldung(res, 'Die bezahlten Rechnungen konnten nicht geladen werden.'));
                return;
            }
            const data: unknown = await res.json();
            setBezahlteItems(Array.isArray(data) ? data as OffenerPosten[] : []);
        } catch {
            toast.error('Die bezahlten Rechnungen konnten nicht geladen werden. Bitte die Verbindung prüfen.');
        }
    }, [toast]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    useEffect(() => {
        if (showBezahlt) void loadBezahlte();
        else setBezahlteItems([]);
    }, [showBezahlt, loadBezahlte]);

    // Nach einem Haken beide Listen neu laden, damit die Rechnung in die richtige Liste wandert.
    const neuLaden = useCallback(async () => {
        await Promise.all([loadData(), showBezahlt ? loadBezahlte() : Promise.resolve()]);
    }, [loadData, loadBezahlte, showBezahlt]);

    // Group invoices with their reminders – nur die offenen zählen für Summen und Kennzahlen.
    const groupedInvoices = useMemo(() => gruppiereRechnungen(items), [items]);
    const bezahlteGruppen = useMemo(() => {
        if (!showBezahlt) return [];
        const offeneIds = new Set(items.map(i => i.id));
        return gruppiereRechnungen(bezahlteItems.filter(i => !offeneIds.has(i.id)));
    }, [items, bezahlteItems, showBezahlt]);

    // Calculate total sum
    const totalSum = useMemo(() => {
        return groupedInvoices.reduce((sum, { invoice }) => sum + (offenerBetragVon(invoice) ?? 0), 0);
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
            .reduce((sum, { invoice }) => sum + (offenerBetragVon(invoice) ?? 0), 0);
        return { overdue, mitMahnung, overdueSum };
    }, [groupedInvoices]);

    // Haken „Bezahlt“: Zeile während des Speicherns gesperrt, Fehler als Toast, danach neu laden.
    const { inArbeit: bezahltWirdGespeichert, speichern } = useHakenSpeichern(neuLaden);

    const toggleBezahlt = async (id: number, currentValue: boolean) => {
        let gespeichert = false;
        await speichern({
            id,
            senden: async () => {
                const res = await fetch(`/api/projekte/dokumente/${id}/bezahlt?bezahlt=${!currentValue}`, { method: 'PATCH' });
                gespeichert = res.ok;
                return res;
            },
            fehlertext: currentValue
                ? 'Die Rechnung konnte nicht wieder auf offen gesetzt werden.'
                : 'Die Rechnung konnte nicht als bezahlt markiert werden.',
        });
        if (!gespeichert) return;
        // Der Toast kann keine Knöpfe – deshalb der Hinweis, wo man den Haken zurücknimmt.
        if (currentValue) toast.success('Die Rechnung ist wieder offen.');
        else toast.success('Als bezahlt markiert. Über „Bezahlte anzeigen“ können Sie den Haken wieder herausnehmen.', 6000);
    };

    const navigate = useNavigate();

    // Render invoice row
    const renderInvoiceRow = (item: InvoiceWithReminders, erledigt = false) => {
        const { invoice } = item;
        // Bei bezahlten Rechnungen sind Mahnungen und Fälligkeit erledigt.
        const mahnungen = erledigt ? [] : item.mahnungen;
        const faelligInfo = erledigt
            ? { label: 'Bezahlt', className: 'text-slate-400', diffTage: null }
            : beschreibeFaelligkeit(invoice.faelligkeitsdatum);
        const tageBis = faelligInfo.diffTage;
        const tageBisText = tageBis != null ? (tageBis > 0 ? `+${tageBis}` : `${tageBis}`) : '–';
        const letzteMahnung = mahnungen.length > 0 ? mahnungen[mahnungen.length - 1] : null;

        return (
            <React.Fragment key={invoice.id}>
                {/* Main invoice row */}
                <tr
                    data-dokument-id={invoice.id}
                    data-bezahlt={erledigt || undefined}
                    aria-busy={bezahltWirdGespeichert.has(invoice.id) || undefined}
                    className={cn(
                        'align-top transition-all',
                        erledigt ? 'bg-slate-50/70 text-slate-500 hover:bg-slate-100/70'
                            : mahnungen.length > 0 ? 'border-l-4 border-amber-300 bg-white hover:bg-rose-50/30' : 'bg-white hover:bg-slate-50/80',
                    )}
                >
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
                    <td className={cn('px-4 py-3 text-right text-sm font-semibold whitespace-nowrap', erledigt ? 'text-slate-500' : 'text-slate-900')}>
                        {(() => {
                            const offen = offenerBetragVon(invoice);
                            const gutschrift = invoice.gutgeschrieben ?? 0;
                            return (
                                <>
                                    {offen != null ? `${formatEuro(offen)} €` : '–'}
                                    {gutschrift > 0 && invoice.rechnungsbetrag != null && (
                                        <span className="block text-xs font-normal text-slate-500">
                                            Rechnung {formatEuro(invoice.rechnungsbetrag)} € · Gutschrift −{formatEuro(gutschrift)} €
                                        </span>
                                    )}
                                </>
                            );
                        })()}
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
                        <label
                            title={bezahltWirdGespeichert.has(invoice.id) ? 'Wird gespeichert …' : undefined}
                            className={cn(
                                'inline-flex items-center gap-2 text-sm font-medium',
                                erledigt ? 'text-slate-500' : 'text-slate-600',
                                bezahltWirdGespeichert.has(invoice.id) ? 'cursor-wait opacity-60' : 'cursor-pointer'
                            )}
                        >
                            <input
                                type="checkbox"
                                checked={invoice.bezahlt}
                                disabled={bezahltWirdGespeichert.has(invoice.id)}
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
                    <Button variant="outline" size="sm" onClick={loadData} disabled={loading}>
                        <RefreshCw className={`w-4 h-4 mr-1 ${loading ? 'animate-spin' : ''}`} />
                        Aktualisieren
                    </Button>
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

                    {/* Controls – wie bei den Eingangsrechnungen */}
                    <div className="flex items-center justify-end gap-1 mb-3 animate-fadeInUp delay-1">
                        <label className="flex items-center gap-2 text-sm text-slate-600 cursor-pointer">
                            <input
                                type="checkbox"
                                checked={showBezahlt}
                                onChange={(e) => setShowBezahlt(e.target.checked)}
                                className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                            />
                            Bezahlte anzeigen
                        </label>
                        <BezahlteAnzeigenInfo />
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
                        ) : groupedInvoices.length === 0 && bezahlteGruppen.length === 0 ? (
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
                                                <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">
                                                    <span className="inline-flex items-center gap-1">
                                                        Status
                                                        <BezahltInfo />
                                                    </span>
                                                </th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-slate-100">
                                            {groupedInvoices.length === 0 ? (
                                                <tr>
                                                    <td colSpan={10} className="px-4 py-6 text-center text-sm text-slate-500">
                                                        Keine offenen Rechnungen vorhanden
                                                    </td>
                                                </tr>
                                            ) : groupedInvoices.map(gruppe => renderInvoiceRow(gruppe))}
                                        </tbody>
                                        {bezahlteGruppen.length > 0 && (
                                            <tbody className="divide-y divide-slate-100 border-t-2 border-slate-200" aria-label="Bezahlte Rechnungen">
                                                <tr className="bg-slate-50">
                                                    <th colSpan={10} scope="colgroup" className="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wide text-slate-600">
                                                        <span className="inline-flex items-center gap-1.5">
                                                            <CheckCircle2 className="w-4 h-4" aria-hidden="true" />
                                                            Bezahlt (letzte 12 Monate) – zählt nicht zu den offenen Posten
                                                        </span>
                                                    </th>
                                                </tr>
                                                {bezahlteGruppen.map(gruppe => renderInvoiceRow(gruppe, true))}
                                            </tbody>
                                        )}
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
        </PageLayout>
    );
}



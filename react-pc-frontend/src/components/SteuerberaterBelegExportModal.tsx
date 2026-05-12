import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Mail, X, Loader2, Calendar } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Select } from './ui/select-custom';

/**
 * Issue #58 – Steuerberater-Beleg-Export.
 *
 * Pattern: identisch zu {@link SteuerberaterEmailModal} fuer die monatliche
 * Stundenuebermittlung — Empfaenger + Ansprechpartner-Auswahl,
 * contentEditable HTML-Editor mit vorgeneriertem Inhalt. Der Buchhalter
 * kann die Tabelle vor dem Senden frei editieren.
 *
 * Die Beleg-PDFs werden NICHT mitgeschickt — der Steuerberater hat die
 * physischen Belege bereits; die Beleg-Nr. dient als Referenz.
 */

interface SteuerberaterAnsprechpartner {
    id: number;
    anrede: string | null;
    vorname: string;
    nachname: string;
    email: string;
    telefon: string;
    istLohnAnsprechpartner: boolean;
}

interface SteuerberaterKontakt {
    id: number;
    name: string;
    email: string;
    telefon: string;
    weitereEmails: string[];
    ansprechpartnerListe: SteuerberaterAnsprechpartner[];
}

interface ExportEntry {
    belegId: number;
    belegDatum: string | null;
    belegNummer: string | null;
    lieferantName: string | null;
    belegKategorie: string | null;
    dokumentTyp: string | null;
    sachkontoNummer: string | null;
    sachkontoBezeichnung: string | null;
    betragNetto: number | null;
    betragBrutto: number | null;
    betragMwst: number | null;
    mwstSatz: number | null;
    notiz: string | null;
    beschreibung: string | null;
    aufteilungsModus: 'VOLLSTAENDIG' | 'TEILWEISE' | null;
    gesamtBruttoOriginal: number | null;
    anzahlPositionenGesamt: number | null;
    anzahlPositionenFirma: number | null;
    positionenHinweis: string | null;
}

interface SteuerberaterBelegExportModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess?: () => void;
}

const FRONTEND_USER_STORAGE_KEY = 'frontendUserSelection';

interface FrontendUserSelection {
    id: number;
    displayName: string;
}

const getCurrentFrontendUser = (): FrontendUserSelection | null => {
    try {
        const raw = localStorage.getItem(FRONTEND_USER_STORAGE_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed.id === 'number') {
            return parsed as FrontendUserSelection;
        }
    } catch (err) {
        console.warn('Frontend-Profil konnte nicht gelesen werden:', err);
    }
    return null;
};

const wrapSignatureHtml = (rawHtml: string): string => {
    const trimmed = (rawHtml || '').trim();
    if (!trimmed) return '';
    if (/email-signature/i.test(trimmed)) return trimmed;
    return `<div class="email-signature" style="margin-top: 20px; padding-top: 10px; border-top: 1px solid #ddd;">${trimmed}</div>`;
};

// Whitelist gefaehrlicher Attribute & Tags. Der Editor ist contentEditable —
// der Buchhalter koennte beim Paste/Drag eigene Inhalte einfuegen. Wir
// entfernen <script>/<style>/<iframe>/<object>/<embed>/<link>, alle
// on*-Eventhandler-Attribute sowie javascript:/data:/vbscript:-URLs in
// href/src — sonst landet das per text/html im Mailclient des Steuerberaters.
const DANGEROUS_TAGS = ['script', 'style', 'iframe', 'object', 'embed', 'link', 'meta', 'base'];
const DANGEROUS_URL_PROTOCOLS = /^\s*(javascript|data|vbscript):/i;

const sanitizeNode = (node: Element): void => {
    Array.from(node.attributes).forEach(attr => {
        const name = attr.name.toLowerCase();
        if (name.startsWith('on')) {
            node.removeAttribute(attr.name);
            return;
        }
        if ((name === 'href' || name === 'src' || name === 'xlink:href')
            && DANGEROUS_URL_PROTOCOLS.test(attr.value || '')) {
            node.removeAttribute(attr.name);
            return;
        }
        if (name === 'contenteditable') {
            node.removeAttribute(attr.name);
        }
    });
    Array.from(node.children).forEach(child => sanitizeNode(child as Element));
};

const prepareHtmlForSending = (rawHtml: string): string => {
    const wrapper = document.createElement('div');
    wrapper.innerHTML = rawHtml || '';
    DANGEROUS_TAGS.forEach(tag => {
        wrapper.querySelectorAll(tag).forEach(n => n.remove());
    });
    sanitizeNode(wrapper);
    return wrapper.innerHTML.trim();
};

const buildAnredeZeile = (anrede: string | null | undefined, nachname: string | undefined): string => {
    const safeNachname = (nachname || '').trim();
    switch (anrede) {
        case 'HERR':
            return safeNachname ? `Sehr geehrter Herr ${safeNachname},` : 'Sehr geehrter Herr,';
        case 'FRAU':
            return safeNachname ? `Sehr geehrte Frau ${safeNachname},` : 'Sehr geehrte Frau,';
        case 'FAMILIE':
            return safeNachname ? `Sehr geehrte Familie ${safeNachname},` : 'Sehr geehrte Familie,';
        case 'DAMEN_HERREN':
        default:
            return 'Sehr geehrte Damen und Herren,';
    }
};

const MONATSNAMEN = [
    'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember',
];

// Handwerker-Sprache: keine technischen UPPERCASE-Enums in der
// Steuerberater-Mail. Mapping deckt die haeufigsten Beleg-Kategorien und
// KI-DokumentTypen ab; alles andere wird per fallback-Helper humanisiert.
const KATEGORIE_LABELS: Record<string, string> = {
    UNZUGEORDNET: 'Beleg',
    KASSE_EINNAHME: 'Kasse – Einnahme',
    KASSE_AUSGABE: 'Kasse – Ausgabe',
    PRIVATENTNAHME: 'Privatentnahme',
    PRIVATEINLAGE: 'Privateinlage',
    BANK: 'Bank',
    KREDITKARTE: 'Kreditkarte',
    SONSTIGER_BELEG: 'Sonstiger Beleg',
};
const DOKUMENT_TYP_LABELS: Record<string, string> = {
    RECHNUNG: 'Rechnung',
    GUTSCHRIFT: 'Gutschrift',
    LIEFERSCHEIN: 'Lieferschein',
    ANGEBOT: 'Angebot',
    AUFTRAGSBESTAETIGUNG: 'Auftragsbestätigung',
    MAHNUNG: 'Mahnung',
    KASSENBON: 'Kassenbon',
    BELEG: 'Beleg',
};
const labelForEnum = (raw: string | null | undefined, map: Record<string, string>): string => {
    if (!raw) return '–';
    if (map[raw]) return map[raw];
    // Fallback: KASSE_EINNAHME -> "Kasse Einnahme"
    return raw.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
};

// HTML-Escape, damit Lieferanten-/Beleg-Namen mit Sonderzeichen kein
// HTML in den E-Mail-Body einschleusen koennen (XSS-Schutz fuer Empfaenger).
const escapeHtml = (raw: string | null | undefined): string => {
    if (raw == null) return '';
    return String(raw)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
};

const formatEuro = (v: number | null | undefined): string =>
    v == null || !Number.isFinite(v)
        ? '–'
        : new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(v);

const formatDateDe = (iso: string | null): string => {
    if (!iso) return '–';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '–' : d.toLocaleDateString('de-DE');
};

export function SteuerberaterBelegExportModal({
    isOpen,
    onClose,
    onSuccess,
}: SteuerberaterBelegExportModalProps) {
    const heute = new Date();
    // Default: Vormonat – der Steuerberater bekommt typischerweise den abgeschlossenen Monat.
    const defaultMonat = heute.getMonth() === 0 ? 12 : heute.getMonth();
    const defaultJahr = heute.getMonth() === 0 ? heute.getFullYear() - 1 : heute.getFullYear();
    const [jahr, setJahr] = useState<number>(defaultJahr);
    const [monat, setMonat] = useState<number>(defaultMonat);

    const [steuerberaterListe, setSteuerberaterListe] = useState<SteuerberaterKontakt[]>([]);
    const [selectedSteuerberaterId, setSelectedSteuerberaterId] = useState<number | null>(null);
    const [selectedAnsprechpartnerId, setSelectedAnsprechpartnerId] = useState<number | null>(null);
    const [recipient, setRecipient] = useState('');
    const [subject, setSubject] = useState('');
    const [sending, setSending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [loadingSteuerberater, setLoadingSteuerberater] = useState(false);
    const [entries, setEntries] = useState<ExportEntry[]>([]);
    const [loadingEntries, setLoadingEntries] = useState(false);

    const editorRef = useRef<HTMLDivElement>(null);
    const signatureRef = useRef<string>('');
    const lastRenderedKeyRef = useRef<string>('');

    const selectedSteuerberater = useMemo(
        () => steuerberaterListe.find(sb => sb.id === selectedSteuerberaterId) || null,
        [steuerberaterListe, selectedSteuerberaterId]
    );

    const selectedAnsprechpartner = useMemo(
        () => selectedSteuerberater?.ansprechpartnerListe.find(ap => ap.id === selectedAnsprechpartnerId) || null,
        [selectedSteuerberater, selectedAnsprechpartnerId]
    );

    const verfuegbareEmails = useMemo<string[]>(() => {
        if (!selectedSteuerberater) return [];
        const set = new Set<string>();
        if (selectedSteuerberater.email) set.add(selectedSteuerberater.email);
        (selectedSteuerberater.weitereEmails || []).forEach(e => { if (e) set.add(e); });
        (selectedSteuerberater.ansprechpartnerListe || []).forEach(ap => { if (ap.email) set.add(ap.email); });
        return Array.from(set);
    }, [selectedSteuerberater]);

    // Datumsbereich: 1. bis letzter Tag des gewaehlten Monats
    const dateRange = useMemo(() => {
        const von = new Date(jahr, monat - 1, 1);
        const bis = new Date(jahr, monat, 0); // 0 = letzter Tag des Vormonats (= unser Monat)
        const iso = (d: Date) =>
            `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
        return { von: iso(von), bis: iso(bis) };
    }, [jahr, monat]);

    // Belege im Monat laden
    useEffect(() => {
        if (!isOpen) return;
        let cancelled = false;
        setLoadingEntries(true);
        const params = new URLSearchParams({ von: dateRange.von, bis: dateRange.bis });
        fetch(`/api/buchhaltung/steuerberater-export?${params.toString()}`)
            .then(r => r.ok ? r.json() : [])
            .then((list: ExportEntry[]) => {
                if (cancelled) return;
                setEntries(Array.isArray(list) ? list : []);
            })
            .catch(err => {
                if (cancelled) return;
                console.error('Beleg-Export laden fehlgeschlagen', err);
                setEntries([]);
            })
            .finally(() => {
                if (!cancelled) setLoadingEntries(false);
            });
        return () => { cancelled = true; };
    }, [isOpen, dateRange.von, dateRange.bis]);

    const generateEmailBody = useCallback((sig: string, anredeZeile: string) => {
        const monatName = MONATSNAMEN[monat - 1] || '';

        const headerCells = [
            'Datum', 'Beleg-Nr', 'Lieferant', 'Art',
            'Sachkonto', 'Netto', 'MwSt', 'Brutto', 'Hinweis',
        ];
        const headerHtml = headerCells.map(c =>
            `<th style="border:1px solid #dee2e6;padding:6px 8px;text-align:left;background:#f8f9fa;font-size:12px;">${c}</th>`
        ).join('');

        const rows = entries.map(e => {
            const teilweise = e.aufteilungsModus === 'TEILWEISE';
            const sachkonto = e.sachkontoBezeichnung
                ? `${e.sachkontoNummer ? e.sachkontoNummer + ' · ' : ''}${e.sachkontoBezeichnung}`
                : '–';
            const art = e.dokumentTyp
                ? labelForEnum(e.dokumentTyp, DOKUMENT_TYP_LABELS)
                : labelForEnum(e.belegKategorie, KATEGORIE_LABELS);
            const hinweisParts: string[] = [];
            if (teilweise) {
                hinweisParts.push(
                    `<strong>Teilbetrag für Betrieb</strong> `
                    + `(${e.anzahlPositionenFirma ?? '?'} von ${e.anzahlPositionenGesamt ?? '?'} Positionen, `
                    + `Gesamt-Brutto ${formatEuro(e.gesamtBruttoOriginal)}).`
                );
                if (e.positionenHinweis) {
                    hinweisParts.push(`Pos: ${escapeHtml(e.positionenHinweis)}`);
                }
            }
            if (e.notiz) hinweisParts.push(escapeHtml(e.notiz));
            const hinweis = hinweisParts.length > 0 ? hinweisParts.join('<br>') : '';

            const cells: string[] = [
                escapeHtml(formatDateDe(e.belegDatum)),
                escapeHtml(e.belegNummer || `#${e.belegId}`),
                escapeHtml(e.lieferantName || '–'),
                escapeHtml(art),
                escapeHtml(sachkonto),
                formatEuro(e.betragNetto),
                formatEuro(e.betragMwst),
                `<strong>${formatEuro(e.betragBrutto)}</strong>`,
                hinweis,
            ];
            const rowStyle = teilweise ? 'background:#fef2f2;' : '';
            return `<tr style="${rowStyle}">${cells.map(c =>
                `<td style="border:1px solid #dee2e6;padding:6px 8px;font-size:12px;vertical-align:top;">${c}</td>`
            ).join('')}</tr>`;
        }).join('');

        const sumBrutto = entries.reduce((s, e) => s + (e.betragBrutto ?? 0), 0);
        const sumNetto = entries.reduce((s, e) => s + (e.betragNetto ?? 0), 0);
        const sumMwst = entries.reduce((s, e) => s + (e.betragMwst ?? 0), 0);
        const teilweiseCount = entries.filter(e => e.aufteilungsModus === 'TEILWEISE').length;

        const footerHtml = `
            <tr style="background:#f8f9fa;font-weight:bold;">
                <td colspan="5" style="border:1px solid #dee2e6;padding:6px 8px;text-align:right;font-size:12px;">
                    Summe (${entries.length} Belege)
                </td>
                <td style="border:1px solid #dee2e6;padding:6px 8px;font-size:12px;">${formatEuro(sumNetto)}</td>
                <td style="border:1px solid #dee2e6;padding:6px 8px;font-size:12px;">${formatEuro(sumMwst)}</td>
                <td style="border:1px solid #dee2e6;padding:6px 8px;font-size:12px;">${formatEuro(sumBrutto)}</td>
                <td style="border:1px solid #dee2e6;padding:6px 8px;font-size:12px;"></td>
            </tr>`;

        const tableHtml = `
<table style="border-collapse:collapse;width:100%;font-family:Arial,sans-serif;font-size:13px;">
    <thead><tr>${headerHtml}</tr></thead>
    <tbody>${rows || `<tr><td colspan="${headerCells.length}" style="border:1px solid #dee2e6;padding:12px;text-align:center;color:#6b7280;">Keine validierten Belege im Monat.</td></tr>`}</tbody>
    ${entries.length > 0 ? `<tfoot>${footerHtml}</tfoot>` : ''}
</table>`;

        const teilweiseHinweis = teilweiseCount > 0
            ? `<p style="color:#475569;font-size:13px;">
                 <strong>${teilweiseCount} Mischbeleg(e):</strong>
                 Bei diesen Belegen ist nur ein Teil betrieblich – die ausgewählten Positionen
                 sind in der Spalte „Hinweis" aufgeführt. Der vollständige Beleg liegt physisch
                 vor und ist über die Beleg-Nr. zuordenbar.
               </p>`
            : '';

        return `<p>${anredeZeile}</p>

<p><br></p>

<p>anbei sende ich Ihnen die Aufstellung der validierten Belege für den Monat ${monatName} ${jahr}.</p>

<p>Die zugehörigen physischen Belege liegen Ihnen bereits vor – die <strong>Beleg-Nr.</strong> dient als Referenz für die Zuordnung.</p>

${teilweiseHinweis}

<p><br></p>

${tableHtml}

<p><br></p>

<p>Mit freundlichen Grüßen,</p>

${sig}
`;
    }, [entries, monat, jahr]);

    const loadSignature = useCallback(async (): Promise<string> => {
        try {
            const currentUser = getCurrentFrontendUser();
            const params = new URLSearchParams();
            if (currentUser?.id) params.set('frontendUserId', String(currentUser.id));
            const url = params.toString()
                ? `/api/email/signatures/default?${params.toString()}`
                : '/api/email/signatures/default';
            const res = await fetch(url);
            if (res.ok && res.status !== 204) {
                const data = await res.json();
                if (data.html) return wrapSignatureHtml(data.html);
            }
        } catch (err) {
            console.error('Signatur konnte nicht geladen werden:', err);
        }
        return '';
    }, []);

    useEffect(() => {
        if (!isOpen) {
            setSteuerberaterListe([]);
            setSelectedSteuerberaterId(null);
            setSelectedAnsprechpartnerId(null);
            setRecipient('');
            setSubject('');
            setError(null);
            signatureRef.current = '';
            lastRenderedKeyRef.current = '';
            return;
        }
        let cancelled = false;
        setLoadingSteuerberater(true);
        const monatName = MONATSNAMEN[monat - 1] || '';

        Promise.all([
            fetch('/api/firma/steuerberater').then(r => r.ok ? r.json() : []),
            fetch('/api/firma').then(r => r.ok ? r.json() : null).catch(() => null),
            loadSignature(),
        ]).then(([liste, firma, sig]) => {
            if (cancelled) return;
            const sbListe: SteuerberaterKontakt[] = Array.isArray(liste) ? liste : [];
            const firmenname = (firma && typeof firma === 'object' && firma.firmenname)
                ? String(firma.firmenname).trim() : '';
            setSubject(firmenname
                ? `Belegaufstellung ${monatName} ${jahr} - ${firmenname}`
                : `Belegaufstellung ${monatName} ${jahr}`);
            signatureRef.current = sig;
            setSteuerberaterListe(sbListe);

            if (sbListe.length > 0) {
                const firstSb = sbListe[0];
                setSelectedSteuerberaterId(firstSb.id);
                const ap = firstSb.ansprechpartnerListe?.[0] || null;
                setSelectedAnsprechpartnerId(ap?.id || null);
                setRecipient(ap?.email || firstSb.email || '');
            }
            setLoadingSteuerberater(false);
        }).catch(err => {
            if (cancelled) return;
            console.error('Steuerberater konnten nicht geladen werden:', err);
            setLoadingSteuerberater(false);
        });
        return () => { cancelled = true; };
    }, [isOpen, monat, jahr, loadSignature]);

    useEffect(() => {
        if (!selectedSteuerberater) return;
        const ap = selectedSteuerberater.ansprechpartnerListe?.[0] || null;
        setSelectedAnsprechpartnerId(ap?.id || null);
        setRecipient(ap?.email || selectedSteuerberater.email || '');
    }, [selectedSteuerberaterId]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        if (selectedAnsprechpartner?.email) {
            setRecipient(selectedAnsprechpartner.email);
        }
    }, [selectedAnsprechpartnerId]); // eslint-disable-line react-hooks/exhaustive-deps

    // Stabiler Fingerprint statt entries.length — zwei Monate mit zufaellig
    // gleich vielen Belegen sind nicht inhaltsgleich. Wir nehmen die sortierten
    // Beleg-IDs als Fingerprint und sortieren defensiv, falls die Reihenfolge
    // sich nach Re-Render aendert.
    const entriesFingerprint = useMemo(
        () => entries.map(e => e.belegId).sort((a, b) => a - b).join(','),
        [entries]
    );

    useEffect(() => {
        if (!isOpen) return;
        const key = `${selectedSteuerberaterId}|${selectedAnsprechpartnerId}|${monat}|${jahr}|${entriesFingerprint}|${loadingEntries}`;
        if (lastRenderedKeyRef.current === key) return;

        const anredeZeile = selectedAnsprechpartner
            ? buildAnredeZeile(selectedAnsprechpartner.anrede, selectedAnsprechpartner.nachname)
            : 'Sehr geehrte Damen und Herren,';

        const body = generateEmailBody(signatureRef.current, anredeZeile);
        if (editorRef.current) editorRef.current.innerHTML = body;
        lastRenderedKeyRef.current = key;
    }, [isOpen, selectedSteuerberaterId, selectedAnsprechpartnerId, selectedAnsprechpartner, monat, jahr, entriesFingerprint, loadingEntries, generateEmailBody]);

    const jahre: number[] = [];
    for (let j = heute.getFullYear() + 1; j >= heute.getFullYear() - 5; j--) jahre.push(j);

    const handleSend = async () => {
        if (!recipient.trim()) { setError('Bitte Empfänger angeben.'); return; }
        if (!subject.trim()) { setError('Bitte Betreff angeben.'); return; }
        setSending(true);
        setError(null);
        try {
            const currentUser = getCurrentFrontendUser();
            const formData = new FormData();
            const dtoPayload = {
                sender: null,
                recipients: [recipient.trim()],
                cc: [],
                subject: subject.trim(),
                body: prepareHtmlForSending(editorRef.current?.innerHTML || ''),
                direction: 'OUT',
                benutzer: currentUser?.displayName || '',
                frontendUserId: currentUser?.id || null,
            };
            formData.append('dto', new Blob([JSON.stringify(dtoPayload)], { type: 'application/json' }));
            const res = await fetch('/api/emails/send', { method: 'POST', body: formData });
            if (!res.ok) throw new Error('E-Mail senden fehlgeschlagen');
            if (onSuccess) onSuccess();
            onClose();
        } catch (err) {
            console.error('E-Mail senden fehlgeschlagen:', err);
            setError('E-Mail konnte nicht gesendet werden. Bitte erneut versuchen.');
        } finally {
            setSending(false);
        }
    };

    if (!isOpen) return null;

    const steuerberaterOptions = steuerberaterListe.map(sb => ({ value: String(sb.id), label: sb.name }));
    const ansprechpartnerOptions = (selectedSteuerberater?.ansprechpartnerListe || []).map(ap => {
        const fullName = [ap.vorname, ap.nachname].filter(Boolean).join(' ');
        return { value: String(ap.id), label: fullName || '(ohne Namen)' };
    });
    const empfaengerOptions = verfuegbareEmails.map(e => ({ value: e, label: e }));

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-white rounded-xl shadow-2xl w-[95%] max-w-5xl max-h-[95vh] flex flex-col">
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-rose-50 rounded-t-xl">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-rose-100 flex items-center justify-center">
                            <Mail className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <h2 className="text-lg font-semibold text-slate-900">Belegaufstellung an Steuerberater</h2>
                            <p className="text-sm text-slate-500">
                                {loadingEntries ? 'Belege werden geladen…' : `${entries.length} validierte Belege im Monat`}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" onClick={onClose} disabled={sending}>Abbrechen</Button>
                        <Button onClick={handleSend} disabled={sending || loadingEntries}
                                className="bg-rose-600 hover:bg-rose-700 text-white">
                            {sending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
                            Senden
                        </Button>
                        <Button variant="ghost" size="sm" onClick={onClose}
                                className="text-slate-500 hover:text-slate-700">
                            <X className="w-5 h-5" />
                        </Button>
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto p-6 space-y-4">
                    {error && (
                        <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                            {error}
                        </div>
                    )}

                    <div className="bg-rose-50/60 border border-rose-100 rounded-lg p-3 text-xs text-slate-600 flex items-start gap-2">
                        <Calendar className="w-4 h-4 text-rose-600 flex-shrink-0 mt-0.5" />
                        <span>
                            Diese E-Mail enthält die Aufstellung als HTML-Tabelle direkt im Body –
                            keine PDF-Anhänge. Die physischen Belege liegen dem Steuerberater bereits vor;
                            die Beleg-Nr. dient als Referenz.
                        </span>
                    </div>

                    <div className="grid grid-cols-2 gap-3 max-w-md">
                        <div className="space-y-1">
                            <Label>Monat</Label>
                            <Select
                                value={String(monat)}
                                onChange={v => setMonat(Number(v))}
                                options={MONATSNAMEN.map((label, i) => ({ value: String(i + 1), label }))}
                            />
                        </div>
                        <div className="space-y-1">
                            <Label>Jahr</Label>
                            <Select
                                value={String(jahr)}
                                onChange={v => setJahr(Number(v))}
                                options={jahre.map(j => ({ value: String(j), label: String(j) }))}
                            />
                        </div>
                    </div>

                    {loadingSteuerberater ? (
                        <div className="flex items-center gap-2 text-sm text-slate-500">
                            <Loader2 className="w-4 h-4 animate-spin" /> Steuerberater werden geladen...
                        </div>
                    ) : steuerberaterListe.length === 0 ? (
                        <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg text-rose-700 text-sm">
                            Es ist noch kein Steuerberater hinterlegt. Bitte unter <strong>Firma → Steuerberater</strong>
                            einen Kontakt mit mindestens einem Ansprechpartner anlegen.
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {steuerberaterListe.length > 1 && (
                                <div className="space-y-2">
                                    <Label>Steuerberater</Label>
                                    <Select
                                        options={steuerberaterOptions}
                                        value={selectedSteuerberaterId !== null ? String(selectedSteuerberaterId) : ''}
                                        onChange={v => setSelectedSteuerberaterId(v ? Number(v) : null)}
                                        placeholder="Steuerberater wählen"
                                    />
                                </div>
                            )}
                            <div className="space-y-2">
                                <Label>Ansprechpartner</Label>
                                {ansprechpartnerOptions.length > 0 ? (
                                    <Select
                                        options={ansprechpartnerOptions}
                                        value={selectedAnsprechpartnerId !== null ? String(selectedAnsprechpartnerId) : ''}
                                        onChange={v => setSelectedAnsprechpartnerId(v ? Number(v) : null)}
                                        placeholder="Ansprechpartner wählen"
                                    />
                                ) : (
                                    <p className="text-xs text-slate-600">
                                        Kein Ansprechpartner hinterlegt – Anrede wird „Sehr geehrte Damen und Herren" verwendet.
                                    </p>
                                )}
                            </div>
                        </div>
                    )}

                    <div className="space-y-2">
                        <Label>Empfänger</Label>
                        {empfaengerOptions.length > 1 ? (
                            <Select
                                options={empfaengerOptions}
                                value={recipient}
                                onChange={v => setRecipient(v)}
                                placeholder="E-Mail-Adresse wählen"
                            />
                        ) : (
                            <Input value={recipient} onChange={(e) => setRecipient(e.target.value)} placeholder="E-Mail-Adresse..." />
                        )}
                    </div>

                    <div className="space-y-2">
                        <Label>Betreff</Label>
                        <Input value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="Betreff eingeben..." className="font-medium" />
                    </div>

                    <div className="space-y-2">
                        <Label>Nachricht (direkt bearbeitbar)</Label>
                        <div className="border border-slate-200 rounded-lg overflow-hidden bg-white">
                            <div
                                ref={editorRef}
                                className="p-4 min-h-[300px] overflow-auto outline-none focus:ring-2 focus:ring-rose-200"
                                style={{ maxHeight: '500px' }}
                                contentEditable
                                suppressContentEditableWarning
                            />
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

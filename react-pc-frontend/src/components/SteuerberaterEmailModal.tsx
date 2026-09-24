import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Mail, X, Loader2 } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Select } from './ui/select-custom';

interface MitarbeiterStunden {
    mitarbeiterId: number;
    mitarbeiterName: string;
    tagessollWoche: number;
    sollstundenMonat: number; // Monatliche Sollstunden
    arbeitsstunden: number; // Tatsächliche Arbeitsstunden (für Frontend)
    urlaub: number;
    feiertage: number;
    krankheit: number;
    fortbildung: number;
}

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

interface SteuerberaterEmailModalProps {
    isOpen: boolean;
    onClose: () => void;
    mitarbeiterDaten: MitarbeiterStunden[];
    monat: number;
    jahr: number;
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
    if (/email-signature/i.test(trimmed)) {
        return trimmed;
    }
    return `<div class="email-signature" style="margin-top: 20px; padding-top: 10px; border-top: 1px solid #ddd;">${trimmed}</div>`;
};

const prepareHtmlForSending = (rawHtml: string): string => {
    const wrapper = document.createElement('div');
    wrapper.innerHTML = rawHtml || '';
    wrapper.querySelectorAll('script, style').forEach(n => n.remove());
    wrapper.querySelectorAll('[contenteditable]').forEach(n => n.removeAttribute('contenteditable'));
    return wrapper.innerHTML.trim();
};

/**
 * Baut die Anrede-Zeile aus Anrede + Nachname.
 * Bei DAMEN_HERREN entfällt der Nachname; bei FAMILIE wird der Nachname angehängt.
 */
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

export function SteuerberaterEmailModal({
    isOpen,
    onClose,
    mitarbeiterDaten,
    monat,
    jahr,
    onSuccess,
}: SteuerberaterEmailModalProps) {
    const [steuerberaterListe, setSteuerberaterListe] = useState<SteuerberaterKontakt[]>([]);
    const [selectedSteuerberaterId, setSelectedSteuerberaterId] = useState<number | null>(null);
    const [selectedAnsprechpartnerId, setSelectedAnsprechpartnerId] = useState<number | null>(null);
    const [recipient, setRecipient] = useState('');
    const [subject, setSubject] = useState('');
    const [sending, setSending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [loadingSteuerberater, setLoadingSteuerberater] = useState(false);

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

    const getMonthName = (m: number) => {
        const months = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
            'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
        return months[m - 1] || '';
    };

    // Generate the email body with inline table
    // WICHTIG: Für den Steuerberater berechnen wir "Arbeitsstunden" als:
    // Sollstunden - Krankheit - Urlaub - Fortbildung (da wir nach Sollstunden bezahlen)
    const generateEmailBody = useCallback((sig: string, anredeZeile: string) => {
        const monthName = getMonthName(monat);

        // Build HTML table
        let tableHtml = `
<table style="border-collapse: collapse; width: 100%; max-width: 700px; font-family: Arial, sans-serif; font-size: 14px;">
    <thead>
        <tr style="background-color: #f8f9fa;">
            <th style="border: 1px solid #dee2e6; padding: 8px; text-align: left;">Nr.</th>
            <th style="border: 1px solid #dee2e6; padding: 8px; text-align: left;">Name</th>
            <th style="border: 1px solid #dee2e6; padding: 8px; text-align: center;">Tagessoll</th>
            <th style="border: 1px solid #dee2e6; padding: 8px; text-align: center;">Arbeitsstunden</th>
            <th style="border: 1px solid #dee2e6; padding: 8px; text-align: center;">Urlaub</th>
            <th style="border: 1px solid #dee2e6; padding: 8px; text-align: center;">Feiertage</th>
            <th style="border: 1px solid #dee2e6; padding: 8px; text-align: center;">Krankheit</th>
        </tr>
    </thead>
    <tbody>`;

        mitarbeiterDaten.forEach((m, index) => {
            // Für den Steuerberater: Arbeitsstunden = Sollstunden - Abwesenheiten
            // (weil wir nach Sollstunden bezahlen, nicht nach echten Arbeitsstunden)
            const adjustedArbeitsstunden = Math.round((m.sollstundenMonat - m.krankheit - m.urlaub - m.feiertage - m.fortbildung) * 10) / 10;

            tableHtml += `
        <tr>
            <td style="border: 1px solid #dee2e6; padding: 8px;">${index + 1}</td>
            <td style="border: 1px solid #dee2e6; padding: 8px;">${m.mitarbeiterName}</td>
            <td style="border: 1px solid #dee2e6; padding: 8px; text-align: center;">${m.tagessollWoche}</td>
            <td style="border: 1px solid #dee2e6; padding: 8px; text-align: center;">${adjustedArbeitsstunden}</td>
            <td style="border: 1px solid #dee2e6; padding: 8px; text-align: center;">${m.urlaub}</td>
            <td style="border: 1px solid #dee2e6; padding: 8px; text-align: center;">${m.feiertage}</td>
            <td style="border: 1px solid #dee2e6; padding: 8px; text-align: center;">${m.krankheit}</td>
        </tr>`;
        });

        tableHtml += `
    </tbody>
</table>`;

        const bodyHtml = `<p>${anredeZeile}</p>

<p><br></p>

<p>anbei sende ich Ihnen die Stundenaufstellung unserer Mitarbeiter für den Monat ${monthName}.</p>

<p style="color: #dc2626; font-weight: bold;">Alle Werte sind in Stunden angegeben!</p>

<p><br></p>

${tableHtml}

<p><br></p>

<p>Mit freundlichen Grüßen,</p>

${sig}
`;
        return bodyHtml;
    }, [mitarbeiterDaten, monat]);

    // Load signature once
    const loadSignature = useCallback(async () => {
        try {
            const currentUser = getCurrentFrontendUser();
            const params = new URLSearchParams();
            if (currentUser?.id) {
                params.set('frontendUserId', String(currentUser.id));
            }
            const signatureUrl = params.toString()
                ? `/api/email/signatures/default?${params.toString()}`
                : '/api/email/signatures/default';

            const res = await fetch(signatureUrl);
            if (res.ok && res.status !== 204) {
                const data = await res.json();
                if (data.html) {
                    return wrapSignatureHtml(data.html);
                }
            }
        } catch (err) {
            console.error('Signatur konnte nicht geladen werden:', err);
        }
        return '';
    }, []);

    // Open: load Steuerberater + signature
    useEffect(() => {
        if (!isOpen) {
            // Reset on close
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
        const monthName = getMonthName(monat);

        Promise.all([
            fetch('/api/firma/steuerberater').then(r => r.ok ? r.json() : []),
            fetch('/api/firma').then(r => r.ok ? r.json() : null).catch(() => null),
            loadSignature(),
        ]).then(([liste, firma, sig]) => {
            if (cancelled) return;
            const sbListe: SteuerberaterKontakt[] = Array.isArray(liste) ? liste : [];
            const firmenname = (firma && typeof firma === 'object' && firma.firmenname)
                ? firma.firmenname.trim()
                : '';
            setSubject(firmenname
                ? `Stundenaufstellung ${monthName} ${jahr} - ${firmenname}`
                : `Stundenaufstellung ${monthName} ${jahr}`);
            signatureRef.current = sig;
            setSteuerberaterListe(sbListe);

            // Default-Auswahl: erster Steuerberater + sein Lohn-Ansprechpartner
            if (sbListe.length > 0) {
                const firstSb = sbListe[0];
                setSelectedSteuerberaterId(firstSb.id);

                const lohnAp = (firstSb.ansprechpartnerListe || []).find(ap => ap.istLohnAnsprechpartner)
                    || firstSb.ansprechpartnerListe?.[0]
                    || null;
                setSelectedAnsprechpartnerId(lohnAp?.id || null);

                const defaultEmail = lohnAp?.email || firstSb.email || '';
                setRecipient(defaultEmail);
            }
            setLoadingSteuerberater(false);
        }).catch(err => {
            if (cancelled) return;
            console.error('Steuerberater konnten nicht geladen werden:', err);
            setLoadingSteuerberater(false);
        });

        return () => { cancelled = true; };
    }, [isOpen, monat, jahr, loadSignature]);

    // Bei Wechsel des Steuerberaters: Lohn-Ansprechpartner als Default setzen
    useEffect(() => {
        if (!selectedSteuerberater) return;
        const lohnAp = (selectedSteuerberater.ansprechpartnerListe || []).find(ap => ap.istLohnAnsprechpartner)
            || selectedSteuerberater.ansprechpartnerListe?.[0]
            || null;
        setSelectedAnsprechpartnerId(lohnAp?.id || null);
        setRecipient(lohnAp?.email || selectedSteuerberater.email || '');
    }, [selectedSteuerberaterId]); // eslint-disable-line react-hooks/exhaustive-deps

    // Bei Wechsel des Ansprechpartners: dessen Email als Empfänger setzen (falls vorhanden)
    useEffect(() => {
        if (selectedAnsprechpartner?.email) {
            setRecipient(selectedAnsprechpartner.email);
        }
    }, [selectedAnsprechpartnerId]); // eslint-disable-line react-hooks/exhaustive-deps

    // Editor-Inhalt rendern, wenn sich Anrede/Ansprechpartner/Steuerberater ändert.
    // Wir nutzen einen Key, damit wir nicht bei jedem Tippen im contentEditable
    // den Inhalt überschreiben.
    useEffect(() => {
        if (!isOpen) return;
        const key = `${selectedSteuerberaterId}|${selectedAnsprechpartnerId}|${monat}|${jahr}|${mitarbeiterDaten.length}`;
        if (lastRenderedKeyRef.current === key) return;

        const anredeZeile = selectedAnsprechpartner
            ? buildAnredeZeile(selectedAnsprechpartner.anrede, selectedAnsprechpartner.nachname)
            : 'Sehr geehrte Damen und Herren,';

        const body = generateEmailBody(signatureRef.current, anredeZeile);
        if (editorRef.current) {
            editorRef.current.innerHTML = body;
        }
        lastRenderedKeyRef.current = key;
    }, [isOpen, selectedSteuerberaterId, selectedAnsprechpartnerId, selectedAnsprechpartner, monat, jahr, mitarbeiterDaten.length, generateEmailBody]);

    const handleSend = async () => {
        if (!recipient.trim()) {
            setError('Bitte Empfänger angeben.');
            return;
        }
        if (!subject.trim()) {
            setError('Bitte Betreff angeben.');
            return;
        }

        setSending(true);
        setError(null);

        try {
            const currentUser = getCurrentFrontendUser();

            const formData = new FormData();

            const dtoPayload = {
                // Leerer sender = Backend loest aus frontendUserId auf (im
                // FirmaEditor konfigurierte und am Benutzer hinterlegte Adresse).
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

            const res = await fetch('/api/emails/send', {
                method: 'POST',
                body: formData,
            });

            if (!res.ok) {
                throw new Error('E-Mail senden fehlgeschlagen');
            }

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

    const steuerberaterOptions = steuerberaterListe.map(sb => ({
        value: String(sb.id),
        label: sb.name,
    }));

    const ansprechpartnerOptions = (selectedSteuerberater?.ansprechpartnerListe || []).map(ap => {
        const fullName = [ap.vorname, ap.nachname].filter(Boolean).join(' ');
        const labelParts = [fullName || '(ohne Namen)'];
        if (ap.istLohnAnsprechpartner) labelParts.push('· Löhne');
        return { value: String(ap.id), label: labelParts.join(' ') };
    });

    const empfaengerOptions = verfuegbareEmails.map(e => ({ value: e, label: e }));

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-white rounded-xl shadow-2xl w-[90%] max-w-4xl max-h-[90vh] flex flex-col">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-rose-50 rounded-t-xl">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-rose-100 flex items-center justify-center">
                            <Mail className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <h2 className="text-lg font-semibold text-slate-900">Stundenübermittlung</h2>
                            <p className="text-sm text-slate-500">
                                {mitarbeiterDaten.length} Mitarbeiter für {getMonthName(monat)} {jahr}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" onClick={onClose} disabled={sending}>
                            Abbrechen
                        </Button>
                        <Button
                            onClick={handleSend}
                            disabled={sending}
                            className="bg-rose-600 hover:bg-rose-700 text-white"
                        >
                            {sending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
                            Senden
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
                <div className="flex-1 overflow-y-auto p-6 space-y-4">
                    {error && (
                        <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                            {error}
                        </div>
                    )}

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
                            {/* Steuerberater-Auswahl (nur bei mehreren) */}
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

                            {/* Ansprechpartner */}
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

                    <div className="grid grid-cols-1 gap-4">
                        {/* Empfänger */}
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
                                <Input
                                    value={recipient}
                                    onChange={(e) => setRecipient(e.target.value)}
                                    placeholder="E-Mail-Adresse..."
                                />
                            )}
                        </div>

                        {/* Subject */}
                        <div className="space-y-2">
                            <Label>Betreff</Label>
                            <Input
                                value={subject}
                                onChange={(e) => setSubject(e.target.value)}
                                placeholder="Betreff eingeben..."
                                className="font-medium"
                            />
                        </div>

                        {/* Editable Email Content */}
                        <div className="space-y-2">
                            <Label>Nachricht (direkt bearbeitbar)</Label>
                            <div className="border border-slate-200 rounded-lg overflow-hidden bg-white">
                                <div
                                    ref={editorRef}
                                    className="p-4 min-h-[300px] overflow-auto outline-none focus:ring-2 focus:ring-rose-200"
                                    style={{ maxHeight: '400px' }}
                                    contentEditable
                                    suppressContentEditableWarning
                                />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

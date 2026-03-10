import { useState, useEffect, useRef, useCallback } from 'react';
import { Mail, X, Loader2 } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';

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

export function SteuerberaterEmailModal({
    isOpen,
    onClose,
    mitarbeiterDaten,
    monat,
    jahr,
    onSuccess,
}: SteuerberaterEmailModalProps) {
    const [recipient, setRecipient] = useState('a.dengel@clausmueller-steuerberater.de');
    const [subject, setSubject] = useState('');
    const [sending, setSending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [initialized, setInitialized] = useState(false);

    const editorRef = useRef<HTMLDivElement>(null);

    const getMonthName = (m: number) => {
        const months = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
            'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
        return months[m - 1] || '';
    };

    // Generate the email body with inline table
    // WICHTIG: Für den Steuerberater berechnen wir "Arbeitsstunden" als:
    // Sollstunden - Krankheit - Urlaub - Fortbildung (da wir nach Sollstunden bezahlen)
    const generateEmailBody = useCallback((sig: string) => {
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

        const bodyHtml = `<p>Sehr geehrter Hr. Dengel,</p>

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

    // Load signature
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
                    const wrappedSig = wrapSignatureHtml(data.html);
                    return wrappedSig;
                }
            }
        } catch (err) {
            console.error('Signatur konnte nicht geladen werden:', err);
        }
        return '';
    }, []);

    // Initialize - only once when modal opens
    useEffect(() => {
        if (!isOpen) {
            setInitialized(false);
            return;
        }
        if (initialized) return;

        const monthName = getMonthName(monat);
        setSubject(`Stundenaufstellung ${monthName} ${jahr} - Example Company`);

        loadSignature().then(sig => {
            const body = generateEmailBody(sig);
            if (editorRef.current) {
                editorRef.current.innerHTML = body;
            }
            setInitialized(true);
        });
    }, [isOpen, monat, jahr, loadSignature, generateEmailBody, initialized]);

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
                sender: 'info@example-company.de',
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

                    <div className="grid grid-cols-1 gap-4">
                        {/* Recipient */}
                        <div className="space-y-2">
                            <Label>Empfänger</Label>
                            <Input
                                value={recipient}
                                onChange={(e) => setRecipient(e.target.value)}
                                placeholder="E-Mail-Adresse..."
                            />
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

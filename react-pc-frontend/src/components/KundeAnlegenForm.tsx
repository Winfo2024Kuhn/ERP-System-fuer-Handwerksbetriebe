import React, { useState, useEffect } from 'react';
import { ChevronLeft } from 'lucide-react';
import { Button } from './ui/button';
import { Select } from './ui/select-custom';
import { AddressAutocomplete } from './AddressAutocomplete';
import { PhoneInput } from './PhoneInput';
import { KundeDuplikatHinweis, type KundeDuplikatTreffer } from './KundeDuplikatHinweis';
import { KundeDuplikatBestaetigungModal } from './KundeDuplikatBestaetigungModal';
import type { Kunde } from '../types';

interface KundeAnlegenFormProps {
    onSuccess: (kunde: Kunde) => void;
    onBack: () => void;
    /** Kompakte Variante (z.B. innerhalb eines Modals): kein "Zurück"-Header. */
    headerVariant?: 'voll' | 'kompakt';
}

const ANREDE_OPTIONS = [
    { value: 'HERR', label: 'Sehr geehrter Herr' },
    { value: 'FRAU', label: 'Sehr geehrte Frau' },
    { value: 'DAMEN_HERREN', label: 'Sehr geehrte Damen und Herren' },
    { value: 'FAMILIE', label: 'Sehr geehrte Familie' },
];

/**
 * Geteiltes Formular zum Anlegen eines neuen Kunden – verwendet von
 * Kundeneditor, ProjektErstellenModal und AnfrageEditor.
 *
 * <p>Beinhaltet den Live-Duplikat-Hinweis (debounced) und behandelt einen
 * 409-Response von POST /api/kunden, indem der Bestätigungs-Modal geöffnet
 * wird. Bestätigt der User die Neuanlage, wird der POST mit Header
 * {@code X-Duplikat-Bestaetigt: true} wiederholt.
 */
export const KundeAnlegenForm: React.FC<KundeAnlegenFormProps> = ({
    onSuccess, onBack, headerVariant = 'voll',
}) => {
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [kundennummerError, setKundennummerError] = useState<string | null>(null);
    const [autoKundennummer, setAutoKundennummer] = useState(true);
    const [duplikatModal, setDuplikatModal] = useState<{ duplikate: KundeDuplikatTreffer[] } | null>(null);

    const [formData, setFormData] = useState({
        name: '',
        kundennummer: '',
        anrede: '',
        ansprechspartner: '',
        strasse: '',
        plz: '',
        ort: '',
        telefon: '',
        mobiltelefon: '',
        email: '',
        zahlungsziel: 8,
    });

    useEffect(() => {
        if (autoKundennummer) {
            fetch('/api/kunden/next-kundennummer')
                .then(res => res.json())
                .then(data => setFormData(prev => ({ ...prev, kundennummer: data.kundennummer || '' })))
                .catch(() => {});
        }
    }, [autoKundennummer]);

    /** Führt den eigentlichen POST aus. Bei 409 (Duplikat) wird der Modal geöffnet. */
    const speichern = async (bestaetigt: boolean): Promise<void> => {
        if (!formData.name.trim()) {
            setError('Bitte Kundennamen angeben');
            return;
        }
        if (!autoKundennummer && !formData.kundennummer.trim()) {
            setError('Bitte Kundennummer angeben oder "Automatisch" aktivieren');
            return;
        }

        setSaving(true);
        setError(null);
        setKundennummerError(null);
        try {
            const payload = {
                name: formData.name.trim(),
                kundennummer: autoKundennummer ? '' : formData.kundennummer.trim(),
                anrede: formData.anrede || null,
                ansprechspartner: formData.ansprechspartner.trim() || null,
                strasse: formData.strasse.trim() || null,
                plz: formData.plz.trim() || null,
                ort: formData.ort.trim() || null,
                telefon: formData.telefon.trim() || null,
                mobiltelefon: formData.mobiltelefon.trim() || null,
                zahlungsziel: formData.zahlungsziel || 8,
                kundenEmails: formData.email.trim() ? [formData.email.trim()] : [],
            };

            const headers: Record<string, string> = { 'Content-Type': 'application/json' };
            if (bestaetigt) headers['X-Duplikat-Bestaetigt'] = 'true';

            const res = await fetch('/api/kunden', {
                method: 'POST',
                headers,
                body: JSON.stringify(payload),
            });

            if (res.status === 409) {
                const body = await res.json().catch(() => ({}));
                // 409 mit Duplikat-Treffern: Modal öffnen.
                if (body && Array.isArray(body.duplikate) && body.duplikate.length > 0) {
                    setDuplikatModal({ duplikate: body.duplikate });
                    return;
                }
                // 409 ohne Treffer-Liste = Kundennummer-Konflikt
                setKundennummerError(body.message || 'Kundennummer ist bereits vergeben.');
                return;
            }

            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                throw new Error(errData.message || errData.detail || 'Kunde konnte nicht angelegt werden');
            }

            const created: Kunde = await res.json();
            onSuccess(created);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Fehler beim Speichern');
        } finally {
            setSaving(false);
        }
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        speichern(false);
    };

    /** User klickt auf einen bestehenden Kunden im Duplikat-Modal: einfach übernehmen. */
    const handleBestehendenWaehlen = async (treffer: KundeDuplikatTreffer) => {
        setDuplikatModal(null);
        try {
            const res = await fetch(`/api/kunden/${treffer.id}`);
            if (res.ok) {
                const kunde: Kunde = await res.json();
                onSuccess(kunde);
                return;
            }
        } catch (err) {
            console.error('Kunden-Detail konnte nicht geladen werden:', err);
        }
        // Fallback: Treffer-Daten direkt nutzen
        onSuccess({
            id: treffer.id,
            name: treffer.name,
            kundennummer: treffer.kundennummer,
            ansprechspartner: treffer.ansprechspartner,
            strasse: treffer.strasse,
            plz: treffer.plz,
            ort: treffer.ort,
            kundenEmails: treffer.kundenEmails,
        } as Kunde);
    };

    return (
        <div className="space-y-4">
            {headerVariant === 'voll' && (
                <div className="flex items-center gap-3 mb-4">
                    <button onClick={onBack} className="text-slate-500 hover:text-slate-700">
                        <ChevronLeft className="w-5 h-5" />
                    </button>
                    <h3 className="text-lg font-semibold text-slate-900">Neuen Kunden anlegen</h3>
                </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
                {error && (
                    <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">{error}</div>
                )}

                {/* Live-Duplikat-Hinweis */}
                <KundeDuplikatHinweis
                    email={formData.email}
                    telefon={formData.telefon}
                    mobiltelefon={formData.mobiltelefon}
                    name={formData.name}
                    plz={formData.plz}
                    strasse={formData.strasse}
                    onTrefferKlick={handleBestehendenWaehlen}
                />

                {/* Name & Kundennummer */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Kundenname *</label>
                        <input
                            type="text"
                            value={formData.name}
                            onChange={e => setFormData(prev => ({ ...prev, name: e.target.value }))}
                            placeholder="Firma / Name"
                            className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </div>
                    <div>
                        <div className="flex items-center justify-between mb-1">
                            <label className="block text-sm font-medium text-slate-700">Kundennummer</label>
                            <label className="flex items-center gap-1.5 text-sm cursor-pointer select-none">
                                <input
                                    type="checkbox"
                                    checked={autoKundennummer}
                                    onChange={e => {
                                        const checked = e.target.checked;
                                        setAutoKundennummer(checked);
                                        if (!checked) setFormData(prev => ({ ...prev, kundennummer: '' }));
                                    }}
                                    className="accent-rose-600 w-4 h-4"
                                />
                                <span className="text-slate-600">Automatisch</span>
                            </label>
                        </div>
                        <input
                            type="text"
                            value={formData.kundennummer}
                            onChange={e => {
                                setFormData(prev => ({ ...prev, kundennummer: e.target.value }));
                                setKundennummerError(null);
                            }}
                            placeholder={autoKundennummer ? 'Wird automatisch vergeben' : 'z.B. K-1234'}
                            disabled={autoKundennummer}
                            className={`w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:bg-slate-50 disabled:text-slate-400 ${kundennummerError ? 'border-rose-500 bg-rose-50' : 'border-slate-200'}`}
                        />
                        {kundennummerError && (
                            <p className="mt-1 text-xs text-rose-600">{kundennummerError}</p>
                        )}
                    </div>
                </div>

                {/* Anrede & Ansprechpartner */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Anrede</label>
                        <Select
                            options={ANREDE_OPTIONS}
                            value={formData.anrede}
                            onChange={val => setFormData(prev => ({ ...prev, anrede: val }))}
                            placeholder="Anrede wählen"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Ansprechpartner</label>
                        <input
                            type="text"
                            value={formData.ansprechspartner}
                            onChange={e => setFormData(prev => ({ ...prev, ansprechspartner: e.target.value }))}
                            placeholder="Vor- und Nachname"
                            className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </div>
                </div>

                {/* E-Mail */}
                <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">E-Mail</label>
                    <input
                        type="email"
                        value={formData.email}
                        onChange={e => setFormData(prev => ({ ...prev, email: e.target.value }))}
                        placeholder="email@example.com"
                        className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                </div>

                {/* Telefon & Mobiltelefon */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Telefon</label>
                        <PhoneInput
                            value={formData.telefon}
                            onChange={v => setFormData(prev => ({ ...prev, telefon: v }))}
                            variant="festnetz"
                            autoPrefillAreaCode
                            plz={formData.plz}
                            ort={formData.ort}
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Mobiltelefon</label>
                        <PhoneInput
                            value={formData.mobiltelefon}
                            onChange={v => setFormData(prev => ({ ...prev, mobiltelefon: v }))}
                            variant="mobil"
                        />
                    </div>
                </div>

                {/* Adresse */}
                <AddressAutocomplete
                    value={{ strasse: formData.strasse, plz: formData.plz, ort: formData.ort }}
                    onChange={next => setFormData(prev => ({
                        ...prev,
                        strasse: next.strasse,
                        plz: next.plz,
                        ort: next.ort,
                    }))}
                />

                {/* Zahlungsziel */}
                <div className="md:w-1/3">
                    <label className="block text-sm font-medium text-slate-700 mb-1">Zahlungsziel (Tage)</label>
                    <input
                        type="number"
                        min="0"
                        value={formData.zahlungsziel}
                        onChange={e => setFormData(prev => ({ ...prev, zahlungsziel: parseInt(e.target.value) || 8 }))}
                        className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                </div>

                <div className="flex justify-end gap-3 pt-2">
                    <Button type="button" variant="outline" onClick={onBack} disabled={saving}>
                        Abbrechen
                    </Button>
                    <Button type="submit" disabled={saving} className="bg-rose-600 text-white hover:bg-rose-700">
                        {saving ? 'Speichern...' : 'Kunde anlegen'}
                    </Button>
                </div>
            </form>

            <KundeDuplikatBestaetigungModal
                isOpen={!!duplikatModal}
                duplikate={duplikatModal?.duplikate || []}
                onAbbrechen={() => setDuplikatModal(null)}
                onBestehendenWaehlen={handleBestehendenWaehlen}
                onTrotzdemAnlegen={() => {
                    setDuplikatModal(null);
                    speichern(true);
                }}
            />
        </div>
    );
};

export default KundeAnlegenForm;

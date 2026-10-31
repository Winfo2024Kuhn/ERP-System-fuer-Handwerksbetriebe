import React, { useState } from 'react';
import { CheckCircle, Eye, EyeOff, Loader2, Save, XCircle } from 'lucide-react';
import { Card } from '../ui/card';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Button } from '../ui/button';
import { cn } from '../../lib/utils';
import type { TestResult } from './settingsApi';

/**
 * Optische Bausteine der Einstellungs-Bereiche.
 *
 * <p>Jeder Bereich (E-Mail, Dateien, KI, Zeiterfassung) lädt und speichert
 * eigenständig. Was sie teilen, ist rein optischer Natur: Karten-Kopf,
 * Passwortfeld mit Auge, Ergebnis-Banner eines Verbindungstests und der
 * Speichern-Knopf. Ohne diese Datei stünde derselbe Markup-Block viermal
 * im Projekt und würde beim nächsten Design-Wechsel viermal auseinanderlaufen.</p>
 */

interface SettingsCardProps {
    icon: React.ReactNode;
    title: string;
    /** Erklärung in Handwerker-Sprache, direkt unter der Überschrift. */
    description?: React.ReactNode;
    /** Optionaler Info-Knopf (`InfoButton`) direkt neben der Überschrift. */
    info?: React.ReactNode;
    children: React.ReactNode;
    className?: string;
}

/** Karte mit Icon, Überschrift und Erklärtext — der Rahmen jedes Bereichs. */
export function SettingsCard({ icon, title, description, info, children, className }: SettingsCardProps) {
    const ueberschrift = (
        <h3 className={cn('text-lg font-semibold text-slate-900 flex items-center gap-2', !info && 'mb-2')}>
            {icon}
            {title}
        </h3>
    );
    return (
        <Card className={cn('p-6', className)}>
            {/* Der Info-Knopf steht neben, nicht in der Überschrift – sonst hieße die Überschrift für Screenreader „… Mehr zu: …“. */}
            {info ? <div className="mb-2 flex items-center gap-1.5">{ueberschrift}{info}</div> : ueberschrift}
            {description && <div className="text-sm text-slate-500 mb-5 space-y-2">{description}</div>}
            {children}
        </Card>
    );
}

interface PasswordFieldProps {
    id: string;
    label: string;
    value: string;
    onChange: (value: string) => void;
    /** Backend meldet, dass schon ein Passwort hinterlegt ist. */
    isSet?: boolean;
    placeholder?: string;
    /** Platzhalter, solange schon ein Passwort gespeichert ist. */
    isSetPlaceholder?: string;
    /** Gesperrt, z. B. weil der Server den Schlüssel gerade nicht speichern kann. */
    disabled?: boolean;
    /** Id eines Erklärtextes, den Screenreader zum Feld vorlesen. */
    beschreibungId?: string;
    /** Optionaler Info-Knopf (`InfoButton`) direkt neben der Beschriftung. */
    info?: React.ReactNode;
    className?: string;
}

/**
 * Passwortfeld mit Auge zum Aufdecken.
 *
 * <p>Ist bereits ein Passwort gespeichert, steht ein „✓ gesetzt" am Label und
 * der Platzhalter sagt „(leer lassen = unverändert)". Das Passwort selbst
 * kommt nie vom Server zurück — leer lassen heißt daher immer „so lassen",
 * nicht „löschen".</p>
 */
export function PasswordField({
    id,
    label,
    value,
    onChange,
    isSet = false,
    placeholder,
    isSetPlaceholder = '(leer lassen = unverändert)',
    disabled = false,
    beschreibungId,
    info,
    className,
}: PasswordFieldProps) {
    const [visible, setVisible] = useState(false);
    const beschriftung = (
        <Label htmlFor={id} className={info ? 'mb-0' : undefined}>
            {label}
            {isSet && !value && (
                <span className="ml-2 text-xs text-emerald-600 font-normal">✓ gesetzt</span>
            )}
        </Label>
    );
    return (
        <div className={className}>
            {/* Der Info-Knopf steht neben, nicht im Label – sonst hieße das Feld „… Mehr zu: …“. */}
            {info ? <div className="mb-1.5 flex items-center gap-1">{beschriftung}{info}</div> : beschriftung}
            {/* Auge NEBEN statt über dem Eingabefeld (gemeinsamer Rahmen): so verdeckt der
                Knopf nie Text und überlappt das Feld nicht – auch nicht in Dialogen. */}
            <div className={cn(
                'flex items-center rounded border border-slate-200 focus-within:ring-2 focus-within:ring-rose-500',
                disabled ? 'bg-slate-50' : 'bg-white',
            )}>
                <Input
                    id={id}
                    disabled={disabled}
                    aria-describedby={beschreibungId}
                    type={visible ? 'text' : 'password'}
                    value={value}
                    onChange={(e) => onChange(e.target.value)}
                    placeholder={isSet ? isSetPlaceholder : placeholder}
                    autoComplete="new-password"
                    className="min-w-0 flex-1 border-0 bg-transparent focus:ring-0 disabled:cursor-not-allowed disabled:text-slate-400"
                />
                <button
                    type="button"
                    disabled={disabled}
                    aria-label={visible ? 'Passwort verbergen' : 'Passwort anzeigen'}
                    className="mr-1 shrink-0 rounded p-1 text-slate-400 hover:text-slate-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 disabled:cursor-not-allowed disabled:hover:text-slate-400"
                    onClick={() => setVisible((prev) => !prev)}
                >
                    {visible ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
            </div>
        </div>
    );
}

/**
 * Grüner/roter Kasten mit dem Ergebnis eines Verbindungstests.
 *
 * <p>`role="status"` + `aria-live` sorgen dafür, dass Screenreader das
 * Ergebnis vorlesen — sonst bliebe der Test für sie stumm. Das Icon steht
 * zusätzlich zur Farbe, damit die Aussage nicht allein an Rot/Grün hängt.</p>
 */
export function TestResultBanner({ result, className }: { result: TestResult | null; className?: string }) {
    if (!result) return null;
    return (
        <div
            role="status"
            aria-live="polite"
            className={cn(
                'p-3 rounded-lg flex items-start gap-2 text-sm',
                result.success ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-800',
                className
            )}
        >
            {result.success ? (
                <CheckCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
            ) : (
                <XCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
            )}
            {result.message}
        </div>
    );
}

interface SaveButtonProps {
    onClick: () => void;
    saving: boolean;
    disabled?: boolean;
    /** Tooltip – bei deaktiviertem Knopf: warum. */
    title?: string;
    /** Eindeutiger Name, wenn mehrere Speichern-Knöpfe auf einer Seite stehen. */
    ariaLabel?: string;
    children: React.ReactNode;
}

/** Rechtsbündiger Speichern-Knopf mit Ladekringel während des Speicherns. */
export function SaveButton({ onClick, saving, disabled, title, ariaLabel, children }: SaveButtonProps) {
    return (
        <div className="flex justify-end mt-6">
            <Button
                onClick={onClick}
                disabled={saving || disabled}
                title={title}
                aria-label={ariaLabel}
                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
            >
                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                {children}
            </Button>
        </div>
    );
}

/** Ladezustand eines Bereichs, während seine Einstellungen geholt werden. */
export function SectionLoading() {
    return (
        <div className="flex items-center gap-2 text-slate-500 py-8">
            <Loader2 className="w-4 h-4 animate-spin" />
            Lade Einstellungen ...
        </div>
    );
}

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
    children: React.ReactNode;
    className?: string;
}

/** Karte mit Icon, Überschrift und Erklärtext — der Rahmen jedes Bereichs. */
export function SettingsCard({ icon, title, description, children, className }: SettingsCardProps) {
    return (
        <Card className={cn('p-6', className)}>
            <h3 className="text-lg font-semibold text-slate-900 mb-2 flex items-center gap-2">
                {icon}
                {title}
            </h3>
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
    className,
}: PasswordFieldProps) {
    const [visible, setVisible] = useState(false);
    return (
        <div className={className}>
            <Label htmlFor={id}>
                {label}
                {isSet && !value && (
                    <span className="ml-2 text-xs text-emerald-600 font-normal">✓ gesetzt</span>
                )}
            </Label>
            <div className="relative">
                <Input
                    id={id}
                    type={visible ? 'text' : 'password'}
                    value={value}
                    onChange={(e) => onChange(e.target.value)}
                    placeholder={isSet ? '(leer lassen = unverändert)' : placeholder}
                    autoComplete="new-password"
                />
                <button
                    type="button"
                    aria-label={visible ? 'Passwort verbergen' : 'Passwort anzeigen'}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
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
    children: React.ReactNode;
}

/** Rechtsbündiger Speichern-Knopf mit Ladekringel während des Speicherns. */
export function SaveButton({ onClick, saving, disabled, children }: SaveButtonProps) {
    return (
        <div className="flex justify-end mt-6">
            <Button
                onClick={onClick}
                disabled={saving || disabled}
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

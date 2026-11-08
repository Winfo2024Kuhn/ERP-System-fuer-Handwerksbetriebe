import { Plus, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { ANREDE_AUSWAHL } from '../../lib/briefanrede';
import {
    ansprechpartnerAendern, ansprechpartnerEntfernen, ansprechpartnerHinzufuegen, lohnAnsprechpartnerSetzen,
} from './steuerberaterEntwurf';
import type { SteuerberaterAnsprechpartner, SteuerberaterEntwurf } from './firmaTypen';

const ANREDE_OPTIONS = [{ value: '', label: '(keine)' }, ...ANREDE_AUSWAHL];

type Aktualisieren = (aenderung: (prev: SteuerberaterEntwurf | null) => SteuerberaterEntwurf) => void;

interface Props {
    ansprechpartnerListe: SteuerberaterAnsprechpartner[];
    aktualisieren: Aktualisieren;
}

/** Ansprechpartner des Steuerberaters; genau einer bekommt die Stundenaufstellung. */
export function AnsprechpartnerEditor({ ansprechpartnerListe, aktualisieren }: Props) {
    return (
        <div className="space-y-2 pt-2 border-t border-slate-200">
            <div className="flex items-center justify-between">
                <Label>Ansprechpartner</Label>
                <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => aktualisieren(ansprechpartnerHinzufuegen)}
                >
                    <Plus className="w-3 h-3 mr-1" />Ansprechpartner hinzufügen
                </Button>
            </div>
            {ansprechpartnerListe.length === 0 && (
                <p className="text-xs text-slate-500">Noch kein Ansprechpartner. Mindestens einer wird empfohlen, damit die Stundenaufstellung an die richtige Person geht.</p>
            )}
            {ansprechpartnerListe.map((ap, idx) => (
                <AnsprechpartnerZeile
                    key={idx}
                    ap={ap}
                    nummer={idx + 1}
                    aendern={teil => aktualisieren(prev => ansprechpartnerAendern(prev, idx, teil))}
                    entfernen={() => aktualisieren(prev => ansprechpartnerEntfernen(prev, idx))}
                    alsLohnAnsprechpartner={() => aktualisieren(prev => lohnAnsprechpartnerSetzen(prev, idx))}
                />
            ))}
        </div>
    );
}

interface ZeileProps {
    ap: SteuerberaterAnsprechpartner;
    nummer: number;
    aendern: (teil: Partial<SteuerberaterAnsprechpartner>) => void;
    entfernen: () => void;
    alsLohnAnsprechpartner: () => void;
}

function AnsprechpartnerZeile({ ap, nummer, aendern, entfernen, alsLohnAnsprechpartner }: ZeileProps) {
    return (
        <div className="rounded-lg border border-slate-200 p-3 space-y-2 bg-slate-50">
            <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-slate-600">Ansprechpartner {nummer}</span>
                <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={entfernen}
                    aria-label="Ansprechpartner entfernen"
                >
                    <X className="w-4 h-4" />
                </Button>
            </div>
            <div className="grid grid-cols-3 gap-2">
                <div className="space-y-1">
                    <Label className="text-xs">Anrede</Label>
                    <Select
                        options={ANREDE_OPTIONS}
                        value={ap.anrede || ''}
                        onChange={v => aendern({ anrede: v || null })}
                        placeholder="Anrede"
                    />
                </div>
                <KleinesFeld label="Vorname" value={ap.vorname} onChange={vorname => aendern({ vorname })} />
                <KleinesFeld label="Nachname *" value={ap.nachname} onChange={nachname => aendern({ nachname })} />
            </div>
            <div className="grid grid-cols-2 gap-2">
                <KleinesFeld label="E-Mail" value={ap.email} onChange={email => aendern({ email })} />
                <KleinesFeld label="Telefon" value={ap.telefon} onChange={telefon => aendern({ telefon })} />
            </div>
            <label className="flex items-center gap-2 text-sm cursor-pointer pt-1">
                <input
                    type="radio"
                    name="lohnAnsprechpartner"
                    checked={!!ap.istLohnAnsprechpartner}
                    onChange={alsLohnAnsprechpartner}
                    className="accent-rose-600"
                />
                <span className="text-slate-700">Zuständig für Löhne (Empfänger der Stundenaufstellung)</span>
            </label>
        </div>
    );
}

function KleinesFeld({ label, value, onChange }: { label: string; value: string; onChange: (wert: string) => void }) {
    return (
        <div className="space-y-1">
            <Label className="text-xs">{label}</Label>
            <Input
                value={value || ''}
                onChange={e => onChange(e.target.value)}
            />
        </div>
    );
}

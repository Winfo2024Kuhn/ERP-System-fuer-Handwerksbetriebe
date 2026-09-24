import { useEffect, useState } from 'react';
import { Label } from '../ui/label';
import { Select } from '../ui/select-custom';
import { HeartPulse, Briefcase } from 'lucide-react';

interface KrankenkasseOption {
    id: number;
    name: string;
    zusatzbeitragProzent: number;
}

export type Beschaeftigungsart = 'REGULAER' | 'MINIJOB' | 'GF_SV_PFLICHTIG' | 'GF_SV_FREI';

export interface BeschaeftigungsState {
    beschaeftigungsart?: Beschaeftigungsart | null;
    krankenkasseId?: number | null;
    kinderlos?: boolean | null;
}

/**
 * Wizard fuer SV-relevante Felder. Statt Fachbegriffen ("Beschaeftigungsart",
 * "PV-Kinderlos-Zuschlag") fragen wir mit einfachen Ja/Nein-Fragen.
 */
export function BeschaeftigungsWizard({ value, onChange }: { value: BeschaeftigungsState; onChange: (v: BeschaeftigungsState) => void }) {
    const [krankenkassen, setKrankenkassen] = useState<KrankenkasseOption[]>([]);

    useEffect(() => {
        fetch('/api/lohn-stammdaten/krankenkassen?nurAktive=true')
            .then(r => (r.ok ? r.json() : []))
            .then(setKrankenkassen)
            .catch(() => undefined);
    }, []);

    const isMinijob = value.beschaeftigungsart === 'MINIJOB';
    const istChef = value.beschaeftigungsart === 'GF_SV_PFLICHTIG' || value.beschaeftigungsart === 'GF_SV_FREI';
    const istChefMitMehrheit = value.beschaeftigungsart === 'GF_SV_FREI';
    // Tri-State: nur ausgewaehlt, wenn explizit beantwortet wurde.
    const hatKinderJa = value.kinderlos === false;
    const hatKinderNein = value.kinderlos === true;

    const setBeschaeftigungsart = (a: Beschaeftigungsart) => {
        onChange({ ...value, beschaeftigungsart: a });
    };

    return (
        <div className="space-y-4">
            <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2">
                <Briefcase className="w-4 h-4" /> Anstellungsart
            </h3>

            {/* Frage 1: Minijob? */}
            <YesNoCard
                question="Ist das ein 520 €-Job (Minijob)?"
                hint="Minijobs zahlen pauschal Sozialversicherung, der Mitarbeiter zahlt selbst nichts ein."
                yes={isMinijob}
                onYes={() => setBeschaeftigungsart('MINIJOB')}
                onNo={() => setBeschaeftigungsart(istChef ? (value.beschaeftigungsart ?? 'REGULAER') : 'REGULAER')}
            />

            {/* Frage 2: Chef? - nur bei Nicht-Minijob */}
            {!isMinijob && (
                <YesNoCard
                    question="Ist der Mitarbeiter Chef der Firma (Geschäftsführer)?"
                    hint="Geschäftsführer werden anders behandelt – je nachdem, ob ihm die Firma gehört."
                    yes={istChef}
                    onYes={() => setBeschaeftigungsart('GF_SV_PFLICHTIG')}
                    onNo={() => setBeschaeftigungsart('REGULAER')}
                />
            )}

            {/* Frage 3: Mehrheit? - nur wenn Chef */}
            {!isMinijob && istChef && (
                <YesNoCard
                    question="Gehört ihm/ihr mehr als 50 % der Firma?"
                    hint="Mehrheits-Geschäftsführer sind meist sozialversicherungsfrei."
                    yes={istChefMitMehrheit}
                    onYes={() => setBeschaeftigungsart('GF_SV_FREI')}
                    onNo={() => setBeschaeftigungsart('GF_SV_PFLICHTIG')}
                />
            )}

            {/* Frage 4: Krankenkasse - nur wenn nicht GF-SV-frei */}
            {value.beschaeftigungsart !== 'GF_SV_FREI' && (
                <div className="space-y-1.5">
                    <Label className="flex items-center gap-2 text-xs">
                        <HeartPulse className="w-4 h-4" /> Welche Krankenkasse?
                    </Label>
                    <Select
                        value={value.krankenkasseId ? String(value.krankenkasseId) : ''}
                        onChange={(v: string) => onChange({ ...value, krankenkasseId: v ? Number(v) : null })}
                        options={[
                            { value: '', label: '— Bitte wählen —' },
                            ...krankenkassen.map(k => ({
                                value: String(k.id),
                                label: `${k.name} (${Number(k.zusatzbeitragProzent).toFixed(2)} % Zusatzbeitrag)`,
                            })),
                        ]}
                    />
                    <p className="text-xs text-slate-500">
                        Steht deine Krankenkasse nicht in der Liste? Dann lege sie unter „Firma → Lohn-Stammdaten" an.
                    </p>
                </div>
            )}

            {/* Frage 5: Kinder? - nur wenn nicht GF-SV-frei und nicht Minijob */}
            {value.beschaeftigungsart !== 'GF_SV_FREI' && !isMinijob && (
                <YesNoCard
                    question="Hat er/sie Kinder?"
                    hint="Kinderlose ab 23 zahlen einen kleinen Aufschlag bei der Pflegeversicherung."
                    yes={hatKinderJa}
                    no={hatKinderNein}
                    onYes={() => onChange({ ...value, kinderlos: false })}
                    onNo={() => onChange({ ...value, kinderlos: true })}
                />
            )}
        </div>
    );
}

/**
 * Tri-State Ja/Nein-Auswahl: `yes` und `no` werden separat geliefert. Wenn beide
 * false sind, ist nichts ausgewaehlt (Initial-Zustand bei neuem Mitarbeiter).
 */
function YesNoCard({ question, hint, yes, no, onYes, onNo }: { question: string; hint?: string; yes: boolean; no?: boolean; onYes: () => void; onNo: () => void }) {
    // Falls "no" nicht explizit gesetzt ist: klassisches Binaer-Verhalten (no = !yes).
    const isNoSelected = no ?? !yes;
    return (
        <div className="rounded-lg border border-slate-200 p-3 space-y-2">
            <p className="text-sm font-medium text-slate-800">{question}</p>
            {hint && <p className="text-xs text-slate-500">{hint}</p>}
            <div className="flex gap-2">
                <button
                    type="button"
                    onClick={onYes}
                    className={`px-4 py-1.5 text-sm font-medium rounded-md border transition ${yes ? 'bg-rose-600 text-white border-rose-600' : 'border-slate-300 text-slate-700 hover:bg-rose-50'}`}
                >
                    Ja
                </button>
                <button
                    type="button"
                    onClick={onNo}
                    className={`px-4 py-1.5 text-sm font-medium rounded-md border transition ${isNoSelected ? 'bg-rose-600 text-white border-rose-600' : 'border-slate-300 text-slate-700 hover:bg-rose-50'}`}
                >
                    Nein
                </button>
            </div>
        </div>
    );
}

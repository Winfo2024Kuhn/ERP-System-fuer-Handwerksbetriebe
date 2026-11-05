import { useId } from 'react';
import { DatePicker } from '../../components/ui/datepicker';
import { Label } from '../../components/ui/label';
import { Schalter } from '../../components/ui/schalter';
import { SegmentAuswahl } from '../../components/ui/segment-auswahl';
import { Select } from '../../components/ui/select-custom';
import type { BerichtMitarbeiter } from './api';
import { BerichtInfo } from './BerichtInfo';
import type { FilterFeld, FilterWerte, SchalterFeld, ZeitraumArt } from './berichtTypen';
import { gewaehlteIds } from './berichtWerte';
import { MitarbeiterAuswahl } from './MitarbeiterAuswahl';
import { MONATSNAMEN, waehlbareJahre } from './zeitraum';

export interface BerichtFilterProps {
    felder: FilterFeld[];
    werte: FilterWerte;
    onChange: (werte: FilterWerte) => void;
    mitarbeiter: BerichtMitarbeiter[];
    mitarbeiterLaden: boolean;
    mitarbeiterFehler: string | null;
    /** Aktuelles Jahr für die Jahresauswahl. */
    aktuellesJahr: number;
}

const MONAT_OPTIONEN = MONATSNAMEN.map((name, index) => ({ value: String(index + 1), label: name }));

const ZEITRAUM_OPTIONEN: { wert: ZeitraumArt; text: string }[] = [
    { wert: 'monat', text: 'Monat' },
    { wert: 'jahr', text: 'Ganzes Jahr' },
    { wert: 'bereich', text: 'Von–bis' },
];

interface FeldProps extends Omit<BerichtFilterProps, 'felder'> {
    setze: (teil: Partial<FilterWerte>) => void;
}

function MonatJahr({ werte, setze, aktuellesJahr, mitMonat }: FeldProps & { mitMonat: boolean }) {
    const id = useId();
    const jahre = waehlbareJahre(aktuellesJahr).map(j => ({ value: String(j), label: String(j) }));
    return (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {mitMonat && (
                <div className="space-y-1.5">
                    <Label htmlFor={`${id}-monat`}>Monat</Label>
                    <Select id={`${id}-monat`} aria-label="Monat" options={MONAT_OPTIONEN} value={String(werte.monat)}
                        onChange={v => setze({ monat: Number(v) })} />
                </div>
            )}
            <div className="space-y-1.5">
                <Label htmlFor={`${id}-jahr`}>Jahr</Label>
                <Select id={`${id}-jahr`} aria-label="Jahr" options={jahre} value={String(werte.jahr)}
                    onChange={v => setze({ jahr: Number(v) })} />
            </div>
        </div>
    );
}

function ZeitraumFeld(props: FeldProps) {
    const { werte, setze } = props;
    const id = useId();
    return (
        <div className="space-y-3">
            <div className="space-y-1.5">
                <p className="text-sm font-medium text-slate-700">Zeitraum</p>
                <SegmentAuswahl beschriftung="Zeitraum" optionen={ZEITRAUM_OPTIONEN} wert={werte.zeitraumArt}
                    onChange={wert => setze({ zeitraumArt: wert as ZeitraumArt })} />
            </div>
            {werte.zeitraumArt === 'bereich' ? (
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <div className="space-y-1.5">
                        <Label htmlFor={`${id}-von`}>Von</Label>
                        <DatePicker id={`${id}-von`} aria-label="Von" value={werte.von} onChange={von => setze({ von })} />
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor={`${id}-bis`}>Bis</Label>
                        <DatePicker id={`${id}-bis`} aria-label="Bis" value={werte.bis} min={werte.von || undefined}
                            onChange={bis => setze({ bis })} />
                    </div>
                    <p className="text-xs text-slate-500 sm:col-span-2">Höchstens 366 Tage, also etwa ein Jahr.</p>
                </div>
            ) : (
                <MonatJahr {...props} mitMonat={werte.zeitraumArt === 'monat'} />
            )}
        </div>
    );
}

function SchalterZeile({ feld, werte, setze }: { feld: SchalterFeld; werte: FilterWerte; setze: FeldProps['setze'] }) {
    return (
        <div className="flex items-center gap-1">
            <Schalter beschriftung={feld.beschriftung} an={!!werte.schalter[feld.schluessel]}
                onChange={an => setze({ schalter: { ...werte.schalter, [feld.schluessel]: an } })} />
            {feld.info && <BerichtInfo info={feld.info} />}
        </div>
    );
}

/**
 * Rendert die Filterfelder eines Berichts aus seiner Definition – ein
 * Baustein für alle Berichte, damit ein neuer Bericht nur Felder aufzählt.
 */
export function BerichtFilter(props: BerichtFilterProps) {
    const { felder, werte, onChange, mitarbeiter, mitarbeiterLaden, mitarbeiterFehler } = props;
    const setze = (teil: Partial<FilterWerte>) => onChange({ ...werte, ...teil });
    const feldProps: FeldProps = { ...props, setze };
    const schalterFelder = felder.filter((f): f is SchalterFeld => f.typ === 'schalter');

    return (
        <div className="space-y-6">
            {felder.map((feld, index) => {
                switch (feld.typ) {
                    case 'monat':
                        return <MonatJahr key={`${feld.typ}-${index}`} {...feldProps} mitMonat />;
                    case 'zeitraum':
                        return <ZeitraumFeld key={`${feld.typ}-${index}`} {...feldProps} />;
                    case 'mitarbeiter':
                        return (
                            <MitarbeiterAuswahl key={`${feld.typ}-${index}`} mitarbeiter={mitarbeiter} max={feld.max} alleErlaubt={feld.alleErlaubt}
                                gewaehlt={gewaehlteIds(feld, werte, mitarbeiter)} laedt={mitarbeiterLaden} fehler={mitarbeiterFehler}
                                onChange={ids => setze({ mitarbeiterIds: ids })} />
                        );
                    default:
                        return null;
                }
            })}
            {schalterFelder.length > 0 && (
                <div role="group" aria-label="Was soll im Bericht stehen?" className="space-y-1.5">
                    <p className="text-sm font-medium text-slate-700">Was soll im Bericht stehen?</p>
                    <div className="flex flex-wrap gap-x-6 gap-y-1">
                        {schalterFelder.map(feld => <SchalterZeile key={feld.schluessel} feld={feld} werte={werte} setze={setze} />)}
                    </div>
                </div>
            )}
        </div>
    );
}

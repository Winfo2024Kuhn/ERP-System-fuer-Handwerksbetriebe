import { BellRing, CalendarClock, Info, Mail, MailWarning } from 'lucide-react';
import { DecimalInput } from '../../components/ui/decimal-input';
import { Label } from '../../components/ui/label';
import { cn } from '../../lib/utils';
import { ZeitstrahlAbstand, ZeitstrahlStation } from './Zeitstrahl';
import type { FirmaAendern, FirmenEntwurf } from './firmaTypen';

interface Props {
    firma: FirmenEntwurf;
    aendern: FirmaAendern;
}

/** Abschnitt „Automatisches Mahnverfahren“ (volle Breite) mit Zeitstrahl. */
export function MahnverfahrenKarte({ firma, aendern }: Props) {
    return (
        <div className="mt-6 pt-6 border-t space-y-4">
            <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold text-slate-900">Automatisches Mahnverfahren</h3>
                <label className="inline-flex items-center gap-2 text-sm font-medium text-slate-700 cursor-pointer">
                    <input
                        type="checkbox"
                        checked={firma.mahnverfahrenAktiv || false}
                        onChange={e => aendern({ mahnverfahrenAktiv: e.target.checked })}
                        className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                    />
                    Aktiv
                </label>
            </div>
            <p className="text-sm text-slate-500">
                Wenn aktiviert, verschickt das System automatisch Zahlungserinnerung sowie 1. und 2. Mahnung
                per E-Mail an den Kunden. Jeder Schritt wird erst verschickt, wenn seit dem vorherigen
                Schritt die eingestellte Anzahl an Tagen vergangen ist.
            </p>
            <div className={cn("space-y-6 transition-opacity", !firma.mahnverfahrenAktiv && "opacity-50 pointer-events-none")}>
                {/* Zeitstrahl: Rechnung fällig -> Zahlungserinnerung -> 1. Mahnung -> 2. Mahnung */}
                <div className="flex flex-col pt-2 md:flex-row md:items-start">
                    <ZeitstrahlStation icon={CalendarClock} label="Rechnung fällig" />
                    <ZeitstrahlAbstand
                        value={firma.tageBisZahlungserinnerung}
                        onChange={tage => aendern({ tageBisZahlungserinnerung: tage })}
                        beschriftung="Tage nach Fälligkeit"
                    />
                    <ZeitstrahlStation icon={BellRing} label="Zahlungserinnerung" />
                    <ZeitstrahlAbstand
                        value={firma.tageBisErsteMahnung}
                        onChange={tage => aendern({ tageBisErsteMahnung: tage })}
                        beschriftung="Tage nach der Zahlungserinnerung"
                    />
                    <ZeitstrahlStation icon={Mail} label="1. Mahnung" />
                    <ZeitstrahlAbstand
                        value={firma.tageBisZweiteMahnung}
                        onChange={tage => aendern({ tageBisZweiteMahnung: tage })}
                        beschriftung="Tage nach der 1. Mahnung"
                    />
                    <ZeitstrahlStation icon={MailWarning} label="2. Mahnung" />
                </div>

                {/* Neues Zahlungsziel auf der Mahnung */}
                <div>
                    <Label>Neues Zahlungsziel auf der Mahnung (Tage)</Label>
                    <DecimalInput
                        aria-label="Neues Zahlungsziel auf der Mahnung (Tage)"
                        required integer
                        min={1}
                        value={firma.mahnverfahrenNeuesZahlungszielTage}
                        onChange={value => aendern({ mahnverfahrenNeuesZahlungszielTage: value })}
                        className="w-24"
                    />
                    <p className="mt-1 text-xs text-slate-500">
                        So viele Tage Zeit geben wir dem Kunden auf jeder Mahnung, den Betrag noch zu überweisen.
                    </p>
                </div>
            </div>

            {/* Hinweis zur Funktionsweise */}
            <div className="flex items-start gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
                <Info className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
                <p className="text-sm text-slate-600">
                    Schon überfällige Rechnungen starten mit der Zahlungserinnerung — danach gelten die
                    eingestellten Abstände. Automatisch gemahnt werden nur Rechnungen, die mit dem Programm
                    geschrieben wurden.
                </p>
            </div>
        </div>
    );
}

import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { Label } from '../../../components/ui/label';
import { EmailRecipientInput } from '../../../components/EmailRecipientInput';
import type { EmailZuordnung } from '../../../components/EmailZuordnungSearchModal';
import { EinzelversandSchalter } from '../EinzelversandSchalter';
import { verbindeEmpfaenger, zerlegeEmpfaenger } from '../empfaengerListe';
import type { PostfachKurz } from '../postfach';
import { AbsenderFeld } from './AbsenderFeld';
import { ZuordnungFeld } from './ZuordnungFeld';
import type { Absender } from './useAbsender';
import type { Empfaenger } from './useEmpfaenger';

const ZEILE_INLINE = "grid grid-cols-[68px_minmax(0,1fr)] items-center gap-2";

interface EmailComposeDetailsProps {
    inline: boolean;
    empfaenger: Empfaenger;
    vorschlaege: string[];
    /** Antwort mit bekanntem Empfänger: An steht fest. */
    empfaengerFest: boolean;
    absender: Absender;
    absenderFest: boolean;
    weiterleitung: boolean;
    festesPostfach: PostfachKurz | string | null | undefined;
    geschaeftsdokument: boolean;
    subject: string;
    onSubject: (betreff: string) => void;
    /** `undefined`: Zuordnung im Formular nicht wählbar (Projekt-/Anfrage-Seite). */
    zuordnung?: { wert: EmailZuordnung | null; name: string; antwort: boolean; onSuchen: () => void; onEntfernen: () => void };
    markDirty: () => void;
}

/** Karte „E-Mail-Details“: An, Einzelversand, Von, Betreff, Gehört zu und CC. */
export function EmailComposeDetails(p: EmailComposeDetailsProps) {
    const { inline, empfaenger: e, markDirty } = p;
    return (
        <div className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
            <div className={inline ? "hidden" : "border-b border-slate-100 bg-slate-50/80 px-5 py-3"}>
                <p className="text-sm font-semibold text-slate-900">E-Mail-Details</p>
                <p className="text-xs text-slate-500 mt-1">Empfänger, Absender und Betreff in einer kompakten Übersicht.</p>
            </div>

            <div className={inline ? "p-3 flex flex-col gap-2" : "p-5 flex flex-col gap-4"}>
                <div className={inline ? ZEILE_INLINE : "space-y-2"}>
                    <div className={inline ? "flex flex-col items-start gap-0.5" : "flex items-center justify-between gap-3"}>
                        <Label>{inline ? 'An *' : 'Empfänger *'}</Label>
                        {!e.showCc && !e.einzelversandAktiv && (
                            <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                className={inline ? "h-6 min-h-6 px-1 text-xs text-rose-600" : "min-h-11 text-xs text-rose-600 hover:text-rose-700 hover:bg-rose-50 px-3"}
                                onClick={() => { e.setShowCc(true); e.setCcRecipients([]); }}
                            >
                                {inline ? '+ CC' : '+ CC hinzufügen'}
                            </Button>
                        )}
                    </div>
                    <EmailRecipientInput
                        mehrfach
                        ariaLabel="Empfänger"
                        onEntwurfChange={e.setAnEntwurfVorhanden}
                        value={e.recipient}
                        onChange={e.empfaengerAendern}
                        suggestions={p.vorschlaege}
                        placeholder="Name, Firma oder E-Mail eingeben"
                        // Antwort-Empfänger stehen fest. Ist keiner ermittelbar (z. B. Antwort
                        // auf eine Mail an uns selbst), muss er eintragbar sein.
                        readOnly={p.empfaengerFest}
                    />
                </div>

                {e.einzelversandMoeglich && (
                    <EinzelversandSchalter
                        id="email-einzelversand"
                        aktiv={e.einzelversand}
                        onChange={e.einzelversandUmschalten}
                        anzahlEmpfaenger={e.anEmpfaenger.length}
                        className={inline ? 'pl-[76px]' : undefined}
                    />
                )}

                <div className={inline ? ZEILE_INLINE : "space-y-2"}>
                    <Label htmlFor="email-absender">Von</Label>
                    <AbsenderFeld
                        inline={inline}
                        absender={p.absender}
                        absenderFest={p.absenderFest}
                        weiterleitung={p.weiterleitung}
                        festesPostfach={p.festesPostfach}
                        geschaeftsdokument={p.geschaeftsdokument}
                        onWaehlen={(id) => { markDirty(); p.absender.setPostfachId(id); }}
                    />
                </div>

                <div className={inline ? ZEILE_INLINE : "space-y-2"}>
                    <Label htmlFor="subject">Betreff</Label>
                    <Input
                        id="subject"
                        value={p.subject}
                        onChange={(event) => { markDirty(); p.onSubject(event.target.value); }}
                        placeholder="Betreff eingeben"
                        className="font-medium"
                    />
                </div>

                {/* Zuordnung: entfällt, wenn direkt aus einem Projekt/einer Anfrage geschrieben wird. */}
                {p.zuordnung && (
                    <ZuordnungFeld
                        inline={inline}
                        zuordnung={p.zuordnung.wert}
                        name={p.zuordnung.name}
                        antwort={p.zuordnung.antwort}
                        onSuchen={p.zuordnung.onSuchen}
                        onAendern={() => { markDirty(); p.zuordnung?.onSuchen(); }}
                        onEntfernen={() => { markDirty(); p.zuordnung?.onEntfernen(); }}
                    />
                )}

                {e.showCc && !e.einzelversandAktiv && (
                    <div className="space-y-2 animate-in fade-in slide-in-from-top-2 duration-200">
                        <div className="flex items-center justify-between gap-3">
                            <Label>CC</Label>
                            <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                onClick={() => { markDirty(); e.ccAus(); }}
                                className="text-xs text-slate-500 hover:text-slate-700"
                            >
                                Keine
                            </Button>
                        </div>
                        <EmailRecipientInput
                            mehrfach
                            ariaLabel="Kopie (CC)"
                            value={verbindeEmpfaenger(e.ccRecipients)}
                            onChange={(wert) => { markDirty(); e.setCcRecipients(zerlegeEmpfaenger(wert)); }}
                            suggestions={p.vorschlaege}
                            placeholder="Kopie an: Name, Firma oder E-Mail"
                        />
                    </div>
                )}
            </div>
        </div>
    );
}

import { Loader2, Save } from "lucide-react";
import { Button } from "../../ui/button";
import { DecimalInput } from "../../ui/decimal-input";
import { Input } from "../../ui/input";
import { Label } from "../../ui/label";
import { TiptapEditor } from "../../TiptapEditor";
import type { Artikel } from "../../../types";
import { formatZahl } from "./format";
import { AngebotsZeile } from "./AngebotsZeile";

/** Die Eingaben im Reiter. Sie liegen in der Seite, damit ein Reiterwechsel nichts Ungespeichertes verwirft. */
export interface Angebotsfelder {
    kurzbeschreibung: string;
    beschreibung: string;
    aufschlag: string;
}

interface Props {
    artikel: Artikel;
    felder: Angebotsfelder;
    onAendern: (aenderung: Partial<Angebotsfelder>) => void;
    speichert: boolean;
    onSpeichern: () => void;
}

/** Was gilt, wenn am Artikel selbst kein Aufschlag steht: der der Kategorie, sonst der allgemeine. */
function geltenderAufschlagText(a: Artikel): string {
    const prozent = a.wirksamProzent == null ? "" : `${formatZahl(a.wirksamProzent, 2)} %`;
    if (a.wirksamQuelle === "KATEGORIE") return `Leer: es gilt ${prozent} von „${a.wirksamVon || "Kategorie"}“.`;
    if (a.wirksamQuelle === "ALLGEMEIN") return `Leer: es gilt der allgemeine Aufschlag von ${prozent}.`;
    return "Leer: es gilt kein Aufschlag, der Preis ist der Einkaufspreis.";
}

/** Reiter „Für Angebote“: was der Kunde liest, wenn dieses Material in einem Angebot steht. */
export function AngeboteReiter({ artikel, felder, onAendern, speichert, onSpeichern }: Props) {
    return (
        <section className="bg-white border border-slate-200 rounded-lg p-5">
            <h2 className="text-lg font-semibold text-slate-900 mb-1">Für Angebote und Rechnungen</h2>
            <p className="text-sm text-slate-500 mb-4">
                Was der Kunde sieht, wenn dieses Material in einem Angebot steht.
            </p>

            <div className="space-y-4">
                <div className="space-y-1.5">
                    <Label htmlFor="kurzbeschreibung">Kurzbeschreibung</Label>
                    <Input
                        id="kurzbeschreibung"
                        value={felder.kurzbeschreibung}
                        onChange={(e) => onAendern({ kurzbeschreibung: e.target.value })}
                        maxLength={255}
                        placeholder="z.B. T-Stahl 40x40 Lager"
                    />
                    <p className="text-xs text-slate-400">Nur für dich — steht nicht auf dem Angebot.</p>
                </div>

                <div className="space-y-1.5">
                    <Label>Beschreibung</Label>
                    <TiptapEditor value={felder.beschreibung} onChange={(html: string) => onAendern({ beschreibung: html })} />
                    <p className="text-xs text-slate-400">
                        Das liest der Kunde auf Angebot, Rechnung und Freigabe-Seite.
                    </p>
                </div>

                <div className="space-y-1.5 max-w-xs">
                    <Label htmlFor="aufschlag">Aufschlag auf den Einkaufspreis</Label>
                    <div className="relative">
                        {/* 0 % ist ein eigener Wert (z. B. Durchlaufposten), leer heisst "nicht eingestellt". */}
                        <DecimalInput
                            id="aufschlag"
                            placeholder="z. B. 15"
                            min={0}
                            max={999.99}
                            nullBehalten
                            value={felder.aufschlag}
                            onChange={(aufschlag) => onAendern({ aufschlag })}
                            className="pr-8"
                        />
                        <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-slate-400">%</span>
                    </div>
                    {felder.aufschlag === "" && (
                        <p className="text-xs text-slate-500" data-testid="aufschlag-wirksam">{geltenderAufschlagText(artikel)}</p>
                    )}
                </div>

                <AngebotsZeile artikel={artikel} />

                <Button
                    className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                    size="sm"
                    onClick={onSpeichern}
                    disabled={speichert}
                >
                    {speichert
                        ? <Loader2 className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                        : <Save className="w-4 h-4" aria-hidden="true" />}
                    Angebotsfelder speichern
                </Button>
            </div>
        </section>
    );
}

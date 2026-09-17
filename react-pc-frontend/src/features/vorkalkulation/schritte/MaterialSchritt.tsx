import { useState } from 'react';
import { Package, Plus, Trash2, Warehouse } from 'lucide-react';

import { Button } from '../../../components/ui/button';
import { Card } from '../../../components/ui/card';
import { Input } from '../../../components/ui/input';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { ArtikelSuche } from '../../../components/artikel/ArtikelSuche';
import { artikelBezeichnung } from '../../../components/artikel/artikelBezeichnung';
import { formatCurrency } from '../../../components/artikel/formatCurrency';
import { cn } from '../../../lib/utils';
import type { Artikel } from '../../../types';

import { Kennzahl, Schalter, Umschalter, Zahlenfeld } from '../bausteine';
import { materialSummen, positionsMengen } from '../berechnung';
import { neueMaterialzeile } from '../beispieldaten';
import { zeileAusArtikel } from '../artikelUebernahme';
import { formatKg, formatQm } from '../format';
import { MENGENEINHEITEN, type MaterialPosition, type Mengeneinheit, type VorkalkulationDaten } from '../types';

interface MaterialSchrittProps {
    daten: VorkalkulationDaten;
    aendern: (patch: Partial<VorkalkulationDaten>) => void;
    readOnly: boolean;
}

const einheitKurz = (einheit: Mengeneinheit) =>
    MENGENEINHEITEN.find((e) => e.wert === einheit)?.kurz ?? 'Stk';

export function MaterialSchritt({ daten, aendern, readOnly }: MaterialSchrittProps) {
    const [pickerOffen, setPickerOffen] = useState(false);
    const [gewaehlt, setGewaehlt] = useState<Map<number, Artikel>>(new Map());
    const [mengen, setMengen] = useState<Map<number, string>>(new Map());
    const [beschaffung, setBeschaffung] = useState<Map<number, 'LAGER' | 'BESTELLEN'>>(new Map());

    const summen = materialSummen(daten.material, daten.gkzMaterialProzent);

    const setzePosition = (id: string, patch: Partial<MaterialPosition>) => {
        aendern({ material: daten.material.map((p) => (p.id === id ? { ...p, ...patch } : p)) });
    };
    const entfernePosition = (id: string) => {
        aendern({ material: daten.material.filter((p) => p.id !== id) });
    };

    const pickerZuruecksetzen = () => {
        setGewaehlt(new Map());
        setMengen(new Map());
        setBeschaffung(new Map());
    };

    const umschaltenArtikel = (artikel: Artikel) => {
        setGewaehlt((alt) => {
            const neu = new Map(alt);
            if (neu.has(artikel.id)) neu.delete(artikel.id);
            else neu.set(artikel.id, artikel);
            return neu;
        });
        setMengen((alt) => {
            if (alt.has(artikel.id)) return alt;
            const neu = new Map(alt);
            neu.set(artikel.id, '1');
            return neu;
        });
        setBeschaffung((alt) => {
            if (alt.has(artikel.id)) return alt;
            const neu = new Map(alt);
            neu.set(artikel.id, 'LAGER');
            return neu;
        });
    };

    /**
     * Uebernimmt die Auswahl als Materialzeilen.
     *
     * Gewicht, Mantelflaeche und die beiden Eignungen kommen aus den
     * Stammdaten mit — genau die Werte, die die Kalkulation spaeter fuer
     * Verzinkung und Beschichtung braucht. Die Eignung ist dabei nur der
     * Vorschlag: Ob wirklich verzinkt wird, entscheidet die Zeile.
     */
    const uebernehmen = () => {
        const neueZeilen: MaterialPosition[] = [...gewaehlt.values()].map((artikel) =>
            zeileAusArtikel(artikel, mengen.get(artikel.id) ?? '1', beschaffung.get(artikel.id) ?? 'LAGER'),
        );

        aendern({ material: [...daten.material, ...neueZeilen] });
        pickerZuruecksetzen();
        setPickerOffen(false);
    };

    return (
        <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <h3 className="text-base font-bold text-slate-900">Was wird verbaut?</h3>
                    <p className="text-sm text-slate-500">
                        Aus dem Lager holen oder von Hand eintragen. Gewicht und Oberfläche rechnen wir mit.
                    </p>
                </div>
                <div className="flex gap-2">
                    <Button variant="outline" size="sm" disabled={readOnly} onClick={() => setPickerOffen(true)}>
                        <Warehouse className="h-4 w-4" aria-hidden="true" />
                        Artikel aus Lager
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        disabled={readOnly}
                        onClick={() => aendern({ material: [...daten.material, neueMaterialzeile()] })}
                    >
                        <Plus className="h-4 w-4" aria-hidden="true" />
                        Position von Hand
                    </Button>
                </div>
            </div>

            {daten.material.length === 0 ? (
                <Card className="border-dashed p-10 text-center">
                    <Package className="mx-auto mb-2 h-8 w-8 text-rose-200" aria-hidden="true" />
                    <p className="font-medium text-slate-700">Noch kein Material eingetragen</p>
                    <p className="mt-1 text-sm text-slate-500">
                        Über „Artikel aus Lager“ suchen Sie wie in der Materialverwaltung. „Position von Hand“
                        ist für alles, was nicht im Stamm steht.
                    </p>
                </Card>
            ) : (
                <div className="space-y-3">
                    {daten.material.map((position, index) => {
                        const zeilenMengen = positionsMengen(position);
                        const ausStamm = position.artikelId !== null;
                        return (
                            <Card key={position.id} className="p-3">
                                {/* Kopfzeile: Nummer, Bezeichnung, Artikelnummer, Löschen */}
                                <div className="flex items-start gap-3">
                                    <div className="mt-1 flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-lg border border-rose-100 bg-rose-50 text-xs font-bold text-rose-600">
                                        {index + 1}
                                    </div>
                                    <div className="grid flex-1 gap-2 sm:grid-cols-[1fr_180px]">
                                        <Input
                                            value={position.bezeichnung}
                                            disabled={readOnly}
                                            aria-label={`Bezeichnung der Materialzeile ${index + 1}`}
                                            placeholder="Bezeichnung, z. B. Quadratrohr HQ 50x50x3"
                                            onChange={(e) => setzePosition(position.id, { bezeichnung: e.target.value })}
                                            className="py-1.5 font-medium"
                                        />
                                        <Input
                                            value={position.artikelnummer}
                                            disabled={readOnly || ausStamm}
                                            aria-label={`Artikelnummer der Materialzeile ${index + 1}`}
                                            placeholder="Artikelnummer"
                                            title={ausStamm ? 'Kommt aus dem Materialstamm' : undefined}
                                            onChange={(e) => setzePosition(position.id, { artikelnummer: e.target.value })}
                                            className="py-1.5 text-sm text-slate-600"
                                        />
                                    </div>
                                    <button
                                        type="button"
                                        disabled={readOnly}
                                        aria-label={`Materialzeile ${index + 1} entfernen`}
                                        onClick={() => entfernePosition(position.id)}
                                        className="mt-1 rounded-md p-1.5 text-slate-300 transition-colors hover:bg-rose-50 hover:text-rose-600 disabled:opacity-40"
                                    >
                                        <Trash2 className="h-4 w-4" aria-hidden="true" />
                                    </button>
                                </div>

                                {/* Rechenzeile */}
                                <div className="mt-3 flex flex-wrap items-end gap-3 rounded-lg border border-slate-100 bg-slate-50 p-3">
                                    <Zahlenfeld
                                        label="Menge"
                                        wert={position.menge}
                                        onChange={(entwurf) => setzePosition(position.id, { menge: entwurf })}
                                        ariaLabel={`Menge der Materialzeile ${index + 1}`}
                                        breite="w-24"
                                        disabled={readOnly}
                                        platzhalter="0"
                                    />
                                    <div>
                                        <span className="mb-0.5 block text-[9px] font-semibold uppercase tracking-wider text-slate-400">
                                            Einheit
                                        </span>
                                        <Umschalter
                                            klein
                                            wert={position.einheit}
                                            disabled={readOnly}
                                            ariaLabel={`Einheit der Materialzeile ${index + 1}`}
                                            optionen={MENGENEINHEITEN.map((e) => ({ wert: e.wert, text: e.kurz }))}
                                            onChange={(wert) => setzePosition(position.id, { einheit: wert })}
                                        />
                                    </div>

                                    <span className="pb-1.5 text-sm font-light text-slate-300" aria-hidden="true">×</span>

                                    <Zahlenfeld
                                        label="Preis"
                                        wert={position.preis}
                                        onChange={(entwurf) => setzePosition(position.id, { preis: entwurf })}
                                        ariaLabel={`Preis der Materialzeile ${index + 1}`}
                                        einheit="€"
                                        breite="w-24"
                                        disabled={readOnly}
                                        platzhalter="0,00"
                                    />
                                    <div>
                                        <span className="mb-0.5 block text-[9px] font-semibold uppercase tracking-wider text-slate-400">
                                            je
                                        </span>
                                        <Umschalter
                                            klein
                                            wert={position.preisbezug}
                                            disabled={readOnly}
                                            ariaLabel={`Preisbezug der Materialzeile ${index + 1}`}
                                            optionen={[
                                                { wert: 'EINHEIT', text: einheitKurz(position.einheit) },
                                                { wert: 'KILOGRAMM', text: 'kg' },
                                            ]}
                                            onChange={(wert) => setzePosition(position.id, { preisbezug: wert })}
                                        />
                                    </div>

                                    <Zahlenfeld
                                        label={`kg je ${einheitKurz(position.einheit)}`}
                                        wert={position.kgJeEinheit}
                                        onChange={(entwurf) => setzePosition(position.id, { kgJeEinheit: entwurf })}
                                        ariaLabel={`Gewicht je Einheit der Materialzeile ${index + 1}`}
                                        breite="w-20"
                                        disabled={readOnly || position.einheit === 'KILOGRAMM'}
                                        platzhalter="0,00"
                                        hinweis="Braucht die Kalkulation für Kilo-Preis und Verzinkung."
                                    />
                                    {position.pulverbeschichten && (
                                        <Zahlenfeld
                                            label={`m² je ${einheitKurz(position.einheit)}`}
                                            wert={position.qmJeEinheit}
                                            onChange={(entwurf) => setzePosition(position.id, { qmJeEinheit: entwurf })}
                                            ariaLabel={`Fläche je Einheit der Materialzeile ${index + 1}`}
                                            breite="w-20"
                                            disabled={readOnly}
                                            platzhalter="0,00"
                                            hinweis="Zu beschichtende Oberfläche je Einheit."
                                        />
                                    )}

                                    <div className="ml-auto text-right">
                                        <span className="mb-0.5 block text-[9px] font-semibold uppercase tracking-wider text-slate-400">
                                            Zeile
                                        </span>
                                        <p className="tabular-nums text-base font-bold text-slate-900">
                                            {formatCurrency(zeilenMengen.kosten)}
                                        </p>
                                        <p className="text-[11px] tabular-nums text-slate-500">
                                            {formatKg(zeilenMengen.kilogramm)}
                                            {position.pulverbeschichten && ` · ${formatQm(zeilenMengen.quadratmeter)}`}
                                        </p>
                                    </div>
                                </div>

                                {/* Entscheidungen zur Oberfläche und Beschaffung */}
                                <div className="mt-2 flex flex-wrap items-center gap-2">
                                    <Schalter
                                        an={position.verzinken}
                                        disabled={readOnly}
                                        onChange={(an) => setzePosition(position.id, { verzinken: an })}
                                        titel={position.verzinkbar
                                            ? 'Der Materialstamm sagt: verzinkbar.'
                                            : 'Im Materialstamm nicht als verzinkbar hinterlegt.'}
                                    >
                                        verzinken
                                    </Schalter>
                                    {position.verzinken && (
                                        <Umschalter
                                            klein
                                            wert={position.verzinkungsart}
                                            disabled={readOnly}
                                            ariaLabel={`Warenart für die Verzinkung der Materialzeile ${index + 1}`}
                                            optionen={[
                                                { wert: 'SCHLOSSERWARE', text: 'Schlosserware' },
                                                { wert: 'TRAEGERWARE', text: 'Trägerware' },
                                            ]}
                                            onChange={(wert) => setzePosition(position.id, { verzinkungsart: wert })}
                                        />
                                    )}
                                    <Schalter
                                        an={position.pulverbeschichten}
                                        disabled={readOnly}
                                        onChange={(an) => setzePosition(position.id, { pulverbeschichten: an })}
                                        titel={position.pulverbeschichtbar
                                            ? 'Der Materialstamm sagt: pulverbeschichtbar.'
                                            : 'Im Materialstamm nicht als pulverbeschichtbar hinterlegt.'}
                                    >
                                        pulverbeschichten
                                    </Schalter>
                                    <span className="mx-1 h-4 w-px bg-slate-200" aria-hidden="true" />
                                    <Umschalter
                                        klein
                                        wert={position.beschaffung}
                                        disabled={readOnly}
                                        ariaLabel={`Beschaffung der Materialzeile ${index + 1}`}
                                        optionen={[
                                            { wert: 'LAGER', text: 'aus Lager' },
                                            { wert: 'BESTELLEN', text: 'bestellen' },
                                        ]}
                                        onChange={(wert) => setzePosition(position.id, { beschaffung: wert })}
                                    />
                                    {position.verzinken && !position.verzinkbar && (
                                        <span className="text-[11px] text-amber-700">
                                            Laut Stammdaten nicht zum Verzinken vorgesehen — bitte kurz prüfen.
                                        </span>
                                    )}
                                </div>
                            </Card>
                        );
                    })}
                </div>
            )}

            {/* Laufende Kennzahlen des Reiters */}
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                <Kennzahl
                    titel="Gesamtgewicht"
                    wert={formatKg(summen.gesamtKilogramm)}
                    zusatz={`davon zu verzinken ${formatKg(summen.kilogrammVerzinken)}`}
                />
                <Kennzahl
                    titel="Oberfläche"
                    wert={formatQm(summen.gesamtQuadratmeter)}
                    zusatz={`davon zu beschichten ${formatQm(summen.quadratmeterPulver)}`}
                />
                <Kennzahl
                    titel="Materialkosten"
                    wert={formatCurrency(summen.materialkosten)}
                    zusatz={`Zuschlag ${formatCurrency(summen.gkzBetrag)}`}
                />
                <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2">
                    <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
                        Material gesamt
                    </p>
                    <p className="tabular-nums text-base font-bold text-rose-700">
                        {formatCurrency(summen.materialkostenGesamt)}
                    </p>
                    <div className="mt-1 flex items-center gap-1.5">
                        <span className="text-[11px] text-slate-500">Zuschlag</span>
                        <Zahlenfeld
                            wert={daten.gkzMaterialProzent}
                            onChange={(entwurf) => aendern({ gkzMaterialProzent: entwurf })}
                            ariaLabel="Zuschlag auf das Material in Prozent"
                            einheit="%"
                            breite="w-16"
                            disabled={readOnly}
                            platzhalter="0"
                        />
                    </div>
                </div>
            </div>

            {/* Artikelsuche — dieselbe wie in der Materialverwaltung */}
            <Dialog
                open={pickerOffen}
                onOpenChange={(offen) => {
                    setPickerOffen(offen);
                    if (!offen) pickerZuruecksetzen();
                }}
                className="h-[84vh] w-[88vw] max-w-none"
            >
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Artikel aus Lager hinzufügen</DialogTitle>
                        <p className="text-sm text-slate-500">
                            Suchen wie in der Materialverwaltung — Menge eintragen und festlegen, ob der Artikel
                            aus dem Lager kommt oder bestellt werden muss.
                        </p>
                    </DialogHeader>

                    <div className="flex min-h-0 flex-1 flex-col gap-4">
                        {/* onZeilenKlick ist Pflicht: Ohne ihn navigiert ArtikelSuche
                            auf /artikel/:id und der Klick verliesse die Kalkulation. */}
                        <ArtikelSuche
                            urlSync={false}
                            seitenGroesse={15}
                            seitenGroesseAusHoehe
                            onZeilenKlick={umschaltenArtikel}
                            zeilenGedrueckt={(artikel) => gewaehlt.has(artikel.id)}
                            zeilenAktion={(artikel) => {
                                const angehakt = gewaehlt.has(artikel.id);
                                const name = artikelBezeichnung(artikel);
                                return (
                                    <div className="flex items-center gap-3">
                                        <input
                                            type="checkbox"
                                            checked={angehakt}
                                            onChange={() => umschaltenArtikel(artikel)}
                                            aria-label={`${name} auswählen`}
                                            className="h-4 w-4 accent-rose-600"
                                        />
                                        <Input
                                            type="text"
                                            inputMode="decimal"
                                            value={mengen.get(artikel.id) ?? ''}
                                            onChange={(e) => setMengen((alt) => new Map(alt).set(artikel.id, e.target.value))}
                                            aria-label={`Menge für ${name}`}
                                            disabled={!angehakt}
                                            placeholder="1"
                                            className={cn('h-8 w-20 text-sm', !angehakt && 'bg-slate-100')}
                                        />
                                        <Umschalter
                                            klein
                                            wert={beschaffung.get(artikel.id) ?? 'LAGER'}
                                            disabled={!angehakt}
                                            ariaLabel={`Bezug für ${name}`}
                                            optionen={[
                                                { wert: 'LAGER', text: 'Lager' },
                                                { wert: 'BESTELLEN', text: 'Bestellen' },
                                            ]}
                                            onChange={(wert) => setBeschaffung((alt) => new Map(alt).set(artikel.id, wert))}
                                        />
                                    </div>
                                );
                            }}
                        />
                    </div>

                    <DialogFooter>
                        <div className="flex-1 text-sm text-slate-500">{gewaehlt.size} ausgewählt</div>
                        <Button variant="outline" onClick={() => { pickerZuruecksetzen(); setPickerOffen(false); }}>
                            Abbrechen
                        </Button>
                        <Button onClick={uebernehmen} disabled={gewaehlt.size === 0}>
                            Übernehmen ({gewaehlt.size})
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}

import { useState } from 'react';
import { Button } from '../../../components/ui/button';
import { Select } from '../../../components/ui/select-custom';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../../../components/ui/dialog';

export type AbgleichWahl = Record<string, 'lokal' | 'server'>;
export type AbgleichFeld = { id: string; label: string; lokal: string; server: string };
export type EntwurfKonflikt = {
  felder: AbgleichFeld[]; hinweise: string[];
  anwenden: (wahl: AbgleichWahl) => void;
};

/** Keine neue Version übernehmen, bevor jede abweichende Eingabe bewusst abgeglichen wurde. */
export function KonfliktAbgleichDialog({ konflikt, onAbbrechen, onUebernehmen }: {
  konflikt: EntwurfKonflikt; onAbbrechen: () => void; onUebernehmen: (wahl: AbgleichWahl) => void;
}) {
  const [wahl, setWahl] = useState<AbgleichWahl>({});
  const offen = konflikt.felder.filter(feld => !wahl[feld.id]).length;
  return <Dialog open onOpenChange={open => { if (!open) onAbbrechen(); }} className="max-w-4xl">
    <DialogContent className="flex max-h-[85vh] flex-col">
      <DialogHeader><DialogTitle>Zwischenzeitliche Änderungen prüfen</DialogTitle></DialogHeader>
      <p className="text-sm text-slate-600">Ihr Entwurf bleibt erhalten. Entscheiden Sie für jede Abweichung, welcher Stand in die nächste Fassung übernommen wird.</p>
      <div className="min-h-0 space-y-4 overflow-y-auto">
        {konflikt.hinweise.map((hinweis, index) => <p key={index} className="whitespace-pre-wrap rounded border border-slate-200 bg-slate-50 p-3 text-sm">{hinweis}</p>)}
        {konflikt.felder.map(feld => <section key={feld.id} aria-label={feld.label} className="space-y-2 rounded border border-slate-200 p-3">
          <h3 className="font-semibold">{feld.label}</h3>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div><dt className="font-medium">Mein Entwurf</dt><dd className="whitespace-pre-wrap break-words">{feld.lokal || 'Nicht angegeben'}</dd></div>
            <div><dt className="font-medium">Aktuell gespeichert</dt><dd className="whitespace-pre-wrap break-words">{feld.server || 'Nicht angegeben'}</dd></div>
          </dl>
          <Select aria-label={`${feld.label}: Stand auswählen`} value={wahl[feld.id] ?? ''} placeholder="Bitte bewusst auswählen"
            options={[{ value: 'server', label: 'Aktuell gespeicherten Wert übernehmen' }, { value: 'lokal', label: 'Meinen Entwurf verwenden' }]}
            onChange={value => setWahl(old => ({ ...old, [feld.id]: value as 'lokal' | 'server' }))} />
        </section>)}
        {!konflikt.felder.length && <p className="text-sm">Die Eingaben stimmen überein. Die aktuellen Bedarfsangaben und Versionsstände werden übernommen.</p>}
      </div>
      <DialogFooter className="shrink-0 border-t border-slate-100 pt-3">
        <Button variant="outline" onClick={onAbbrechen}>Zurück zum Entwurf</Button>
        <Button disabled={offen > 0} title={offen ? `${offen} Abweichungen müssen noch entschieden werden.` : undefined}
          onClick={() => onUebernehmen(wahl)}>Abgleich übernehmen</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>;
}

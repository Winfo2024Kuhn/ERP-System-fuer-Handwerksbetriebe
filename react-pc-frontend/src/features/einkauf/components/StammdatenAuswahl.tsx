import { useState } from 'react';
import { ArtikelSuche } from '../../../components/artikel/ArtikelSuche';
import { ProjektSearchModal } from '../../../components/ProjektSearchModal';
import { Button } from '../../../components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../../../components/ui/dialog';

export interface StammdatenWahl { id: number; name: string }
export function StammdatenAuswahl({ art, value, onChange }: { art: 'Artikel' | 'Projekt'; value: StammdatenWahl | null; onChange: (value: StammdatenWahl | null) => void }) {
  const [offen, setOffen] = useState(false);
  const waehlen = (wahl: StammdatenWahl) => { onChange(wahl); setOffen(false); };
  return <div className="space-y-2 text-sm">
    <p className="font-medium">{art}: {value?.name ?? 'Nicht ausgewählt'}</p>
    <div className="flex flex-wrap gap-2"><Button type="button" variant="outline" size="sm" onClick={() => setOffen(true)}>{art} auswählen</Button>
      {value && <Button type="button" variant="ghost" size="sm" onClick={() => onChange(null)}>{art} entfernen</Button>}</div>
    {art === 'Projekt' && offen && <ProjektSearchModal isOpen onClose={() => setOffen(false)} currentProjektId={value?.id} onSelect={projekt => waehlen({ id: projekt.id, name: [projekt.auftragsnummer, projekt.bauvorhaben].filter(Boolean).join(' · ') })} />}
    {art === 'Artikel' && offen && <Dialog open onOpenChange={setOffen} className="w-[min(70rem,calc(100vw-2rem))]">
      <DialogHeader className="px-5 pt-5"><DialogTitle>Artikel auswählen</DialogTitle></DialogHeader>
      <DialogContent className="overflow-y-auto px-5 pb-5"><ArtikelSuche urlSync={false}
        onZeilenKlick={artikel => waehlen({ id: artikel.id, name: [artikel.artikelnummer, artikel.produktname].filter(Boolean).join(' · ') })}
        zeilenAktion={artikel => <Button type="button" variant="outline" size="sm" aria-label={`${artikel.produktname} auswählen`} onClick={event => { event.stopPropagation(); waehlen({ id: artikel.id, name: [artikel.artikelnummer, artikel.produktname].filter(Boolean).join(' · ') }); }}>Auswählen</Button>} />
      </DialogContent>
    </Dialog>}
  </div>;
}

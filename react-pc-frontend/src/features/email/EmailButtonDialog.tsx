import { useEffect, useState } from 'react';
import { Link2, Star } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import {
  BEWERTUNGS_URL_PLATZHALTER,
  MAX_BUTTON_TEXT_LAENGE,
  STANDARD_BUTTON_TEXT,
  normalisiereEigeneAdresse,
} from './emailButton';
import type { EmailButtonAttribute } from './emailButtonExtension';

type Ziel = 'bewertung' | 'eigene';

interface EmailButtonDialogProps {
  offen: boolean;
  /** Vorhandener Button beim Ändern, `null` beim Neu-Einfügen. */
  vorhanden: EmailButtonAttribute | null;
  onSchliessen: () => void;
  onUebernehmen: (attribute: EmailButtonAttribute) => void;
}

const ZIELE: { wert: Ziel; titel: string; beschreibung: string; icon: typeof Star }[] = [
  {
    wert: 'bewertung',
    titel: 'Google-Bewertung',
    beschreibung: 'Nimmt den Bewertungs-Link aus den Firmendaten.',
    icon: Star,
  },
  {
    wert: 'eigene',
    titel: 'Eigene Adresse',
    beschreibung: 'Zum Beispiel Ihre Webseite oder ein Formular.',
    icon: Link2,
  },
];

export function EmailButtonDialog({ offen, vorhanden, onSchliessen, onUebernehmen }: EmailButtonDialogProps) {
  const toast = useToast();
  const [text, setText] = useState(STANDARD_BUTTON_TEXT);
  const [ziel, setZiel] = useState<Ziel>('bewertung');
  const [adresse, setAdresse] = useState('');

  // Beim Öffnen mit den Werten des vorhandenen Buttons bzw. den Standardwerten starten.
  useEffect(() => {
    if (!offen) return;
    const istBewertung = !vorhanden || vorhanden.href === BEWERTUNGS_URL_PLATZHALTER;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setText(vorhanden?.text || STANDARD_BUTTON_TEXT);
    setZiel(istBewertung ? 'bewertung' : 'eigene');
    setAdresse(istBewertung ? '' : vorhanden?.href ?? '');
  }, [offen, vorhanden]);

  const uebernehmen = () => {
    const beschriftung = text.trim();
    if (!beschriftung) {
      toast.warning('Bitte eine Beschriftung für den Button eingeben.');
      return;
    }
    let href = BEWERTUNGS_URL_PLATZHALTER;
    if (ziel === 'eigene') {
      const normalisiert = normalisiereEigeneAdresse(adresse);
      if (!normalisiert) {
        toast.warning('Bitte eine gültige Internet-Adresse eingeben, z. B. www.ihre-firma.de');
        return;
      }
      href = normalisiert;
    }
    onUebernehmen({ text: beschriftung.slice(0, MAX_BUTTON_TEXT_LAENGE), href });
  };

  return (
    <Dialog
      open={offen}
      onOpenChange={(o) => { if (!o) onSchliessen(); }}
      className="w-full max-w-lg"
      aria-labelledby="email-button-dialog-titel"
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle id="email-button-dialog-titel" className="text-slate-900">
            {vorhanden ? 'Button ändern' : 'Button einfügen'}
          </DialogTitle>
          <DialogDescription>
            Ein Button fällt in der E-Mail mehr auf als ein einfacher Link.
          </DialogDescription>
        </DialogHeader>

        <form
          className="space-y-5"
          onSubmit={(event) => {
            event.preventDefault();
            uebernehmen();
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="email-button-text">Beschriftung</Label>
            <Input
              id="email-button-text"
              value={text}
              maxLength={MAX_BUTTON_TEXT_LAENGE}
              onChange={(event) => setText(event.target.value)}
              placeholder={STANDARD_BUTTON_TEXT}
              autoFocus
            />
          </div>

          <div className="space-y-2">
            <Label id="email-button-ziel-label">Wohin führt der Button?</Label>
            <div role="radiogroup" aria-labelledby="email-button-ziel-label" className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {ZIELE.map(({ wert, titel, beschreibung, icon: Icon }) => (
                <button
                  key={wert}
                  type="button"
                  role="radio"
                  aria-checked={ziel === wert}
                  onClick={() => setZiel(wert)}
                  className={cn(
                    'flex items-start gap-3 rounded-lg border p-3 text-left transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500',
                    ziel === wert
                      ? 'border-rose-300 bg-rose-50'
                      : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50'
                  )}
                >
                  <Icon
                    aria-hidden="true"
                    className={cn('mt-0.5 h-4 w-4 shrink-0', ziel === wert ? 'text-rose-600' : 'text-slate-400')}
                  />
                  <span>
                    <span className={cn('block text-sm font-medium', ziel === wert ? 'text-rose-900' : 'text-slate-900')}>
                      {titel}
                    </span>
                    <span className="block text-xs text-slate-500">{beschreibung}</span>
                  </span>
                </button>
              ))}
            </div>
          </div>

          {ziel === 'eigene' ? (
            <div className="space-y-2">
              <Label htmlFor="email-button-adresse">Internet-Adresse</Label>
              <Input
                id="email-button-adresse"
                value={adresse}
                onChange={(event) => setAdresse(event.target.value)}
                placeholder="z. B. www.ihre-firma.de"
                inputMode="url"
              />
            </div>
          ) : (
            <p className="text-xs text-slate-500">
              Den Bewertungs-Link hinterlegen Sie unter Firma › „Bewertungs-Link (Google)“. Ist dort keiner eingetragen,
              wird der Button beim Versand weggelassen.
            </p>
          )}

          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" size="sm" onClick={onSchliessen}>
              Abbrechen
            </Button>
            <Button type="submit" size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700">
              {vorhanden ? 'Übernehmen' : 'Einfügen'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

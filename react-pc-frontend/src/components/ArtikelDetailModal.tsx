import { useEffect, useMemo, useState } from 'react';
import { X, Edit2, TrendingUp, Package, ChevronDown } from 'lucide-react';
import { Button } from './ui/button';
import { cn } from '../lib/utils';
import type { Artikel, ArtikelLieferantPreis, ArtikelPreisHistorieEintrag, PreisQuelle } from '../types';

const HISTORIE_INITIAL = 10;

const formatCurrency = (val?: number) =>
  val === undefined || val === null
    ? '—'
    : new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', minimumFractionDigits: 2, maximumFractionDigits: 4 }).format(val);

const formatDate = (dateStr?: string) =>
  dateStr ? new Date(dateStr).toLocaleDateString('de-DE') : '—';

const formatDateTime = (dateStr?: string) =>
  dateStr
    ? new Date(dateStr).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
    : '—';

const formatMenge = (val?: number) =>
  val === undefined || val === null ? '—' : new Intl.NumberFormat('de-DE', { maximumFractionDigits: 3 }).format(val);

const einheitLabel = (einheit?: string | { name: string; anzeigename?: string }): string => {
  if (!einheit) return '';
  if (typeof einheit === 'string') return einheit;
  return einheit.anzeigename || einheit.name;
};

const einheitKuerzel = (einheit?: string | { name: string; anzeigename?: string }): string => {
  const name = typeof einheit === 'string' ? einheit : einheit?.name;
  switch (name) {
    case 'KILOGRAMM': return 'kg';
    case 'LAUFENDE_METER': return 'm';
    case 'QUADRATMETER': return 'm²';
    case 'STUECK': return 'Stk';
    default: return einheitLabel(einheit);
  }
};

const QUELLE_META: Record<PreisQuelle, { label: string; className: string }> = {
  RECHNUNG: { label: 'Rechnung', className: 'bg-rose-100 text-rose-800 border-rose-200' },
  ANGEBOT: { label: 'Angebot', className: 'bg-amber-100 text-amber-800 border-amber-200' },
  KATALOG: { label: 'Katalog', className: 'bg-slate-100 text-slate-700 border-slate-200' },
  MANUELL: { label: 'Manuell', className: 'bg-sky-100 text-sky-800 border-sky-200' },
  VORSCHLAG: { label: 'Vorschlag', className: 'bg-violet-100 text-violet-800 border-violet-200' },
};

interface ArtikelDetailModalProps {
  artikel: Artikel;
  highlightLieferantName?: string;
  onClose: () => void;
  onEditPrice: (artikel: Artikel, lieferant: { id: number; name: string } | null) => void;
}

export function ArtikelDetailModal({ artikel, highlightLieferantName, onClose, onEditPrice }: ArtikelDetailModalProps) {
  const [historie, setHistorie] = useState<ArtikelPreisHistorieEintrag[]>([]);
  const [historieLoading, setHistorieLoading] = useState(true);
  const [historieError, setHistorieError] = useState<string | null>(null);
  const [historieLimit, setHistorieLimit] = useState(HISTORIE_INITIAL);

  useEffect(() => {
    let cancelled = false;
    fetch(`/api/artikel/${artikel.id}/preis-historie`)
      .then(res => {
        if (!res.ok) throw new Error('Historie konnte nicht geladen werden');
        return res.json();
      })
      .then((data: ArtikelPreisHistorieEintrag[]) => {
        if (cancelled) return;
        setHistorie(Array.isArray(data) ? data : []);
        setHistorieError(null);
        setHistorieLoading(false);
      })
      .catch(err => {
        if (cancelled) return;
        console.error(err);
        setHistorieError('Preis-Historie konnte nicht geladen werden.');
        setHistorie([]);
        setHistorieLoading(false);
      });
    return () => { cancelled = true; };
  }, [artikel.id]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const lieferantenpreise: ArtikelLieferantPreis[] = useMemo(
    () => artikel.lieferantenpreise ?? [],
    [artikel.lieferantenpreise]
  );

  const einheit = einheitKuerzel(artikel.verrechnungseinheit);
  const durchschnitt = artikel.durchschnittspreisNetto;
  const durchschnittMenge = artikel.durchschnittspreisMenge;
  const durchschnittDatum = artikel.durchschnittspreisAktualisiertAm;

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl max-h-[90vh] flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        {/* Header */}
        <div className="p-5 border-b border-slate-100 flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-xs font-semibold text-rose-600 uppercase tracking-wide">Artikel-Detail</p>
            <h3 className="text-xl font-bold text-slate-900 truncate">{artikel.produktname}</h3>
            <p className="text-sm text-slate-500 truncate">
              {[artikel.produktlinie, artikel.werkstoffName].filter(Boolean).join(' · ') || '—'}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg p-1 transition-colors"
            aria-label="Schließen"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scroll-Bereich */}
        <div className="flex-1 overflow-y-auto">
          {/* Ø-Preis-Header */}
          <div className="p-5 bg-gradient-to-br from-rose-50 to-white border-b border-slate-100">
            <div className="flex items-start gap-3">
              <div className="p-2 bg-rose-100 rounded-lg">
                <TrendingUp className="w-5 h-5 text-rose-600" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-xs text-slate-500 uppercase tracking-wide font-medium">Durchschnittspreis</p>
                {durchschnitt !== undefined && durchschnitt !== null ? (
                  <>
                    <p className="text-3xl font-bold text-slate-900 mt-1">
                      {formatCurrency(durchschnitt)}
                      <span className="text-base font-normal text-slate-500 ml-1">/ {einheit || 'Einheit'}</span>
                    </p>
                    <p className="text-sm text-slate-500 mt-1">
                      {durchschnittMenge && durchschnittMenge > 0
                        ? <>aus {formatMenge(durchschnittMenge)} {einheit} Historie · </>
                        : null}
                      aktualisiert am {formatDateTime(durchschnittDatum)}
                    </p>
                  </>
                ) : (
                  <p className="text-base text-slate-500 mt-1">Noch kein Durchschnittspreis erfasst.</p>
                )}
              </div>
            </div>
          </div>

          {/* Lieferantenpreise */}
          <div className="p-5 border-b border-slate-100">
            <div className="flex items-center gap-2 mb-3">
              <Package className="w-4 h-4 text-slate-500" />
              <h4 className="text-sm font-semibold text-slate-900">
                Aktuelle Lieferantenpreise
                <span className="ml-2 text-slate-500 font-normal">({lieferantenpreise.length})</span>
              </h4>
            </div>
            {lieferantenpreise.length === 0 ? (
              <p className="text-sm text-slate-500 italic">Für diesen Artikel ist noch kein Lieferantenpreis hinterlegt.</p>
            ) : (
              <div className="overflow-x-auto -mx-5 px-5">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-xs text-slate-500 uppercase tracking-wide border-b border-slate-200">
                      <th className="py-2 pr-3 font-medium">Lieferant</th>
                      <th className="py-2 pr-3 font-medium">Externe Nr.</th>
                      <th className="py-2 pr-3 font-medium text-right">Preis</th>
                      <th className="py-2 pr-3 font-medium text-right">Datum</th>
                      <th className="py-2 w-10"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {lieferantenpreise.map(lp => {
                      const highlighted =
                        highlightLieferantName &&
                        lp.lieferantName?.toLowerCase() === highlightLieferantName.toLowerCase();
                      return (
                        <tr
                          key={lp.lieferantId}
                          className={cn(highlighted && 'bg-rose-50/70')}
                        >
                          <td className="py-2 pr-3 font-medium text-slate-900">{lp.lieferantName}</td>
                          <td className="py-2 pr-3 font-mono text-xs text-slate-600">{lp.externeArtikelnummer || '—'}</td>
                          <td className="py-2 pr-3 text-right font-medium text-slate-900">
                            {formatCurrency(lp.preis)}
                            {einheit && <span className="text-xs font-normal text-slate-400 ml-1">/ {einheit}</span>}
                          </td>
                          <td className="py-2 pr-3 text-right text-slate-500 text-xs">{formatDate(lp.preisDatum)}</td>
                          <td className="py-2">
                            <button
                              onClick={() => onEditPrice(artikel, { id: lp.lieferantId, name: lp.lieferantName })}
                              className="p-1 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded transition-colors"
                              title={`Preis von ${lp.lieferantName} bearbeiten`}
                              aria-label={`Preis von ${lp.lieferantName} bearbeiten`}
                            >
                              <Edit2 className="w-4 h-4" />
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
            <div className="mt-3">
              <Button
                variant="outline"
                size="sm"
                className="border-rose-300 text-rose-700 hover:bg-rose-50"
                onClick={() => onEditPrice(artikel, null)}
              >
                Neuen Lieferantenpreis hinzufügen
              </Button>
            </div>
          </div>

          {/* Preis-Historie */}
          <div className="p-5">
            <h4 className="text-sm font-semibold text-slate-900 mb-3">
              Preis-Historie
              {!historieLoading && <span className="ml-2 text-slate-500 font-normal">({historie.length})</span>}
            </h4>
            {historieLoading ? (
              <p className="text-sm text-slate-500">Wird geladen…</p>
            ) : historieError ? (
              <p className="text-sm text-rose-600">{historieError}</p>
            ) : historie.length === 0 ? (
              <p className="text-sm text-slate-500 italic">Noch keine Preis-Historie vorhanden.</p>
            ) : (
              <>
                <ul className="divide-y divide-slate-100">
                  {historie.slice(0, historieLimit).map(eintrag => {
                    const meta = QUELLE_META[eintrag.quelle] ?? QUELLE_META.MANUELL;
                    const eintragEinheit = einheitKuerzel(eintrag.einheit);
                    return (
                      <li key={eintrag.id} className="py-3 flex items-start gap-3">
                        <span
                          className={cn(
                            'shrink-0 text-xs font-medium px-2 py-1 rounded-md border',
                            meta.className
                          )}
                        >
                          {meta.label}
                        </span>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-baseline justify-between gap-2 flex-wrap">
                            <p className="text-sm font-medium text-slate-900">
                              {formatCurrency(eintrag.preis)}
                              {eintragEinheit && <span className="text-xs font-normal text-slate-400 ml-1">/ {eintragEinheit}</span>}
                              {eintrag.menge && eintrag.menge > 0 && (
                                <span className="text-xs font-normal text-slate-500 ml-2">
                                  · Menge {formatMenge(eintrag.menge)} {eintragEinheit}
                                </span>
                              )}
                            </p>
                            <span className="text-xs text-slate-500">{formatDateTime(eintrag.erfasstAm)}</span>
                          </div>
                          <p className="text-xs text-slate-500 mt-0.5 truncate">
                            {eintrag.lieferantName || 'Ohne Lieferant'}
                            {eintrag.belegReferenz ? ` · Beleg ${eintrag.belegReferenz}` : ''}
                            {eintrag.externeNummer ? ` · Nr. ${eintrag.externeNummer}` : ''}
                          </p>
                          {eintrag.bemerkung && (
                            <p className="text-xs text-slate-500 italic mt-0.5 truncate">{eintrag.bemerkung}</p>
                          )}
                        </div>
                      </li>
                    );
                  })}
                </ul>
                {historie.length > historieLimit && (
                  <button
                    type="button"
                    className="mt-3 inline-flex items-center gap-1 text-sm text-rose-700 hover:text-rose-800 font-medium"
                    onClick={() => setHistorieLimit(l => l + HISTORIE_INITIAL)}
                  >
                    <ChevronDown className="w-4 h-4" />
                    Mehr anzeigen ({historie.length - historieLimit} weitere)
                  </button>
                )}
              </>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-slate-100 bg-slate-50 flex justify-end rounded-b-2xl">
          <Button variant="ghost" size="sm" onClick={onClose}>Schließen</Button>
        </div>
      </div>
    </div>
  );
}

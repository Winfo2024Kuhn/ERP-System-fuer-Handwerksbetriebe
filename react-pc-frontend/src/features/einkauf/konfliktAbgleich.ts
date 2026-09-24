import type { AbgleichFeld, AbgleichWahl } from './components/KonfliktAbgleichDialog';
import type { PositionDraft } from './positionDrafts';
import type { EinkaufAnlage, KontaktSnapshot } from './types';

const normal = (value: unknown): unknown => Array.isArray(value) ? value.map(normal)
  : value !== null && typeof value === 'object' ? Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => [k, normal(v)])) : value ?? null;
export const gleich = (a: unknown, b: unknown) => JSON.stringify(normal(a)) === JSON.stringify(normal(b));
export function abgleichFeld<T>(felder: AbgleichFeld[], id: string, label: string, lokal: T, server: T, anzeigen: (v: T) => string) {
  if (!gleich(lokal, server)) felder.push({ id, label, lokal: anzeigen(lokal), server: anzeigen(server) });
}
export function abgeglichenerWert<T>(wahl: AbgleichWahl, id: string, lokal: T, server: T): T {
  if (gleich(lokal, server)) return lokal;
  if (!wahl[id]) throw new Error('Bitte alle abweichenden Angaben bewusst abgleichen.');
  return wahl[id] === 'server' ? server : lokal;
}
export const kontaktText = (k?: KontaktSnapshot) => k ? `${k.lieferantenname || ''}\n${k.name || 'Ohne Ansprechpartner'} · ${k.email || 'Ohne E-Mail'}\nAnrede: ${k.anrede || 'Keine'}\nUnsere Kundennummer: ${k.eigeneKundennummer || 'Nicht hinterlegt'}` : 'Nicht enthalten';
export const positionsFelder: Record<keyof PositionDraft, string> = {
  art: 'Positionsart', artikelId: 'Artikelbezug', interneReferenz: 'Interne Referenz', zeichnungsnummer: 'Zeichnungsnummer', zeichnungsrevision: 'Zeichnungsrevision',
  bezeichnung: 'Bezeichnung', werkstoff: 'Werkstoff', abmessung: 'Abmessung', menge: 'Bedarfsmenge', einheit: 'Einheit', stueckzahl: 'Stückzahl',
  einzelLaengeMm: 'Einzellänge in mm', kgJeMeter: 'Gewicht je Meter', faktorQuelle: 'Quelle des Faktors', schnittForm: 'Schnittform',
  winkelLinks: 'Linker Winkel', winkelRechts: 'Rechter Winkel', bearbeitung: 'Bearbeitung', oberflaeche: 'Oberfläche', dokumente: 'Geforderte Nachweise', anlageVersionIds: 'Anlagenversionen',
  beschaffungsdetails: 'Lieferant und Zuschnitt', positionsnummer: 'Positionsnummer', gesamtwerte: 'Gewicht und Mantelfläche',
};
export function positionsWertText(key: keyof PositionDraft, value: PositionDraft[keyof PositionDraft], anlagen: EinkaufAnlage[]): string {
  if (key === 'anlageVersionIds') return (value as number[]).map(id => { const a = anlagen.find(item => item.id === id); return a ? `${a.dateiname} · Revision ${a.revision} · ${a.freigegeben ? 'Freigegeben' : 'Nicht freigegeben'}` : 'Dateiversion nicht mehr verfügbar'; }).join('\n') || 'Keine Anlagen';
  if (key === 'beschaffungsdetails') {
    // Interne Kennungen sagen im Abgleich nichts; lesbar ist nur die Artikelnummer beim Lieferanten.
    const d = (value as PositionDraft['beschaffungsdetails'])?.werte;
    const teile = d ? [(d.lieferantId != null || d.kategorieId != null) && 'Lieferantenangaben hinterlegt',
      (d.schnittbildId != null || d.schnittAchseId != null) && 'Schnittbild für den Zuschnitt hinterlegt',
      d.externeArtikelnummer && `Artikelnummer beim Lieferanten: ${d.externeArtikelnummer}`].filter(Boolean) : [];
    return teile.join('\n') || 'Nicht angegeben';
  }
  if (key === 'gesamtwerte') {
    const g = value as PositionDraft['gesamtwerte'];
    const zahl = (wert: number) => wert.toLocaleString('de-DE', { maximumFractionDigits: 4 });
    const teile = g ? [g.gesamtgewichtKg != null && `${zahl(g.gesamtgewichtKg)} kg`,
      g.mantelflaecheM2 != null && `Mantelfläche ${zahl(g.mantelflaecheM2)} m²`].filter(Boolean) : [];
    return teile.join(' · ') || 'Nicht angegeben';
  }
  if (key === 'dokumente') return (value as PositionDraft['dokumente']).map(d => `${d.art.replaceAll('_', ' ')} · ${d.grundlage} · ${d.grundlageVersion} · ${d.fachlichBestaetigt ? 'Bestätigt' : 'Offen'}`).join('\n') || 'Keine Nachweise';
  return value == null || value === '' ? 'Nicht angegeben' : String(value).replaceAll('_', ' ');
}

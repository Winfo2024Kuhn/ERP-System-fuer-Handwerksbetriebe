import { describe, expect, it } from 'vitest';
import { leereArbeitszeit, zeitEntwurf, pruefeArbeitszeit, BUCHUNGSZEIT_LABELS, ZEITFENSTER_LABELS } from './arbeitszeitInput';
const leer = { montagStunden: 0, dienstagStunden: 0, mittwochStunden: 0, donnerstagStunden: 0, freitagStunden: 0, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: null, buchungEndeZeit: null };
const entwurf = { montagStunden: '8,5', dienstagStunden: '8', mittwochStunden: '7,25', donnerstagStunden: '24', freitagStunden: '0', samstagStunden: '0', sonntagStunden: '0', buchungStartZeit: '06:00', buchungEndeZeit: '18:00' };
it('erstellt unabhängige Nullwerte ohne Zeitfenster', () => {
 const a = leereArbeitszeit(); a.montagStunden = 5; expect(leereArbeitszeit()).toEqual(leer);
});
it('konvertiert deutsche Stunden und optionale Serverzeiten in Text', () => {
 expect(zeitEntwurf({ ...leer, montagStunden: 8.5, dienstagStunden: 7.25, buchungStartZeit: '06:00:00' })).toEqual({ montagStunden: '8,5', dienstagStunden: '7,25', mittwochStunden: '0', donnerstagStunden: '0', freitagStunden: '0', samstagStunden: '0', sonntagStunden: '0', buchungStartZeit: '06:00', buchungEndeZeit: '' });
});
it('übernimmt sieben gültige Tage einschließlich 0 und 24', () => {
 expect(pruefeArbeitszeit(entwurf)).toEqual({ ...leer, montagStunden: 8.5, dienstagStunden: 8, mittwochStunden: 7.25, donnerstagStunden: 24, buchungStartZeit: '06:00', buchungEndeZeit: '18:00' });
});
it.each(['montagStunden', 'dienstagStunden', 'mittwochStunden', 'donnerstagStunden', 'freitagStunden', 'samstagStunden', 'sonntagStunden'])('verlangt auch für %s eine vollständige Pflichtzahl', feld => {
 expect(() => pruefeArbeitszeit({ ...entwurf, [feld]: '' })).toThrow(/Stunden eingeben/);
});
it.each(['8,', '8.5', '-1', '24,01', 'abc'])('verhindert ungültige Stunden %s', wert => {
 expect(() => pruefeArbeitszeit({ ...entwurf, montagStunden: wert })).toThrow(/Montag Stunden/);
});
it('belässt ein leeres Ende und reicht den gespeicherten Beginn unverändert durch', () => {
 expect(pruefeArbeitszeit({ ...entwurf, buchungStartZeit: '', buchungEndeZeit: '' })).toMatchObject({ buchungStartZeit: null, buchungEndeZeit: null });
 expect(pruefeArbeitszeit({ ...entwurf, buchungEndeZeit: '' })).toMatchObject({ buchungStartZeit: '06:00', buchungEndeZeit: null });
});
it('prüft die Reihenfolge Beginn/Ende nicht mehr, weil es den Beginn in der Oberfläche nicht gibt', () => {
 expect(pruefeArbeitszeit({ ...entwurf, buchungStartZeit: '19:00', buchungEndeZeit: '18:00' })).toMatchObject({ buchungStartZeit: '19:00', buchungEndeZeit: '18:00' });
});
describe.each([
 [BUCHUNGSZEIT_LABELS, 'Späteste Buchung'],
 [ZEITFENSTER_LABELS, 'Spätestes Ende'],
] as const)('Zeitfenster mit seitenspezifischen Meldungen', (labels, ende) => {
 it.each(['8:00', '25:00', '12:60'])('weist ungültiges Ende %s zurück', wert => {
  expect(() => pruefeArbeitszeit({ ...entwurf, buchungEndeZeit: wert }, labels)).toThrow(ende);
 });
});

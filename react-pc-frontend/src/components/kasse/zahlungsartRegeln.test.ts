import { describe, expect, it } from 'vitest';
import {
    folgeSatz, fragtNachZahlung, giltAlsBezahlt, kategorieAusZahlungsart,
} from './zahlungsartRegeln';

describe('zahlungsartRegeln', () => {
    it.each([
        ['Bar', 'Bar → landet im Kassenbuch', 'KASSE_AUSGABE'],
        ['EC-Karte', 'EC-Karte → läuft über die Bank, nicht über die Kasse', 'BANK'],
        ['Überweisung', 'Überweisung → läuft über die Bank, nicht über die Kasse', 'BANK'],
        ['Lastschrift', 'Lastschrift → wird von der Bank abgebucht, nicht aus der Kasse', 'BANK'],
        ['Kreditkarte', 'Kreditkarte → läuft über die Kreditkarten-Abrechnung', 'KREDITKARTE'],
        ['PayPal', 'PayPal → läuft über das PayPal-Konto, nicht über die Kasse', 'BANK'],
        ['Online-Zahlung', 'Online-Zahlung → läuft über die Bank, nicht über die Kasse', 'BANK'],
        ['Scheck', 'Scheck → wird über die Bank eingelöst', 'BANK'],
        ['Rechnung', 'Rechnung → wird später bezahlt, nicht bar', 'SONSTIGER_BELEG'],
    ] as const)('ordnet %s mit Satz und Kategorie zu', (zahlungsart, satz, kategorie) => {
        expect(folgeSatz(zahlungsart)).toBe(satz);
        expect(kategorieAusZahlungsart(zahlungsart, true)).toBe(kategorie);
    });

    it('ordnet Bareinnahmen der Kasse als Einnahme zu', () => {
        expect(kategorieAusZahlungsart('Bar', false)).toBe('KASSE_EINNAHME');
    });

    it('fragt nur bei noch nicht automatisch bezahlten Rechnungen nach dem Zahlungsstand', () => {
        expect(fragtNachZahlung('Bar', 'RECHNUNG')).toBe(false);
        expect(fragtNachZahlung('Überweisung', 'RECHNUNG')).toBe(true);
        expect(fragtNachZahlung('Überweisung', 'LIEFERSCHEIN')).toBe(false);
    });

    it('erkennt die sofort bezahlten Zahlungsarten', () => {
        expect(giltAlsBezahlt('Bar')).toBe(true);
        expect(giltAlsBezahlt('EC-Karte')).toBe(true);
        expect(giltAlsBezahlt('Kreditkarte')).toBe(true);
        expect(giltAlsBezahlt('PayPal')).toBe(true);
        expect(giltAlsBezahlt('Online-Zahlung')).toBe(true);
        expect(giltAlsBezahlt('Überweisung')).toBe(false);
    });

    it('erklärt unbekannte oder leere Zahlungsarten ehrlich', () => {
        const satz = 'Bitte wählen – daraus ergibt sich, wo die Buchung landet.';
        expect(folgeSatz('Gutschein')).toBe(satz);
        expect(folgeSatz(null)).toBe(satz);
        expect(kategorieAusZahlungsart('Gutschein', true)).toBeNull();
    });
});

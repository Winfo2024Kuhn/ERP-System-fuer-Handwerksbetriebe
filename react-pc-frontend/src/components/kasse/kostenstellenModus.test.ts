import { describe, expect, it } from 'vitest';
import { istEinfacheKostenstellenZuordnung } from './kostenstellenModus';

describe('istEinfacheKostenstellenZuordnung', () => {
    it('lässt nur einen unveränderten 100-Prozent-Split in der einfachen Auswahl zu', () => {
        expect(istEinfacheKostenstellenZuordnung([])).toBe(true);
        expect(istEinfacheKostenstellenZuordnung([{ kostenstelleId: 4, prozent: 100, absoluterBetrag: null, streckungJahre: 1, streckungStartJahr: null }])).toBe(true);
        expect(istEinfacheKostenstellenZuordnung([{ kostenstelleId: 4, prozent: 100, absoluterBetrag: null, streckungJahre: 1, streckungStartJahr: 2026 }])).toBe(true);
    });

    it('zeigt einen einzelnen unvollständigen Split weiter im Editor, damit er nicht verloren geht', () => {
        expect(istEinfacheKostenstellenZuordnung([{ kostenstelleId: 4, prozent: 0, absoluterBetrag: null, streckungJahre: 1, streckungStartJahr: null }])).toBe(false);
    });

    it('behält Streckung, Betrag, Beschreibung und mehrere Splits im Editor', () => {
        expect(istEinfacheKostenstellenZuordnung([{ kostenstelleId: 4, prozent: null, absoluterBetrag: 50, streckungJahre: 1, streckungStartJahr: null }])).toBe(false);
        expect(istEinfacheKostenstellenZuordnung([{ kostenstelleId: 4, prozent: 100, absoluterBetrag: null, streckungJahre: 2, streckungStartJahr: 2026 }])).toBe(false);
        expect(istEinfacheKostenstellenZuordnung([{ kostenstelleId: 4, prozent: 100, absoluterBetrag: null, streckungJahre: 1, streckungStartJahr: null, beschreibung: 'Aufteilung' }])).toBe(false);
        expect(istEinfacheKostenstellenZuordnung([{ kostenstelleId: 4, prozent: 100, absoluterBetrag: null, streckungJahre: 1, streckungStartJahr: null }, { kostenstelleId: 5, prozent: 0, absoluterBetrag: null, streckungJahre: 1, streckungStartJahr: null }])).toBe(false);
    });
});

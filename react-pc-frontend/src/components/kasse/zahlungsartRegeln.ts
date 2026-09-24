import type { BelegKategorie } from '../../types';

// Diese Klartext-Tabelle entspricht der Stammdaten-Tabelle in
// ZahlungsartMapper.java. KI-Codes werden dort zuerst in diese Werte übersetzt.
const REGELN: Record<string, { satz: string; kategorie: BelegKategorie | 'BAR'; bezahlt: boolean }> = {
    Bar: { satz: 'Bar → landet im Kassenbuch', kategorie: 'BAR', bezahlt: true },
    'EC-Karte': { satz: 'EC-Karte → läuft über die Bank, nicht über die Kasse', kategorie: 'BANK', bezahlt: true },
    Überweisung: { satz: 'Überweisung → läuft über die Bank, nicht über die Kasse', kategorie: 'BANK', bezahlt: false },
    Lastschrift: { satz: 'Lastschrift → wird von der Bank abgebucht, nicht aus der Kasse', kategorie: 'BANK', bezahlt: false },
    Kreditkarte: { satz: 'Kreditkarte → läuft über die Kreditkarten-Abrechnung', kategorie: 'KREDITKARTE', bezahlt: true },
    PayPal: { satz: 'PayPal → läuft über das PayPal-Konto, nicht über die Kasse', kategorie: 'BANK', bezahlt: true },
    'Online-Zahlung': { satz: 'Online-Zahlung → läuft über die Bank, nicht über die Kasse', kategorie: 'BANK', bezahlt: true },
    Scheck: { satz: 'Scheck → wird über die Bank eingelöst', kategorie: 'BANK', bezahlt: false },
    Rechnung: { satz: 'Rechnung → wird später bezahlt, nicht bar', kategorie: 'SONSTIGER_BELEG', bezahlt: false },
};

const BITTE_WAEHLEN = 'Bitte wählen – daraus ergibt sich, wo die Buchung landet.';

function regel(zahlungsart: string | null | undefined) {
    return zahlungsart ? REGELN[zahlungsart.trim()] : undefined;
}

/** Ein Satz, der sagt, was die Zahlungsart bedeutet. */
export function folgeSatz(zahlungsart: string | null | undefined): string {
    return regel(zahlungsart)?.satz ?? BITTE_WAEHLEN;
}

/** "Wo gezahlt" aus der Zahlungsart. null = keine Ableitung moeglich. */
export function kategorieAusZahlungsart(
    zahlungsart: string | null | undefined,
    richtungAusgabe: boolean,
): BelegKategorie | null {
    const kategorie = regel(zahlungsart)?.kategorie;
    if (!kategorie) return null;
    return kategorie === 'BAR' ? (richtungAusgabe ? 'KASSE_AUSGABE' : 'KASSE_EINNAHME') : kategorie;
}

/** true, wenn diese Zahlungsart automatisch als bezahlt gilt. */
export function giltAlsBezahlt(zahlungsart: string | null | undefined): boolean {
    return regel(zahlungsart)?.bezahlt === true;
}

/** true, wenn die Frage "Ist die Rechnung schon bezahlt?" gestellt wird. */
export function fragtNachZahlung(zahlungsart: string | null | undefined, dokumentTyp: string | null | undefined): boolean {
    return !giltAlsBezahlt(zahlungsart) && (dokumentTyp === 'RECHNUNG' || dokumentTyp === 'GUTSCHRIFT');
}

/**
 * Urlaubstage im deutschen Zahlenformat.
 *
 * Halbe Urlaubstage zählen 0,5 – der Server liefert Resturlaub, genommene und
 * geplante Tage deshalb als Dezimalzahl. Ganze Zahlen erscheinen ohne
 * Nachkomma („12"), halbe mit Komma („12,5"). Ungültige Werte werden als „0"
 * gezeigt, damit nie „NaN Tage" auf dem Bildschirm steht.
 */
export function formatUrlaubstage(tage: number | null | undefined): string {
    const wert = Number(tage);
    if (!Number.isFinite(wert)) return '0';
    return wert.toLocaleString('de-DE', { maximumFractionDigits: 2 });
}

/** „12,5 Tage" bzw. „1 Tag" – für Sätze, in denen die Einheit dazugehört. */
export function formatUrlaubstageMitEinheit(tage: number | null | undefined): string {
    const text = formatUrlaubstage(tage);
    return `${text} ${text === '1' ? 'Tag' : 'Tage'}`;
}

/** Was der Server zum Resturlaub aus dem Vorjahr liefert (Saldo und `/api/urlaub/resturlaub`). */
export interface VorjahresRest {
    /** Tage aus dem Vorjahr, die noch offen sind (stecken im Resturlaub). */
    ausVorjahrOffen?: number | null;
    /** Tage aus dem Vorjahr, die schon verfallen sind. */
    verfallen?: number | null;
    /** Letzter Tag für den Vorjahresrest (ISO); `null` = verfällt nicht. */
    verfallsdatum?: string | null;
}

/** „2027-03-31" → „31.03.2027", ohne Zeitzonen-Umweg über Date. */
export function formatIsoDatumDe(iso: string): string {
    const [jahr, monat, tag] = iso.slice(0, 10).split('-');
    return `${tag}.${monat}.${jahr}`;
}

/**
 * Ein kurzer Satz zum Vorjahresrest – oder `null`, wenn es keinen gibt:
 * „davon 3 Tage aus dem Vorjahr, verfallen am 31.03.2027",
 * „davon 3 Tage aus dem Vorjahr, verfallen nicht" bzw.
 * „2 Tage aus dem Vorjahr sind am 31.03.2027 verfallen".
 */
export function vorjahresHinweis(rest: VorjahresRest | null | undefined): string | null {
    if (!rest) return null;
    const offen = Number(rest.ausVorjahrOffen);
    if (Number.isFinite(offen) && offen > 0) {
        const teil = `davon ${formatUrlaubstageMitEinheit(offen)} aus dem Vorjahr`;
        return rest.verfallsdatum ? `${teil}, verfallen am ${formatIsoDatumDe(rest.verfallsdatum)}` : `${teil}, verfallen nicht`;
    }
    const verfallen = Number(rest.verfallen);
    if (Number.isFinite(verfallen) && verfallen > 0 && rest.verfallsdatum) {
        const text = formatUrlaubstageMitEinheit(verfallen);
        return `${text} aus dem Vorjahr ${text.endsWith(' Tag') ? 'ist' : 'sind'} am ${formatIsoDatumDe(rest.verfallsdatum)} verfallen`;
    }
    return null;
}

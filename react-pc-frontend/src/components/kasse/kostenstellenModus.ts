import type { KostenstellenSplit } from './KostenstellenSplitsEditor';

/** Ein Split darf nur dann als einfache Zuordnung erscheinen, wenn keine Information verborgen wird. */
export function istEinfacheKostenstellenZuordnung(splits: KostenstellenSplit[]): boolean {
    if (splits.length === 0) return true;
    if (splits.length !== 1) return false;
    const [split] = splits;
    return split.kostenstelleId != null
        && Number(split.prozent) === 100
        && split.absoluterBetrag == null
        && split.streckungJahre === 1
        && split.streckungStartJahr == null
        && !split.beschreibung;
}

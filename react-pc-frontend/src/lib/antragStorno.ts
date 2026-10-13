/** Arten eines Abwesenheitsantrags, wie sie /api/urlaub/antraege liefert. */
export type AntragTyp = 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ZEITAUSGLEICH';

const KALENDER_HINWEIS = 'Bereits eingetragene Tage werden aus dem Kalender gelöscht';

const RUECKFRAGEN: Record<AntragTyp, { title: string; message: string }> = {
    URLAUB: {
        title: 'Urlaubsantrag stornieren?',
        message: `Der Antrag wird storniert. ${KALENDER_HINWEIS}, der Resturlaub steigt wieder.`,
    },
    KRANKHEIT: {
        title: 'Krankmeldung stornieren?',
        message: `Die Krankmeldung wird storniert. ${KALENDER_HINWEIS}.`,
    },
    FORTBILDUNG: {
        title: 'Fortbildung stornieren?',
        message: `Die Fortbildung wird storniert. ${KALENDER_HINWEIS}.`,
    },
    ZEITAUSGLEICH: {
        title: 'Zeitausgleich stornieren?',
        message: `Der Zeitausgleich wird storniert. ${KALENDER_HINWEIS}.`,
    },
};

/**
 * Rückfrage vor dem Stornieren eines Antrags – Titel und Text je Art.
 * Nur beim Urlaub steigt der Resturlaub wieder, deshalb steht das nur dort.
 */
export function stornoRueckfrage(typ: string): { title: string; message: string } {
    return RUECKFRAGEN[typ as AntragTyp] ?? {
        title: 'Antrag stornieren?',
        message: `Der Antrag wird storniert. ${KALENDER_HINWEIS}.`,
    };
}

/**
 * Nur für Tests: Antworten des Endpunkts `GET /api/emails/reiter/{bezug}/{id}`,
 * damit die Reiter-Tests nicht jeder für sich die Seitenform nachbauen.
 * Dummy-Daten (DSGVO): Max Mustermann, max@example.com.
 */

/** Ein Verlauf, wie ihn der Server schickt (vor dem Normalisieren). */
export interface ReiterEintragRoh {
    id: number;
    subject?: string | null;
    fromAddress?: string | null;
    recipient?: string | null;
    direction: 'IN' | 'OUT';
    sentAt?: string | null;
    bodyHtml?: string | null;
    nachrichtenAnzahl?: number;
    mitAnhang?: boolean;
    zustellStatus?: 'OFFEN' | 'UNZUSTELLBAR' | null;
    zustellFehler?: string | null;
}

export function reiterEintrag(id: number, felder: Partial<ReiterEintragRoh> = {}): ReiterEintragRoh {
    return {
        id, subject: `Anfrage ${id}`, fromAddress: 'Max Mustermann <max@example.com>', recipient: 'buero@example.com',
        direction: 'IN', sentAt: '2026-11-03T10:15:00', bodyHtml: `<p>Text ${id}</p>`,
        nachrichtenAnzahl: 1, mitAnhang: false, zustellStatus: null, zustellFehler: null, ...felder,
    };
}

export function reiterSeite(eintraege: ReiterEintragRoh[], gesamt = eintraege.length, seite = 0) {
    return { eintraege, gesamt, seite, seitenGroesse: 25 };
}

export function jsonAntwort(body: unknown, status = 200): Promise<Response> {
    return Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) } as Response);
}

/** Ist das eine Abfrage der Reiter-Liste? */
export const istReiterUrl = (url: unknown): url is string => typeof url === 'string' && url.startsWith('/api/emails/reiter/');

/**
 * `fetch`-Ersatz: die Reiter-Liste liefert `eintraege`, alles andere geht an `sonst`.
 * So bleiben die Tests rund um den geöffneten Verlauf bei ihrer eigenen Logik.
 */
export function mitReiter(
    eintraege: ReiterEintragRoh[],
    sonst: (url: string, init?: RequestInit) => Promise<Response> = () => jsonAntwort(null, 404),
) {
    return (url: string, init?: RequestInit) => (istReiterUrl(url) ? jsonAntwort(reiterSeite(eintraege)) : sonst(url, init));
}

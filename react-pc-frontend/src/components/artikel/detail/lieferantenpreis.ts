import { leseFehlermeldung } from "../../../lib/apiFehler";

export interface LieferantenpreisEintrag {
    artikelId: number;
    preis: number;
    /** `null` = die bisherige Nummer beim Lieferanten bleibt stehen. */
    externeArtikelnummer: string | null;
}

/**
 * Traegt einen Preis fuer einen Lieferanten ein. Der Server legt dafuer einen
 * neuen Preisstand an; der bisherige bleibt im Preisverlauf erhalten.
 * Wirft bei einer Ablehnung einen Fehler mit der Meldung des Servers.
 */
export async function speichereLieferantenpreis(lieferantId: number, eintrag: LieferantenpreisEintrag): Promise<void> {
    const res = await fetch(`/api/lieferanten/${encodeURIComponent(lieferantId)}/artikelpreise`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(eintrag),
    });
    if (!res.ok) throw new Error(await leseFehlermeldung(res, "Der Preis konnte nicht gespeichert werden."));
}

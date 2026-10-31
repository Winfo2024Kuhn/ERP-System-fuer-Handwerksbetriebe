/**
 * Wer steckt hinter „Aus Lieferanten-Shop“? Bei einem Shop sein Name, bei
 * zweien beide, sonst die ersten zwei „& weitere“. Ohne Namen das bekannte
 * Beispiel, damit die Zeile nie leer ist.
 */
export function shopUnterzeile(lieferantenNamen: string[] = []): string {
    const namen = lieferantenNamen.map((n) => n.trim()).filter(Boolean);
    const wer = namen.length === 0 ? 'Würth & Co.'
        : namen.length === 1 ? namen[0]
            : namen.length === 2 ? `${namen[0]} & ${namen[1]}`
                : `${namen[0]}, ${namen[1]} & weitere`;
    return `${wer} – aktuelle Einkaufspreise holen`;
}

/** Darf der angemeldete PC-Benutzer den Menü-Bereich „Zeiterfassung“ sehen? Fehler zählen als „nein“. */
export async function ladeZeiterfassungSichtbarkeit(signal?: AbortSignal): Promise<boolean> {
    const res = await fetch('/api/zeitverwaltung/sichtbarkeit', { signal });
    if (!res.ok) return false;
    const daten = await res.json();
    return daten?.darfZeiterfassungSehen === true;
}

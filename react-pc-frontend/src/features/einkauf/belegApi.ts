export interface BestellBeleg {
  lieferantDokumentId: number;
  typ: string;
  dateiname: string;
  verfuegbar: boolean;
}

export interface BestellBelegDatei extends BestellBeleg {
  dateiId: number;
}

async function jsonRequest<T>(url: string, method: 'GET' | 'POST'): Promise<T> {
  const response = await fetch(url, { method });
  if (!response.ok) {
    throw new Error(response.status === 404
      ? 'Der Beleg fehlt. Bitte laden Sie die PDF-Datei erneut hoch.'
      : `Belege konnten nicht geladen werden (HTTP ${response.status}).`);
  }
  return await response.json() as T;
}

export function ladeBestellBelege(bestellungId: number): Promise<BestellBeleg[]> {
  return jsonRequest(`/api/einkauf/bestellungen/${bestellungId}/belege`, 'GET');
}

export function registriereBestellBeleg(bestellungId: number, lieferantDokumentId: number): Promise<BestellBelegDatei> {
  return jsonRequest(`/api/einkauf/bestellungen/${bestellungId}/belege/${lieferantDokumentId}/datei`, 'POST');
}

export function ladeBestellBelegHoch(lieferantDokumentId: number): string {
  return `/api/lieferant-dokumente/${lieferantDokumentId}/download`;
}

export async function ladeNeueBestellDatei(bestellungId: number, typ: string, datei: File): Promise<BestellBelegDatei> {
  const body = new FormData();
  body.append('datei', datei);
  const response = await fetch(`/api/einkauf/bestellungen/${bestellungId}/belege?typ=${encodeURIComponent(typ)}`, {
    method: 'POST',
    body,
  });
  if (!response.ok) throw new Error(response.status === 413
    ? 'Die PDF-Datei darf höchstens 10 MiB groß sein.'
    : `PDF-Datei konnte nicht gespeichert werden (HTTP ${response.status}).`);
  return await response.json() as BestellBelegDatei;
}

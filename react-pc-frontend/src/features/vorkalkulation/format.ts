/** Anzeige-Formate der Vor-Kalkulation. Deutsch, mit Dezimalkomma. */

const kgFormat = new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const qmFormat = new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const stundenFormat = new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const prozentFormat = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 2 });

export const formatKg = (wert: number) => `${kgFormat.format(wert)} kg`;
export const formatQm = (wert: number) => `${qmFormat.format(wert)} m²`;
export const formatStunden = (wert: number) => `${stundenFormat.format(wert)} h`;
export const formatProzent = (wert: number) => `${prozentFormat.format(wert)} %`;

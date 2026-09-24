import type {
    AnalyticsSnapshot,
    BeitragDetail,
    BeitragSummary,
    BeitragUpsert,
    KiAnfrage,
    KiEntwurf,
    ProjektBild,
    VerlaufPunkt,
} from './typen';

/**
 * Einheitlicher Fehler des Website-Moduls. `status` ist 0, wenn die Anfrage
 * gar nicht erst rausging (Netzwerkabbruch), sonst der HTTP-Status.
 * 502 bedeutet: das ERP laeuft, aber die Website hat nicht geantwortet.
 */
export class WebsiteApiFehler extends Error {
    // Feld und Zuweisung getrennt, keine Parameter-Property im Konstruktor.
    // tsconfig.app.json setzt erasableSyntaxOnly, damit sind
    // Parameter-Properties verboten (TS1294): der Compiler muesste dafuer
    // Laufzeitcode erzeugen statt nur Typen wegzuschneiden.
    public readonly status: number;

    constructor(message: string, status: number) {
        super(message);
        this.status = status;
        this.name = 'WebsiteApiFehler';
    }
}

/**
 * Fuehrt eine Anfrage aus und wirft bei jedem Fehlschlag einen WebsiteApiFehler.
 * Den CSRF-Header setzt der globale Interceptor in main.tsx, hier bewusst nicht.
 */
async function anfrage(url: string, optionen?: RequestInit): Promise<Response> {
    let res: Response;
    try {
        res = await fetch(url, optionen ?? {});
    } catch (e) {
        throw new WebsiteApiFehler(
            e instanceof Error ? e.message : 'Netzwerkfehler', 0);
    }
    if (!res.ok) {
        let meldung = `Fehler ${res.status}`;
        try {
            const koerper = await res.json();
            if (koerper && typeof koerper.message === 'string') meldung = koerper.message;
        } catch {
            // Antwort ohne JSON-Koerper, die Standardmeldung genuegt.
        }
        throw new WebsiteApiFehler(meldung, res.status);
    }
    return res;
}

async function holeJson<T>(url: string, optionen?: RequestInit): Promise<T> {
    const res = await anfrage(url, optionen);
    return res.json() as Promise<T>;
}

function alsJson(koerper: unknown): RequestInit {
    return {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(koerper),
    };
}

// --- Beitraege ---

export function ladeBeitraege(): Promise<BeitragSummary[]> {
    return holeJson<BeitragSummary[]>('/api/beitraege', {});
}

export function ladeBeitrag(id: number): Promise<BeitragDetail> {
    return holeJson<BeitragDetail>(`/api/beitraege/${id}`, {});
}

export function legeBeitragAn(daten: BeitragUpsert): Promise<BeitragDetail> {
    return holeJson<BeitragDetail>('/api/beitraege', alsJson(daten));
}

export function aktualisiereBeitrag(id: number, daten: BeitragUpsert): Promise<BeitragDetail> {
    return holeJson<BeitragDetail>(`/api/beitraege/${id}`, {
        ...alsJson(daten),
        method: 'PATCH',
    });
}

export function setzeStatus(id: number, status: 'draft' | 'published'): Promise<BeitragDetail> {
    return holeJson<BeitragDetail>(`/api/beitraege/${id}/status`, alsJson({ status }));
}

/**
 * Laedt ein Bild hoch. Kein Content-Type von Hand setzen: der Browser muss die
 * multipart-boundary selbst ergaenzen, sonst kann die Website den Teil nicht lesen.
 * Das Feld muss `bild` heissen, so erwartet es BeitraegeController.
 */
export function ladeBildHoch(id: number, bild: Blob, dateiname: string): Promise<BeitragDetail> {
    const daten = new FormData();
    daten.append('bild', bild, dateiname);
    return holeJson<BeitragDetail>(`/api/beitraege/${id}/bilder`, {
        method: 'POST',
        body: daten,
    });
}

export function loescheBild(id: number, bildId: number): Promise<BeitragDetail> {
    return holeJson<BeitragDetail>(`/api/beitraege/${id}/bilder/${bildId}`, {
        method: 'DELETE',
    });
}

export function setzeAltText(id: number, bildId: number, altText: string): Promise<BeitragDetail> {
    return holeJson<BeitragDetail>(`/api/beitraege/${id}/bilder/${bildId}`, {
        ...alsJson({ altText }),
        method: 'PATCH',
    });
}

export function setzeTitelbild(id: number, bildId: number): Promise<BeitragDetail> {
    return holeJson<BeitragDetail>(`/api/beitraege/${id}/titelbild`, alsJson({ imageId: bildId }));
}

// --- Zahlen der Website ---

/** Gibt null zurueck, wenn noch nie ein Schnappschuss ankam (Backend antwortet 204). */
export async function ladeAnalyticsAktuell(): Promise<AnalyticsSnapshot | null> {
    const res = await anfrage('/api/website-analytics/latest', {});
    if (res.status === 204) return null;
    return res.json() as Promise<AnalyticsSnapshot>;
}

export function ladeAnalyticsVerlauf(tage: number): Promise<VerlaufPunkt[]> {
    return holeJson<VerlaufPunkt[]>(
        `/api/website-analytics/verlauf?tage=${encodeURIComponent(String(tage))}`, {});
}

// --- Projektbilder ---

interface NotizAntwort {
    notiz: string;
    erstelltAm: string;
    bilder: { id: number; originalDateiname: string; url: string; thumbnailUrl: string; erstelltAm: string }[];
}

interface DokumentAntwort {
    id: number;
    originalDateiname: string;
    dateityp: string;
    url: string;
    thumbnailUrl: string;
    dokumentGruppe: string;
    uploadDatum: string | null;
}

/**
 * Fuehrt die zwei Bildquellen eines Projekts zu einer Liste zusammen.
 * Bautagebuch zuerst, weil dort die Baustellenfotos liegen.
 */
export async function ladeProjektBilder(projektId: number): Promise<ProjektBild[]> {
    const [notizen, dokumente] = await Promise.all([
        holeJson<NotizAntwort[]>(`/api/projekte/${projektId}/notizen`, {}),
        holeJson<DokumentAntwort[]>(`/api/projekte/${projektId}/dokumente`, {}),
    ]);

    const ausNotizen: ProjektBild[] = notizen.flatMap(notiz =>
        (notiz.bilder ?? []).map(bild => ({
            schluessel: `notiz-${bild.id}`,
            quelle: 'bautagebuch' as const,
            url: bild.url,
            thumbnailUrl: bild.thumbnailUrl,
            originalDateiname: bild.originalDateiname,
            datum: bild.erstelltAm ?? notiz.erstelltAm ?? null,
            hinweis: notiz.notiz ? notiz.notiz.slice(0, 120) : null,
        })));

    const ausDokumenten: ProjektBild[] = dokumente
        .filter(d => d.dokumentGruppe === 'BILDER')
        .map(d => ({
            schluessel: `dokument-${d.id}`,
            quelle: 'dokument' as const,
            url: d.url,
            thumbnailUrl: d.thumbnailUrl,
            originalDateiname: d.originalDateiname,
            datum: d.uploadDatum,
            hinweis: null,
        }));

    return [...ausNotizen, ...ausDokumenten];
}

// --- KI ---

export function erzeugeBeitragsvorschlag(kiAnfrage: KiAnfrage, bilder: Blob[]): Promise<KiEntwurf> {
    const daten = new FormData();
    daten.append('anfrage', new Blob([JSON.stringify(kiAnfrage)], { type: 'application/json' }));
    bilder.forEach((bild, i) => daten.append('bilder', bild, `bild-${i}.jpg`));
    return holeJson<KiEntwurf>('/api/beitraege/ki/entwurf', {
        method: 'POST',
        body: daten,
    });
}

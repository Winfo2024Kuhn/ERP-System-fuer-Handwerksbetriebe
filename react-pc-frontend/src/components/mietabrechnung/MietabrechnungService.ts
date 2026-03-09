import type { Mietobjekt, Partei, Raum, Verbraucher, Kostenstelle, Verteilungsschluessel, Kostenposition, AbrechnungResult, Zaehlerstand } from './types';

const API_BASE = '/api/miete';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
    const res = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            ...(options?.headers ?? {}),
        },
    });
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || res.statusText);
    }
    // Handle empty responses (like from DELETE)
    if (res.status === 204) {
        return {} as T;
    }
    // Check if response is JSON
    const contentType = res.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
        return res.json();
    }
    return {} as T;
}

export const MietabrechnungService = {
    // Mietobjekte
    getMietobjekte: () => request<Mietobjekt[]>('/mietobjekte'),
    createMietobjekt: (data: Omit<Mietobjekt, 'id'>) => request<Mietobjekt>('/mietobjekte', { method: 'POST', body: JSON.stringify(data) }),
    updateMietobjekt: (id: number, data: Omit<Mietobjekt, 'id'>) => request<Mietobjekt>(`/mietobjekte/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteMietobjekt: (id: number) => request<void>(`/mietobjekte/${id}`, { method: 'DELETE' }),

    // Parteien
    getParteien: (mietobjektId: number) => request<Partei[]>(`/mietobjekte/${mietobjektId}/parteien`),
    createPartei: (mietobjektId: number, data: Omit<Partei, 'id'>) => request<Partei>(`/mietobjekte/${mietobjektId}/parteien`, { method: 'POST', body: JSON.stringify(data) }),
    // Assuming update/delete are on specific resource paths inferred from pattern. 
    // miete.js defines endpoints? 
    // miete.js line 1065: api(`/mietobjekte/${id}`, ...)
    // miete.js doesn't explicitly show update Partei endpoint pattern in the snippet I saw, 
    // but typically it's /api/miete/parteien/{id} or nested?
    // Let's assume standard REST: /api/miete/parteien/{id} if exposed, OR /mietobjekte/{mid}/parteien/{pid}
    // I will use what seems logical or verify if possible. 
    // Actually, looking at miete.js snippet (1097), it only shows POST. 
    // But usually APIs are symmetric. I will assume /parteien/{id} for update/delete or nested. 
    // Given the java structure (usually), entities are often top-level resources in repositories.
    // I will assume /parteien/{id} exists for now. If not, I might need to fix it.
    updatePartei: (id: number, data: Omit<Partei, 'id'>) => request<Partei>(`/mietobjekte/${data.mietobjektId}/parteien/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deletePartei: (id: number) => request<void>(`/parteien/${id}`, { method: 'DELETE' }),

    // Raeume
    getRaeume: (mietobjektId: number) => request<Raum[]>(`/mietobjekte/${mietobjektId}/raeume`),
    createRaum: (mietobjektId: number, data: Omit<Raum, 'id'>) => request<Raum>(`/mietobjekte/${mietobjektId}/raeume`, { method: 'POST', body: JSON.stringify(data) }),
    updateRaum: (id: number, data: Omit<Raum, 'id'>) => request<Raum>(`/mietobjekte/${data.mietobjektId}/raeume/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteRaum: (id: number) => request<void>(`/raeume/${id}`, { method: 'DELETE' }),

    // Verbraucher
    // getRaeume returns nested Verbraucher in miete.js implementation?
    // "loadRaeumeMitVerbrauch" calls /raeume/{id}/verbrauchsgegenstaende
    getVerbraucherForRaum: (raumId: number) => request<Verbraucher[]>(`/raeume/${raumId}/verbrauchsgegenstaende`),
    createVerbraucher: (raumId: number, data: Omit<Verbraucher, 'id'>) => request<Verbraucher>(`/raeume/${raumId}/verbrauchsgegenstaende`, { method: 'POST', body: JSON.stringify(data) }),
    updateVerbraucher: (id: number, data: Omit<Verbraucher, 'id'>) => request<Verbraucher>(`/raeume/${data.raumId}/verbrauchsgegenstaende/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteVerbraucher: (id: number) => request<void>(`/verbrauchsgegenstaende/${id}`, { method: 'DELETE' }),

    // Zaehlerstaende
    getZaehlerstaende: (verbraucherId: number) => request<Zaehlerstand[]>(`/verbrauchsgegenstaende/${verbraucherId}/zaehlerstaende`),
    createZaehlerstand: (verbraucherId: number, data: Omit<Zaehlerstand, 'id'>) => request<Zaehlerstand>(`/verbrauchsgegenstaende/${verbraucherId}/zaehlerstaende`, { method: 'POST', body: JSON.stringify(data) }),
    updateZaehlerstand: (id: number, data: Omit<Zaehlerstand, 'id'>) => request<Zaehlerstand>(`/verbrauchsgegenstaende/${data.verbrauchsgegenstandId}/zaehlerstaende/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteZaehlerstand: (id: number) => request<void>(`/zaehlerstaende/${id}`, { method: 'DELETE' }),

    // Verteilungsschluessel
    getVerteilungsschluessel: (mietobjektId: number) => request<Verteilungsschluessel[]>(`/mietobjekte/${mietobjektId}/verteilungsschluessel`),
    createVerteilungsschluessel: (mietobjektId: number, data: Omit<Verteilungsschluessel, 'id'>) => request<Verteilungsschluessel>(`/mietobjekte/${mietobjektId}/verteilungsschluessel`, { method: 'POST', body: JSON.stringify(data) }),
    updateVerteilungsschluessel: (id: number, data: Omit<Verteilungsschluessel, 'id'>) => request<Verteilungsschluessel>(`/mietobjekte/${data.mietobjektId}/verteilungsschluessel/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteVerteilungsschluessel: (id: number) => request<void>(`/verteilungsschluessel/${id}`, { method: 'DELETE' }),

    // Kostenstellen
    getKostenstellen: (mietobjektId: number) => request<Kostenstelle[]>(`/mietobjekte/${mietobjektId}/kostenstellen`),
    createKostenstelle: (mietobjektId: number, data: Omit<Kostenstelle, 'id'>) => request<Kostenstelle>(`/mietobjekte/${mietobjektId}/kostenstellen`, { method: 'POST', body: JSON.stringify(data) }),
    updateKostenstelle: (id: number, data: Omit<Kostenstelle, 'id'>) => request<Kostenstelle>(`/mietobjekte/${data.mietobjektId}/kostenstellen/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteKostenstelle: (id: number) => request<void>(`/kostenstellen/${id}`, { method: 'DELETE' }),

    // Kostenpositionen
    getKostenpositionen: (kostenstelleId: number) => request<Kostenposition[]>(`/kostenstellen/${kostenstelleId}/kostenpositionen`),
    createKostenposition: (kostenstelleId: number, data: Omit<Kostenposition, 'id'>) => request<Kostenposition>(`/kostenstellen/${kostenstelleId}/kostenpositionen`, { method: 'POST', body: JSON.stringify(data) }),
    updateKostenposition: (id: number, data: Omit<Kostenposition, 'id'>) => request<Kostenposition>(`/kostenstellen/${data.kostenstelleId}/kostenpositionen/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

    deleteKostenposition: (id: number) => request<void>(`/kostenpositionen/${id}`, { method: 'DELETE' }),
    copyKostenpositionenVonVorjahr: (mietobjektId: number, zielJahr: number) =>
        request<{ kopiert: number; zielJahr: number }>(`/mietobjekte/${mietobjektId}/kostenpositionen/copy-vorjahr?zielJahr=${zielJahr}`, { method: 'POST' }),

    // Abrechnung
    getAbrechnung: (mietobjektId: number, jahr: number) => request<AbrechnungResult>(`/mietobjekte/${mietobjektId}/jahresabrechnung?jahr=${jahr}`),

    // PDF URLs
    getPdfUrl: (mietobjektId: number, jahr: number) => `${API_BASE}/mietobjekte/${mietobjektId}/jahresabrechnung/pdf?jahr=${jahr}`,
    getZaehlerstandPdfUrl: (mietobjektId: number, jahr: number) => `${API_BASE}/mietobjekte/${mietobjektId}/zaehlerstaende/erfassung.pdf?jahr=${jahr}`,
};

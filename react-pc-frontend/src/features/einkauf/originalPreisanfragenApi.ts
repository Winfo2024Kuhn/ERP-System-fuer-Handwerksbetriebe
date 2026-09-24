import { einkaufApi } from './api';
import type { AnfrageDetail, AnfrageKopf, Page } from './types';
import type { PreisanfrageListeEintrag } from '../../pages/PreisanfragenPage';

/** Keep the existing overview while using the same persisted requests as the detail editor. */
export function originalPreisanfrage(detail: AnfrageDetail): PreisanfrageListeEintrag {
    const { kopf, lieferanten } = detail;
    const answered = lieferanten.filter(l => ['BEANTWORTET', 'ABGESAGT', 'ERLEDIGT'].includes(l.status)).length;
    return {
        id: kopf.id, version: kopf.version, nummer: kopf.paNummer,
        antwortFrist: kopf.antwortfrist,
        status: kopf.status === 'ABGEBROCHEN' ? 'ABGEBROCHEN'
            : kopf.status === 'VERGEBEN' ? 'VERGEBEN'
            : answered && answered === lieferanten.length ? 'VOLLSTAENDIG'
            : answered ? 'TEILWEISE_BEANTWORTET' : 'OFFEN',
        positionen: detail.positionen,
        lieferanten: lieferanten.map(l => ({
            id: l.id, lieferantId: l.lieferantId, lieferantenname: l.lieferantenname,
            token: '', versendetAn: l.kontakt?.email,
            status: l.status === 'BEANTWORTET' ? 'BEANTWORTET'
                : l.status === 'ABGESAGT' ? 'ABGELEHNT'
                : l.status === 'ERLEDIGT' ? 'ERLEDIGT'
                : l.status === 'VERSENDET' ? 'VERSENDET' : 'AUSSTEHEND',
        })),
    };
}

export async function ladeOriginalPreisanfragen(status: string): Promise<PreisanfrageListeEintrag[]> {
    const result: PreisanfrageListeEintrag[] = [];
    let page = 0;
    let totalPages = 1;
    do {
        const response = await einkaufApi.get<Page<AnfrageKopf>>(`/api/einkauf/anfragen?page=${page}&size=20`);
        // Bound simultaneous detail requests; never issue one request per entry all at once.
        for (let offset = 0; offset < response.content.length; offset += 5) {
            const details = await Promise.all(response.content.slice(offset, offset + 5).map(kopf =>
                einkaufApi.get<AnfrageDetail>(`/api/einkauf/anfragen/${kopf.id}`)));
            result.push(...details.map(originalPreisanfrage));
        }
        totalPages = response.totalPages;
        page++;
    } while (page < totalPages);
    return status === 'ALLE' ? result : result.filter(item => item.status === status);
}

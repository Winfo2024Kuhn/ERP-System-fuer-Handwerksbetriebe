package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.RechtstextFassung;
import org.example.kalkulationsprogramm.dto.Formular.RechtstextDtos;
import org.example.kalkulationsprogramm.service.RechtstextService;
import org.example.kalkulationsprogramm.service.WiderrufsbelehrungPdfService;
import org.springframework.stereotype.Component;

import java.util.List;

/** Rechtstexte und Fassungen → DTOs für das Formularwesen. */
@Component
public class RechtstextMapper
{
    public RechtstextDtos.Uebersicht zuUebersicht(RechtstextService.Rechtstext t)
    {
        return new RechtstextDtos.Uebersicht(t.art().name(), t.art().getBezeichnung(), t.fassung(), t.stand(),
                t.standardtext(), t.geaendertAm());
    }

    /** Detail mit den Platzhaltern der Widerrufsbelehrung (heute die einzige Art). */
    public RechtstextDtos.Detail zuDetail(RechtstextService.Rechtstext t)
    {
        List<RechtstextDtos.Platzhalter> platzhalter = WiderrufsbelehrungPdfService.PLATZHALTER.stream()
                .map(p -> new RechtstextDtos.Platzhalter(p.token(), p.beschreibung(), p.beispiel()))
                .toList();
        return new RechtstextDtos.Detail(t.art().name(), t.art().getBezeichnung(), t.fassung(), t.stand(),
                t.standardtext(), t.geaendertAm(), t.html(), platzhalter);
    }

    public RechtstextDtos.FassungKurz zuFassungKurz(RechtstextFassung f)
    {
        return new RechtstextDtos.FassungKurz(f.getFassung(), f.getStand(), f.getGeaendertAm());
    }

    public RechtstextDtos.FassungVoll zuFassungVoll(RechtstextFassung f)
    {
        return new RechtstextDtos.FassungVoll(f.getFassung(), f.getStand(), f.getGeaendertAm(), f.getInhaltHtml());
    }
}

package org.example.kalkulationsprogramm.service.einkauf;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto.AnlageDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EinkaufZeichnungsbedarfService {
    private static final long VORLÄUFIGE_ANLAGENREFERENZ = Long.MAX_VALUE;
    private final EinkaufBedarfService bedarfe;
    private final EinkaufDateiService dateien;

    public EinkaufZeichnungsbedarfService(EinkaufBedarfService bedarfe, EinkaufDateiService dateien) {
        this.bedarfe = bedarfe;
        this.dateien = dateien;
    }

    @Transactional
    public EinkaufBedarfDto.Response anlegen(EinkaufBedarfDto.Create eingabe, MultipartFile datei,
            String revision, Long akteurId) {
        if (eingabe == null || eingabe.position() == null || eingabe.position().art() == null
                || eingabe.position().art() != org.example.kalkulationsprogramm.domain.einkauf.Positionsart.ZEICHNUNGSTEIL)
            throw new IllegalArgumentException("Bitte einen vollständigen Zeichnungsteil-Bedarf angeben.");
        dateien.validiereUpload(datei, revision);
        PositionSnapshot position = eingabe.position();
        PositionSnapshot zwischenstand = mitAnlagen(position, List.of(VORLÄUFIGE_ANLAGENREFERENZ));
        EinkaufBedarfDto.Response angelegt = bedarfe.anlegen(
                new EinkaufBedarfDto.Create(zwischenstand, eingabe.liefergruppe(), eingabe.artikelInProjektId()), akteurId);
        AnlageDto hochgeladen = dateien.hochladen(angelegt.id(), datei, revision, akteurId);
        AnlageDto freigegeben = dateien.freigeben(hochgeladen.id(), akteurId);
        PositionSnapshot fertig = mitAnlagen(position, List.of(freigegeben.id()));
        return bedarfe.aktualisieren(angelegt.id(),
                new EinkaufBedarfDto.Update(angelegt.version(), fertig, eingabe.liefergruppe()), akteurId);
    }

    private static PositionSnapshot mitAnlagen(PositionSnapshot position, List<Long> anlagen) {
        return new PositionSnapshot(position.art(), position.artikelId(), position.interneReferenz(),
                position.zeichnungsnummer(), position.zeichnungsrevision(), position.bezeichnung(),
                position.werkstoff(), position.abmessung(), position.basis(), position.schnittForm(),
                position.winkelLinks(), position.winkelRechts(), position.bearbeitung(), position.oberflaeche(),
                position.dokumente(), anlagen, position.beschaffungsdetails());
    }
}

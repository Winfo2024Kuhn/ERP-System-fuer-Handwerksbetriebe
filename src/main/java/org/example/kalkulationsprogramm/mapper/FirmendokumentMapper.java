package org.example.kalkulationsprogramm.mapper;

import org.example.kalkulationsprogramm.domain.firmendokumente.Firmendokument;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentSeiteDto;
import org.springframework.data.domain.Page;

/** Firmendokument → DTO. Die Datei-URL zeigt immer auf den geschützten Endpunkt, nie auf den Speicherort. */
public final class FirmendokumentMapper {

    private FirmendokumentMapper() {
    }

    public static FirmendokumentDto zuDto(Firmendokument d) {
        return new FirmendokumentDto(d.getId(), d.getOrdner().getId(), d.getName(), d.getOriginalDateiname(),
                d.getGroesseBytes(), d.getHochgeladenAm(), d.getHochgeladenVon(), d.getFaelligAm(),
                "/api/firmendokumente/" + d.getId() + "/datei");
    }

    public static FirmendokumentSeiteDto zuSeite(Page<Firmendokument> seite) {
        return new FirmendokumentSeiteDto(seite.map(FirmendokumentMapper::zuDto).getContent(),
                seite.getTotalElements(), seite.getNumber(), seite.getSize());
    }
}

package org.example.kalkulationsprogramm.mapper;

import java.util.function.Predicate;

import org.example.kalkulationsprogramm.domain.firmendokumente.Firmendokument;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentSeiteDto;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentDateiart;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentDateinamen;
import org.springframework.data.domain.Page;

/**
 * Firmendokument → DTO. Die Datei-URL zeigt immer auf den geschützten Endpunkt, nie auf den Speicherort; die
 * Dateiart kommt aus der Endung, das Ändern-Recht aus den Ordnerrechten (vom Dienst berechnet).
 */
public final class FirmendokumentMapper {

    private FirmendokumentMapper() {
    }

    public static FirmendokumentDto zuDto(Firmendokument d, boolean darfAendern) {
        FirmendokumentDateiart art = FirmendokumentDateiart.aus(FirmendokumentDateinamen.endungDesDokuments(d));
        return new FirmendokumentDto(d.getId(), d.getOrdner().getId(), d.getName(), d.getOriginalDateiname(),
                d.getGroesseBytes(), d.getHochgeladenAm(), d.getHochgeladenVon(), d.getFaelligAm(),
                "/api/firmendokumente/" + d.getId() + "/datei", art.name(), darfAendern);
    }

    public static FirmendokumentSeiteDto zuSeite(Page<Firmendokument> seite, Predicate<Firmendokument> darfAendern) {
        return new FirmendokumentSeiteDto(seite.map(d -> zuDto(d, darfAendern.test(d))).getContent(),
                seite.getTotalElements(), seite.getNumber(), seite.getSize());
    }
}

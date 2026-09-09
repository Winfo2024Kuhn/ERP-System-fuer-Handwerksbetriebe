package org.example.kalkulationsprogramm.dto;

import java.time.LocalDate;
import org.example.kalkulationsprogramm.domain.ZeitkontoVersion;

public record ZeitkontoVersionDto(Long id, Long version, Long mitarbeiterId, LocalDate gueltigVon,
        LocalDate gueltigBis, Long vorlageId, ZeitkontenmodellDto.Arbeitszeit arbeitszeit) {
    public static ZeitkontoVersionDto from(ZeitkontoVersion v) {
        return new ZeitkontoVersionDto(v.getId(), v.getVersion(), v.getMitarbeiter().getId(),
                v.getGueltigVon(), v.getGueltigBis(), v.getVorlage() == null ? null : v.getVorlage().getId(),
                ZeitkontenmodellDto.Arbeitszeit.from(v));
    }
}

package org.example.kalkulationsprogramm.dto.Firmendokumente;

import java.util.List;

public record FirmendokumentSeiteDto(List<FirmendokumentDto> eintraege, long gesamt, int seite, int seitenGroesse) {
}

package org.example.kalkulationsprogramm.service;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangErstellenDto;
import org.example.kalkulationsprogramm.dto.Arbeitsgang.ArbeitsgangStundensatzDto;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@AllArgsConstructor
public class ArbeitsgangManagementService {
    private final ArbeitsgangRepository arbeitsgangRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final ArbeitsgangStundensatzRepository stundensatzRepository;
    private final AbteilungRepository abteilungRepository;

    @Transactional
    public Arbeitsgang erstelleArbeitsgang(ArbeitsgangErstellenDto dto) {
        Abteilung abteilung = abteilungRepository.findById(dto.getAbteilungId())
                .orElseThrow(() -> new RuntimeException("Abteilung nicht gefunden: " + dto.getAbteilungId()));

        Arbeitsgang neuerArbeitsgang = new Arbeitsgang();
        neuerArbeitsgang.setBeschreibung(dto.getBeschreibung());
        neuerArbeitsgang.setAbteilung(abteilung);
        return this.arbeitsgangRepository.save(neuerArbeitsgang);
    }

    @Transactional
    public void loescheArbeitsgang(Long arbeitsgangID) {
        Arbeitsgang arbeitsgang = this.arbeitsgangRepository.findById(arbeitsgangID)
                .orElseThrow(() -> new RuntimeException(
                        "Dieser Arbeitsgang konnte nicht gefunden werden! ID: " + arbeitsgangID));
        long referenzen = zeitbuchungRepository.countByArbeitsgangId(arbeitsgangID);
        if (referenzen > 0) {
            throw new IllegalStateException("Arbeitsgang kann nicht gelöscht werden, da er referenziert wird.");
        }
        this.arbeitsgangRepository.delete(arbeitsgang);
    }

    public List<Arbeitsgang> findeAlle() {
        return arbeitsgangRepository.findAll();
    }

    @Transactional
    public void aktualisiereStundensaetze(List<ArbeitsgangStundensatzDto> dtos) {
        int jahr = java.time.LocalDate.now().getYear();
        for (ArbeitsgangStundensatzDto dto : dtos) {
            aktualisiereEinzelnenStundensatz(dto.getArbeitsgangId(), dto.getStundensatz(), jahr);
        }
    }

    @Transactional
    public void aktualisiereEinzelnenStundensatz(Long arbeitsgangId, BigDecimal neuerSatz) {
        int jahr = java.time.LocalDate.now().getYear();
        aktualisiereEinzelnenStundensatz(arbeitsgangId, neuerSatz, jahr);
    }

    private void aktualisiereEinzelnenStundensatz(Long arbeitsgangId, BigDecimal neuerSatz, int jahr) {
        Arbeitsgang arbeitsgang = arbeitsgangRepository.findById(arbeitsgangId)
                .orElseThrow(() -> new RuntimeException("Arbeitsgang nicht gefunden"));

        ArbeitsgangStundensatz satz = stundensatzRepository
                .findTopByArbeitsgangIdAndJahrOrderByIdDesc(arbeitsgang.getId(), jahr)
                .orElseGet(() -> {
                    ArbeitsgangStundensatz neu = new ArbeitsgangStundensatz();
                    neu.setArbeitsgang(arbeitsgang);
                    neu.setJahr(jahr);
                    return neu;
                });

        satz.setSatz(neuerSatz);
        stundensatzRepository.save(satz);
    }
}

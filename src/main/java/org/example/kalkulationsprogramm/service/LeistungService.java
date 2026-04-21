package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Wps;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.dto.Leistung.WpsRefDto;
import org.example.kalkulationsprogramm.mapper.LeistungMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.WpsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeistungService {

    private final LeistungRepository leistungRepository;
    private final LeistungMapper leistungMapper;
    private final WpsRepository wpsRepository;

    @Transactional(readOnly = true)
    public List<LeistungDto> getAllLeistungen() {
        return leistungRepository.findAll().stream()
                .map(leistungMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeistungDto createLeistung(LeistungCreateDto dto) {
        Leistung leistung = leistungMapper.toEntity(dto);
        leistung = leistungRepository.save(leistung);
        return leistungMapper.toDto(leistung);
    }

    @Transactional
    public LeistungDto updateLeistung(Long id, LeistungCreateDto dto) {
        Leistung leistung = leistungRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Leistung nicht gefunden: " + id));
        leistungMapper.updateEntity(leistung, dto);
        leistung = leistungRepository.save(leistung);
        return leistungMapper.toDto(leistung);
    }

    @Transactional
    public void deleteLeistung(Long id) {
        leistungRepository.deleteById(id);
    }

    /**
     * Liefert die an diese Leistung gehängten Schweißanweisungen,
     * sortiert nach WPS-Nummer für eine stabile UI-Reihenfolge.
     */
    @Transactional(readOnly = true)
    public List<WpsRefDto> getVerknuepfteWps(Long leistungId) {
        Leistung leistung = leistungRepository.findById(leistungId)
                .orElseThrow(() -> new RuntimeException("Leistung nicht gefunden: " + leistungId));
        return leistung.getVerknuepfteWps().stream()
                .sorted(Comparator.comparing(Wps::getWpsNummer, Comparator.nullsLast(String::compareTo)))
                .map(this::toWpsRef)
                .collect(Collectors.toList());
    }

    /**
     * Ersetzt die WPS-Verknüpfungen einer Leistung vollständig.
     * Unbekannte IDs werden stillschweigend ignoriert – das Frontend hat
     * nur aus der gültigen WPS-Liste gewählt, alles andere ist ein Bug.
     */
    @Transactional
    public List<WpsRefDto> setVerknuepfteWps(Long leistungId, Set<Long> wpsIds) {
        Leistung leistung = leistungRepository.findById(leistungId)
                .orElseThrow(() -> new RuntimeException("Leistung nicht gefunden: " + leistungId));
        Set<Wps> neu = new HashSet<>();
        if (wpsIds != null && !wpsIds.isEmpty()) {
            neu.addAll(wpsRepository.findAllById(wpsIds));
        }
        leistung.getVerknuepfteWps().clear();
        leistung.getVerknuepfteWps().addAll(neu);
        leistungRepository.save(leistung);
        return getVerknuepfteWps(leistungId);
    }

    private WpsRefDto toWpsRef(Wps w) {
        return new WpsRefDto(
                w.getId(),
                w.getWpsNummer(),
                w.getBezeichnung(),
                w.getSchweissProzes(),
                w.getGrundwerkstoff());
    }
}

package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.mapper.LeistungMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeistungService {

    private final LeistungRepository leistungRepository;
    private final LeistungMapper leistungMapper;

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
}

package org.example.kalkulationsprogramm.service;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.dto.Artikel.KategorieResponseDto;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class KategorieService {

    private final KategorieRepository kategorieRepository;

    public List<KategorieResponseDto> findeHauptkategorien() {
        return kategorieRepository.findByParentKategorieIsNull()
                .stream()
                .sorted(Comparator.comparing(Kategorie::getBeschreibung, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<KategorieResponseDto> alleKategorien() {
        return kategorieRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Kategorie::getBeschreibung, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<KategorieResponseDto> findeUnterkategorien(Integer parentId) {
        return kategorieRepository.findByParentKategorie_Id(parentId)
                .stream()
                .sorted(Comparator.comparing(Kategorie::getBeschreibung, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<Integer> findeKategorieUndUnterkategorieIds(Integer kategorieId) {
        if (kategorieId == null) {
            return List.of();
        }
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        collectKategorieIds(kategorieId, ids);
        return new ArrayList<>(ids);
    }

    private void collectKategorieIds(Integer id, LinkedHashSet<Integer> collector) {
        if (id == null || collector.contains(id)) {
            return;
        }
        collector.add(id);
        kategorieRepository.findByParentKategorie_Id(id)
                .forEach(child -> collectKategorieIds(child.getId(), collector));
    }

    private KategorieResponseDto toDto(Kategorie kategorie) {
        KategorieResponseDto dto = new KategorieResponseDto();
        dto.setId(kategorie.getId());
        dto.setBezeichnung(kategorie.getBeschreibung());
        dto.setLeaf(!kategorieRepository.existsByParentKategorie_Id(kategorie.getId()));
        return dto;
    }
}

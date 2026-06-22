package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.LieferantRolle;
import org.example.kalkulationsprogramm.dto.Artikel.KategorieCreateDto;
import org.example.kalkulationsprogramm.dto.Artikel.KategorieResponseDto;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

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

    @Transactional
    public KategorieResponseDto erstelleKategorie(KategorieCreateDto dto) {
        String bezeichnung = dto.getBezeichnung() == null ? "" : dto.getBezeichnung().trim();
        if (bezeichnung.isEmpty()) {
            throw new IllegalArgumentException("Kategoriebezeichnung darf nicht leer sein.");
        }

        Kategorie neueKategorie = new Kategorie();
        neueKategorie.setBeschreibung(bezeichnung);
        if (dto.getTypischeRollen() != null) {
            neueKategorie.setTypischeRollen(new HashSet<>(dto.getTypischeRollen()));
        }

        if (dto.getParentId() != null) {
            Kategorie parent = kategorieRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Eltern-Kategorie nicht gefunden."));
            neueKategorie.setParentKategorie(parent);
        }

        Kategorie gespeichert = kategorieRepository.save(neueKategorie);
        return toDto(gespeichert);
    }

    @Transactional
    public KategorieResponseDto aktualisiereTypischeRollen(Integer kategorieId, Set<LieferantRolle> rollen) {
        Kategorie kategorie = kategorieRepository.findById(kategorieId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kategorie nicht gefunden."));
        kategorie.setTypischeRollen(rollen != null ? new HashSet<>(rollen) : new HashSet<>());
        Kategorie gespeichert = kategorieRepository.save(kategorie);
        return toDto(gespeichert);
    }

    /**
     * Liefert die "effektiven" Rollen einer Kategorie: eigene typischeRollen, sonst von der
     * naechsten Oberkategorie mit Rollen geerbt (Kategorie.typischeRollen leer = erbt nach oben).
     */
    @Transactional(readOnly = true)
    public Set<LieferantRolle> findeEffektiveRollen(Integer kategorieId) {
        Kategorie aktuelle = kategorieRepository.findById(kategorieId).orElse(null);
        Set<Integer> besucht = new java.util.HashSet<>();
        while (aktuelle != null && besucht.add(aktuelle.getId())) {
            if (aktuelle.getTypischeRollen() != null && !aktuelle.getTypischeRollen().isEmpty()) {
                return aktuelle.getTypischeRollen();
            }
            aktuelle = aktuelle.getParentKategorie();
        }
        return Set.of();
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
        dto.setTypischeRollen(kategorie.getTypischeRollen());
        return dto;
    }
}

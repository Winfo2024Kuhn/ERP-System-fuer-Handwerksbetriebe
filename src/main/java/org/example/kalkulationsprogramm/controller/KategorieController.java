package org.example.kalkulationsprogramm.controller;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kategorien")
@AllArgsConstructor
public class KategorieController {

    private final KategorieRepository kategorieRepository;

    @GetMapping
    public List<KategorieDto> alle() {
        return kategorieRepository.findAll().stream()
                .map(KategorieDto::from)
                .toList();
    }

    public record KategorieDto(
            Integer id,
            String beschreibung,
            Integer parentId,
            String zeugnisExc1,
            String zeugnisExc2,
            String zeugnisExc3,
            String zeugnisExc4
    ) {
        static KategorieDto from(Kategorie k) {
            return new KategorieDto(
                    k.getId(),
                    k.getBeschreibung(),
                    k.getParentKategorie() != null ? k.getParentKategorie().getId() : null,
                    k.getZeugnisExc1() != null ? k.getZeugnisExc1().name() : null,
                    k.getZeugnisExc2() != null ? k.getZeugnisExc2().name() : null,
                    k.getZeugnisExc3() != null ? k.getZeugnisExc3().name() : null,
                    k.getZeugnisExc4() != null ? k.getZeugnisExc4().name() : null
            );
        }
    }
}

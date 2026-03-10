package org.example.kalkulationsprogramm.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.domain.TextbausteinTyp;
import org.example.kalkulationsprogramm.dto.Textbaustein.TextbausteinDto;
import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.repository.TextbausteinRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TextbausteinService {

    private final TextbausteinRepository repository;

    @Transactional(readOnly = true)
    public List<Textbaustein> list(String typ) {
        if (StringUtils.hasText(typ)) {
            return repository.findByTypOrderBySortOrderAscNameAsc(TextbausteinTyp.fromString(typ));
        }
        Comparator<Textbaustein> comparator = Comparator
                .comparing((Textbaustein t) -> Optional.ofNullable(t.getSortOrder()).orElse(Integer.MAX_VALUE))
                .thenComparing(Textbaustein::getName, Comparator.nullsLast(String::compareToIgnoreCase));
        List<Textbaustein> all = repository.findAllWithDokumenttypen();
        return all.stream().sorted(comparator).toList();
    }

    public Textbaustein create(TextbausteinDto dto) {
        Textbaustein entity = new Textbaustein();
        dto.applyToEntity(entity);
        applyDokumenttypen(dto, entity);
        return repository.save(entity);
    }

    public Textbaustein update(Long id, TextbausteinDto dto) {
        Textbaustein entity = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Textbaustein nicht gefunden"));
        dto.applyToEntity(entity);
        applyDokumenttypen(dto, entity);
        return repository.save(entity);
    }

    public void delete(Long id) {
        if (id == null) {
            return;
        }
        repository.deleteById(id);
    }

    public Textbaustein get(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Textbaustein nicht gefunden"));
    }

    private void applyDokumenttypen(TextbausteinDto dto, Textbaustein entity) {
        entity.getDokumenttypen().clear();
        if (dto.getDokumenttypen() == null) {
            return;
        }
        dto.getDokumenttypen().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(Dokumenttyp::fromLabel)
                .forEach(entity.getDokumenttypen()::add);
    }
}

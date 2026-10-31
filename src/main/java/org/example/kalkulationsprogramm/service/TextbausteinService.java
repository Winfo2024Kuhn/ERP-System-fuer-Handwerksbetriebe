package org.example.kalkulationsprogramm.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.dto.Textbaustein.TextbausteinDto;
import org.example.kalkulationsprogramm.repository.TextbausteinRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TextbausteinService {

    private static final String NICHT_GEFUNDEN = "Diesen Textbaustein gibt es nicht mehr.";

    private final TextbausteinRepository repository;

    @Transactional(readOnly = true)
    public List<Textbaustein> list() {
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
        Textbaustein entity = get(id);
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

    /** @throws ResponseStatusException 404, wenn es den Textbaustein nicht gibt */
    private Textbaustein get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NICHT_GEFUNDEN));
    }

    /**
     * Übernimmt die Dokumentarten. Eine unbekannte Dokumentart ist ein Eingabefehler (400),
     * vorher wurde sie als „nicht gefunden“ bzw. Serverfehler gemeldet.
     */
    private void applyDokumenttypen(TextbausteinDto dto, Textbaustein entity) {
        entity.getDokumenttypen().clear();
        if (dto.getDokumenttypen() == null) {
            return;
        }
        dto.getDokumenttypen().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(TextbausteinService::dokumenttyp)
                .forEach(entity.getDokumenttypen()::add);
    }

    private static Dokumenttyp dokumenttyp(String label) {
        try {
            return Dokumenttyp.fromLabel(label);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekannte Dokumentart: " + kurz(label));
        }
    }

    /** Meldung bleibt lesbar, auch wenn jemand einen Roman als Dokumentart schickt. */
    private static String kurz(String text) {
        return text.length() > 40 ? text.substring(0, 40) + "…" : text;
    }
}

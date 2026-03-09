package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Arbeitszeitart;
import org.example.kalkulationsprogramm.dto.Arbeitszeitart.ArbeitszeitartCreateDto;
import org.example.kalkulationsprogramm.dto.Arbeitszeitart.ArbeitszeitartDto;
import org.example.kalkulationsprogramm.repository.ArbeitszeitartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArbeitszeitartService {

    private final ArbeitszeitartRepository repository;

    /**
     * Gibt alle aktiven Arbeitszeitarten zurück (für Dokumenterstellung)
     */
    public List<ArbeitszeitartDto> findAllAktiv() {
        return repository.findAllAktiv().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Gibt alle Arbeitszeitarten zurück (für Verwaltung)
     */
    public List<ArbeitszeitartDto> findAll() {
        return repository.findAllSorted().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Findet eine Arbeitszeitart nach ID
     */
    public ArbeitszeitartDto findById(Long id) {
        return repository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new RuntimeException("Arbeitszeitart nicht gefunden: " + id));
    }

    /**
     * Erstellt eine neue Arbeitszeitart
     */
    @Transactional
    public ArbeitszeitartDto create(ArbeitszeitartCreateDto dto) {
        Arbeitszeitart entity = new Arbeitszeitart();
        entity.setBezeichnung(dto.getBezeichnung());
        entity.setBeschreibung(dto.getBeschreibung());
        entity.setStundensatz(dto.getStundensatz());
        entity.setAktiv(dto.isAktiv());
        entity.setSortierung(dto.getSortierung());
        
        return toDto(repository.save(entity));
    }

    /**
     * Aktualisiert eine bestehende Arbeitszeitart.
     * HINWEIS: Änderungen haben keinen Einfluss auf bereits erstellte Dokumente,
     * da diese die Werte als Snapshot in positionenJson speichern.
     */
    @Transactional
    public ArbeitszeitartDto update(Long id, ArbeitszeitartCreateDto dto) {
        Arbeitszeitart entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Arbeitszeitart nicht gefunden: " + id));
        
        entity.setBezeichnung(dto.getBezeichnung());
        entity.setBeschreibung(dto.getBeschreibung());
        entity.setStundensatz(dto.getStundensatz());
        entity.setAktiv(dto.isAktiv());
        entity.setSortierung(dto.getSortierung());
        
        return toDto(repository.save(entity));
    }

    /**
     * Löscht eine Arbeitszeitart (Soft-Delete durch Deaktivierung empfohlen)
     */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * Deaktiviert eine Arbeitszeitart (empfohlen statt Löschen)
     */
    @Transactional
    public void deaktivieren(Long id) {
        Arbeitszeitart entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Arbeitszeitart nicht gefunden: " + id));
        entity.setAktiv(false);
        repository.save(entity);
    }

    private ArbeitszeitartDto toDto(Arbeitszeitart entity) {
        return new ArbeitszeitartDto(
                entity.getId(),
                entity.getBezeichnung(),
                entity.getBeschreibung(),
                entity.getStundensatz(),
                entity.isAktiv(),
                entity.getSortierung()
        );
    }
}

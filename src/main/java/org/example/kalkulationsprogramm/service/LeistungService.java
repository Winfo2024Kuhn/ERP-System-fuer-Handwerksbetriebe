package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.mapper.LeistungMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeistungService {

    /** Laenge der Spalte {@code leistung.bezeichnung} (VARCHAR(255)). */
    static final int MAX_NAME_LAENGE = 255;

    private final LeistungRepository leistungRepository;
    private final LeistungMapper leistungMapper;
    private final ProduktkategorieRepository produktkategorieRepository;

    @Transactional(readOnly = true)
    public List<LeistungDto> getAllLeistungen() {
        return leistungRepository.findAll().stream()
                .map(leistungMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeistungDto createLeistung(LeistungCreateDto dto) {
        pruefeEingaben(dto);
        // Wie im Leistungseditor: neue Leistungen nur in der untersten Ordnerebene.
        // Bestehende Leistungen in Zwischenordnern (Altdaten) bleiben beim Bearbeiten erlaubt.
        if (dto.getFolderId() != null && produktkategorieRepository.existsByUebergeordneteKategorieId(dto.getFolderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Leistungen können nur in der untersten Ordnerebene gespeichert werden.");
        }
        Leistung leistung = leistungMapper.toEntity(dto);
        leistung = leistungRepository.save(leistung);
        return leistungMapper.toDto(leistung);
    }

    @Transactional
    public LeistungDto updateLeistung(Long id, LeistungCreateDto dto) {
        Leistung leistung = leistungRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Diese Leistung gibt es nicht mehr."));
        pruefeEingaben(dto);
        leistungMapper.updateEntity(leistung, dto);
        leistung = leistungRepository.save(leistung);
        return leistungMapper.toDto(leistung);
    }

    @Transactional
    public void deleteLeistung(Long id) {
        leistungRepository.deleteById(id);
    }

    /**
     * Faengt fehlende oder ungueltige Angaben ab, bevor die Datenbank sie mit
     * einem unverstaendlichen Fehler ablehnt (Name und Einheit sind Pflicht).
     */
    private void pruefeEingaben(LeistungCreateDto dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte geben Sie der Leistung einen Namen.");
        }
        dto.setName(dto.getName().strip());
        if (dto.getName().length() > MAX_NAME_LAENGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Name der Leistung ist zu lang (höchstens " + MAX_NAME_LAENGE + " Zeichen).");
        }
        if (dto.getFolderId() != null) {
            if (dto.getFolderId() <= 0 || !produktkategorieRepository.existsById(dto.getFolderId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der gewählte Ordner existiert nicht mehr.");
            }
        } else if (dto.getUnit() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bitte wählen Sie einen Ordner oder eine Einheit für die Leistung.");
        }
    }
}

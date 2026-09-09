package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Zeitkontenmodell;
import org.example.kalkulationsprogramm.dto.ZeitkontenmodellDto;
import org.example.kalkulationsprogramm.repository.ZeitkontenmodellRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZeitkontenmodellService {
    private final ZeitkontenmodellRepository repository;
    private final ZeitkontoVersionRepository versionRepository;
    private final EntityManager entityManager;
    private final Validator validator;

    public List<ZeitkontenmodellDto> alle() {
        return repository.findAll(org.springframework.data.domain.Sort.by("bezeichnung", "id"))
                .stream().map(ZeitkontenmodellDto::from).toList();
    }

    @Transactional
    public ZeitkontenmodellDto erstellen(ZeitkontenmodellDto.Create request) {
        validiere(request);
        Zeitkontenmodell modell = new Zeitkontenmodell();
        modell.setBezeichnung(request.bezeichnung().trim());
        request.arbeitszeit().kopiereNach(modell);
        return ZeitkontenmodellDto.from(repository.saveAndFlush(modell));
    }

    @Transactional
    public ZeitkontenmodellDto aktualisieren(Long id, ZeitkontenmodellDto.Update request) {
        validiere(request);
        Zeitkontenmodell modell = gesperrt(id);
        pruefeVersion(modell, request.expectedVersion());
        modell.setBezeichnung(request.bezeichnung().trim());
        request.arbeitszeit().kopiereNach(modell);
        return ZeitkontenmodellDto.from(repository.saveAndFlush(modell));
    }

    @Transactional
    public void loeschen(Long id, Long expectedVersion) {
        Zeitkontenmodell modell = gesperrt(id);
        pruefeVersion(modell, expectedVersion);
        if (versionRepository.existsByVorlageId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Diese Vorlage wurde bereits zugewiesen und gehört zur Arbeitszeithistorie. Sie kann nicht gelöscht werden.");
        }
        repository.delete(modell);
        repository.flush();
    }

    /** Auch die Zuweisung sperrt dieselbe Vorlage: Löschung/Kopie sind dadurch atomar. */
    private Zeitkontenmodell gesperrt(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Vorlagen-ID.");
        Zeitkontenmodell modell = entityManager.find(Zeitkontenmodell.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (modell == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arbeitszeit-Vorlage nicht gefunden.");
        entityManager.refresh(modell, LockModeType.PESSIMISTIC_WRITE);
        return modell;
    }

    private void pruefeVersion(Zeitkontenmodell modell, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte den erwarteten Versionsstand der Vorlage angeben.");
        }
        if (!Objects.equals(modell.getVersion(), expectedVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Vorlage wurde inzwischen geändert. Bitte neu laden.");
        }
    }

    private void validiere(Object request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte Vorlagendaten angeben.");
        var fehler = validator.validate(request);
        if (!fehler.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                fehler.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted()
                        .collect(java.util.stream.Collectors.joining("; ")));
    }
}

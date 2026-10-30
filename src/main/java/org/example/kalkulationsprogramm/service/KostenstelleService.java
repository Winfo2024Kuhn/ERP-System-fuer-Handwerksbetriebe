package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.KostenstellenTyp;
import org.example.kalkulationsprogramm.dto.KostenstelleDto;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KostenstelleService {

    /** Spaltenlängen in {@code firma_kostenstelle}. */
    private static final int MAX_NAME = 255;
    private static final int MAX_BESCHREIBUNG = 500;

    private final KostenstelleRepository repository;

    @Transactional(readOnly = true)
    public List<KostenstelleDto> findAll() {
        return repository.findByAktivTrueOrderBySortierungAsc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<KostenstelleDto> findByTyp(KostenstellenTyp typ) {
        return repository.findByTypAndAktivTrue(typ)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KostenstelleDto findById(Long id) {
        return repository.findById(id)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<KostenstelleDto> findGeloeschte() {
        return repository.findByAktivFalseOrderByBezeichnungAsc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Legt eine Kostenstelle an oder ändert sie. Ob sie aktiv ist, ändert sich hier nie - dafür gibt es
     * {@link #loeschen(Long)} und {@link #wiederherstellen(Long)}. Gibt es beim Anlegen eine gelöschte Kostenstelle
     * mit gleichem Namen, wird diese zurückgeholt (der Name ist eindeutig, und ihre Buchungen hängen schon an ihr).
     */
    @Transactional
    public KostenstelleDto speichern(KostenstelleDto dto) {
        String name = pruefeEingaben(dto);
        List<Kostenstelle> gleicherName = repository.findAllByBezeichnungIgnoreCase(name).stream()
                .filter(k -> !Objects.equals(k.getId(), dto.getId()))
                .toList();
        if (gleicherName.stream().anyMatch(Kostenstelle::isAktiv)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Eine Kostenstelle mit diesem Namen gibt es schon.");
        }

        Kostenstelle ks;
        if (dto.getId() != null) {
            ks = repository.findById(dto.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diese Kostenstelle gibt es nicht mehr."));
            if (!gleicherName.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Eine gelöschte Kostenstelle heißt schon so. Hol sie unter „Gelöschte Kostenstellen“ zurück.");
            }
        } else if (!gleicherName.isEmpty()) {
            ks = gleicherName.getFirst();
            ks.setAktiv(true);
        } else {
            ks = new Kostenstelle();
            ks.setAktiv(true);
        }

        ks.setBezeichnung(name);
        ks.setTyp(dto.getTyp());
        ks.setBeschreibung(dto.getBeschreibung());
        ks.setIstFixkosten(dto.isIstFixkosten());
        ks.setIstInvestition(dto.isIstInvestition());
        if (dto.getSortierung() != null) {
            ks.setSortierung(dto.getSortierung());
        } else if (ks.getSortierung() == null) {
            ks.setSortierung(0);
        }

        ks = repository.save(ks);
        return toDto(ks);
    }

    /** Blendet die Kostenstelle nur aus; bisherige Buchungen bleiben ihr zugeordnet. */
    @Transactional
    public void loeschen(Long id) {
        repository.findById(id).ifPresent(ks -> {
            ks.setAktiv(false);
            repository.save(ks);
        });
    }

    /** Holt eine gelöschte Kostenstelle zurück - samt ihrer bisherigen Buchungen. */
    @Transactional
    public KostenstelleDto wiederherstellen(Long id) {
        Kostenstelle ks = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diese Kostenstelle gibt es nicht mehr."));
        boolean nameSchonAktiv = repository.findAllByBezeichnungIgnoreCase(ks.getBezeichnung()).stream()
                .anyMatch(k -> k.isAktiv() && !Objects.equals(k.getId(), ks.getId()));
        if (nameSchonAktiv) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Es gibt schon eine Kostenstelle mit diesem Namen. Benenne sie zuerst um.");
        }
        ks.setAktiv(true);
        return toDto(repository.save(ks));
    }

    private static String pruefeEingaben(KostenstelleDto dto) {
        String name = dto.getBezeichnung() == null ? "" : dto.getBezeichnung().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte gib einen Namen für die Kostenstelle ein.");
        }
        if (name.length() > MAX_NAME) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Name darf höchstens " + MAX_NAME + " Zeichen lang sein.");
        }
        if (dto.getTyp() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte wähle aus, welche Art Kostenstelle das ist.");
        }
        if (dto.getBeschreibung() != null && dto.getBeschreibung().length() > MAX_BESCHREIBUNG) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die Beschreibung darf höchstens " + MAX_BESCHREIBUNG + " Zeichen lang sein.");
        }
        return name;
    }

    /**
     * Erstellt Standard-Kostenstellen falls noch keine existieren.
     */
    @Transactional
    public void erstelleStandardKostenstellen() {
        if (repository.count() == 0) {
            Kostenstelle lager = new Kostenstelle();
            lager.setBezeichnung("Lager");
            lager.setTyp(KostenstellenTyp.LAGER);
            lager.setBeschreibung("Lagerbestand - Investitionen");
            lager.setIstInvestition(true);
            lager.setSortierung(1);
            repository.save(lager);

            Kostenstelle gemeinkosten = new Kostenstelle();
            gemeinkosten.setBezeichnung("Gemeinkosten");
            gemeinkosten.setTyp(KostenstellenTyp.GEMEINKOSTEN);
            gemeinkosten.setBeschreibung("Fixkosten für Gemeinkostensatz");
            gemeinkosten.setIstFixkosten(true);
            gemeinkosten.setSortierung(2);
            repository.save(gemeinkosten);
        }
    }

    private KostenstelleDto toDto(Kostenstelle ks) {
        KostenstelleDto dto = new KostenstelleDto();
        dto.setId(ks.getId());
        dto.setBezeichnung(ks.getBezeichnung());
        dto.setTyp(ks.getTyp());
        dto.setBeschreibung(ks.getBeschreibung());
        dto.setIstFixkosten(ks.isIstFixkosten());
        dto.setIstInvestition(ks.isIstInvestition());
        dto.setAktiv(ks.isAktiv());
        dto.setSortierung(ks.getSortierung());
        return dto;
    }
}

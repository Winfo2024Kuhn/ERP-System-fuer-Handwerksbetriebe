package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.KostenstellenTyp;
import org.example.kalkulationsprogramm.dto.KostenstelleDto;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KostenstelleService {

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

    @Transactional
    public KostenstelleDto speichern(KostenstelleDto dto) {
        Kostenstelle ks;
        if (dto.getId() != null) {
            ks = repository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Kostenstelle nicht gefunden: " + dto.getId()));
        } else {
            ks = new Kostenstelle();
        }

        ks.setBezeichnung(dto.getBezeichnung());
        ks.setTyp(dto.getTyp());
        ks.setBeschreibung(dto.getBeschreibung());
        ks.setIstFixkosten(dto.isIstFixkosten());
        ks.setIstInvestition(dto.isIstInvestition());
        ks.setAktiv(dto.isAktiv());
        ks.setSortierung(dto.getSortierung() != null ? dto.getSortierung() : 0);

        ks = repository.save(ks);
        return toDto(ks);
    }

    @Transactional
    public void loeschen(Long id) {
        repository.findById(id).ifPresent(ks -> {
            ks.setAktiv(false);
            repository.save(ks);
        });
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

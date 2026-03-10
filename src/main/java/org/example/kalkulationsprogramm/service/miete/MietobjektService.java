package org.example.kalkulationsprogramm.service.miete;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Mietpartei;
import org.example.kalkulationsprogramm.domain.miete.MietparteiRolle;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.miete.MietobjektRepository;
import org.example.kalkulationsprogramm.repository.miete.MietparteiRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MietobjektService {

    private final MietobjektRepository mietobjektRepository;
    private final MietparteiRepository mietparteiRepository;

    public List<Mietobjekt> findAll() {
        return mietobjektRepository.findAll();
    }

    public Mietobjekt getById(Long id) {
        return mietobjektRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Mietobjekt " + id + " nicht gefunden"));
    }

    public Mietobjekt save(Mietobjekt mietobjekt) {
        return mietobjektRepository.save(mietobjekt);
    }

    public void delete(Long id) {
        Mietobjekt entity = getById(id);
        mietobjektRepository.delete(entity);
    }

    public Mietpartei savePartei(Long mietobjektId, Mietpartei partei) {
        Mietobjekt mietobjekt = getById(mietobjektId);
        partei.setMietobjekt(mietobjekt);
        if (partei.getRolle() != MietparteiRolle.MIETER) {
            partei.setMonatlicherVorschuss(null);
        } else if (partei.getMonatlicherVorschuss() != null) {
            BigDecimal value = partei.getMonatlicherVorschuss().max(BigDecimal.ZERO);
            partei.setMonatlicherVorschuss(value.setScale(2, RoundingMode.HALF_UP));
        }
        return mietparteiRepository.save(partei);
    }

    public void deletePartei(Long parteiId) {
        Mietpartei partei = mietparteiRepository.findById(parteiId)
                .orElseThrow(() -> new NotFoundException("Mietpartei " + parteiId + " nicht gefunden"));
        mietparteiRepository.delete(partei);
    }

    public List<Mietpartei> getParteien(Long mietobjektId) {
        Mietobjekt mietobjekt = getById(mietobjektId);
        return mietparteiRepository.findByMietobjektOrderByNameAsc(mietobjekt);
    }
}

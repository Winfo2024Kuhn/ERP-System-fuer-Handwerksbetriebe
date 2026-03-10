package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.BwaPosition;
import org.example.kalkulationsprogramm.domain.BwaUpload;
import org.example.kalkulationsprogramm.dto.BwaPositionDto;
import org.example.kalkulationsprogramm.dto.BwaUploadDto;
import org.example.kalkulationsprogramm.repository.BwaUploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BwaService {

    private final BwaUploadRepository bwaUploadRepository;

    @Transactional(readOnly = true)
    public List<BwaUploadDto> findByJahr(Integer jahr) {
        return bwaUploadRepository.findByJahrOrderByMonatDesc(jahr)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BwaUploadDto findById(Long id) {
        return bwaUploadRepository.findById(id)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public void delete(Long id) {
        bwaUploadRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Integer> findAvailableYears() {
        return bwaUploadRepository.findAll().stream()
                .map(BwaUpload::getJahr)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    private BwaUploadDto toDto(BwaUpload b) {
        BwaUploadDto dto = new BwaUploadDto();
        dto.setId(b.getId());
        dto.setTyp(b.getTyp());
        dto.setJahr(b.getJahr());
        dto.setMonat(b.getMonat());
        dto.setOriginalDateiname(b.getOriginalDateiname());
        dto.setPdfUrl("/api/bwa/" + b.getId() + "/pdf");
        dto.setUploadDatum(b.getUploadDatum());
        dto.setAnalyseDatum(b.getAnalyseDatum());
        dto.setAiConfidence(b.getAiConfidence());
        dto.setAnalysiert(b.getAnalysiert());
        dto.setFreigegeben(b.getFreigegeben());
        dto.setFreigegebenAm(b.getFreigegebenAm());
        
        if (b.getFreigegebenVon() != null) {
            dto.setFreigegebenVonName(b.getFreigegebenVon().getVorname() + " " + b.getFreigegebenVon().getNachname());
        }
        
        dto.setGesamtGemeinkosten(b.getGesamtGemeinkosten());
        dto.setKostenAusRechnungen(b.getKostenAusRechnungen());
        dto.setKostenAusBwa(b.getKostenAusBwa());
        
        if (b.getSteuerberater() != null) {
            dto.setSteuerberaterName(b.getSteuerberater().getName());
        }
        
        if (b.getPositionen() != null) {
            dto.setPositionen(b.getPositionen().stream()
                    .map(this::toPositionDto)
                    .collect(Collectors.toList()));
        }
        
        return dto;
    }

    private BwaPositionDto toPositionDto(BwaPosition p) {
        BwaPositionDto dto = new BwaPositionDto();
        dto.setId(p.getId());
        dto.setKontonummer(p.getKontonummer());
        dto.setBezeichnung(p.getBezeichnung());
        dto.setBetragMonat(p.getBetragMonat());
        dto.setBetragKumuliert(p.getBetragKumuliert());
        dto.setKategorie(p.getKategorie());
        
        if (p.getKostenstelle() != null) {
            dto.setKostenstelleId(p.getKostenstelle().getId());
            dto.setKostenstelleBezeichnung(p.getKostenstelle().getBezeichnung());
        }
        
        dto.setInRechnungenGefunden(p.getInRechnungenGefunden());
        dto.setRechnungssumme(p.getRechnungssumme());
        dto.setDifferenz(p.getDifferenz());
        dto.setManuellKorrigiert(p.getManuellKorrigiert());
        dto.setNotiz(p.getNotiz());
        return dto;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<String> findStoredFilename(Long id) {
        return bwaUploadRepository.findById(id).map(BwaUpload::getGespeicherterDateiname);
    }
}

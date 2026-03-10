package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FirmeninformationService {

    private final FirmeninformationRepository repository;

    @Transactional(readOnly = true)
    public FirmeninformationDto getFirmeninformation() {
        Firmeninformation fi = repository.getOrCreate();
        return toDto(fi);
    }

    @Transactional
    public FirmeninformationDto speichern(FirmeninformationDto dto) {
        Firmeninformation fi = repository.getOrCreate();
        
        fi.setFirmenname(dto.getFirmenname());
        fi.setStrasse(dto.getStrasse());
        fi.setPlz(dto.getPlz());
        fi.setOrt(dto.getOrt());
        fi.setTelefon(dto.getTelefon());
        fi.setFax(dto.getFax());
        fi.setEmail(dto.getEmail());
        fi.setWebsite(dto.getWebsite());
        fi.setSteuernummer(dto.getSteuernummer());
        fi.setUstIdNr(dto.getUstIdNr());
        fi.setHandelsregister(dto.getHandelsregister());
        fi.setHandelsregisterNummer(dto.getHandelsregisterNummer());
        fi.setBankName(dto.getBankName());
        fi.setIban(dto.getIban());
        fi.setBic(dto.getBic());
        fi.setLogoDateiname(dto.getLogoDateiname());
        fi.setGeschaeftsfuehrer(dto.getGeschaeftsfuehrer());
        fi.setFusszeileText(dto.getFusszeileText());
        
        fi = repository.save(fi);
        return toDto(fi);
    }

    private FirmeninformationDto toDto(Firmeninformation fi) {
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setId(fi.getId());
        dto.setFirmenname(fi.getFirmenname());
        dto.setStrasse(fi.getStrasse());
        dto.setPlz(fi.getPlz());
        dto.setOrt(fi.getOrt());
        dto.setTelefon(fi.getTelefon());
        dto.setFax(fi.getFax());
        dto.setEmail(fi.getEmail());
        dto.setWebsite(fi.getWebsite());
        dto.setSteuernummer(fi.getSteuernummer());
        dto.setUstIdNr(fi.getUstIdNr());
        dto.setHandelsregister(fi.getHandelsregister());
        dto.setHandelsregisterNummer(fi.getHandelsregisterNummer());
        dto.setBankName(fi.getBankName());
        dto.setIban(fi.getIban());
        dto.setBic(fi.getBic());
        dto.setLogoDateiname(fi.getLogoDateiname());
        dto.setGeschaeftsfuehrer(fi.getGeschaeftsfuehrer());
        dto.setFusszeileText(fi.getFusszeileText());
        return dto;
    }
}

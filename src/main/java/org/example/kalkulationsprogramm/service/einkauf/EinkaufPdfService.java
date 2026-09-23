package org.example.kalkulationsprogramm.service.einkauf;

import com.lowagie.text.Image;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.Beleg;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class EinkaufPdfService {
    private final EinkaufPdfPositionsRenderer renderer;
    private final FirmeninformationService firmeninformationService;
    private final EinkaufBedarfRepository bedarfRepository;

    public EinkaufPdfService(EinkaufPdfPositionsRenderer renderer, FirmeninformationService firmeninformationService,
            EinkaufBedarfRepository bedarfRepository) {
        this.renderer = renderer;
        this.firmeninformationService = firmeninformationService;
        this.bedarfRepository = bedarfRepository;
    }

    @Transactional(readOnly = true)
    public byte[] erzeugen(Beleg beleg) {
        return renderer.render(beleg, loadLogo());
    }

    @Transactional(readOnly = true)
    public Resource entnahmeliste(List<Long> bedarfIds) {
        if (bedarfIds == null || bedarfIds.isEmpty() || bedarfIds.size() > 500
                || bedarfIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Bitte wählen Sie bis zu 500 gültige Bedarfe aus.");
        }
        List<Long> uniqueIds = new ArrayList<>(new HashSet<>(bedarfIds));
        Map<Long, EinkaufBedarf> bedarfe = new HashMap<>();
        bedarfRepository.findAllById(uniqueIds).forEach(b -> bedarfe.put(b.getId(), b));
        if (bedarfe.size() != uniqueIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mindestens ein ausgewählter Bedarf wurde nicht gefunden.");
        }
        List<EinkaufPdfPositionsRenderer.EntnahmeZeile> zeilen = uniqueIds.stream().map(id -> {
            EinkaufBedarf bedarf = bedarfe.get(id);
            var position = bedarf.getPosition();
            var basis = position == null ? null : position.basis();
            return new EinkaufPdfPositionsRenderer.EntnahmeZeile(bedarf.getId(), bedarf.getBezeichnung(), position,
                    basis == null ? null : basis.menge(), basis == null || basis.einheit() == null ? "" : basis.einheit().name());
        }).toList();
        return new ByteArrayResource(renderer.renderEntnahmeliste(zeilen, loadLogo()));
    }

    private Image loadLogo() {
        return firmeninformationService == null ? null : firmeninformationService.loadLogoImage();
    }
}

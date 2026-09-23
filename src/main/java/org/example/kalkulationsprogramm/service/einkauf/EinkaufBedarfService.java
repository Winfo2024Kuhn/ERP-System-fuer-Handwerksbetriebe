package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class EinkaufBedarfService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6);
    private final EinkaufBedarfRepository bedarfRepository;
    private final ArtikelInProjektRepository artikelInProjektRepository;
    private final ProjektRepository projektRepository;
    private final EinkaufPositionService positionService;
    private final ObjectMapper objectMapper;
    private EinkaufAnfrageMengenProvider angefragtMengenProvider;

    @Autowired(required = false)
    public void setAngefragtMengenProvider(EinkaufAnfrageMengenProvider provider) {
        this.angefragtMengenProvider = provider;
    }

    public EinkaufBedarfService(EinkaufBedarfRepository bedarfRepository,
            ArtikelInProjektRepository artikelInProjektRepository, ProjektRepository projektRepository,
            EinkaufPositionService positionService, ObjectMapper objectMapper) {
        this.bedarfRepository = bedarfRepository;
        this.artikelInProjektRepository = artikelInProjektRepository;
        this.projektRepository = projektRepository;
        this.positionService = positionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<EinkaufBedarfDto.Response> suche(String q, Long projektId, Pageable p) {
        if (q != null && q.length() > 255) throw new IllegalArgumentException("Die Suche ist zu lang.");
        if (projektId != null && projektId <= 0) throw new IllegalArgumentException("Die Projekt-ID ist ungültig.");
        if (p == null) throw new IllegalArgumentException("Die Seiteneinstellungen fehlen.");
        Page<EinkaufBedarf> page = bedarfRepository.suche(blankToNull(q), projektId, p);
        Map<Long, BigDecimal> angefragt = angefragtFuer(page.getContent());
        return page.map(bedarf -> toResponse(bedarf, angefragt.get(bedarf.getId())));
    }

    @Transactional
    public EinkaufBedarfDto.Response anlegen(EinkaufBedarfDto.Create request, Long akteurId) {
        if (request == null || request.liefergruppe() == null) {
            throw new IllegalArgumentException("Bitte geben Sie die Liefergruppe an.");
        }
        validateActor(akteurId);
        Liefergruppe gruppe = request.liefergruppe();
        Projekt projekt = null;
        if (gruppe.projektId() != null) {
            projekt = projektRepository.findById(gruppe.projektId())
                    .orElseThrow(() -> new NotFoundException("Das ausgewählte Projekt wurde nicht gefunden."));
        }
        PositionSnapshot position = positionService.validiere(request.position(), gruppe.projektId());
        pruefeProjektkennung(position, projekt, null);
        ArtikelInProjekt aip = ladeArtikelposition(request.artikelInProjektId(), projekt);
        EinkaufBedarf bedarf = new EinkaufBedarf(position, gruppe,
                projekt == null ? null : projekt.getId(), aip == null ? null : aip.getId(), brauchtNachpflege(position));
        EinkaufBedarf gespeichert = bedarfRepository.save(bedarf);
        return toResponse(gespeichert, angefragtFuer(List.of(gespeichert)).get(gespeichert.getId()));
    }

    @Transactional
    public EinkaufBedarfDto.Response aktualisieren(Long id, EinkaufBedarfDto.Update request, Long akteurId) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Die Bedarfs-ID ist ungültig.");
        if (request == null || request.liefergruppe() == null) {
            throw new IllegalArgumentException("Bitte geben Sie die Liefergruppe an.");
        }
        validateActor(akteurId);
        EinkaufBedarf bedarf = bedarfRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Der Einkaufsbedarf wurde nicht gefunden."));
        if (bedarf.getVersion() == null || bedarf.getVersion() != request.version()) {
            throw conflict("Der Einkaufsbedarf wurde zwischenzeitlich geändert.");
        }
        PositionSnapshot position = positionService.validiere(request.position(), request.liefergruppe().projektId());
        Projekt projekt = request.liefergruppe().projektId() == null ? null
                : projektRepository.findById(request.liefergruppe().projektId())
                    .orElseThrow(() -> new NotFoundException("Das ausgewählte Projekt wurde nicht gefunden."));
        Long projektId = projekt == null ? null : projekt.getId();
        if (bedarf.getArtikelInProjektId() != null && !Objects.equals(projektId, bedarf.getProjektId())) {
            throw conflict("Eine verknüpfte Projektposition muss in ihrem Projekt bleiben.");
        }
        boolean positionsGeaendert = !safe(serialize(position)).equals(safe(serialize(bedarf.getPosition())));
        if (positionsGeaendert && (bedarf.getReserviert().signum() > 0 || bedarf.getBestellt().signum() > 0
                || bedarf.getLagergedeckt().signum() > 0 || bedarf.getGeliefert().signum() > 0)) {
            throw conflict("Eine bereits disponierte Position kann nicht geändert werden.");
        }
        pruefeProjektkennung(position, projekt, id);
        Mengenbasis basis = position.basis();
        BigDecimal neueMenge = basis == null ? null : basis.menge();
        BigDecimal gedeckt = bedarf.getLagergedeckt().add(bedarf.getBestellt()).add(bedarf.getReserviert());
        if (neueMenge == null || neueMenge.compareTo(gedeckt) < 0) {
            throw conflict("Der Bedarf darf nicht unter bereits gedeckte Mengen sinken.");
        }
        bedarf.setPosition(position);
        bedarf.setLiefergruppe(request.liefergruppe());
        bedarf.setProjektId(projektId);
        bedarf.setInterneKennung(position.art() == Positionsart.ZEICHNUNGSTEIL
                ? position.interneReferenz().trim() : null);
        bedarf.setBezeichnung(position.bezeichnung());
        bedarf.setBedarfMenge(neueMenge);
        bedarf.setNachpflegeErforderlich(false);
        EinkaufBedarf gespeichert = bedarfRepository.saveAndFlush(bedarf);
        return toResponse(gespeichert, angefragtFuer(List.of(gespeichert)).get(gespeichert.getId()));
    }

    @Transactional
    @EventListener
    public void synchronisiereProjektposition(ArtikelInProjekt aip) {
        if (aip == null || aip.getId() == null || aip.getProjekt() == null || aip.getProjekt().getId() == null) return;
        EinkaufBedarf bedarf = bedarfRepository.findByArtikelInProjektIdForUpdate(aip.getId()).orElse(null);
        org.example.kalkulationsprogramm.domain.Artikel artikel = aip.getArtikel();
        String name = artikel == null ? null : artikel.getProduktname();
        String referenz = artikel == null ? null : artikel.getArtikelnummer();
        Einheit einheit = null;
        BigDecimal menge = null;
        if (artikel != null && artikel.getVerrechnungseinheit() != null) {
            switch (artikel.getVerrechnungseinheit()) {
                case KILOGRAMM -> { einheit = Einheit.KILOGRAMM; menge = aip.getKilogramm(); }
                case LAUFENDE_METER -> { einheit = Einheit.METER; menge = aip.getMeter(); }
                case QUADRATMETER -> { einheit = Einheit.QUADRATMETER; menge = aip.getMeter(); }
                case STUECK -> {
                    einheit = Einheit.STUECK;
                    menge = aip.getStueckzahl() == null ? null : BigDecimal.valueOf(aip.getStueckzahl());
                }
            }
        }
        if (menge != null && menge.signum() <= 0) menge = null;
        PositionSnapshot snapshot = new PositionSnapshot(Positionsart.ARTIKEL,
                artikel == null ? null : artikel.getId(), referenz, null, null, name, null, null,
                new Mengenbasis(menge, einheit,
                        aip.getStueckzahl() == null ? null : BigDecimal.valueOf(aip.getStueckzahl()),
                        null, null, null), aip.getSchnittForm(), aip.getAnschnittWinkelLinks(),
                aip.getAnschnittWinkelRechts(), aip.getKommentar(), null, null, null);
        boolean legacyOrdered = aip.isBestellt() && !aip.isAusLager();
        boolean legacyStock = aip.isAusLager();
        boolean repair = artikel == null || name == null || name.isBlank() || referenz == null
                || referenz.isBlank() || menge == null || einheit == null;
        Liefergruppe gruppe = new Liefergruppe(null, null, aip.getProjekt().getId(), null);
        if (bedarf == null) {
            bedarf = new EinkaufBedarf(snapshot, gruppe, aip.getProjekt().getId(), aip.getId(), repair);
            bedarf.setHistorischBestellt(legacyOrdered);
            bedarf.setHistorischAusLager(legacyStock);
            if (legacyOrdered || legacyStock) {
                bedarf.setHistorischerHinweis(legacyOrdered
                        ? "Historisch als bestellt markiert; bitte Beleg und offenen Rest prüfen."
                        : "Historisch als Lagerentnahme markiert; bitte Lagerdeckung prüfen.");
                bedarf.setNachpflegeErforderlich(true);
            }
        } else {
            BigDecimal bereitsGedeckt = bedarf.getLagergedeckt().add(bedarf.getBestellt()).add(bedarf.getReserviert());
            if ((menge == null && bereitsGedeckt.signum() > 0)
                    || (menge != null && menge.compareTo(bereitsGedeckt) < 0)) {
                throw conflict("Die geänderte Projektmenge liegt unter bereits disponierten Mengen.");
            }
            if (positionartIstZeichnungsteil(bedarf.getPosition()) && referenz != null && !referenz.isBlank()
                    && bedarfRepository.existsByProjektIdAndInterneKennungAndIdNot(
                            aip.getProjekt().getId(), referenz.trim(), bedarf.getId())) {
                throw conflict("Diese Teilkennung gibt es in diesem Projekt bereits.");
            }
            bedarf.setPosition(snapshot);
            bedarf.setBezeichnung(name == null || name.isBlank() ? "Nachpflege erforderlich" : name);
            bedarf.setInterneKennung(positionartIstZeichnungsteil(snapshot) ? referenz : null);
            bedarf.setBedarfMenge(menge);
            bedarf.setNachpflegeErforderlich(repair || bedarf.isHistorischBestellt() || bedarf.isHistorischAusLager());
        }
        bedarfRepository.saveAndFlush(bedarf);
    }

    private ArtikelInProjekt ladeArtikelposition(Long id, Projekt projekt) {
        if (id == null) return null;
        if (id <= 0) throw new IllegalArgumentException("Die Projektposition ist ungültig.");
        ArtikelInProjekt aip = artikelInProjektRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Die Projektposition wurde nicht gefunden."));
        if (projekt == null || aip.getProjekt() == null || !projekt.getId().equals(aip.getProjekt().getId())) {
            throw new IllegalArgumentException("Die Projektposition gehört nicht zum ausgewählten Projekt.");
        }
        return aip;
    }

    private void pruefeProjektkennung(PositionSnapshot position, Projekt projekt, Long eigeneId) {
        if (position.art() != Positionsart.ZEICHNUNGSTEIL) return;
        // Project identity comes from the Liefergruppe; the technical validator
        // only checks that one was supplied, so all ownership checks live here.
        if (projekt == null) throw new IllegalArgumentException("Ein Zeichnungsteil braucht ein Projekt.");
        String kennung = position.interneReferenz().trim();
        boolean duplicate = eigeneId == null
                ? bedarfRepository.existsByProjektIdAndInterneKennung(projekt.getId(), kennung)
                : bedarfRepository.existsByProjektIdAndInterneKennungAndIdNot(projekt.getId(), kennung, eigeneId);
        if (duplicate) throw conflict("Diese Teilkennung gibt es in diesem Projekt bereits.");
    }

    private EinkaufBedarfDto.Response toResponse(EinkaufBedarf bedarf, BigDecimal angefragt) {
        BigDecimal bedarfMenge = bedarf.getBedarfMenge();
        angefragt = nullToZero(angefragt);
        EinkaufBedarfDto.Mengenstand mengen = new EinkaufBedarfDto.Mengenstand(bedarfMenge,
                bedarf.getLagergedeckt(), angefragt, bedarf.getReserviert(), bedarf.getBestellt(),
                bedarf.getGeliefert(), bedarf.getStorniert(), bedarf.ungedeckt(), bedarf.disponierbar());
        return new EinkaufBedarfDto.Response(bedarf.getId(), bedarf.getVersion() == null ? 0 : bedarf.getVersion(),
                bedarf.getPosition(), bedarf.getLiefergruppe(), mengen, bedarf.isNachpflegeErforderlich(),
                bedarf.getHistorischerHinweis());
    }

    private static void validateActor(Long actor) {
        if (actor == null || actor <= 0) throw new IllegalArgumentException("Der handelnde Benutzer fehlt.");
    }
    private static boolean brauchtNachpflege(PositionSnapshot position) {
        return position == null || position.interneReferenz() == null || position.interneReferenz().isBlank()
                || position.bezeichnung() == null || position.bezeichnung().isBlank()
                || position.basis() == null || position.basis().menge() == null || position.basis().einheit() == null;
    }
    private static boolean positionartIstZeichnungsteil(PositionSnapshot position) {
        return position != null && position.art() == Positionsart.ZEICHNUNGSTEIL;
    }
    private static BigDecimal nullToZero(BigDecimal value) { return value == null ? ZERO : value; }
    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
    private Map<Long, BigDecimal> angefragtFuer(Collection<EinkaufBedarf> bedarfe) {
        if (angefragtMengenProvider == null) return Map.of();
        List<Long> ids = bedarfe.stream().map(EinkaufBedarf::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, BigDecimal> ergebnis = angefragtMengenProvider.angefragtFuerBedarfe(ids);
        return ergebnis == null ? Map.of() : ergebnis;
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String serialize(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Der Positionsstand konnte nicht geprüft werden.", e); }
    }
    private static String safe(String value) { return value == null ? "" : value; }
}

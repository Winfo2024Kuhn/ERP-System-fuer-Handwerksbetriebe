package org.example.kalkulationsprogramm.service.miete;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Raum;
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand;
import org.example.kalkulationsprogramm.domain.miete.Zaehlerstand;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.miete.MietobjektRepository;
import org.example.kalkulationsprogramm.repository.miete.RaumRepository;
import org.example.kalkulationsprogramm.repository.miete.VerbrauchsgegenstandRepository;
import org.example.kalkulationsprogramm.repository.miete.ZaehlerstandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RaumVerbrauchService {

    private final MietobjektRepository mietobjektRepository;
    private final RaumRepository raumRepository;
    private final VerbrauchsgegenstandRepository verbrauchsgegenstandRepository;
    private final ZaehlerstandRepository zaehlerstandRepository;

    public List<Raum> getRaeume(Long mietobjektId) {
        Mietobjekt mietobjekt = mietobjektRepository.findById(mietobjektId)
                .orElseThrow(() -> new NotFoundException("Mietobjekt " + mietobjektId + " nicht gefunden"));
        return raumRepository.findByMietobjektOrderByNameAsc(mietobjekt);
    }

    public Raum saveRaum(Long mietobjektId, Raum raum) {
        Mietobjekt mietobjekt = mietobjektRepository.findById(mietobjektId)
                .orElseThrow(() -> new NotFoundException("Mietobjekt " + mietobjektId + " nicht gefunden"));
        raum.setMietobjekt(mietobjekt);
        return raumRepository.save(raum);
    }

    public void deleteRaum(Long raumId) {
        Raum raum = raumRepository.findById(raumId)
                .orElseThrow(() -> new NotFoundException("Raum " + raumId + " nicht gefunden"));
        raumRepository.delete(raum);
    }

    public List<Verbrauchsgegenstand> getVerbrauchsgegenstaende(Long raumId) {
        Raum raum = raumRepository.findById(raumId)
                .orElseThrow(() -> new NotFoundException("Raum " + raumId + " nicht gefunden"));
        return verbrauchsgegenstandRepository.findByRaumOrderByNameAsc(raum);
    }

    public Verbrauchsgegenstand saveVerbrauchsgegenstand(Long raumId, Verbrauchsgegenstand gegenstand) {
        Raum raum = raumRepository.findById(raumId)
                .orElseThrow(() -> new NotFoundException("Raum " + raumId + " nicht gefunden"));
        gegenstand.setRaum(raum);
        return verbrauchsgegenstandRepository.save(gegenstand);
    }

    public void deleteVerbrauchsgegenstand(Long verbrauchsgegenstandId) {
        Verbrauchsgegenstand gegenstand = verbrauchsgegenstandRepository.findById(verbrauchsgegenstandId)
                .orElseThrow(() -> new NotFoundException("Verbrauchsgegenstand " + verbrauchsgegenstandId + " nicht gefunden"));
        verbrauchsgegenstandRepository.delete(gegenstand);
    }

    public Zaehlerstand saveZaehlerstand(Long verbrauchsgegenstandId, Zaehlerstand eingabe) {
        Verbrauchsgegenstand gegenstand = verbrauchsgegenstandRepository.findById(verbrauchsgegenstandId)
                .orElseThrow(() -> new NotFoundException("Verbrauchsgegenstand " + verbrauchsgegenstandId + " nicht gefunden"));

        Zaehlerstand ziel = zaehlerstandRepository
                .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, eingabe.getAbrechnungsJahr())
                .orElseGet(Zaehlerstand::new);

        ziel.setVerbrauchsgegenstand(gegenstand);
        ziel.setAbrechnungsJahr(eingabe.getAbrechnungsJahr());
        ziel.setStichtag(eingabe.getStichtag());
        ziel.setStand(eingabe.getStand());
        ziel.setKommentar(eingabe.getKommentar());

        berechneVerbrauch(ziel);

        Zaehlerstand gespeichert = zaehlerstandRepository.save(ziel);

        aktualisiereNachfolgerVerbrauch(gegenstand, ziel.getAbrechnungsJahr());

        return gespeichert;
    }

    public List<Zaehlerstand> getZaehlerstaende(Long verbrauchsgegenstandId) {
        Verbrauchsgegenstand gegenstand = verbrauchsgegenstandRepository.findById(verbrauchsgegenstandId)
                .orElseThrow(() -> new NotFoundException("Verbrauchsgegenstand " + verbrauchsgegenstandId + " nicht gefunden"));
        List<Zaehlerstand> werte = zaehlerstandRepository.findByVerbrauchsgegenstandOrderByAbrechnungsJahrDesc(gegenstand);
        werte.sort(Comparator.comparing(Zaehlerstand::getAbrechnungsJahr));
        return werte;
    }

    public void deleteZaehlerstand(Long zaehlerstandId) {
        Zaehlerstand zaehlerstand = zaehlerstandRepository.findById(zaehlerstandId)
                .orElseThrow(() -> new NotFoundException("Zaehlerstand " + zaehlerstandId + " nicht gefunden"));
        Integer jahr = zaehlerstand.getAbrechnungsJahr();
        Verbrauchsgegenstand gegenstand = zaehlerstand.getVerbrauchsgegenstand();
        zaehlerstandRepository.delete(zaehlerstand);
        aktualisiereNachfolgerVerbrauch(gegenstand, jahr - 1);
    }

    private void aktualisiereNachfolgerVerbrauch(Verbrauchsgegenstand gegenstand, Integer jahr) {
        if (jahr == null) {
            return;
        }
        Zaehlerstand basis = null;
        if (jahr > 0) {
            basis = zaehlerstandRepository
                    .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, jahr)
                    .orElse(null);
        }
        Zaehlerstand nachfolger = zaehlerstandRepository
                .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, jahr + 1)
                .orElse(null);
        if (nachfolger != null) {
            if (basis != null && basis.getStand() != null && nachfolger.getStand() != null) {
                BigDecimal verbrauch = nachfolger.getStand().subtract(basis.getStand());
                nachfolger.setVerbrauch(verbrauch);
            } else {
                nachfolger.setVerbrauch(null);
            }
            zaehlerstandRepository.save(nachfolger);
            aktualisiereNachfolgerVerbrauch(gegenstand, jahr + 1);
        }
    }

    private void berechneVerbrauch(Zaehlerstand ziel) {
        Verbrauchsgegenstand gegenstand = ziel.getVerbrauchsgegenstand();
        Integer jahr = ziel.getAbrechnungsJahr();
        if (gegenstand == null || jahr == null || ziel.getStand() == null) {
            ziel.setVerbrauch(null);
            return;
        }
        zaehlerstandRepository
                .findByVerbrauchsgegenstandAndAbrechnungsJahr(gegenstand, jahr - 1)
                .ifPresentOrElse(vorjahr -> {
                    if (vorjahr.getStand() != null) {
                        BigDecimal verbrauch = ziel.getStand().subtract(vorjahr.getStand());
                        ziel.setVerbrauch(verbrauch);
                    } else {
                        ziel.setVerbrauch(null);
                    }
                }, () -> ziel.setVerbrauch(null));
    }
}

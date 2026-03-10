package org.example.kalkulationsprogramm.service.miete;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.miete.Kostenposition;
import org.example.kalkulationsprogramm.domain.miete.KostenpositionBerechnung;
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle;
import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Mietpartei;
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel;
import org.example.kalkulationsprogramm.domain.miete.VerteilungsschluesselEintrag;
import org.example.kalkulationsprogramm.domain.miete.Verbrauchsgegenstand;
import org.example.kalkulationsprogramm.exception.MietabrechnungValidationException;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.miete.KostenpositionRepository;
import org.example.kalkulationsprogramm.repository.miete.MieteKostenstelleRepository;
import org.example.kalkulationsprogramm.repository.miete.MietobjektRepository;
import org.example.kalkulationsprogramm.repository.miete.MietparteiRepository;
import org.example.kalkulationsprogramm.repository.miete.VerteilungsschluesselEintragRepository;
import org.example.kalkulationsprogramm.repository.miete.VerteilungsschluesselRepository;
import org.example.kalkulationsprogramm.repository.miete.VerbrauchsgegenstandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class KostenVerteilungService {

    private final MietobjektRepository mietobjektRepository;
    private final MietparteiRepository mietparteiRepository;
    private final VerbrauchsgegenstandRepository verbrauchsgegenstandRepository;
    private final MieteKostenstelleRepository kostenstelleRepository;
    private final KostenpositionRepository kostenpositionRepository;
    private final VerteilungsschluesselRepository verteilungsschluesselRepository;
    private final VerteilungsschluesselEintragRepository verteilungsschluesselEintragRepository;

    public List<Kostenstelle> getKostenstellen(Long mietobjektId) {
        return kostenstelleRepository.findByMietobjektIdOrderByNameAsc(mietobjektId);
    }

    public Kostenstelle saveKostenstelle(Long mietobjektId, Kostenstelle kostenstelle) {
        Mietobjekt mietobjekt = mietobjektRepository.findById(mietobjektId)
                .orElseThrow(() -> new NotFoundException("Mietobjekt " + mietobjektId + " nicht gefunden"));
        Kostenstelle ziel = kostenstelle.getId() != null
                ? kostenstelleRepository.findById(kostenstelle.getId())
                        .orElseThrow(
                                () -> new NotFoundException("Kostenstelle " + kostenstelle.getId() + " nicht gefunden"))
                : new Kostenstelle();
        ziel.setMietobjekt(mietobjekt);
        ziel.setName(kostenstelle.getName());
        ziel.setBeschreibung(kostenstelle.getBeschreibung());
        ziel.setUmlagefaehig(kostenstelle.isUmlagefaehig());
        if (kostenstelle.getStandardSchluessel() != null && kostenstelle.getStandardSchluessel().getId() != null) {
            Verteilungsschluessel schluessel = verteilungsschluesselRepository
                    .findById(kostenstelle.getStandardSchluessel().getId())
                    .orElseThrow(() -> new NotFoundException("Verteilungsschlüssel "
                            + kostenstelle.getStandardSchluessel().getId() + " nicht gefunden"));
            ziel.setStandardSchluessel(schluessel);
        } else {
            ziel.setStandardSchluessel(null);
        }
        return kostenstelleRepository.save(ziel);
    }

    public void deleteKostenstelle(Long kostenstelleId) {
        Kostenstelle kostenstelle = kostenstelleRepository.findById(kostenstelleId)
                .orElseThrow(() -> new NotFoundException("Kostenstelle " + kostenstelleId + " nicht gefunden"));
        kostenstelleRepository.delete(kostenstelle);
    }

    public List<Kostenposition> getKostenpositionen(Long kostenstelleId, Integer jahr) {
        Kostenstelle kostenstelle = kostenstelleRepository.findById(kostenstelleId)
                .orElseThrow(() -> new NotFoundException("Kostenstelle " + kostenstelleId + " nicht gefunden"));
        if (jahr != null) {
            return kostenpositionRepository.findByKostenstelleAndAbrechnungsJahr(kostenstelle, jahr);
        }
        return kostenpositionRepository.findByKostenstelle(kostenstelle);
    }

    public Kostenposition saveKostenposition(Long kostenstelleId, Kostenposition kostenposition) {
        Kostenstelle kostenstelle = kostenstelleRepository.findById(kostenstelleId)
                .orElseThrow(() -> new NotFoundException("Kostenstelle " + kostenstelleId + " nicht gefunden"));
        Kostenposition ziel = kostenposition.getId() != null
                ? kostenpositionRepository.findById(kostenposition.getId())
                        .orElseThrow(() -> new NotFoundException(
                                "Kostenposition " + kostenposition.getId() + " nicht gefunden"))
                : new Kostenposition();
        ziel.setKostenstelle(kostenstelle);
        ziel.setAbrechnungsJahr(kostenposition.getAbrechnungsJahr());
        KostenpositionBerechnung berechnung = kostenposition.getBerechnung() != null
                ? kostenposition.getBerechnung()
                : KostenpositionBerechnung.BETRAG;
        ziel.setBerechnung(berechnung);
        BigDecimal betrag = kostenposition.getBetrag();
        if (berechnung == KostenpositionBerechnung.VERBRAUCHSFAKTOR) {
            betrag = betrag != null ? betrag : BigDecimal.ZERO;
        } else if (betrag == null) {
            throw new MietabrechnungValidationException(
                    "Das Feld 'Betrag' darf nicht leer sein.",
                    "Kostenposition ohne Betrag kann nicht gespeichert werden.");
        }
        if (betrag != null) {
            betrag = betrag.setScale(2, RoundingMode.HALF_UP);
        }
        ziel.setBetrag(betrag);
        BigDecimal verbrauchsfaktor = null;
        if (berechnung == KostenpositionBerechnung.VERBRAUCHSFAKTOR && kostenposition.getVerbrauchsfaktor() != null) {
            verbrauchsfaktor = kostenposition.getVerbrauchsfaktor().setScale(5, RoundingMode.HALF_UP);
        }
        ziel.setVerbrauchsfaktor(verbrauchsfaktor);
        ziel.setBeschreibung(kostenposition.getBeschreibung());
        ziel.setBelegNummer(kostenposition.getBelegNummer());
        ziel.setBuchungsdatum(kostenposition.getBuchungsdatum());
        if (kostenposition.getVerteilungsschluesselOverride() != null
                && kostenposition.getVerteilungsschluesselOverride().getId() != null) {
            Verteilungsschluessel schluessel = verteilungsschluesselRepository
                    .findById(kostenposition.getVerteilungsschluesselOverride().getId())
                    .orElseThrow(() -> new NotFoundException("Verteilungsschlüssel "
                            + kostenposition.getVerteilungsschluesselOverride().getId() + " nicht gefunden"));
            ziel.setVerteilungsschluesselOverride(schluessel);
        } else {
            ziel.setVerteilungsschluesselOverride(null);
        }
        return kostenpositionRepository.save(ziel);
    }

    public void deleteKostenposition(Long kostenpositionId) {
        Kostenposition kostenposition = kostenpositionRepository.findById(kostenpositionId)
                .orElseThrow(() -> new NotFoundException("Kostenposition " + kostenpositionId + " nicht gefunden"));
        kostenpositionRepository.delete(kostenposition);
    }

    /**
     * Kopiert alle Kostenpositionen des Vorjahres in das Zieljahr für alle
     * Kostenstellen eines Mietobjekts.
     * Bereits vorhandene Positionen im Zieljahr werden nicht überschrieben.
     * 
     * @return Anzahl der kopierten Positionen
     */
    public int copyKostenpositionenVonVorjahr(Long mietobjektId, int zielJahr) {
        int vorjahr = zielJahr - 1;
        List<Kostenstelle> kostenstellen = kostenstelleRepository.findByMietobjektIdOrderByNameAsc(mietobjektId);
        int kopiert = 0;
        for (Kostenstelle ks : kostenstellen) {
            List<Kostenposition> vorjahrPositionen = kostenpositionRepository.findByKostenstelleAndAbrechnungsJahr(ks,
                    vorjahr);
            List<Kostenposition> zielPositionen = kostenpositionRepository.findByKostenstelleAndAbrechnungsJahr(ks,
                    zielJahr);
            if (!zielPositionen.isEmpty()) {
                // Kostenstelle hat bereits Positionen im Zieljahr — nicht überschreiben
                continue;
            }
            for (Kostenposition quelle : vorjahrPositionen) {
                Kostenposition kopie = new Kostenposition();
                kopie.setKostenstelle(ks);
                kopie.setAbrechnungsJahr(zielJahr);
                kopie.setBeschreibung(quelle.getBeschreibung());
                kopie.setBerechnung(quelle.getBerechnung());
                kopie.setBetrag(quelle.getBetrag());
                kopie.setVerbrauchsfaktor(quelle.getVerbrauchsfaktor());
                kopie.setVerteilungsschluesselOverride(quelle.getVerteilungsschluesselOverride());
                // Belegnummer wird NICHT kopiert (ist jahresspezifisch)
                // Buchungsdatum wird auf heute gesetzt
                kopie.setBuchungsdatum(LocalDate.now());
                kostenpositionRepository.save(kopie);
                kopiert++;
            }
        }
        return kopiert;
    }

    public List<Verteilungsschluessel> getVerteilungsschluessel(Long mietobjektId) {
        Mietobjekt mietobjekt = mietobjektRepository.findById(mietobjektId)
                .orElseThrow(() -> new NotFoundException("Mietobjekt " + mietobjektId + " nicht gefunden"));
        return verteilungsschluesselRepository.findByMietobjektOrderByNameAsc(mietobjekt);
    }

    public Verteilungsschluessel saveVerteilungsschluessel(Long mietobjektId, Verteilungsschluessel schluessel) {
        Mietobjekt mietobjekt = mietobjektRepository.findById(mietobjektId)
                .orElseThrow(() -> new NotFoundException("Mietobjekt " + mietobjektId + " nicht gefunden"));
        Verteilungsschluessel ziel = schluessel.getId() != null
                ? verteilungsschluesselRepository.findById(schluessel.getId())
                        .orElseThrow(() -> new NotFoundException(
                                "Verteilungsschlüssel " + schluessel.getId() + " nicht gefunden"))
                : new Verteilungsschluessel();
        ziel.setMietobjekt(mietobjekt);
        ziel.setName(schluessel.getName());
        ziel.setBeschreibung(schluessel.getBeschreibung());
        ziel.setTyp(schluessel.getTyp());

        ziel.getEintraege().clear();
        if (schluessel.getEintraege() != null) {
            for (VerteilungsschluesselEintrag eingabe : schluessel.getEintraege()) {
                VerteilungsschluesselEintrag eintrag = eingabe.getId() != null
                        ? verteilungsschluesselEintragRepository.findById(eingabe.getId())
                                .orElse(new VerteilungsschluesselEintrag())
                        : new VerteilungsschluesselEintrag();
                Mietpartei mietpartei = mietparteiRepository.findById(eingabe.getMietpartei().getId())
                        .orElseThrow(() -> new NotFoundException(
                                "Mietpartei " + eingabe.getMietpartei().getId() + " nicht gefunden"));
                eintrag.setVerteilungsschluessel(ziel);
                eintrag.setMietpartei(mietpartei);
                eintrag.setAnteil(eingabe.getAnteil());
                eintrag.setKommentar(eingabe.getKommentar());
                if (eingabe.getVerbrauchsgegenstand() != null && eingabe.getVerbrauchsgegenstand().getId() != null) {
                    Verbrauchsgegenstand gegenstand = verbrauchsgegenstandRepository
                            .findById(eingabe.getVerbrauchsgegenstand().getId())
                            .orElseThrow(() -> new NotFoundException("Verbrauchsgegenstand "
                                    + eingabe.getVerbrauchsgegenstand().getId() + " nicht gefunden"));
                    eintrag.setVerbrauchsgegenstand(gegenstand);
                } else {
                    eintrag.setVerbrauchsgegenstand(null);
                }
                ziel.getEintraege().add(eintrag);
            }
        }
        return verteilungsschluesselRepository.save(ziel);
    }

    public void deleteVerteilungsschluessel(Long id) {
        Verteilungsschluessel schluessel = verteilungsschluesselRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Verteilungsschlüssel " + id + " nicht gefunden"));
        verteilungsschluesselRepository.delete(schluessel);
    }
}

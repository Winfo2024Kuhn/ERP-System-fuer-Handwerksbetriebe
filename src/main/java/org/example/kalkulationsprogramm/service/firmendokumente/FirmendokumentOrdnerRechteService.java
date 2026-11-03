package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentEingaben.fehler;
import static org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentEingaben.nichtGefunden;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdnerRecht;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentAbteilungsRechtDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteSpeichernDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteSpeichernDto.Eintrag;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRechtRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Pflegt die Rechte-Einträge der obersten Ordner: anzeigen und speichern (nur Admins, abgesichert in der
 * SecurityConfig) und beim Verschieben bzw. Löschen eines Ordners mitnehmen. Wer was sehen darf, entscheidet
 * {@link FirmendokumentZugriff}.
 */
@Service
@RequiredArgsConstructor
public class FirmendokumentOrdnerRechteService {

    /** Obergrenze für die Abteilungsliste (Abteilungen sind wenige; schützt vor riesigen Anfragen). */
    static final int MAX_ABTEILUNGEN = 500;

    private static final String ORDNER_FEHLT = "Den Ordner gibt es nicht mehr.";
    private static final String NUR_OBEN = "Rechte werden am obersten Ordner eingestellt. "
            + "Sie gelten auch für alle Unterordner.";
    private static final String ABTEILUNG_FEHLT = "Die Abteilung gibt es nicht mehr.";
    private static final String STUFE_FALSCH = "Bitte für jede Abteilung „Kein Zugriff“, „Ansehen“ oder „Ändern“ wählen.";

    private final FirmendokumentOrdnerRepository ordnerRepository;
    private final FirmendokumentOrdnerRechtRepository rechtRepository;
    private final AbteilungRepository abteilungRepository;

    @Transactional(readOnly = true)
    public FirmendokumentOrdnerRechteDto laden(Long id) {
        return dto(obersterOrdner(id));
    }

    /**
     * Speichert Schalter und Stufen. Fehlende Abteilungen und {@code KEINE} löschen den Eintrag. Bei
     * {@code eingeschraenkt=false} bleiben die Einträge erhalten (für späteres Einschalten), wirken aber nicht.
     */
    @Transactional
    public FirmendokumentOrdnerRechteDto speichern(Long id, FirmendokumentOrdnerRechteSpeichernDto eingabe) {
        FirmendokumentOrdner ordner = obersterOrdner(id);
        if (eingabe == null || eingabe.eingeschraenkt() == null) {
            throw fehler("Bitte angeben, ob der Ordner eingeschränkt ist.");
        }
        Map<Long, FirmendokumentZugriffsStufe> gewuenscht = gewuenschteStufen(eingabe.abteilungen());
        Map<Long, Abteilung> abteilungen = vorhandeneAbteilungen(gewuenscht);
        Map<Long, FirmendokumentOrdnerRecht> bisher = rechtRepository.findeFuerOrdner(id).stream()
                .collect(Collectors.toMap(r -> r.getAbteilung().getId(), Function.identity(), (a, b) -> a));
        bisher.forEach((abteilungId, recht) -> {
            FirmendokumentZugriffsStufe stufe = gewuenscht.getOrDefault(abteilungId, FirmendokumentZugriffsStufe.KEINE);
            if (stufe == FirmendokumentZugriffsStufe.KEINE) {
                rechtRepository.delete(recht);
            } else {
                recht.setStufe(stufe);
            }
        });
        gewuenscht.forEach((abteilungId, stufe) -> {
            if (stufe != FirmendokumentZugriffsStufe.KEINE && !bisher.containsKey(abteilungId)) {
                rechtRepository.save(neuesRecht(ordner, abteilungen.get(abteilungId), stufe));
            }
        });
        ordner.setRechteEingeschraenkt(eingabe.eingeschraenkt());
        try {
            rechtRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // Zwei Admins haben gleichzeitig denselben Eintrag angelegt (UNIQUE ordner_id + abteilung_id).
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Jemand anders hat die Rechte gerade gespeichert. Bitte neu laden.");
        }
        return dto(ordner);
    }

    /**
     * Vor dem Umhängen aufrufen ({@code ordner.getParent()} ist noch der alte Elternordner). Kommt ein Unterordner auf
     * die oberste Ebene, übernimmt er Schalter und Einträge seines bisherigen obersten Ordners – wer vorher nichts
     * sah, sieht auch danach nichts. Wandert ein oberster Ordner in einen anderen Zweig, gelten dessen Rechte.
     */
    @Transactional
    public void beimVerschieben(FirmendokumentOrdner ordner, FirmendokumentOrdner ziel) {
        boolean warOben = ordner.getParent() == null;
        if (!warOben && ziel == null) {
            FirmendokumentOrdner alteWurzel = FirmendokumentZugriff.wurzel(ordner);
            loescheEintraege(ordner.getId());
            if (alteWurzel == null) {
                return;
            }
            ordner.setRechteEingeschraenkt(alteWurzel.isRechteEingeschraenkt());
            List<FirmendokumentOrdnerRecht> kopie = rechtRepository.findeFuerOrdner(alteWurzel.getId()).stream()
                    .map(r -> neuesRecht(ordner, r.getAbteilung(), r.getStufe()))
                    .toList();
            rechtRepository.saveAll(kopie);
        } else if (warOben && ziel != null) {
            loescheEintraege(ordner.getId());
            ordner.setRechteEingeschraenkt(false);
        }
    }

    /** Einträge eines Ordners entfernen (beim Löschen; in PostgreSQL räumt zusätzlich ON DELETE CASCADE auf). */
    @Transactional
    public void loescheEintraege(Long ordnerId) {
        List<FirmendokumentOrdnerRecht> eintraege = rechtRepository.findeFuerOrdner(ordnerId);
        if (!eintraege.isEmpty()) {
            rechtRepository.deleteAll(eintraege);
        }
    }

    private FirmendokumentOrdner obersterOrdner(Long id) {
        FirmendokumentOrdner ordner = ordnerRepository.findById(id).orElseThrow(() -> nichtGefunden(ORDNER_FEHLT));
        if (ordner.getParent() != null) {
            throw fehler(NUR_OBEN);
        }
        return ordner;
    }

    /** Prüft Form der Einträge (Abteilung angegeben, Stufe gültig, keine Doppel); Reihenfolge bleibt erhalten. */
    private static Map<Long, FirmendokumentZugriffsStufe> gewuenschteStufen(List<Eintrag> eintraege) {
        Map<Long, FirmendokumentZugriffsStufe> stufen = new LinkedHashMap<>();
        if (eintraege == null) {
            return stufen;
        }
        if (eintraege.size() > MAX_ABTEILUNGEN) {
            throw fehler("Es können höchstens " + MAX_ABTEILUNGEN + " Abteilungen eingetragen werden.");
        }
        for (Eintrag eintrag : eintraege) {
            if (eintrag == null || eintrag.abteilungId() == null) {
                throw fehler(ABTEILUNG_FEHLT);
            }
            FirmendokumentZugriffsStufe stufe = stufe(eintrag.stufe());
            if (stufen.put(eintrag.abteilungId(), stufe) != null) {
                throw fehler("Jede Abteilung darf nur einmal vorkommen.");
            }
        }
        return stufen;
    }

    private static FirmendokumentZugriffsStufe stufe(String text) {
        if (text != null) {
            for (FirmendokumentZugriffsStufe stufe : FirmendokumentZugriffsStufe.values()) {
                if (stufe.name().equals(text)) {
                    return stufe;
                }
            }
        }
        throw fehler(STUFE_FALSCH);
    }

    /** Alle genannten Abteilungen müssen existieren (auch die mit KEINE), sonst 400. */
    private Map<Long, Abteilung> vorhandeneAbteilungen(Map<Long, FirmendokumentZugriffsStufe> gewuenscht) {
        if (gewuenscht.isEmpty()) {
            return Map.of();
        }
        Map<Long, Abteilung> vorhanden = new HashMap<>();
        abteilungRepository.findAllById(gewuenscht.keySet()).forEach(a -> vorhanden.put(a.getId(), a));
        if (!vorhanden.keySet().containsAll(gewuenscht.keySet())) {
            throw fehler(ABTEILUNG_FEHLT);
        }
        return vorhanden;
    }

    private static FirmendokumentOrdnerRecht neuesRecht(FirmendokumentOrdner ordner, Abteilung abteilung,
            FirmendokumentZugriffsStufe stufe) {
        FirmendokumentOrdnerRecht recht = new FirmendokumentOrdnerRecht();
        recht.setOrdner(ordner);
        recht.setAbteilung(abteilung);
        recht.setStufe(stufe);
        return recht;
    }

    private FirmendokumentOrdnerRechteDto dto(FirmendokumentOrdner ordner) {
        Map<Long, FirmendokumentZugriffsStufe> stufen = new HashMap<>();
        rechtRepository.findeFuerOrdner(ordner.getId()).forEach(r -> stufen.put(r.getAbteilung().getId(), r.getStufe()));
        List<FirmendokumentAbteilungsRechtDto> abteilungen = abteilungRepository
                .findeSortiert(PageRequest.of(0, MAX_ABTEILUNGEN)).stream()
                .map(a -> new FirmendokumentAbteilungsRechtDto(a.getId(), a.getName(),
                        stufen.getOrDefault(a.getId(), FirmendokumentZugriffsStufe.KEINE)))
                .toList();
        return new FirmendokumentOrdnerRechteDto(ordner.getId(), ordner.getName(), ordner.isRechteEingeschraenkt(),
                abteilungen);
    }
}

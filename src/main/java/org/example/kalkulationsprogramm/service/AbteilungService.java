package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Abteilungen anlegen, umbenennen und löschen (Einstellungen → Abteilungen &amp; Rechte).
 *
 * <p>Lag vorher als lose Endpunkte im {@code ArbeitsgangController} – ohne Prüfung auf
 * doppelte Namen, und ein Löschen mit zugeordneten Mitarbeitern scheiterte an der
 * Datenbank und kam als „nicht gefunden“ zurück. Jetzt sagt das Programm, was im Weg ist.</p>
 */
@Service
@RequiredArgsConstructor
public class AbteilungService {

    static final int MAX_NAME_LAENGE = 100;

    private final AbteilungRepository abteilungRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final ArbeitsgangRepository arbeitsgangRepository;
    private final AbteilungDokumentBerechtigungRepository berechtigungRepository;

    @Transactional(readOnly = true)
    public List<Abteilung> findeAlle() {
        return abteilungRepository.findAll();
    }

    @Transactional
    public Abteilung erstelle(String rohName) {
        String name = pruefeName(rohName);
        if (abteilungRepository.existsByNameIgnoreCase(name)) {
            throw schonVorhanden(name);
        }
        Abteilung abteilung = new Abteilung();
        abteilung.setName(name);
        return abteilungRepository.save(abteilung);
    }

    @Transactional
    public Abteilung benenneUm(Long id, String rohName) {
        Abteilung abteilung = lade(id);
        String name = pruefeName(rohName);
        if (abteilungRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw schonVorhanden(name);
        }
        abteilung.setName(name);
        return abteilungRepository.save(abteilung);
    }

    /**
     * Löscht die Abteilung samt ihren Rechten. Die Freigaben für E-Mail-Postfächer
     * räumt die Datenbank selbst ab ({@code ON DELETE CASCADE}, V408).
     * Gehören noch Mitarbeiter oder Arbeitsgänge dazu, bleibt sie stehen.
     */
    @Transactional
    public void loesche(Long id) {
        Abteilung abteilung = lade(id);
        long mitarbeiter = mitarbeiterRepository.countByAbteilungen_Id(id);
        long arbeitsgaenge = arbeitsgangRepository.countByAbteilung_Id(id);
        if (mitarbeiter > 0 || arbeitsgaenge > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    nochZugeordnetMeldung(abteilung.getName(), mitarbeiter, arbeitsgaenge));
        }
        berechtigungRepository.deleteAll(berechtigungRepository.findByAbteilungId(id));
        abteilungRepository.delete(abteilung);
    }

    private Abteilung lade(Long id) {
        return abteilungRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diese Abteilung gibt es nicht mehr."));
    }

    private static String pruefeName(String rohName) {
        String name = rohName == null ? "" : rohName.strip();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Namen für die Abteilung eingeben.");
        }
        if (name.length() > MAX_NAME_LAENGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Name ist zu lang – höchstens " + MAX_NAME_LAENGE + " Zeichen.");
        }
        return name;
    }

    private static ResponseStatusException schonVorhanden(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Eine Abteilung „" + name + "“ gibt es schon.");
    }

    static String nochZugeordnetMeldung(String name, long mitarbeiter, long arbeitsgaenge) {
        List<String> teile = new ArrayList<>();
        if (mitarbeiter > 0) {
            teile.add(mitarbeiter + " Mitarbeiter");
        }
        if (arbeitsgaenge > 0) {
            teile.add(arbeitsgaenge + (arbeitsgaenge == 1 ? " Arbeitsgang" : " Arbeitsgänge"));
        }
        boolean einzahl = teile.size() == 1 && mitarbeiter + arbeitsgaenge == 1;
        String hinweis;
        if (mitarbeiter > 0 && arbeitsgaenge > 0) {
            hinweis = "Bitte zuerst beim Mitarbeiter (auch bei ausgeschiedenen) eine andere Abteilung wählen und die Arbeitsgänge löschen.";
        } else if (mitarbeiter > 0) {
            hinweis = "Bitte zuerst beim Mitarbeiter (auch bei ausgeschiedenen) eine andere Abteilung wählen.";
        } else {
            hinweis = "Bitte zuerst die Arbeitsgänge unter Vorlagen & Stammdaten → Arbeitsgänge löschen.";
        }
        return "„" + name + "“ kann nicht gelöscht werden: " + String.join(" und ", teile)
                + (einzahl ? " ist" : " sind") + " noch zugeordnet. " + hinweis;
    }
}

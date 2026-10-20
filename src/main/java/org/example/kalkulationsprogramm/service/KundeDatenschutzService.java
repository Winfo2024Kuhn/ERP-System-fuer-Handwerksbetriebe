package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.KundeLoeschAktion;
import org.example.kalkulationsprogramm.domain.KundeLoeschprotokoll;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Kunde.KundeLoeschPruefungDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.KundeLoeschprotokollRepository;
import org.example.kalkulationsprogramm.repository.KundeNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.SprachnachrichtRepository;
import org.example.kalkulationsprogramm.repository.TelefonAnrufRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kunde aus dem System bringen, wenn er die Löschung seiner Daten verlangt (Issue #264).
 *
 * <ul>
 *   <li><b>Löschen:</b> nur, wenn keine Projekte, Anfragen, Angebote oder Rechnungen an ihm hängen.
 *       Notizen, E-Mail-Adressen und weitere Rufnummern gehen mit; Termine, Anrufe und
 *       Sprachnachrichten bleiben und verlieren nur den Bezug zum Kunden.</li>
 *   <li><b>Datenschutz-Sperre</b> (Art. 18 DSGVO): Hängen solche Unterlagen dran, müssen sie aufbewahrt
 *       werden (§ 147 AO, § 257 HGB). Dann wird alles gelöscht, was nicht aufbewahrt werden muss,
 *       und der Kunde gesperrt. Name, Anschrift und Kundennummer bleiben für die Unterlagen.</li>
 * </ul>
 *
 * <p>Beides landet im {@link KundeLoeschprotokoll} (Rechenschaftspflicht, Art. 5 Abs. 2 DSGVO).
 * Die endgültige Löschung nach Fristablauf ist bewusst noch nicht Teil dieser Klasse.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KundeDatenschutzService {

    /** Rechnungen und Buchungsbelege: 8 Jahre (§ 147 Abs. 3 AO seit 2025). */
    static final int JAHRE_BUCHUNGSBELEG = 8;
    /** Handels- und Geschäftsbriefe (Angebote, Auftragsbestätigungen, Mahnungen, Projekte): 6 Jahre. */
    static final int JAHRE_GESCHAEFTSBRIEF = 6;

    private static final Set<AusgangsGeschaeftsDokumentTyp> BUCHUNGSBELEGE = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.RECHNUNG,
            AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT,
            AusgangsGeschaeftsDokumentTyp.STORNO);

    static final String GESPERRT = "Dieser Kunde ist aus Datenschutzgründen gesperrt und kann nicht mehr geändert werden.";

    private final KundeRepository kundeRepository;
    private final ProjektRepository projektRepository;
    private final AnfrageRepository anfrageRepository;
    private final AusgangsGeschaeftsDokumentRepository dokumentRepository;
    private final KundeNotizRepository notizRepository;
    private final KontaktRufnummerRepository rufnummerRepository;
    private final KalenderEintragRepository kalenderRepository;
    private final TelefonAnrufRepository anrufRepository;
    private final SprachnachrichtRepository sprachnachrichtRepository;
    private final KundeLoeschprotokollRepository protokollRepository;

    /** Was hängt am Kunden, und bis wann müsste bei einer Sperre aufbewahrt werden? */
    @Transactional(readOnly = true)
    public KundeLoeschPruefungDto pruefe(Long kundeId) {
        return pruefe(unterlagen(kundeId));
    }

    /**
     * Löscht einen Kunden ohne aufbewahrungspflichtige Unterlagen.
     *
     * @throws ResponseStatusException 404 unbekannt, 409 gesperrt oder Unterlagen vorhanden
     */
    @Transactional
    public void loesche(Long kundeId, String benutzer) {
        Kunde kunde = ladeKunde(kundeId);
        if (kunde.istGesperrt()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dieser Kunde ist gesperrt. Er kann erst nach Ablauf der Aufbewahrungsfrist gelöscht werden.");
        }
        KundeLoeschPruefungDto pruefung = pruefe(unterlagen(kundeId));
        if (!pruefung.loeschbar()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Der Kunde kann nicht gelöscht werden, weil "
                    + "noch Unterlagen an ihm hängen (" + String.join(", ", pruefung.hindernisse()) + "). "
                    + "Nutze stattdessen „Daten löschen und sperren“.");
        }
        entferneNebendaten(kundeId);
        // Kunde.projekts ist leer (geprüft): das CascadeType.ALL dort kann nichts mitreißen.
        kundeRepository.delete(kunde);
        protokolliere(kunde, KundeLoeschAktion.GELOESCHT, null, null, benutzer);
        log.info("Kunde ID={} aus Datenschutzgründen gelöscht von '{}'", kundeId, benutzer);
    }

    /**
     * Löscht alles, was nicht aufbewahrt werden muss, und sperrt den Kunden bis zum Fristende.
     * Ein schon gesperrter Kunde bleibt unverändert.
     *
     * @throws ResponseStatusException 404 unbekannt, 409 wenn nichts aufbewahrt werden muss
     */
    @Transactional
    public void sperre(Long kundeId, String grund, String benutzer) {
        Kunde kunde = ladeKunde(kundeId);
        if (kunde.istGesperrt()) {
            return;
        }
        Unterlagen unterlagen = unterlagen(kundeId);
        KundeLoeschPruefungDto pruefung = pruefe(unterlagen);
        if (pruefung.loeschbar()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An diesem Kunden hängen keine Unterlagen, die aufbewahrt werden müssen. Bitte direkt löschen.");
        }
        long laufend = unterlagen.projekte().stream().filter(p -> !p.isAbgeschlossen()).count();
        if (laufend > 0) {
            // Eine spätere Schlussrechnung würde die Aufbewahrungsfrist verlängern.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bei diesem Kunden "
                    + (laufend == 1 ? "läuft noch 1 Projekt" : "laufen noch " + laufend + " Projekte")
                    + ". Bitte erst abschließen und abrechnen, dann die Daten löschen.");
        }

        kunde.setTelefon(null);
        kunde.setMobiltelefon(null);
        kunde.setAnsprechspartner(null);
        kunde.getKundenEmails().clear();
        // Die Adressen an Projekten und Anfragen ordnen sonst weiter neue Mails zu.
        unterlagen.projekte().forEach(p -> p.getKundenEmails().clear());
        unterlagen.anfragen().forEach(a -> a.getKundenEmails().clear());
        entferneNebendaten(kundeId);

        kunde.setGesperrtAm(LocalDateTime.now());
        kunde.setAufbewahrenBis(pruefung.aufbewahrenBis());
        kundeRepository.save(kunde);
        protokolliere(kunde, KundeLoeschAktion.GESPERRT, grund, pruefung.aufbewahrenBis(), benutzer);
        log.info("Kunde ID={} aus Datenschutzgründen gesperrt bis {} von '{}'", kundeId,
                pruefung.aufbewahrenBis(), benutzer);
    }

    /**
     * Für alles, was einen Kunden ändert oder Neues an ihn hängt (Stammdaten, Notiz, E-Mail,
     * Projekt, Anfrage, Dokument, Termin, Rufnummer): gesperrte Kunden ablehnen (409).
     * {@code null} = kein Kunde, nichts zu prüfen.
     */
    public static void pruefeNichtGesperrt(Kunde kunde) {
        if (kunde != null && kunde.istGesperrt()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, GESPERRT);
        }
    }

    /**
     * Ende der Aufbewahrungsfrist: Sie beginnt mit dem Ende des Jahres, in dem die Unterlage
     * entstanden ist (§ 147 Abs. 4 AO), und endet am 31.12. nach {@code jahre} Jahren.
     */
    static LocalDate fristende(LocalDate entstanden, int jahre) {
        return LocalDate.of(entstanden.getYear() + jahre, 12, 31);
    }

    private KundeLoeschPruefungDto pruefe(Unterlagen u) {
        List<String> hindernisse = new ArrayList<>();
        zaehle(hindernisse, u.projekte().size(), "Projekt", "Projekte");
        long laufend = u.projekte().stream().filter(p -> !p.isAbgeschlossen()).count();
        if (laufend > 0) {
            int letztes = hindernisse.size() - 1;
            hindernisse.set(letztes, hindernisse.get(letztes) + " (" + laufend + " noch nicht abgeschlossen)");
        }
        zaehle(hindernisse, u.anfragen().size(), "Anfrage", "Anfragen");
        zaehle(hindernisse, u.dokumente().size(), "Angebot/Rechnung", "Angebote/Rechnungen");
        if (hindernisse.isEmpty()) {
            return new KundeLoeschPruefungDto(true, List.of(), null);
        }
        LocalDate bis = null;
        for (AusgangsGeschaeftsDokument d : u.dokumente()) {
            LocalDate datum = d.getDatum() != null ? d.getDatum()
                    : d.getErstelltAm() != null ? d.getErstelltAm().toLocalDate() : LocalDate.now();
            bis = spaeter(bis, fristende(datum,
                    BUCHUNGSBELEGE.contains(d.getTyp()) ? JAHRE_BUCHUNGSBELEG : JAHRE_GESCHAEFTSBRIEF));
        }
        for (Projekt p : u.projekte()) {
            LocalDate datum = p.getAbschlussdatum() != null ? p.getAbschlussdatum()
                    : p.getAnlegedatum() != null ? p.getAnlegedatum() : LocalDate.now();
            bis = spaeter(bis, fristende(datum, JAHRE_GESCHAEFTSBRIEF));
        }
        for (Anfrage a : u.anfragen()) {
            bis = spaeter(bis, fristende(a.getAnlegedatum() != null ? a.getAnlegedatum() : LocalDate.now(),
                    JAHRE_GESCHAEFTSBRIEF));
        }
        return new KundeLoeschPruefungDto(false, List.copyOf(hindernisse), bis);
    }

    private static LocalDate spaeter(LocalDate a, LocalDate b) {
        return a == null || b.isAfter(a) ? b : a;
    }

    private static void zaehle(List<String> hindernisse, int anzahl, String einzahl, String mehrzahl) {
        if (anzahl > 0) {
            hindernisse.add(anzahl + " " + (anzahl == 1 ? einzahl : mehrzahl));
        }
    }

    /** Projekte, Anfragen und Ausgangsdokumente des Kunden – auch die nur über Projekt/Anfrage verknüpften. */
    private Unterlagen unterlagen(Long kundeId) {
        List<Projekt> projekte = projektRepository.findByKundenId_Id(kundeId);
        List<Anfrage> anfragen = anfrageRepository.findByKundeId(kundeId);
        Map<Long, AusgangsGeschaeftsDokument> dokumente = new LinkedHashMap<>();
        dokumentRepository.findByKundeIdOrderByDatumDesc(kundeId).forEach(d -> dokumente.put(d.getId(), d));
        if (!projekte.isEmpty()) {
            dokumentRepository.findByProjektIdIn(projekte.stream().map(Projekt::getId).toList())
                    .forEach(d -> dokumente.put(d.getId(), d));
        }
        if (!anfragen.isEmpty()) {
            dokumentRepository.findByAnfrageIdIn(anfragen.stream().map(Anfrage::getId).toList())
                    .forEach(d -> dokumente.put(d.getId(), d));
        }
        return new Unterlagen(projekte, anfragen, List.copyOf(dokumente.values()));
    }

    private record Unterlagen(List<Projekt> projekte, List<Anfrage> anfragen, List<AusgangsGeschaeftsDokument> dokumente) {
    }

    /** Notizen und weitere Rufnummern weg; Termine, Anrufe und Sprachnachrichten verlieren nur den Kundenbezug. */
    private void entferneNebendaten(Long kundeId) {
        notizRepository.loescheAlleVonKunde(kundeId);
        rufnummerRepository.loescheAlleVonKunde(kundeId);
        kalenderRepository.loeseVonKunde(kundeId);
        anrufRepository.loeseVonKunde(kundeId);
        sprachnachrichtRepository.loeseVonKunde(kundeId);
    }

    private Kunde ladeKunde(Long kundeId) {
        if (kundeId == null || kundeId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Kundennummer.");
        }
        return kundeRepository.findById(kundeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kunde wurde nicht gefunden."));
    }

    private void protokolliere(Kunde kunde, KundeLoeschAktion aktion, String grund, LocalDate aufbewahrenBis,
            String benutzer) {
        KundeLoeschprotokoll eintrag = new KundeLoeschprotokoll();
        eintrag.setKundeId(kunde.getId());
        eintrag.setKundennummer(kunde.getKundennummer());
        eintrag.setAktion(aktion);
        eintrag.setGrund(StringUtils.hasText(grund) ? grund.trim() : null);
        eintrag.setAufbewahrenBis(aufbewahrenBis);
        eintrag.setAusgefuehrtVon(benutzer);
        eintrag.setAusgefuehrtAm(LocalDateTime.now());
        protokollRepository.save(eintrag);
    }
}

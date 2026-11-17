package org.example.kalkulationsprogramm.service.dokumentstand;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentOffeneBearbeitung;
import org.example.kalkulationsprogramm.domain.AusgangsDokumentStand;
import org.example.kalkulationsprogramm.domain.AusgangsDokumentStandArt;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentOffeneBearbeitungRepository;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentStandRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Hält Stände eines Ausgangsdokuments fest und merkt sich, wer seit dem letzten Stand bearbeitet hat.
 *
 * <ul>
 *   <li>{@link #merkeBearbeitung}: nach jedem Speichern (PUT). Erzeugt keinen Stand.</li>
 *   <li>{@link #festhaltenNachExport}: nach dem Speichern des exportierten PDFs. Neuer Stand nur,
 *       wenn sich der Inhalt seit dem neuesten Stand geändert hat.</li>
 *   <li>{@link #festhalten}: auch vor dem Zurückholen eines älteren Stands
 *       ({@link AusgangsDokumentStandArt#VOR_WIEDERHERSTELLEN}).</li>
 * </ul>
 *
 * <p>Vor der Nummernvergabe wird die Dokumentzeile gesperrt ({@code sperreZumFesthalten}), damit zwei
 * gleichzeitige Exporte nicht dieselbe Nummer bekommen. Stände sind sonst unveränderlich; einzige
 * Ausnahme ist das Nachtragen des PDFs bei gleichem Inhalt ({@link #pdfNachtragen}).</p>
 *
 * <p>Ein neuer Stand übernimmt die offene Bearbeitung ({@code bearbeitetVonIds},
 * {@code wiederhergestelltAusNummer}) und leert sie. Die Änderungssätze gegenüber dem vorigen
 * Stand rechnet {@link DokumentStandVergleich} einmal beim Festhalten.</p>
 */
@Service
@RequiredArgsConstructor
public class DokumentStandService {

    private final AusgangsDokumentStandRepository standRepository;
    private final AusgangsDokumentOffeneBearbeitungRepository bearbeitungRepository;
    private final AusgangsGeschaeftsDokumentRepository dokumentRepository;
    private final Clock clock;

    /** Merkt sich, dass der Benutzer das Dokument seit dem letzten Stand bearbeitet hat. */
    @Transactional
    public void merkeBearbeitung(Long dokumentId, Long benutzerId) {
        if (dokumentId == null || benutzerId == null) {
            return;
        }
        offeneBearbeitung(dokumentId, benutzerId);
    }

    /** Merkt sich, dass der Benutzer Stand {@code nummer} zurückgeholt hat (zählt auch als Bearbeitung). */
    @Transactional
    public void merkeWiederherstellung(Long dokumentId, Long benutzerId, int nummer) {
        if (benutzerId == null) {
            return;
        }
        AusgangsDokumentOffeneBearbeitung eintrag = offeneBearbeitung(dokumentId, benutzerId);
        eintrag.setWiederhergestelltAusNummer(nummer);
        eintrag.setWiederhergestelltAm(LocalDateTime.now(clock));
        bearbeitungRepository.save(eintrag);
    }

    /**
     * Hält nach einem Export einen Stand fest, wenn sich der Inhalt geändert hat.
     *
     * @return der neue Stand, leer wenn der Inhalt dem neuesten Stand gleicht
     */
    @Transactional
    public Optional<AusgangsDokumentStand> festhaltenNachExport(Long dokumentId, Long benutzerId, String pdfDateiname) {
        AusgangsGeschaeftsDokument dokument = dokumentRepository.sperreZumFesthalten(dokumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dieses Dokument gibt es nicht."));
        return festhalten(dokument, AusgangsDokumentStandArt.EXPORT, benutzerId, pdfDateiname);
    }

    /**
     * Hält den aktuellen Inhalt des Dokuments als neuen Stand fest – außer er gleicht dem neuesten.
     *
     * @return der neue Stand, leer wenn der Inhalt dem neuesten Stand gleicht
     */
    @Transactional
    public Optional<AusgangsDokumentStand> festhalten(AusgangsGeschaeftsDokument dokument,
            AusgangsDokumentStandArt art, Long benutzerId, String pdfDateiname) {
        // Zeile sperren, bevor die nächste Nummer (höchste + 1) bestimmt wird.
        dokumentRepository.sperreZumFesthalten(dokument.getId());
        DokumentStandInhalt inhalt = DokumentStandInhalt.von(dokument);
        String fingerabdruck = inhalt.fingerabdruck();
        Optional<AusgangsDokumentStand> neuester = standRepository.findFirstByDokumentIdOrderByNummerDesc(dokument.getId());
        if (neuester.isPresent() && fingerabdruck.equals(neuester.get().getInhaltFingerabdruck())) {
            pdfNachtragen(neuester.get(), pdfDateiname);
            return Optional.empty();
        }
        List<AusgangsDokumentOffeneBearbeitung> offen =
                bearbeitungRepository.findByDokumentIdOrderBySeitAscIdAsc(dokument.getId());

        AusgangsDokumentStand stand = new AusgangsDokumentStand();
        stand.setDokumentId(dokument.getId());
        stand.setNummer(neuester.map(s -> s.getNummer() + 1).orElse(1));
        stand.setArt(art);
        stand.setFestgehaltenAm(LocalDateTime.now(clock));
        stand.setFestgehaltenVonId(benutzerId);
        stand.setBearbeitetVonIds(DokumentStandJson.schreibeIds(offen.stream()
                .map(AusgangsDokumentOffeneBearbeitung::getBenutzerId).distinct().toList()));
        stand.setWiederhergestelltAusNummer(offen.stream()
                .filter(b -> b.getWiederhergestelltAm() != null && b.getWiederhergestelltAusNummer() != null)
                .max(Comparator.comparing(AusgangsDokumentOffeneBearbeitung::getWiederhergestelltAm))
                .map(AusgangsDokumentOffeneBearbeitung::getWiederhergestelltAusNummer).orElse(null));
        stand.setAenderungen(DokumentStandJson.schreibeSaetze(DokumentStandVergleich.aenderungen(
                neuester.map(DokumentStandInhalt::von).orElse(null), inhalt)));
        stand.setInhaltFingerabdruck(fingerabdruck);
        stand.setPdfDateiname(pdfDateiname);
        uebernimmInhalt(dokument, stand);

        AusgangsDokumentStand gespeichert = standRepository.save(stand);
        bearbeitungRepository.loescheFuerDokument(dokument.getId());
        return Optional.of(gespeichert);
    }

    /**
     * Die einzige erlaubte Änderung an einem Stand: Wurde er ohne PDF festgehalten (Druck oder
     * PDF-Knopf bei Angebot/AB) und kommt dasselbe Dokument danach mit PDF (z. B. per E-Mail),
     * bekommt er dieses PDF. Ein vorhandenes PDF wird nie ersetzt.
     */
    private void pdfNachtragen(AusgangsDokumentStand stand, String pdfDateiname) {
        if (pdfDateiname != null && stand.getPdfDateiname() == null) {
            stand.setPdfDateiname(pdfDateiname);
            standRepository.save(stand);
        }
    }

    private static void uebernimmInhalt(AusgangsGeschaeftsDokument dokument, AusgangsDokumentStand stand) {
        stand.setBetreff(dokument.getBetreff());
        stand.setDatum(dokument.getDatum());
        stand.setBetragNetto(dokument.getBetragNetto());
        stand.setMwstSatz(dokument.getMwstSatz());
        stand.setZahlungszielTage(dokument.getZahlungszielTage());
        stand.setRechnungsadresseOverride(dokument.getRechnungsadresseOverride());
        stand.setPositionenJson(dokument.getPositionenJson());
        stand.setHtmlInhalt(dokument.getHtmlInhalt());
    }

    private AusgangsDokumentOffeneBearbeitung offeneBearbeitung(Long dokumentId, Long benutzerId) {
        return bearbeitungRepository.findByDokumentIdAndBenutzerId(dokumentId, benutzerId)
                .orElseGet(() -> {
                    AusgangsDokumentOffeneBearbeitung neu = new AusgangsDokumentOffeneBearbeitung();
                    neu.setDokumentId(Objects.requireNonNull(dokumentId));
                    neu.setBenutzerId(benutzerId);
                    neu.setSeit(LocalDateTime.now(clock));
                    return bearbeitungRepository.save(neu);
                });
    }
}

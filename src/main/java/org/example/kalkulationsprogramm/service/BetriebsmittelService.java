package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Betriebsmittel;
import org.example.kalkulationsprogramm.domain.BetriebsmittelPruefung;
import org.example.kalkulationsprogramm.repository.BetriebsmittelPruefungRepository;
import org.example.kalkulationsprogramm.repository.BetriebsmittelRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service für elektrische Betriebsmittel-Prüfungen (E-Check / BGV A3 / DGUV V3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BetriebsmittelService {

    private final BetriebsmittelRepository betriebsmittelRepository;
    private final BetriebsmittelPruefungRepository pruefungRepository;
    private final MitarbeiterRepository mitarbeiterRepository;

    @Value("${en1090.features.enabled:false}")
    private boolean en1090Enabled;

    // -----------------------------------------------------------------------
    // Betriebsmittel CRUD
    // -----------------------------------------------------------------------

    public List<Betriebsmittel> findAll() {
        return betriebsmittelRepository.findAll();
    }

    public Optional<Betriebsmittel> findById(Long id) {
        return betriebsmittelRepository.findById(id);
    }

    public Optional<Betriebsmittel> findByBarcode(String barcode) {
        return betriebsmittelRepository.findByBarcode(barcode);
    }

    @Transactional
    public Betriebsmittel save(Betriebsmittel betriebsmittel) {
        return betriebsmittelRepository.save(betriebsmittel);
    }

    @Transactional
    public void delete(Long id) {
        betriebsmittelRepository.deleteById(id);
    }

    // -----------------------------------------------------------------------
    // Prüfprotokoll
    // -----------------------------------------------------------------------

    public List<BetriebsmittelPruefung> findPruefungen(Long betriebsmittelId) {
        return pruefungRepository.findByBetriebsmittelIdOrderByPruefDatumDesc(betriebsmittelId);
    }

    public List<BetriebsmittelPruefung> findOffenePruefungen() {
        return pruefungRepository.findByVonElektrikerVerifiziertFalse();
    }

    @Transactional
    public BetriebsmittelPruefung pruefungErfassen(Long betriebsmittelId, Long prueferId,
                                                    BetriebsmittelPruefung pruefung) {
        Betriebsmittel bm = betriebsmittelRepository.findById(betriebsmittelId)
                .orElseThrow(() -> new IllegalArgumentException("Betriebsmittel nicht gefunden: " + betriebsmittelId));

        pruefung.setBetriebsmittel(bm);
        if (prueferId != null) {
            mitarbeiterRepository.findById(prueferId)
                    .ifPresent(pruefung::setPruefer);
        }
        if (pruefung.getPruefDatum() == null) {
            pruefung.setPruefDatum(LocalDate.now());
        }

        // Nächstes Prüfdatum automatisch berechnen
        LocalDate naechstes = pruefung.getPruefDatum().plusMonths(bm.getPruefIntervallMonate());
        pruefung.setNaechstesPruefDatum(naechstes);

        // Betriebsmittel aktualisieren
        bm.setNaechstesPruefDatum(naechstes);
        betriebsmittelRepository.save(bm);

        BetriebsmittelPruefung saved = pruefungRepository.save(pruefung);
        log.info("E-Check Protokoll erstellt: BM={}, Datum={}, Bestanden={}", betriebsmittelId, pruefung.getPruefDatum(), pruefung.isBestanden());
        return saved;
    }

    @Transactional
    public BetriebsmittelPruefung elektrikerVerifizieren(Long pruefungId) {
        BetriebsmittelPruefung p = pruefungRepository.findById(pruefungId)
                .orElseThrow(() -> new IllegalArgumentException("Prüfung nicht gefunden: " + pruefungId));
        p.setVonElektrikerVerifiziert(true);
        return pruefungRepository.save(p);
    }

    // -----------------------------------------------------------------------
    // Notification-Unterstützung
    // -----------------------------------------------------------------------

    public List<Betriebsmittel> findFaellig() {
        // Alle Betriebsmittel die heute oder früher geprüft werden müssten
        return betriebsmittelRepository.findFaelligBis(LocalDate.now());
    }
}

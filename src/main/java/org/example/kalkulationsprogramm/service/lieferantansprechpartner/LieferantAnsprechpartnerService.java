package org.example.kalkulationsprogramm.service.lieferantansprechpartner;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.LieferantAnsprechpartner;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAnsprechpartnerRequestDto;
import org.example.kalkulationsprogramm.mapper.LieferantAnsprechpartnerMapper;
import org.example.kalkulationsprogramm.repository.LieferantAnsprechpartnerRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.telefon.RufnummernZuordnungService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Pflege der Ansprechpartner eines Lieferanten (Reiter „Ansprechpartner“ der Lieferanten-Detailseite).
 *
 * <p>Nach jeder Änderung wird das Rufnummern-Verzeichnis der Anruferkennung verworfen
 * (nach dem Commit), damit neue Nummern sofort erkannt werden. Das FRITZ!Box-Telefonbuch
 * stößt der {@code TelefonbuchAenderungsBeobachter} von selbst an.</p>
 */
@Service
@RequiredArgsConstructor
public class LieferantAnsprechpartnerService {

    /** Obergrenze je Lieferant – für Liste und Anlegen. */
    public static final int MAX_JE_LIEFERANT = 200;

    private final LieferantAnsprechpartnerRepository repository;
    private final LieferantenRepository lieferantenRepository;
    private final RufnummernZuordnungService rufnummernZuordnung;

    @Transactional(readOnly = true)
    public List<LieferantAnsprechpartnerDto> liste(Long lieferantId) {
        if (!lieferantenRepository.existsById(lieferantId)) {
            throw lieferantFehlt();
        }
        return repository.findeFuerLieferant(lieferantId, PageRequest.of(0, MAX_JE_LIEFERANT)).stream()
                .map(LieferantAnsprechpartnerMapper::toDto)
                .toList();
    }

    @Transactional
    public LieferantAnsprechpartnerDto anlegen(Long lieferantId, LieferantAnsprechpartnerRequestDto eingabe) {
        LieferantAnsprechpartnerEingabe.Werte werte = LieferantAnsprechpartnerEingabe.pruefe(eingabe);
        Lieferanten lieferant = lieferantenRepository.findById(lieferantId).orElseThrow(this::lieferantFehlt);
        if (repository.countByLieferantId(lieferantId) >= MAX_JE_LIEFERANT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Höchstens " + MAX_JE_LIEFERANT + " Ansprechpartner je Lieferant.");
        }
        LieferantAnsprechpartner neu = new LieferantAnsprechpartner();
        neu.setLieferant(lieferant);
        LieferantAnsprechpartnerMapper.uebernehme(werte, neu);
        LieferantAnsprechpartner gespeichert = repository.save(neu);
        verwerfeRufnummernNachCommit();
        return LieferantAnsprechpartnerMapper.toDto(gespeichert);
    }

    @Transactional
    public LieferantAnsprechpartnerDto aendern(Long lieferantId, Long ansprechpartnerId,
                                               LieferantAnsprechpartnerRequestDto eingabe) {
        LieferantAnsprechpartnerEingabe.Werte werte = LieferantAnsprechpartnerEingabe.pruefe(eingabe);
        LieferantAnsprechpartner vorhanden = lade(lieferantId, ansprechpartnerId);
        LieferantAnsprechpartnerMapper.uebernehme(werte, vorhanden);
        verwerfeRufnummernNachCommit();
        return LieferantAnsprechpartnerMapper.toDto(vorhanden);
    }

    @Transactional
    public void loeschen(Long lieferantId, Long ansprechpartnerId) {
        repository.delete(lade(lieferantId, ansprechpartnerId));
        verwerfeRufnummernNachCommit();
    }

    /** Nur beim eigenen Lieferanten; sonst 404 – und zwar „Lieferant“, wenn es den gar nicht gibt. */
    private LieferantAnsprechpartner lade(Long lieferantId, Long ansprechpartnerId) {
        return repository.findByIdAndLieferantId(ansprechpartnerId, lieferantId).orElseThrow(() ->
                lieferantenRepository.existsById(lieferantId)
                        ? new ResponseStatusException(HttpStatus.NOT_FOUND, "Ansprechpartner nicht gefunden.")
                        : lieferantFehlt());
    }

    private ResponseStatusException lieferantFehlt() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Lieferant nicht gefunden.");
    }

    /**
     * Erst nach dem Commit verwerfen: Sonst könnte ein paralleler Anruf das Verzeichnis
     * noch mit dem alten Stand neu aufbauen und 60 Sekunden lang behalten.
     */
    private void verwerfeRufnummernNachCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rufnummernZuordnung.verwerfeCache();
                }
            });
        } else {
            rufnummernZuordnung.verwerfeCache();
        }
    }
}

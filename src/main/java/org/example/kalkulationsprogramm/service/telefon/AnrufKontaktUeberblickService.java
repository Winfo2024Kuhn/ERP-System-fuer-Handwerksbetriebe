package org.example.kalkulationsprogramm.service.telefon;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufKontaktUeberblickDto;
import org.example.kalkulationsprogramm.dto.Telefon.KontaktKurzDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.LongSupplier;

import static org.example.kalkulationsprogramm.dto.Telefon.AnrufKontaktUeberblickDto.MAX_EINTRAEGE;

/**
 * Überblick über den Anrufer für das Anruf-Fenster: Adresse, Ansprechpartner,
 * Projekte (mit Auftragsnummer) und Anfragen (mit Angebotsnummer).
 *
 * <p>Bewusst schlanker als die Kundenakte – kein E-Mail-Verlauf, keine
 * Dokumente, nur die nötigen Spalten und höchstens {@code MAX_EINTRAEGE}
 * je Liste –, damit das Fenster beim Klingeln sofort vollständig ist.</p>
 */
@Service
@RequiredArgsConstructor
public class AnrufKontaktUeberblickService {

    private static final PageRequest ERSTE_EINTRAEGE = PageRequest.of(0, MAX_EINTRAEGE);

    private final KundeRepository kundeRepository;
    private final LieferantenRepository lieferantenRepository;
    private final ProjektRepository projektRepository;
    private final AnfrageRepository anfrageRepository;
    private final AusgangsGeschaeftsDokumentRepository dokumentRepository;

    @Transactional(readOnly = true)
    public AnrufKontaktUeberblickDto ueberblick(Long kundeId, Long lieferantId) {
        if ((kundeId == null) == (lieferantId == null)) {
            throw new IllegalArgumentException("Bitte genau kundeId oder lieferantId angeben.");
        }
        return kundeId != null ? kunde(kundeId) : lieferant(lieferantId);
    }

    private AnrufKontaktUeberblickDto kunde(Long id) {
        Kunde k = kundeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Kunde nicht gefunden"));
        List<AnrufKontaktUeberblickDto.Projekt> projekte = projektRepository.findAnrufUeberblick(k.getId(), ERSTE_EINTRAEGE)
                .stream()
                .map(p -> new AnrufKontaktUeberblickDto.Projekt(p.getId(), p.getBauvorhaben(), p.getAuftragsnummer(),
                        p.getOrt(), p.isAbgeschlossen()))
                .toList();
        List<AnfrageRepository.AnrufUeberblickZeile> anfragen = anfrageRepository.findAnrufUeberblick(k.getId(), ERSTE_EINTRAEGE);
        Map<Long, String> angebotsnummern = angebotsnummern(anfragen);
        return new AnrufKontaktUeberblickDto(KontaktKurzDto.KUNDE, k.getId(), k.getName(), k.getKundennummer(),
                k.getAnsprechspartner(), k.getStrasse(), k.getPlz(), k.getOrt(),
                projekte, gesamt(projekte.size(), () -> projektRepository.countByKundenId_Id(k.getId())),
                anfragen.stream()
                        .map(a -> new AnrufKontaktUeberblickDto.Anfrage(a.getId(), a.getBauvorhaben(),
                                angebotsnummern.get(a.getId()), a.getProjektOrt(), a.isAbgeschlossen()))
                        .toList(),
                gesamt(anfragen.size(), () -> anfrageRepository.countByKundeId(k.getId())));
    }

    private AnrufKontaktUeberblickDto lieferant(Long id) {
        Lieferanten l = lieferantenRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lieferant nicht gefunden"));
        return new AnrufKontaktUeberblickDto(KontaktKurzDto.LIEFERANT, l.getId(), l.getLieferantenname(), null,
                l.getVertreter(), l.getStrasse(), l.getPlz(), l.getOrt(), List.of(), 0, List.of(), 0);
    }

    /** Nur wenn die Liste voll ist, kann es mehr geben – dann erst zählen. */
    private static long gesamt(int geladen, LongSupplier zaehle) {
        return geladen < MAX_EINTRAEGE ? geladen : zaehle.getAsLong();
    }

    /** Nummer des neuesten nicht stornierten Angebots je Anfrage. */
    private Map<Long, String> angebotsnummern(List<AnfrageRepository.AnrufUeberblickZeile> anfragen) {
        Map<Long, String> nummern = new HashMap<>();
        if (anfragen.isEmpty()) {
            return nummern;
        }
        List<Long> ids = anfragen.stream().map(AnfrageRepository.AnrufUeberblickZeile::getId).toList();
        for (var zeile : dokumentRepository.findDokumentnummernJeAnfrage(ids, AusgangsGeschaeftsDokumentTyp.ANGEBOT)) {
            if (zeile.getDokumentNummer() != null) {
                nummern.putIfAbsent(zeile.getAnfrageId(), zeile.getDokumentNummer());
            }
        }
        return nummern;
    }
}

package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentEingaben.fehler;
import static org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentEingaben.nichtGefunden;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerAnlegenDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerZaehler;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Ordnerbaum der Firmendokumente: anzeigen, anlegen, umbenennen, verschieben, leere Ordner löschen. */
@Service
@RequiredArgsConstructor
public class FirmendokumentOrdnerService {

    /** Obergrenze, damit die Baum-Abfrage nie unbegrenzt wächst. */
    static final int MAX_ORDNER = 2000;

    private static final String ORDNER_FEHLT = "Den Ordner gibt es nicht mehr.";
    private static final String NAME_FEHLT = "Bitte einen Namen für den Ordner eingeben.";

    private final FirmendokumentOrdnerRepository ordnerRepository;
    private final FirmendokumentRepository dokumentRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<FirmendokumentOrdnerDto> liste() {
        Map<Long, FirmendokumentOrdnerZaehler> zaehler = zaehlerJeOrdner();
        return ordnerRepository.findeSortiert(PageRequest.of(0, MAX_ORDNER)).stream()
                .map(o -> dto(o, zaehler.get(o.getId())))
                .toList();
    }

    @Transactional
    public FirmendokumentOrdnerDto anlegen(FirmendokumentOrdnerAnlegenDto eingabe) {
        String name = FirmendokumentEingaben.name(eingabe.name(), NAME_FEHLT);
        FirmendokumentOrdner parent = eingabe.parentId() == null ? null
                : ordnerRepository.findById(eingabe.parentId())
                        .orElseThrow(() -> fehler("Den übergeordneten Ordner gibt es nicht mehr."));
        pruefeNameFrei(parent, name, null);
        FirmendokumentOrdner ordner = new FirmendokumentOrdner();
        ordner.setName(name);
        ordner.setParent(parent);
        ordner.setAngelegtAm(LocalDateTime.now(clock));
        return dto(ordnerRepository.save(ordner), null);
    }

    @Transactional
    public FirmendokumentOrdnerDto umbenennen(Long id, String neuerName) {
        FirmendokumentOrdner ordner = finde(id);
        String name = FirmendokumentEingaben.name(neuerName, NAME_FEHLT);
        pruefeNameFrei(ordner.getParent(), name, id);
        ordner.setName(name);
        return dto(ordner, zaehlerJeOrdner().get(id));
    }

    @Transactional
    public FirmendokumentOrdnerDto verschieben(Long id, Long neuerParentId) {
        FirmendokumentOrdner ordner = finde(id);
        FirmendokumentOrdner ziel = neuerParentId == null ? null
                : ordnerRepository.findById(neuerParentId)
                        .orElseThrow(() -> fehler("Den Zielordner gibt es nicht mehr."));
        if (liegtInnerhalb(ziel, id)) {
            throw fehler("Ein Ordner kann nicht in sich selbst oder einen seiner Unterordner verschoben werden.");
        }
        pruefeNameFrei(ziel, ordner.getName(), id);
        ordner.setParent(ziel);
        return dto(ordner, zaehlerJeOrdner().get(id));
    }

    @Transactional
    public void loeschen(Long id) {
        FirmendokumentOrdner ordner = finde(id);
        if (ordnerRepository.existsByParentId(id) || dokumentRepository.existsByOrdnerId(id)) {
            throw fehler("Der Ordner ist nicht leer. Bitte zuerst die Dokumente und Unterordner "
                    + "verschieben oder löschen.");
        }
        ordnerRepository.delete(ordner);
    }

    private Map<Long, FirmendokumentOrdnerZaehler> zaehlerJeOrdner() {
        LocalDate heute = LocalDate.now(clock);
        return dokumentRepository.zaehleJeOrdner(heute, heute.plusDays(FirmendokumentService.BALD_FAELLIG_TAGE))
                .stream()
                .collect(Collectors.toMap(FirmendokumentOrdnerZaehler::ordnerId, Function.identity()));
    }

    /** Ordner per ID oder 404. */
    private FirmendokumentOrdner finde(Long id) {
        return ordnerRepository.findById(id).orElseThrow(() -> nichtGefunden(ORDNER_FEHLT));
    }

    /** Ist {@code ordner} der Ordner {@code id} selbst oder liegt er (beliebig tief) darunter? */
    private static boolean liegtInnerhalb(FirmendokumentOrdner ordner, Long id) {
        Set<Long> gesehen = new HashSet<>();
        for (FirmendokumentOrdner o = ordner; o != null && gesehen.add(o.getId()); o = o.getParent()) {
            if (o.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void pruefeNameFrei(FirmendokumentOrdner parent, String name, Long ausserId) {
        List<FirmendokumentOrdner> gleichnamig = parent == null
                ? ordnerRepository.findByParentIsNullAndNameIgnoreCase(name)
                : ordnerRepository.findByParentIdAndNameIgnoreCase(parent.getId(), name);
        if (gleichnamig.stream().anyMatch(o -> !o.getId().equals(ausserId))) {
            throw fehler("Hier gibt es schon einen Ordner „" + name + "“.");
        }
    }

    private static FirmendokumentOrdnerDto dto(FirmendokumentOrdner o, FirmendokumentOrdnerZaehler z) {
        Long parentId = o.getParent() == null ? null : o.getParent().getId();
        return new FirmendokumentOrdnerDto(o.getId(), o.getName(), parentId,
                z == null ? 0 : z.anzahl(),
                z == null || z.ueberfaellig() == null ? 0 : z.ueberfaellig(),
                z == null || z.baldFaellig() == null ? 0 : z.baldFaellig());
    }
}

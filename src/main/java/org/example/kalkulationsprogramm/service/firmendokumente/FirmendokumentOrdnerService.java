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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Ordnerbaum der Firmendokumente: anzeigen (nur sichtbare Ordner), anlegen, umbenennen, verschieben, leere Ordner
 * löschen. Schreiben braucht „Ändern“ ({@link FirmendokumentZugriff}); die Rechte-Einträge der obersten Ordner zieht
 * {@link FirmendokumentOrdnerRechteService} beim Verschieben und Löschen mit.
 */
@Service
@RequiredArgsConstructor
public class FirmendokumentOrdnerService {

    private static final String ORDNER_FEHLT = "Den Ordner gibt es nicht mehr.";
    private static final String NAME_FEHLT = "Bitte einen Namen für den Ordner eingeben.";

    private final FirmendokumentOrdnerRepository ordnerRepository;
    private final FirmendokumentRepository dokumentRepository;
    private final FirmendokumentZugriff zugriff;
    private final FirmendokumentOrdnerRechteService rechteService;
    private final Clock clock;

    /** Alle Ordner, die der Angemeldete sehen darf (höchstens {@link FirmendokumentZugriff#MAX_ORDNER}). */
    @Transactional(readOnly = true)
    public List<FirmendokumentOrdnerDto> liste(Authentication authentication) {
        FirmendokumentSichtbarkeit sichtbar = zugriff.sichtbarkeit(authentication);
        Map<Long, FirmendokumentOrdnerZaehler> zaehler = zaehlerJeOrdner();
        return sichtbar.sichtbareOrdner().stream()
                .map(o -> dto(o, zaehler.get(o.getId()), sichtbar.stufe(o.getId()).darfAendern(),
                        sichtbar.eingeschraenkt(o.getId())))
                .toList();
    }

    /** Oberste Ebene darf jeder (neu = nicht eingeschränkt); ein Unterordner braucht „Ändern“ am Elternordner. */
    @Transactional
    public FirmendokumentOrdnerDto anlegen(FirmendokumentOrdnerAnlegenDto eingabe, Authentication authentication) {
        String name = FirmendokumentEingaben.name(eingabe.name(), NAME_FEHLT);
        FirmendokumentOrdner parent = null;
        if (eingabe.parentId() != null) {
            String fehlt = "Den übergeordneten Ordner gibt es nicht mehr.";
            parent = ordnerRepository.findById(eingabe.parentId()).orElseThrow(() -> fehler(fehlt));
            zugriff.verlangeAendern(parent, authentication, () -> fehler(fehlt));
        }
        pruefeNameFrei(parent, name, null);
        if (ordnerRepository.count() >= FirmendokumentZugriff.MAX_ORDNER) {
            throw fehler("Es gibt schon " + FirmendokumentZugriff.MAX_ORDNER
                    + " Ordner. Bitte zuerst leere Ordner löschen.");
        }
        FirmendokumentOrdner ordner = new FirmendokumentOrdner();
        ordner.setName(name);
        ordner.setParent(parent);
        ordner.setRechteEingeschraenkt(false);
        ordner.setAngelegtAm(LocalDateTime.now(clock));
        return aenderbarDto(ordnerRepository.save(ordner), null);
    }

    @Transactional
    public FirmendokumentOrdnerDto umbenennen(Long id, String neuerName, Authentication authentication) {
        FirmendokumentOrdner ordner = aenderbarerOrdner(id, authentication);
        String name = FirmendokumentEingaben.name(neuerName, NAME_FEHLT);
        pruefeNameFrei(ordner.getParent(), name, id);
        ordner.setName(name);
        return aenderbarDto(ordner, zaehlerJeOrdner().get(id));
    }

    /**
     * Braucht „Ändern“ am Ordner und am Ziel (Ziel {@code null} = oberste Ebene, immer erlaubt). Aus einem
     * eingeschränkten Zweig unter einen anderen obersten Ordner nur als Admin
     * ({@link FirmendokumentZugriff#verlangeVerschieben}).
     */
    @Transactional
    public FirmendokumentOrdnerDto verschieben(Long id, Long neuerParentId, Authentication authentication) {
        FirmendokumentOrdner ordner = aenderbarerOrdner(id, authentication);
        FirmendokumentOrdner ziel = null;
        if (neuerParentId != null) {
            String fehlt = "Den Zielordner gibt es nicht mehr.";
            ziel = ordnerRepository.findById(neuerParentId).orElseThrow(() -> fehler(fehlt));
            zugriff.verlangeAendern(ziel, authentication, () -> fehler(fehlt));
        }
        zugriff.verlangeVerschieben(ordner, ziel, authentication);
        if (liegtInnerhalb(ziel, id)) {
            throw fehler("Ein Ordner kann nicht in sich selbst oder einen seiner Unterordner verschoben werden.");
        }
        pruefeNameFrei(ziel, ordner.getName(), id);
        rechteService.beimVerschieben(ordner, ziel);
        ordner.setParent(ziel);
        return aenderbarDto(ordner, zaehlerJeOrdner().get(id));
    }

    @Transactional
    public void loeschen(Long id, Authentication authentication) {
        FirmendokumentOrdner ordner = aenderbarerOrdner(id, authentication);
        if (ordnerRepository.existsByParentId(id) || dokumentRepository.existsByOrdnerId(id)) {
            throw fehler("Der Ordner ist nicht leer. Bitte zuerst die Dokumente und Unterordner "
                    + "verschieben oder löschen.");
        }
        rechteService.loescheEintraege(id);
        ordnerRepository.delete(ordner);
    }

    private Map<Long, FirmendokumentOrdnerZaehler> zaehlerJeOrdner() {
        LocalDate heute = LocalDate.now(clock);
        return dokumentRepository.zaehleJeOrdner(heute, heute.plusDays(FirmendokumentService.BALD_FAELLIG_TAGE))
                .stream()
                .collect(Collectors.toMap(FirmendokumentOrdnerZaehler::ordnerId, Function.identity()));
    }

    /** Ordner per ID, den der Angemeldete ändern darf; fehlt oder unsichtbar → 404, nur ansehen → 403. */
    private FirmendokumentOrdner aenderbarerOrdner(Long id, Authentication authentication) {
        FirmendokumentOrdner ordner = ordnerRepository.findById(id).orElseThrow(() -> nichtGefunden(ORDNER_FEHLT));
        zugriff.verlangeAendern(ordner, authentication, () -> nichtGefunden(ORDNER_FEHLT));
        return ordner;
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

    /** DTO nach einer erlaubten Änderung: darf ändern, Schloss nach dem obersten Ordner. */
    private static FirmendokumentOrdnerDto aenderbarDto(FirmendokumentOrdner o, FirmendokumentOrdnerZaehler z) {
        FirmendokumentOrdner wurzel = FirmendokumentZugriff.wurzel(o);
        return dto(o, z, true, wurzel != null && wurzel.isRechteEingeschraenkt());
    }

    private static FirmendokumentOrdnerDto dto(FirmendokumentOrdner o, FirmendokumentOrdnerZaehler z,
            boolean darfAendern, boolean eingeschraenkt) {
        Long parentId = o.getParent() == null ? null : o.getParent().getId();
        return new FirmendokumentOrdnerDto(o.getId(), o.getName(), parentId,
                z == null ? 0 : z.anzahl(),
                z == null || z.ueberfaellig() == null ? 0 : z.ueberfaellig(),
                z == null || z.baldFaellig() == null ? 0 : z.baldFaellig(),
                darfAendern, eingeschraenkt);
    }
}

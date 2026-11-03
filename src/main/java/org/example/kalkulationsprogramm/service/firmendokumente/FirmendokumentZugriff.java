package org.example.kalkulationsprogramm.service.firmendokumente;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdnerRecht;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRechtRepository;
import org.example.kalkulationsprogramm.repository.FirmendokumentOrdnerRepository;
import org.example.kalkulationsprogramm.service.AbteilungsRechtPruefung;
import org.example.kalkulationsprogramm.service.AbteilungsRechtPruefung.Zugehoerigkeit;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Entscheidet, wer einen Ordner der Firmendokumente sehen oder ändern darf. Rechte stehen nur am obersten Ordner
 * (Unterordner und Dokumente erben): nicht eingeschränkt → alle dürfen ändern; eingeschränkt → nur die
 * eingetragenen Abteilungen mit ihrer höchsten Stufe. Admins dürfen immer alles.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FirmendokumentZugriff {

    /** Obergrenze, damit die Baum-Abfrage nie unbegrenzt wächst. */
    static final int MAX_ORDNER = 2000;

    static final String VERBOTEN = "Du darfst in diesem Ordner nichts ändern.";
    static final String NUR_ADMIN_VERSCHIEBEN =
            "Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.";

    private final AbteilungsRechtPruefung rechtPruefung;
    private final FirmendokumentOrdnerRepository ordnerRepository;
    private final FirmendokumentOrdnerRechtRepository rechtRepository;

    /** Stufe des Angemeldeten an einem Ordner (über dessen obersten Ordner). */
    public FirmendokumentZugriffsStufe stufe(FirmendokumentOrdner ordner, Authentication authentication) {
        FirmendokumentOrdner wurzel = wurzel(ordner);
        if (wurzel != null && !wurzel.isRechteEingeschraenkt()) {
            return FirmendokumentZugriffsStufe.AENDERN;
        }
        Zugehoerigkeit wer = rechtPruefung.zugehoerigkeit(authentication);
        if (wer.admin()) {
            return FirmendokumentZugriffsStufe.AENDERN;
        }
        if (wurzel == null || wer.abteilungIds().isEmpty()) {
            return FirmendokumentZugriffsStufe.KEINE;
        }
        return hoechste(rechtRepository.findeFuerOrdnerUndAbteilungen(wurzel.getId(), wer.abteilungIds()));
    }

    /** Wie {@link #stufe}, wirft aber {@code wennUnsichtbar}, wenn der Angemeldete den Ordner nicht sehen darf. */
    public FirmendokumentZugriffsStufe verlangeAnsehen(FirmendokumentOrdner ordner, Authentication authentication,
            Supplier<? extends RuntimeException> wennUnsichtbar) {
        FirmendokumentZugriffsStufe stufe = stufe(ordner, authentication);
        if (!stufe.darfAnsehen()) {
            throw wennUnsichtbar.get();
        }
        return stufe;
    }

    /** Unsichtbar → {@code wennUnsichtbar}; nur ansehen → 403 „Du darfst in diesem Ordner nichts ändern.“ */
    public void verlangeAendern(FirmendokumentOrdner ordner, Authentication authentication,
            Supplier<? extends RuntimeException> wennUnsichtbar) {
        if (!verlangeAnsehen(ordner, authentication, wennUnsichtbar).darfAendern()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, VERBOTEN);
        }
    }

    /**
     * Verschieben darf eine Einschränkung nicht aufheben: Liegt {@code quelle} (der verschobene Ordner bzw. der Ordner
     * des verschobenen Dokuments) in einem eingeschränkten Zweig und käme es unter einen anderen obersten Ordner,
     * darf das nur ein Admin – sonst 403. Erlaubt bleibt das Verschieben innerhalb desselben Zweigs und das Hochholen
     * eines Unterordners auf die oberste Ebene ({@code ziel == null}; er übernimmt die Rechte). Das Ändern-Recht an
     * Quelle und Ziel prüft der Aufrufer vorher mit {@link #verlangeAendern}.
     */
    public void verlangeVerschieben(FirmendokumentOrdner quelle, FirmendokumentOrdner ziel,
            Authentication authentication) {
        FirmendokumentOrdner quellWurzel = wurzel(quelle);
        if (quellWurzel != null && !quellWurzel.isRechteEingeschraenkt() || ziel == null) {
            return;
        }
        FirmendokumentOrdner zielWurzel = wurzel(ziel);
        if (quellWurzel != null && zielWurzel != null && quellWurzel.getId().equals(zielWurzel.getId())) {
            return;
        }
        if (!rechtPruefung.zugehoerigkeit(authentication).admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, NUR_ADMIN_VERSCHIEBEN);
        }
    }

    /**
     * Sichtbarkeit des ganzen Baums (höchstens {@link #MAX_ORDNER} Ordner) in einem Rutsch: eine Ordner-Abfrage und
     * höchstens eine Rechte-Abfrage. Ist nichts eingeschränkt, werden Profil und Rechte gar nicht erst geladen.
     */
    public FirmendokumentSichtbarkeit sichtbarkeit(Authentication authentication) {
        List<FirmendokumentOrdner> ordner = ordnerRepository.findeSortiert(PageRequest.of(0, MAX_ORDNER));
        boolean vollstaendig = ordner.size() < MAX_ORDNER;
        if (vollstaendig && ordner.stream().noneMatch(FirmendokumentOrdner::isRechteEingeschraenkt)) {
            return FirmendokumentSichtbarkeit.alles(ordner);
        }
        Zugehoerigkeit wer = rechtPruefung.zugehoerigkeit(authentication);
        Map<Long, FirmendokumentZugriffsStufe> stufeJeWurzel = new HashMap<>();
        if (!wer.admin() && !wer.abteilungIds().isEmpty()) {
            for (FirmendokumentOrdnerRecht recht : rechtRepository.findeFuerAbteilungen(wer.abteilungIds())) {
                stufeJeWurzel.merge(recht.getOrdner().getId(), recht.getStufe(), FirmendokumentZugriffsStufe::hoechste);
            }
        }
        return FirmendokumentSichtbarkeit.berechne(ordner, wer.admin(), stufeJeWurzel, vollstaendig);
    }

    /** Oberster Ordner über die Elternkette; {@code null} bei kaputten (zyklischen) Daten. */
    static FirmendokumentOrdner wurzel(FirmendokumentOrdner ordner) {
        Set<Long> gesehen = new HashSet<>();
        for (FirmendokumentOrdner o = ordner; o != null && gesehen.add(o.getId()); o = o.getParent()) {
            if (o.getParent() == null) {
                return o;
            }
        }
        return null;
    }

    private static FirmendokumentZugriffsStufe hoechste(List<FirmendokumentOrdnerRecht> rechte) {
        FirmendokumentZugriffsStufe stufe = FirmendokumentZugriffsStufe.KEINE;
        for (FirmendokumentOrdnerRecht recht : rechte) {
            stufe = FirmendokumentZugriffsStufe.hoechste(stufe, recht.getStufe());
        }
        return stufe;
    }
}

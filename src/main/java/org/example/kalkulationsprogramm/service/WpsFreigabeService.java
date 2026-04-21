package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.En1090Rolle;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Wps;
import org.example.kalkulationsprogramm.domain.WpsFreigabe;
import org.example.kalkulationsprogramm.repository.WpsFreigabeRepository;
import org.example.kalkulationsprogramm.repository.WpsRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Verwaltet digitale SAP-Freigaben einer {@link Wps}. Nur Mitarbeiter mit der
 * EN-1090-Rolle „Schweißaufsicht (SAP)" oder „Stellvertreter SAP" dürfen
 * signieren (siehe V219-Seed). Der Schweißer selbst signiert nach EN ISO 14731
 * keine Dokumente.
 */
@Service
@RequiredArgsConstructor
public class WpsFreigabeService {

    /** Kurztexte aus {@code V219__en1090_rollen_qualifikationen.sql}. */
    static final Set<String> SAP_ROLLE_KURZTEXTE = Set.of(
            "Schweißaufsicht (SAP)",
            "Stellvertreter SAP");

    private final WpsRepository wpsRepository;
    private final WpsFreigabeRepository freigabeRepository;

    /**
     * Prüft, ob ein Mitarbeiter aktuell als Schweißaufsicht signieren darf.
     */
    public boolean kannAlsSapSignieren(Mitarbeiter m) {
        if (m == null || m.getEn1090Rollen() == null) return false;
        return m.getEn1090Rollen().stream()
                .map(En1090Rolle::getKurztext)
                .filter(java.util.Objects::nonNull)
                .anyMatch(SAP_ROLLE_KURZTEXTE::contains);
    }

    /**
     * Erstellt eine neue SAP-Freigabe. Wirft {@link FreigabeException} wenn
     * Rollen-Voraussetzungen fehlen, die WPS nicht existiert oder der
     * Mitarbeiter schon freigegeben hat.
     */
    public WpsFreigabe freigeben(Long wpsId, Mitarbeiter mitarbeiter) {
        if (mitarbeiter == null) {
            throw new FreigabeException("Kein Mitarbeiter bestimmt – bitte neu anmelden.");
        }
        if (!kannAlsSapSignieren(mitarbeiter)) {
            throw new FreigabeException(
                    "Nur eine Schweißaufsichtsperson kann diese Freigabe erteilen.");
        }

        Wps wps = wpsRepository.findById(wpsId)
                .orElseThrow(() -> new FreigabeException("Schweißanweisung nicht gefunden."));

        boolean schonFreigegeben = wps.getFreigaben().stream()
                .anyMatch(f -> f.getMitarbeiter() != null
                        && f.getMitarbeiter().getId() != null
                        && f.getMitarbeiter().getId().equals(mitarbeiter.getId()));
        if (schonFreigegeben) {
            throw new FreigabeException(
                    "Du hast diese Schweißanweisung bereits freigegeben.");
        }

        WpsFreigabe freigabe = new WpsFreigabe();
        freigabe.setWps(wps);
        freigabe.setMitarbeiter(mitarbeiter);
        freigabe.setMitarbeiterName(vorzeigeName(mitarbeiter));
        freigabe.setZeitpunkt(LocalDateTime.now());
        return freigabeRepository.save(freigabe);
    }

    /**
     * Zieht eine eigene Freigabe zurück. Fremde Freigaben sind unantastbar –
     * das entspricht dem Audit-Trail-Prinzip einer echten Unterschrift.
     */
    public void zuruecknehmen(Long freigabeId, Mitarbeiter mitarbeiter) {
        if (mitarbeiter == null || mitarbeiter.getId() == null) {
            throw new FreigabeException("Kein Mitarbeiter bestimmt.");
        }
        WpsFreigabe f = freigabeRepository.findById(freigabeId)
                .orElseThrow(() -> new FreigabeException("Freigabe nicht gefunden."));
        if (f.getMitarbeiter() == null
                || f.getMitarbeiter().getId() == null
                || !f.getMitarbeiter().getId().equals(mitarbeiter.getId())) {
            throw new FreigabeException(
                    "Nur der Urheber kann eine Freigabe zurückziehen.");
        }
        freigabeRepository.delete(f);
    }

    /**
     * Entfernt alle Freigaben einer WPS – aufzurufen bei jeder inhaltlichen
     * Änderung, damit eine signierte WPS nicht nachträglich modifiziert
     * werden kann, ohne dass die SAP neu freigibt.
     */
    public void resetAlleFreigaben(Wps wps) {
        if (wps == null || wps.getFreigaben() == null || wps.getFreigaben().isEmpty()) return;
        wps.getFreigaben().clear();
    }

    private static String vorzeigeName(Mitarbeiter m) {
        String v = Optional.ofNullable(m.getVorname()).orElse("").trim();
        String n = Optional.ofNullable(m.getNachname()).orElse("").trim();
        String full = (v + " " + n).trim();
        return full.isEmpty() ? ("Mitarbeiter #" + m.getId()) : full;
    }

    /** Vom Service geworfene Fachfehler – Controller mappt sie auf 400. */
    public static class FreigabeException extends RuntimeException {
        public FreigabeException(String msg) { super(msg); }
    }
}

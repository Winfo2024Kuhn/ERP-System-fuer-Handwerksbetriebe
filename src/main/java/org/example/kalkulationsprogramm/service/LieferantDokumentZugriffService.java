package org.example.kalkulationsprogramm.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Ermittelt serverseitig, welche Lieferanten-Dokumenttypen der aufrufende
 * Benutzer sehen darf (Abteilungsrechte aus {@code abteilung_dokument_berechtigung}).
 *
 * <p>Der Aufrufer wird nie aus Client-Angaben wie einer Mitarbeiter-ID abgeleitet:
 * <ol>
 *   <li>Mobile-PWA: gültiges Mitarbeiter-Login-Token ({@code ?token=}) — die
 *       Zeiterfassungs-Chain erlaubt auch Aufrufe ohne PC-Session.</li>
 *   <li>PC: Spring-Security-Principal der Session. {@code ROLE_ADMIN} sieht alle
 *       Typen; alle anderen die Typen ihres verknüpften Mitarbeiters.</li>
 * </ol>
 * Wer weder ein gültiges Token noch eine Session hat, ist nicht angemeldet.
 * Wer angemeldet, aber keinem Mitarbeiter zugeordnet ist (und kein Admin), sieht nichts.
 */
@Service
@RequiredArgsConstructor
public class LieferantDokumentZugriffService {

    private final BelegService belegService;
    private final LieferantDokumentService dokumentService;

    /**
     * @return die sichtbaren Dokumenttypen (ggf. leer), oder {@link Optional#empty()}
     *         wenn der Aufrufer nicht angemeldet ist (Controller antwortet dann mit 401).
     */
    public Optional<Set<LieferantDokumentTyp>> sichtbareTypen(String token, Authentication auth) {
        Mitarbeiter perToken = belegService.findByToken(token);
        if (perToken != null) {
            return Optional.of(typenVon(perToken));
        }
        if (auth == null || !(auth.getPrincipal() instanceof FrontendUserPrincipal principal)) {
            return Optional.empty();
        }
        if (principal.hasRole(FrontendUserRole.ADMIN)) {
            return Optional.of(EnumSet.allOf(LieferantDokumentTyp.class));
        }
        Mitarbeiter perSession = belegService.findCaller(null, auth);
        return Optional.of(perSession == null ? keine() : typenVon(perSession));
    }

    /**
     * Beschränkt die Dokumente einer Lieferanten-Detailantwort auf die sichtbaren Typen.
     * Wer nicht zuzuordnen ist, bekommt keine Dokumente — die Stammdaten bleiben unberührt.
     */
    public void beschraenkeDokumente(LieferantDetailDto detail, String token, Authentication auth) {
        Set<LieferantDokumentTyp> sichtbar = sichtbareTypen(token, auth).orElseGet(this::keine);
        List<LieferantDokumentDto.Response> dokumente = detail.getDokumente();
        if (dokumente == null) {
            // nurStammdaten: nur der Zähler für den Reiter ist gefüllt, aber ungefiltert.
            if (!sichtbar.equals(EnumSet.allOf(LieferantDokumentTyp.class))) {
                detail.setDokumenteAnzahl(dokumentService.zaehleDokumente(detail.getId(), sichtbar));
            }
            return;
        }
        List<LieferantDokumentDto.Response> erlaubt = beschraenkeDokumente(dokumente, sichtbar);
        detail.setDokumente(erlaubt);
        detail.setDokumenteAnzahl((long) erlaubt.size());
    }

    /** Filtert auch Dokumentverknüpfungen, damit verbotene Typen keine Metadaten preisgeben. */
    public List<LieferantDokumentDto.Response> beschraenkeDokumente(
            List<LieferantDokumentDto.Response> dokumente, Set<LieferantDokumentTyp> sichtbar) {
        return dokumente.stream()
                .filter(d -> d.getTyp() != null && sichtbar.contains(d.getTyp()))
                .map(d -> {
                    if (d.getVerknuepfteDokumente() != null) {
                        d.setVerknuepfteDokumente(d.getVerknuepfteDokumente().stream()
                                .filter(ref -> ref.getTyp() != null && sichtbar.contains(ref.getTyp()))
                                .toList());
                    }
                    return d;
                })
                .toList();
    }

    private Set<LieferantDokumentTyp> typenVon(Mitarbeiter mitarbeiter) {
        var sichtbar = dokumentService.getBerechtigungen(mitarbeiter.getId()).getSichtbareTypen();
        return sichtbar.isEmpty() ? keine() : EnumSet.copyOf(sichtbar);
    }

    /** EnumSet statt Set.of(): {@code contains(null)} wirft dort keine NPE. */
    private Set<LieferantDokumentTyp> keine() {
        return EnumSet.noneOf(LieferantDokumentTyp.class);
    }
}

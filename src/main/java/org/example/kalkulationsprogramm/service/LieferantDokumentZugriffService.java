package org.example.kalkulationsprogramm.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.MobilePrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAttachmentViewDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantKommunikationDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantStatistikDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailFileDto;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Ermittelt serverseitig, welche Lieferanten-Dokumenttypen der aufrufende
 * Benutzer sehen darf (Abteilungsrechte aus {@code abteilung_dokument_berechtigung}).
 *
 * <p>Der Aufrufer wird nie aus Client-Angaben wie einer Mitarbeiter-ID abgeleitet:
 * <ol>
 *   <li>Mobile-PWA: gültiges Mitarbeiter-Login-Token ({@code ?token=} oder vom
 *       Mobile-Filter geprüfter {@code X-Auth-Token} als {@code MobilePrincipal}).</li>
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
        if (auth != null && auth.getPrincipal() instanceof MobilePrincipal) {
            // Token im Header, vom Mobile-Filter geprüft: nie Admin-Rechte, nur die des Mitarbeiters.
            Mitarbeiter perMobile = belegService.findCaller(null, auth);
            return Optional.of(perMobile == null ? keine() : typenVon(perMobile));
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
     * Beschränkt eine Lieferanten-Detailantwort auf die sichtbaren Dokumenttypen: Dokumente,
     * E-Mail-Anhänge (Rechnung per Mail) und Kennzahlen aus Dokumenten. Wer nicht zuzuordnen
     * ist, bekommt nichts davon — die Stammdaten bleiben unberührt.
     */
    public void beschraenkeDokumente(LieferantDetailDto detail, String token, Authentication auth) {
        Set<LieferantDokumentTyp> sichtbar = sichtbareTypen(token, auth).orElseGet(this::keine);
        beschraenkeStatistik(detail.getStatistik(), sichtbar);
        beschraenkeKommunikation(detail.getKommunikation(), sichtbar);
        beschraenkeEmailVerlauf(detail.getEmails(), sichtbar);
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
                .map(d -> beschraenkeDokument(d, sichtbar))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Einzelnes Dokument: leer, wenn der Typ nicht sichtbar ist (Controller antwortet dann 404,
     * damit die Existenz nicht verraten wird). Sonst mit gefilterten Verknüpfungen.
     */
    public Optional<LieferantDokumentDto.Response> beschraenkeDokument(
            LieferantDokumentDto.Response dokument, Set<LieferantDokumentTyp> sichtbar) {
        if (dokument == null || dokument.getTyp() == null || !sichtbar.contains(dokument.getTyp())) {
            return Optional.empty();
        }
        if (dokument.getVerknuepfteDokumente() != null) {
            dokument.setVerknuepfteDokumente(dokument.getVerknuepfteDokumente().stream()
                    .filter(ref -> ref.getTyp() != null && sichtbar.contains(ref.getTyp()))
                    .toList());
        }
        return Optional.of(dokument);
    }

    /**
     * Ist das Dokument mit dieser ID für den Aufrufer sichtbar? Unbekannte IDs gelten als
     * nicht sichtbar, damit schreibende Endpunkte nicht verraten, ob die ID existiert.
     */
    public boolean istSichtbar(Long dokumentId, Set<LieferantDokumentTyp> sichtbar) {
        if (dokumentId == null) {
            return false;
        }
        var dokument = dokumentService.findById(dokumentId);
        return dokument != null && sichtbar.contains(dokument.getTyp());
    }

    /**
     * Darf der Aufrufer diesen E-Mail-Anhang öffnen? Nein, wenn er als Lieferanten-Dokument
     * eines nicht sichtbaren Typs abgelegt ist (z. B. eine Rechnung, die per Mail kam).
     * Anhänge ohne Dokument sind frei.
     */
    public boolean istAnhangSichtbar(Long anhangId, Set<LieferantDokumentTyp> sichtbar) {
        return anhangId == null || gesperrteAnhaenge(Set.of(anhangId), sichtbar).isEmpty();
    }

    /**
     * Blendet Kennzahlen aus, die aus nicht sichtbaren Dokumenttypen stammen:
     * Gesamtkosten (Rechnungen) sowie Bestellungen und Lieferzeit (Auftragsbestätigungen).
     */
    public void beschraenkeStatistik(LieferantStatistikDto statistik, Set<LieferantDokumentTyp> sichtbar) {
        if (statistik == null) {
            return;
        }
        if (!sichtbar.contains(LieferantDokumentTyp.RECHNUNG)) {
            statistik.setGesamtKosten(null);
        }
        if (!sichtbar.contains(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG)) {
            statistik.setBestellungAnzahl(0);
            statistik.setLieferzeit(null);
        }
    }

    /** E-Mail-Verlauf: Anhänge, die als Dokument eines nicht sichtbaren Typs abgelegt sind, entfallen. */
    public void beschraenkeEmailVerlauf(List<ProjektEmailDto> emails, Set<LieferantDokumentTyp> sichtbar) {
        if (emails == null) {
            return;
        }
        Set<Long> gesperrt = gesperrteAnhaenge(emails.stream()
                .filter(e -> e.getAttachments() != null)
                .flatMap(e -> e.getAttachments().stream())
                .map(ProjektEmailFileDto::getId)
                .collect(Collectors.toSet()), sichtbar);
        if (gesperrt.isEmpty()) {
            return;
        }
        emails.stream().filter(e -> e.getAttachments() != null).forEach(e -> e.setAttachments(
                e.getAttachments().stream().filter(a -> !gesperrt.contains(a.getId())).toList()));
    }

    /** Kommunikation der Detailantwort: wie {@link #beschraenkeEmailVerlauf}. */
    public void beschraenkeKommunikation(List<LieferantKommunikationDto> kommunikation,
            Set<LieferantDokumentTyp> sichtbar) {
        if (kommunikation == null) {
            return;
        }
        Set<Long> gesperrt = gesperrteAnhaenge(kommunikation.stream()
                .filter(k -> k.getAttachments() != null)
                .flatMap(k -> k.getAttachments().stream())
                .map(LieferantAttachmentViewDto::getId)
                .collect(Collectors.toSet()), sichtbar);
        if (gesperrt.isEmpty()) {
            return;
        }
        kommunikation.stream().filter(k -> k.getAttachments() != null).forEach(k -> k.setAttachments(
                k.getAttachments().stream().filter(a -> !gesperrt.contains(a.getId())).toList()));
    }

    /** Anhänge, die als Dokument eines nicht sichtbaren Typs abgelegt sind. */
    private Set<Long> gesperrteAnhaenge(Collection<Long> anhangIds, Set<LieferantDokumentTyp> sichtbar) {
        Set<Long> ids = anhangIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        EnumSet<LieferantDokumentTyp> gesperrteTypen = EnumSet.allOf(LieferantDokumentTyp.class);
        gesperrteTypen.removeAll(sichtbar);
        if (ids.isEmpty() || gesperrteTypen.isEmpty()) {
            return Set.of();
        }
        return dokumentService.findAnhangIdsMitTyp(ids, gesperrteTypen);
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

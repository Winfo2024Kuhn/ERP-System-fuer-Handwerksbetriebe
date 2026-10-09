package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentPosition;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.PositionsArt;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.dto.Bestellung.DokumentPositionenDto;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ordnet Lieferanten-Rechnungen Projekten und Kostenstellen zu – nach
 * Prozent, nach Betrag oder nach Positionen.
 *
 * <p>Alle drei Wege enden in {@link LieferantDokumentProjektAnteil}: Projekt-
 * kosten, Kostenstellen-Auswertung und Nachkalkulation kennen nur diese
 * Anteile. Die Positionsaufteilung merkt sich zusätzlich an jeder Position ihr
 * Ziel, damit der Dialog sie beim nächsten Öffnen wieder zeigt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LieferantDokumentZuordnungService {

    /** Größte zulässige Kostenstreckung in Jahren. */
    static final int MAX_STRECKUNG_JAHRE = 20;
    /** Ab dieser Abweichung zwischen Positionssumme und Belegnetto zeigt der Dialog einen Hinweis. */
    static final BigDecimal ABWEICHUNG_EURO = BigDecimal.ONE;
    static final BigDecimal ABWEICHUNG_ANTEIL = new BigDecimal("0.01");

    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final LieferantDokumentProjektAnteilRepository projektAnteilRepository;
    private final LieferantDokumentPositionRepository positionRepository;
    private final ProjektRepository projektRepository;
    private final KostenstelleRepository kostenstelleRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final FrontendUserProfileRepository frontendUserProfileRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.mail-attachment-dir}")
    private String attachmentDir;

    /**
     * Ein Anteil nach Prozent ODER Betrag.
     *
     * @param streckungJahre nur für Kostenstellen: Kosten über mehrere Jahre verteilen
     */
    public record Anteil(Long projektId, Long kostenstelleId, BigDecimal betrag, BigDecimal prozentanteil,
            String beschreibung, Integer streckungJahre) {
    }

    // ------------------------------------------------------------ Prozent / Betrag

    /**
     * Speichert eine Aufteilung nach Prozent oder Betrag. Eine vorherige
     * Positionsaufteilung wird dabei aufgehoben.
     *
     * @return Anzahl gespeicherter Anteile
     * @throws NoSuchElementException   wenn das Geschäftsdokument fehlt
     * @throws IllegalArgumentException bei ungültigen Anteilen
     */
    @Transactional
    public int speichereAnteile(Long geschaeftsdokumentId, List<Anteil> anteile, FrontendUserProfile zugeordnetVon) {
        LieferantGeschaeftsdokument gd = ladeGeschaeftsdokument(geschaeftsdokumentId);
        int anzahl = schreibeAnteile(gd, anteile, zugeordnetVon);
        positionRepository.entferneZuordnungen(gd.getId());
        return anzahl;
    }

    /** Hebt jede Zuordnung auf (auch die Lagerbestellung und die Positionsaufteilung). */
    @Transactional
    public void hebeZuordnungAuf(Long geschaeftsdokumentId) {
        LieferantGeschaeftsdokument gd = ladeGeschaeftsdokument(geschaeftsdokumentId);
        if (gd.getDokument() != null) {
            projektAnteilRepository.deleteAll(projektAnteilRepository.findByDokumentId(gd.getDokument().getId()));
        }
        positionRepository.entferneZuordnungen(gd.getId());
        gd.setLagerbestellung(false);
        geschaeftsdokumentRepository.save(gd);
    }

    // ------------------------------------------------------------ Positionen

    /** Positionen eines Dokuments mit aktuellem Ziel und Plausibilitätsprüfung. */
    @Transactional(readOnly = true)
    public DokumentPositionenDto.Uebersicht positionsUebersicht(Long geschaeftsdokumentId) {
        LieferantGeschaeftsdokument gd = ladeGeschaeftsdokument(geschaeftsdokumentId);
        List<LieferantDokumentPosition> positionen = positionRepository.findByGeschaeftsdokumentId(gd.getId());

        BigDecimal summe = positionen.stream()
                .map(LieferantDokumentPosition::getGesamtpreisNetto)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal abweichung = gd.getBetragNetto() != null && !positionen.isEmpty()
                ? gd.getBetragNetto().subtract(summe)
                : null;
        boolean nachPositionen = positionen.stream()
                .anyMatch(p -> p.getProjekt() != null || p.getKostenstelle() != null);
        var typ = gd.getDokument() != null ? gd.getDokument().getTyp() : null;

        return new DokumentPositionenDto.Uebersicht(
                gd.getId(),
                typ != null ? typ.name() : null,
                LieferantDokumentPositionService.hatPositionen(typ),
                gd.getBetragNetto(),
                gd.getBetragBrutto(),
                summe,
                abweichung,
                istAuffaellig(abweichung, gd.getBetragNetto()),
                nachPositionen,
                positionen.stream().map(LieferantDokumentZuordnungService::alsDto).toList());
    }

    /** Rechnet eine Positionsaufteilung durch, ohne zu speichern. */
    @Transactional(readOnly = true)
    public DokumentPositionenDto.Vorschau vorschau(Long geschaeftsdokumentId,
            List<DokumentPositionenDto.PositionsZiel> zuordnung) {
        LieferantGeschaeftsdokument gd = ladeGeschaeftsdokument(geschaeftsdokumentId);
        List<LieferantDokumentPosition> positionen = positionRepository.findByGeschaeftsdokumentId(gd.getId());
        PositionsAufteilung.Ergebnis ergebnis = berechne(gd, positionen, zielJePosition(positionen, zuordnung));
        String hinweis = pruefe(ergebnis, positionen);
        return new DokumentPositionenDto.Vorschau(
                ergebnis.ziele().stream().map(LieferantDokumentZuordnungService::alsDto).toList(),
                ergebnis.nichtZugeordnet(),
                ergebnis.warenwert(),
                ergebnis.nebenkosten(),
                ergebnis.abweichung(),
                hinweis == null,
                hinweis);
    }

    /**
     * Speichert eine Aufteilung nach Positionen: merkt sich das Ziel jeder
     * Warenposition und legt daraus die Projekt-/Kostenstellen-Anteile an.
     *
     * @return Anzahl gespeicherter Anteile
     * @throws IllegalArgumentException wenn nicht alle Warenpositionen ein Ziel haben
     *                                  oder die Beträge nicht aufgehen
     */
    @Transactional
    public int speichereNachPositionen(Long geschaeftsdokumentId, DokumentPositionenDto.AufteilungRequest request,
            FrontendUserProfile zugeordnetVon) {
        LieferantGeschaeftsdokument gd = ladeGeschaeftsdokument(geschaeftsdokumentId);
        List<LieferantDokumentPosition> positionen = positionRepository.findByGeschaeftsdokumentId(gd.getId());
        Map<Long, PositionsAufteilung.Ziel> ziele = zielJePosition(positionen, request.positionen());
        PositionsAufteilung.Ergebnis ergebnis = berechne(gd, positionen, ziele);
        String fehler = pruefe(ergebnis, positionen);
        if (fehler != null) {
            throw new IllegalArgumentException(fehler);
        }

        Map<Long, Projekt> projekte = new HashMap<>();
        Map<Long, Kostenstelle> kostenstellen = new HashMap<>();
        for (LieferantDokumentPosition p : positionen) {
            PositionsAufteilung.Ziel ziel = p.getPositionsArt() == PositionsArt.WARE ? ziele.get(p.getId()) : null;
            p.setProjekt(ziel != null && ziel.projektId() != null
                    ? projekte.computeIfAbsent(ziel.projektId(), this::ladeProjekt)
                    : null);
            p.setKostenstelle(ziel != null && ziel.kostenstelleId() != null
                    ? kostenstellen.computeIfAbsent(ziel.kostenstelleId(), this::ladeKostenstelle)
                    : null);
        }
        positionRepository.saveAll(positionen);

        Map<PositionsAufteilung.Ziel, DokumentPositionenDto.ZielDetail> details = new HashMap<>();
        if (request.ziele() != null) {
            request.ziele().forEach(d -> details.put(new PositionsAufteilung.Ziel(d.projektId(), d.kostenstelleId()), d));
        }
        List<Anteil> anteile = new ArrayList<>();
        for (PositionsAufteilung.ZielBetrag zb : ergebnis.ziele()) {
            DokumentPositionenDto.ZielDetail detail = details.get(zb.ziel());
            anteile.add(new Anteil(zb.ziel().projektId(), zb.ziel().kostenstelleId(), zb.betrag(), null,
                    detail != null ? detail.beschreibung() : null,
                    detail != null ? detail.streckungJahre() : null));
        }
        return schreibeAnteile(gd, anteile, zugeordnetVon);
    }

    // ------------------------------------------------------------ Wer ordnet zu?

    /**
     * Ermittelt das Frontend-Profil des Zuordnenden: bevorzugt die angemeldete
     * Sitzung, sonst das Profil zum Mitarbeiter (Token-Zugriff vom Handy).
     */
    @Transactional(readOnly = true)
    public FrontendUserProfile zugeordnetVon(Mitarbeiter caller, Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof FrontendUserPrincipal principal && principal.getId() != null) {
            FrontendUserProfile sessionProfile = frontendUserProfileRepository.findById(principal.getId()).orElse(null);
            if (sessionProfile != null && sessionProfile.isActive()) {
                if (caller == null || sessionProfile.getMitarbeiter() == null
                        || Objects.equals(sessionProfile.getMitarbeiter().getId(), caller.getId())) {
                    return sessionProfile;
                }
            }
        }
        if (caller != null && caller.getId() != null) {
            return frontendUserProfileRepository.findByMitarbeiterIdAndActiveTrue(caller.getId()).orElse(null);
        }
        return null;
    }

    // ------------------------------------------------------------ intern

    private LieferantGeschaeftsdokument ladeGeschaeftsdokument(Long id) {
        if (id == null || id <= 0) {
            throw new NoSuchElementException("Geschäftsdokument nicht gefunden");
        }
        return geschaeftsdokumentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Geschäftsdokument nicht gefunden"));
    }

    private Projekt ladeProjekt(Long id) {
        return projektRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Projekt nicht gefunden: " + id));
    }

    private Kostenstelle ladeKostenstelle(Long id) {
        return kostenstelleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kostenstelle nicht gefunden: " + id));
    }

    /** Ziel je Positions-ID; unbekannte Positionen oder Doppelziele werden abgelehnt. */
    private static Map<Long, PositionsAufteilung.Ziel> zielJePosition(List<LieferantDokumentPosition> positionen,
            List<DokumentPositionenDto.PositionsZiel> zuordnung) {
        Map<Long, PositionsAufteilung.Ziel> ziele = new HashMap<>();
        if (zuordnung == null) {
            return ziele;
        }
        var bekannte = positionen.stream().map(LieferantDokumentPosition::getId).toList();
        for (DokumentPositionenDto.PositionsZiel z : zuordnung) {
            if (z == null || !bekannte.contains(z.positionId())) {
                throw new IllegalArgumentException("Position gehört nicht zu diesem Dokument");
            }
            if (z.projektId() == null && z.kostenstelleId() == null) {
                continue;
            }
            PositionsAufteilung.Ziel ziel = new PositionsAufteilung.Ziel(z.projektId(), z.kostenstelleId());
            if (!ziel.gueltig()) {
                throw new IllegalArgumentException("Eine Position gehört entweder zu einem Projekt oder zu einer Kostenstelle");
            }
            ziele.put(z.positionId(), ziel);
        }
        return ziele;
    }

    private static PositionsAufteilung.Ergebnis berechne(LieferantGeschaeftsdokument gd,
            List<LieferantDokumentPosition> positionen, Map<Long, PositionsAufteilung.Ziel> ziele) {
        List<PositionsAufteilung.Eingabe> eingabe = positionen.stream()
                .map(p -> new PositionsAufteilung.Eingabe(p.getPositionsArt(), p.getGesamtpreisNetto(),
                        ziele.get(p.getId())))
                .toList();
        return PositionsAufteilung.berechne(eingabe, gd.getBetragNetto(), gd.getBetragBrutto());
    }

    /** @return Fehlertext in Handwerker-Sprache, oder {@code null} wenn speicherbar */
    private static String pruefe(PositionsAufteilung.Ergebnis ergebnis, List<LieferantDokumentPosition> positionen) {
        if (positionen.isEmpty()) {
            return "Für dieses Dokument sind noch keine Positionen ausgelesen.";
        }
        if (ergebnis.nichtZugeordnet() > 0) {
            return ergebnis.nichtZugeordnet() == 1
                    ? "Eine Position hat noch kein Projekt."
                    : ergebnis.nichtZugeordnet() + " Positionen haben noch kein Projekt.";
        }
        if (ergebnis.ziele().isEmpty()) {
            return "Bitte mindestens eine Position einem Projekt zuordnen.";
        }
        if (ergebnis.warenwert().signum() <= 0) {
            return "Die Positionen haben keine Preise. Bitte nach Prozent oder Betrag aufteilen.";
        }
        boolean nichtPositiv = ergebnis.ziele().stream()
                .anyMatch(z -> z.betrag() == null || z.betrag().signum() <= 0);
        if (nichtPositiv) {
            return "Diese Aufteilung ergibt keinen positiven Betrag je Projekt (z. B. bei Gutschriften). "
                    + "Bitte nach Prozent oder Betrag aufteilen.";
        }
        return null;
    }

    /**
     * Prüft die Anteile, löscht die alten und legt die neuen an. Bei Projekten
     * landet das PDF zusätzlich bei den Eingangsrechnungen des Projekts.
     */
    private int schreibeAnteile(LieferantGeschaeftsdokument gd, List<Anteil> anteile,
            FrontendUserProfile zugeordnetVon) {
        LieferantDokument dokument = gd.getDokument();
        if (dokument == null) {
            throw new IllegalArgumentException("Kein Basis-Dokument vorhanden");
        }
        List<Anteil> liste = anteile != null ? anteile : List.of();
        pruefeAnteile(gd, liste);

        // Ziele vorab laden: Fehler sollen auftreten, bevor etwas gelöscht wird.
        List<LieferantDokumentProjektAnteil> neu = new ArrayList<>();
        List<Projekt> projekteMitPdf = new ArrayList<>();
        for (Anteil anteil : liste) {
            Projekt projekt = anteil.projektId() != null ? ladeProjekt(anteil.projektId()) : null;
            Kostenstelle kostenstelle = projekt == null && anteil.kostenstelleId() != null
                    ? ladeKostenstelle(anteil.kostenstelleId())
                    : null;
            if (projekt == null && kostenstelle == null) {
                continue;
            }
            LieferantDokumentProjektAnteil pa = new LieferantDokumentProjektAnteil();
            pa.setDokument(dokument);
            pa.setProjekt(projekt);
            pa.setKostenstelle(kostenstelle);
            if (anteil.prozentanteil() != null) {
                pa.setProzent(anteil.prozentanteil().intValue());
            } else {
                pa.setAbsoluterBetrag(anteil.betrag());
            }
            pa.setBeschreibung(anteil.beschreibung());

            // Kostenstreckung nur für Kostenstellen (periodische Gemeinkosten, z. B.
            // Zertifizierung alle 3 Jahre). Projekt-Anteile bleiben einmalig.
            if (kostenstelle != null) {
                pa.setStreckungJahre(normalisiereStreckungJahre(anteil.streckungJahre()));
                pa.setStreckungStartJahr(gd.getDokumentDatum() != null
                        ? gd.getDokumentDatum().getYear()
                        : LocalDate.now().getYear());
            }

            // Kostenstellen-Anteile werden netto verrechnet (Vorsteuerabzug landet beim
            // Finanzamt), Projekt-Anteile brutto – berechneAnteil entscheidet das.
            if (gd.getBetragNetto() != null || gd.getBetragBrutto() != null) {
                pa.berechneAnteil(gd.getBetragNetto(), gd.getBetragBrutto());
            }
            pa.setZugeordnetVon(zugeordnetVon);
            neu.add(pa);
            if (projekt != null) {
                projekteMitPdf.add(projekt);
            }
        }

        projektAnteilRepository.deleteAll(projektAnteilRepository.findByDokumentId(dokument.getId()));
        projektAnteilRepository.saveAll(neu);
        projekteMitPdf.forEach(p -> kopierePdfInsProjekt(gd, p));
        return neu.size();
    }

    private static void pruefeAnteile(LieferantGeschaeftsdokument gd, List<Anteil> anteile) {
        BigDecimal absolutSumme = BigDecimal.ZERO;
        BigDecimal prozentSumme = BigDecimal.ZERO;
        BigDecimal maximalbetrag = gd.getBetragBrutto() != null ? gd.getBetragBrutto() : gd.getBetragNetto();
        for (Anteil anteil : anteile) {
            if (anteil.prozentanteil() != null && anteil.betrag() != null) {
                throw new IllegalArgumentException("Nur Prozent oder Betrag angeben");
            }
            if (anteil.prozentanteil() != null) {
                if (anteil.prozentanteil().compareTo(BigDecimal.ZERO) < 0
                        || anteil.prozentanteil().compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new IllegalArgumentException("Prozent muss zwischen 0 und 100 liegen");
                }
                prozentSumme = prozentSumme.add(anteil.prozentanteil());
            } else if (anteil.betrag() != null) {
                if (anteil.betrag().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Betrag muss groesser als 0 sein");
                }
                absolutSumme = absolutSumme.add(anteil.betrag());
            }
            if (anteil.kostenstelleId() != null && anteil.projektId() == null
                    && normalisiereStreckungJahre(anteil.streckungJahre()) == null) {
                throw new IllegalArgumentException("Streckung darf höchstens " + MAX_STRECKUNG_JAHRE + " Jahre betragen");
            }
        }
        if (prozentSumme.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Summe der Prozent-Anteile darf 100% nicht ueberschreiten");
        }
        if (maximalbetrag != null && absolutSumme.compareTo(maximalbetrag) > 0) {
            throw new IllegalArgumentException("Summe der Betraege darf den Rechnungsbetrag nicht ueberschreiten");
        }
    }

    /**
     * Streckungs-Jahre einer Kostenstellen-Zuordnung: null/&lt;1 wird zu 1 (keine
     * Streckung); über {@link #MAX_STRECKUNG_JAHRE} ergibt {@code null}.
     */
    public static Integer normalisiereStreckungJahre(Integer streckungJahre) {
        int wert = (streckungJahre != null && streckungJahre >= 1) ? streckungJahre : 1;
        return wert > MAX_STRECKUNG_JAHRE ? null : wert;
    }

    static boolean istAuffaellig(BigDecimal abweichung, BigDecimal belegNetto) {
        if (abweichung == null || abweichung.signum() == 0) {
            return false;
        }
        BigDecimal betrag = abweichung.abs();
        if (betrag.compareTo(ABWEICHUNG_EURO) <= 0) {
            return false;
        }
        return belegNetto == null || belegNetto.signum() == 0
                || betrag.divide(belegNetto.abs(), 4, RoundingMode.HALF_UP).compareTo(ABWEICHUNG_ANTEIL) > 0;
    }

    /** Kopiert das PDF des Geschäftsdokuments in die Eingangsrechnungen des Projekts. */
    private void kopierePdfInsProjekt(LieferantGeschaeftsdokument gd, Projekt projekt) {
        if (gd.getDokument() == null || gd.getDokument().getAttachment() == null) {
            return;
        }
        var attachment = gd.getDokument().getAttachment();
        var email = attachment.getEmail();
        if (email == null || email.getLieferant() == null) {
            return;
        }
        Long lieferantId = email.getLieferant().getId();
        String storedFilename = attachment.getStoredFilename();
        String originalFilename = attachment.getOriginalFilename();
        if (storedFilename == null || storedFilename.isBlank()) {
            return;
        }

        Path basis = Path.of(attachmentDir).toAbsolutePath().normalize().resolve("email");
        Path sourcePath = basis.resolve(String.valueOf(lieferantId)).resolve(storedFilename).normalize();
        if (!sourcePath.startsWith(basis) || !Files.exists(sourcePath)) {
            return;
        }

        String lieferantName = email.getLieferant().getLieferantenname();
        String dokumentNummer = gd.getDokumentNummer() != null ? gd.getDokumentNummer() : "unbekannt";
        String gespeicherterName = "ER_%s_%s_%d.pdf".formatted(
                sanitizeFilename(lieferantName != null ? lieferantName : ""),
                sanitizeFilename(dokumentNummer),
                System.currentTimeMillis());

        Path projektDir = Path.of(uploadDir).toAbsolutePath().normalize().resolve(String.valueOf(projekt.getId()));
        Path targetPath = projektDir.resolve(gespeicherterName);
        try {
            Files.createDirectories(projektDir);
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            ProjektDokument dok = new ProjektDokument();
            dok.setProjekt(projekt);
            dok.setOriginalDateiname(originalFilename != null ? originalFilename : storedFilename);
            dok.setGespeicherterDateiname(gespeicherterName);
            dok.setDateityp("application/pdf");
            dok.setDateigroesse(Files.size(targetPath));
            dok.setUploadDatum(LocalDate.now());
            dok.setDokumentGruppe(DokumentGruppe.EINGANGSRECHNUNGEN);
            dok.setLieferant(email.getLieferant());
            projektDokumentRepository.save(dok);
        } catch (IOException e) {
            // Kopieren ist Beiwerk – die Zuordnung selbst bleibt gültig.
            log.warn("PDF konnte nicht ins Projekt {} kopiert werden: {}", projekt.getId(), e.getClass().getSimpleName());
        }
    }

    private static String sanitizeFilename(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[^a-zA-Z0-9äöüÄÖÜß_-]", "_").replaceAll("_{2,}", "_");
    }

    private static DokumentPositionenDto.Position alsDto(LieferantDokumentPosition p) {
        return new DokumentPositionenDto.Position(
                p.getId(), p.getPositionNr(),
                p.getPositionsArt() != null ? p.getPositionsArt().name() : PositionsArt.WARE.name(),
                p.getExterneArtikelnummer(), p.getBezeichnung(), p.getMenge(), p.getMengeneinheit(),
                p.getEinzelpreis(), p.getPreiseinheit(), p.getGesamtpreisNetto(),
                p.getProjekt() != null ? p.getProjekt().getId() : null,
                p.getProjekt() != null ? p.getProjekt().getBauvorhaben() : null,
                p.getKostenstelle() != null ? p.getKostenstelle().getId() : null,
                p.getKostenstelle() != null ? p.getKostenstelle().getBezeichnung() : null);
    }

    private static DokumentPositionenDto.ZielBetrag alsDto(PositionsAufteilung.ZielBetrag zb) {
        return new DokumentPositionenDto.ZielBetrag(
                zb.ziel().projektId(), zb.ziel().kostenstelleId(), zb.warenwert(),
                zb.anteil().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                zb.betragNetto(), zb.betragBrutto(), zb.betrag());
    }
}

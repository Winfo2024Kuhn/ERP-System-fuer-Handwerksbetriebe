package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.BestellStatus;
import org.example.kalkulationsprogramm.domain.Bestellposition;
import org.example.kalkulationsprogramm.domain.Bestellung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.BestellungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Legt neue {@link Bestellung}en aus {@link ArtikelInProjekt}-Gruppen
 * an (Aggregat-Root des Einkaufs-Vorgangs).
 *
 * <p>Wird beim Export (PDF/E-Mail) aus {@code BestellungService}
 * aufgerufen: pro (Lieferant, Projekt) entsteht genau eine Bestellung
 * mit Status {@link BestellStatus#VERSENDET}; jede AiP dieser Gruppe
 * wird als {@link Bestellposition} vollkopiert, der Rueckverweis
 * {@link Bestellposition#getAusArtikelInProjekt()} bleibt erhalten.</p>
 *
 * <p>Existiert fuer eine AiP bereits eine Bestellposition (Backfill aus
 * V236 oder frueherer Export), wird keine zweite angelegt — idempotent.</p>
 */
@Service
@RequiredArgsConstructor
public class BestellauftragService {

    private final BestellungRepository bestellungRepository;

    /**
     * Erzeugt pro (Lieferant, Projekt)-Gruppe eine Bestellung aus den
     * uebergebenen AiP-Zeilen. Status {@link BestellStatus#VERSENDET}
     * inklusive {@code exportiertAm}/{@code versendetAm} — Kurzform fuer
     * den klassischen Export-Pfad (PDF/Mail hat schon stattgefunden).
     *
     * @param positionen  zu buendelnde Kalkulationspositionen
     * @param erstellVon  Mitarbeiter, der den Export ausloest (nullable —
     *                    dann nur Snapshot-Name leer/System)
     * @return die erzeugten Bestellungen (kann leer sein, wenn alles schon
     *         gebuendelt war)
     */
    @Transactional
    public List<Bestellung> erzeugeBestellungenAusExport(
            List<ArtikelInProjekt> positionen,
            Mitarbeiter erstellVon) {
        return erzeugeBestellungen(positionen, erstellVon, BestellStatus.VERSENDET);
    }

    /**
     * Erzeugt pro (Lieferant, Projekt)-Gruppe eine Bestellung.
     *
     * <p>Der {@code status} steuert, wann die Bestellung entsteht:</p>
     * <ul>
     *   <li>{@link BestellStatus#ENTWURF} — Bedarfsbuendelung vor Export
     *       (z.B. Chef legt aus mehreren offenen AiP-Zeilen eine Bestellung
     *       an, die spaeter erst per PDF versendet wird).</li>
     *   <li>{@link BestellStatus#VERSENDET} — direkter Versand-Pfad (Export
     *       oder Preisanfrage-Vergabe per Mail/IDS).</li>
     * </ul>
     *
     * @param positionen zu buendelnde Kalkulationspositionen
     * @param erstellVon Mitarbeiter, der den Vorgang ausloest (nullable)
     * @param status     ENTWURF oder VERSENDET
     * @return die erzeugten Bestellungen
     */
    @Transactional
    public List<Bestellung> erzeugeBestellungen(
            List<ArtikelInProjekt> positionen,
            Mitarbeiter erstellVon,
            BestellStatus status) {
        if (positionen == null || positionen.isEmpty()) {
            return List.of();
        }

        // Gruppieren nach (lieferant_id, projekt_id). LinkedHashMap fuer
        // stabile Reihenfolge — wichtig fuer Tests und fuer die
        // aufsteigende Nummernvergabe.
        Map<GruppenSchluessel, List<ArtikelInProjekt>> gruppen = new LinkedHashMap<>();
        for (ArtikelInProjekt aip : positionen) {
            Long lieferantId = aip.getLieferant() != null ? aip.getLieferant().getId() : null;
            Long projektId   = aip.getProjekt()   != null ? aip.getProjekt().getId()   : null;
            gruppen.computeIfAbsent(new GruppenSchluessel(lieferantId, projektId),
                    k -> new ArrayList<>()).add(aip);
        }

        List<Bestellung> ergebnis = new ArrayList<>();
        for (var eintrag : gruppen.entrySet()) {
            List<ArtikelInProjekt> gruppe = eintrag.getValue();
            Bestellung bestellung = erstelleBestellungFuerGruppe(gruppe, erstellVon, status);
            ergebnis.add(bestellung);
        }
        return ergebnis;
    }

    private Bestellung erstelleBestellungFuerGruppe(
            List<ArtikelInProjekt> gruppe,
            Mitarbeiter erstellVon,
            BestellStatus status) {
        ArtikelInProjekt erster = gruppe.get(0);
        Bestellung bestellung = new Bestellung();
        bestellung.setBestellnummer(naechsteBestellnummer());
        bestellung.setLieferant(erster.getLieferant());
        bestellung.setProjekt(erster.getProjekt());
        bestellung.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        if (status == BestellStatus.VERSENDET) {
            bestellung.setBestelltAm(LocalDate.now());
            bestellung.setVersendetAm(now);
            bestellung.setExportiertAm(now);
        }
        bestellung.setErstelltVon(erstellVon);
        bestellung.setErstelltVonName(snapshotName(erstellVon));
        bestellung.setErstelltAm(now);

        int positionsnummer = 1;
        for (ArtikelInProjekt aip : gruppe) {
            bestellung.addPosition(toBestellposition(aip, positionsnummer++));
        }
        return bestellungRepository.save(bestellung);
    }

    private Bestellposition toBestellposition(ArtikelInProjekt aip, int positionsnummer) {
        Bestellposition bp = new Bestellposition();
        bp.setPositionsnummer(positionsnummer);
        bp.setAusArtikelInProjekt(aip);
        bp.setArtikel(aip.getArtikel());
        if (aip.getArtikel() != null) {
            bp.setExterneArtikelnummer(aip.getArtikel().getExterneArtikelnummer());
        }
        bp.setFreitextProduktname(aip.getFreitextProduktname());
        bp.setFreitextProdukttext(aip.getFreitextProdukttext());
        bp.setKategorie(aip.getKategorie() != null
                ? aip.getKategorie()
                : (aip.getArtikel() != null ? aip.getArtikel().getKategorie() : null));
        bp.setMenge(bestimmeMenge(aip));
        bp.setEinheit(bestimmeEinheit(aip));
        bp.setStueckzahl(aip.getStueckzahl());
        bp.setPreisProEinheit(aip.getPreisProStueck());
        bp.setKilogramm(aip.getKilogramm());
        bp.setSchnittForm(aip.getSchnittForm());
        bp.setAnschnittWinkelLinks(aip.getAnschnittWinkelLinks());
        bp.setAnschnittWinkelRechts(aip.getAnschnittWinkelRechts());
        bp.setFixmassMm(aip.getFixmassMm());
        bp.setZeugnisAnforderung(aip.getZeugnisAnforderung());
        bp.setKommentar(aip.getKommentar());
        return bp;
    }

    private BigDecimal bestimmeMenge(ArtikelInProjekt aip) {
        if (aip.getFreitextMenge() != null) {
            return aip.getFreitextMenge();
        }
        if (aip.getMeter() != null && aip.getMeter().signum() > 0) {
            return aip.getMeter();
        }
        if (aip.getStueckzahl() != null) {
            return BigDecimal.valueOf(aip.getStueckzahl());
        }
        return null;
    }

    private String bestimmeEinheit(ArtikelInProjekt aip) {
        if (aip.getFreitextEinheit() != null && !aip.getFreitextEinheit().isBlank()) {
            return aip.getFreitextEinheit();
        }
        if (aip.getMeter() != null && aip.getMeter().signum() > 0) {
            return "m";
        }
        return "Stück";
    }

    private String snapshotName(Mitarbeiter m) {
        if (m == null) {
            return "System";
        }
        StringBuilder sb = new StringBuilder();
        if (m.getVorname() != null) sb.append(m.getVorname()).append(' ');
        if (m.getNachname() != null) sb.append(m.getNachname());
        String name = sb.toString().trim();
        return name.isEmpty() ? "Mitarbeiter #" + m.getId() : name;
    }

    /**
     * Naechste Bestellnummer im Schema {@code B-YYYY-NNNN} (vierstellig).
     * Eindeutig durch das unique-constraint auf {@code bestellnummer}.
     */
    String naechsteBestellnummer() {
        String prefix = "B-" + Year.now().getValue() + "-";
        Optional<String> letzte = bestellungRepository.findMaxBestellnummerForPrefix(prefix + "%");
        int naechste = letzte.map(nr -> {
            String zaehlTeil = nr.substring(prefix.length());
            try {
                return Integer.parseInt(zaehlTeil) + 1;
            } catch (NumberFormatException e) {
                return 1;
            }
        }).orElse(1);
        return String.format("%s%04d", prefix, naechste);
    }

    /** Key fuer die Gruppierung nach (lieferant, projekt). */
    private record GruppenSchluessel(Long lieferantId, Long projektId) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GruppenSchluessel that)) return false;
            return Objects.equals(lieferantId, that.lieferantId)
                && Objects.equals(projektId, that.projektId);
        }
    }
}

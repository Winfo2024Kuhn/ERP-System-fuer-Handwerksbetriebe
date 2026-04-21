package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.SchweisserZertifikat;
import org.example.kalkulationsprogramm.domain.Wps;
import org.example.kalkulationsprogramm.repository.SchweisserZertifikatRepository;
import org.example.kalkulationsprogramm.repository.WpsRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Prüft, ob ein Schweißer-Zertifikat die Anforderungen einer WPS abdeckt.
 * Pragmatische Umsetzung der Kernkriterien aus EN ISO 9606-1 / EN ISO 15614-1:
 * Schweißprozess, Werkstoffgruppe und Gültigkeitsdatum.
 */
@Service
@RequiredArgsConstructor
public class ZertifikatMatchingService {

    private final SchweisserZertifikatRepository zertifikatRepository;
    private final WpsRepository wpsRepository;

    /** Werkstoffgruppen nach CR ISO 15608 – hier vereinfachte Zuordnung für die gängigsten Baustähle. */
    private static final Map<String, Integer> WERKSTOFFGRUPPEN = buildWerkstoffgruppen();

    private static Map<String, Integer> buildWerkstoffgruppen() {
        Map<String, Integer> m = new HashMap<>();
        // Gruppe 1 – unlegierte Baustähle
        m.put("S185", 1);
        m.put("S235", 1);
        m.put("S275", 1);
        m.put("S355", 1);
        m.put("S420", 1);
        m.put("S460", 1);
        m.put("E295", 1);
        m.put("E335", 1);
        // Gruppe 8 – nichtrostende austenitische Stähle
        m.put("1.4301", 8);
        m.put("1.4307", 8);
        m.put("1.4401", 8);
        m.put("1.4404", 8);
        m.put("1.4541", 8);
        m.put("1.4571", 8);
        return m;
    }

    public enum Gueltigkeit { OK, BALD_ABLAUFEND, ABGELAUFEN }

    /** Detailergebnis einer Match-Prüfung zwischen Zertifikat und WPS. */
    public record MatchResult(
            boolean qualifiziert,
            List<String> abweichungen,
            Gueltigkeit gueltigkeit) {
    }

    /** Ein Schweißer mit bestem Match gegen eine WPS – für das Frontend. */
    public record QualifizierterSchweisserDto(
            Long mitarbeiterId,
            String name,
            Long zertifikatId,
            String zertifikatsnummer,
            boolean qualifiziert,
            List<String> abweichungen,
            Gueltigkeit gueltigkeit) {
    }

    /**
     * Prüft ein konkretes Zertifikat gegen eine WPS.
     * Harte Kriterien (führen zu {@code qualifiziert=false}): Prozess-Nummer und Werkstoffgruppe.
     * Die Gültigkeit wird zusätzlich als separater Status geliefert.
     */
    public MatchResult istQualifiziert(SchweisserZertifikat z, Wps w) {
        return istQualifiziert(z, w, LocalDate.now());
    }

    /** Test-Hook mit expliziter „Heute"-Referenz. */
    MatchResult istQualifiziert(SchweisserZertifikat z, Wps w, LocalDate heute) {
        List<String> abweichungen = new ArrayList<>();

        String prozessZ = normalisiereProzess(z.getSchweissProzes());
        String prozessW = normalisiereProzess(w.getSchweissProzes());
        if (!prozessZ.isEmpty() && !prozessW.isEmpty() && !prozessZ.equals(prozessW)) {
            abweichungen.add("Schweißprozess abweichend (Zertifikat: " + prozessZ
                    + ", WPS: " + prozessW + ")");
        }

        Integer gruppeZ = werkstoffgruppe(z.getGrundwerkstoff());
        Integer gruppeW = werkstoffgruppe(w.getGrundwerkstoff());
        if (gruppeZ != null && gruppeW != null && !gruppeZ.equals(gruppeW)) {
            abweichungen.add("Werkstoffgruppe abweichend (Zertifikat: " + z.getGrundwerkstoff()
                    + " / Gruppe " + gruppeZ + ", WPS: " + w.getGrundwerkstoff()
                    + " / Gruppe " + gruppeW + ")");
        } else if (gruppeZ == null && z.getGrundwerkstoff() != null && gruppeW != null) {
            abweichungen.add("Werkstoff im Zertifikat („" + z.getGrundwerkstoff()
                    + "\") nicht in bekannter Gruppe – bitte prüfen");
        }

        Gueltigkeit gueltigkeit = bewerteGueltigkeit(z, heute);

        boolean qualifiziert = abweichungen.isEmpty() && gueltigkeit != Gueltigkeit.ABGELAUFEN;
        return new MatchResult(qualifiziert, abweichungen, gueltigkeit);
    }

    /**
     * Liefert alle Schweißer, die mindestens ein Zertifikat haben, das zur WPS geprüft wurde.
     * Pro Mitarbeiter wird das beste Zertifikat ausgewählt (qualifiziert &gt; bald ablaufend &gt; abweichend &gt; abgelaufen).
     */
    public List<QualifizierterSchweisserDto> findeQualifizierteSchweisser(Long wpsId) {
        Wps w = wpsRepository.findById(wpsId).orElse(null);
        if (w == null) {
            return List.of();
        }
        LocalDate heute = LocalDate.now();
        List<SchweisserZertifikat> alle = zertifikatRepository.findAll();

        Map<Long, QualifizierterSchweisserDto> besteProMitarbeiter = new HashMap<>();
        for (SchweisserZertifikat z : alle) {
            Mitarbeiter m = z.getMitarbeiter();
            if (m == null || m.getId() == null) continue;
            MatchResult r = istQualifiziert(z, w, heute);
            QualifizierterSchweisserDto dto = new QualifizierterSchweisserDto(
                    m.getId(),
                    Optional.ofNullable(m.getVorname()).orElse("") + " "
                            + Optional.ofNullable(m.getNachname()).orElse(""),
                    z.getId(),
                    z.getZertifikatsnummer(),
                    r.qualifiziert(),
                    r.abweichungen(),
                    r.gueltigkeit());
            QualifizierterSchweisserDto bisher = besteProMitarbeiter.get(m.getId());
            if (bisher == null || scoreDto(dto) > scoreDto(bisher)) {
                besteProMitarbeiter.put(m.getId(), dto);
            }
        }

        List<QualifizierterSchweisserDto> result = new ArrayList<>(besteProMitarbeiter.values());
        result.sort(Comparator.comparingInt(ZertifikatMatchingService::scoreDto).reversed()
                .thenComparing(QualifizierterSchweisserDto::name, Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    private static int scoreDto(QualifizierterSchweisserDto d) {
        if (d.qualifiziert() && d.gueltigkeit() == Gueltigkeit.OK) return 4;
        if (d.qualifiziert() && d.gueltigkeit() == Gueltigkeit.BALD_ABLAUFEND) return 3;
        if (d.abweichungen().isEmpty() && d.gueltigkeit() == Gueltigkeit.ABGELAUFEN) return 2;
        if (!d.abweichungen().isEmpty() && d.gueltigkeit() != Gueltigkeit.ABGELAUFEN) return 1;
        return 0;
    }

    private Gueltigkeit bewerteGueltigkeit(SchweisserZertifikat z, LocalDate heute) {
        LocalDate warnFrist = heute.plusDays(60);
        LocalDate frist6Monate = heute.minusMonths(6);
        LocalDate frist5Monate = heute.minusMonths(5);
        LocalDate refDate = z.getLetzteVerlaengerung() != null
                ? z.getLetzteVerlaengerung()
                : z.getAusstellungsdatum();

        boolean generalAbgelaufen = z.getAblaufdatum() != null && z.getAblaufdatum().isBefore(heute);
        boolean verlaengerungUeberfaellig = refDate != null && refDate.isBefore(frist6Monate);
        if (generalAbgelaufen || verlaengerungUeberfaellig) {
            return Gueltigkeit.ABGELAUFEN;
        }

        boolean generalBald = z.getAblaufdatum() != null && z.getAblaufdatum().isBefore(warnFrist);
        boolean verlaengerungBald = refDate != null && refDate.isBefore(frist5Monate);
        if (generalBald || verlaengerungBald) {
            return Gueltigkeit.BALD_ABLAUFEND;
        }
        return Gueltigkeit.OK;
    }

    /** Extrahiert die führende Prozess-Nummer (z.B. „135 MAG" → „135"). */
    static String normalisiereProzess(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty()) return "";
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        return digits.length() > 0 ? digits.toString() : t.toUpperCase();
    }

    /** Normalisiert einen Werkstoff-String (z.B. „S355J2+N" → „S355") und liefert die Gruppe. */
    static Integer werkstoffgruppe(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toUpperCase();
        if (t.isEmpty()) return null;
        if (WERKSTOFFGRUPPEN.containsKey(t)) return WERKSTOFFGRUPPEN.get(t);
        for (Map.Entry<String, Integer> e : WERKSTOFFGRUPPEN.entrySet()) {
            if (t.startsWith(e.getKey())) return e.getValue();
        }
        return null;
    }
}

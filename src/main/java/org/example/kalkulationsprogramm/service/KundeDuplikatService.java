package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatGrund;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatTrefferDto;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * Erkennt potenzielle Duplikate beim Anlegen eines Kunden.
 *
 * <p>Wird sowohl vom Live-Hinweis im Frontend (GET /api/kunden/duplikat-check)
 * als auch vom POST /api/kunden als harter Stop genutzt. Alle Eingabefelder
 * werden normalisiert (E-Mail/Name lowercase, Telefon nur Ziffern mit führender 0
 * statt +49) damit Tipp-Varianten denselben Treffer liefern.
 */
@Service
@RequiredArgsConstructor
public class KundeDuplikatService {

    private final KundeRepository kundeRepository;

    @Transactional(readOnly = true)
    public KundeDuplikatResponseDto findeDuplikate(String email,
                                                   String telefon,
                                                   String mobiltelefon,
                                                   String name,
                                                   String plz,
                                                   String strasse) {
        String emailNorm = normalisiereEmail(email);
        String telDigits = normalisiereTelefon(telefon);
        String mobilDigits = normalisiereTelefon(mobiltelefon);
        String nameNorm = normalisiereName(name);
        String plzNorm = StringUtils.hasText(plz) ? plz.trim() : null;
        String strasseNorm = normalisiereStrasse(strasse);

        // Wenn nichts angegeben ist, gibt es nichts zu prüfen.
        if (emailNorm == null && telDigits == null && mobilDigits == null
                && (nameNorm == null || (plzNorm == null && strasseNorm == null))) {
            return new KundeDuplikatResponseDto(List.of(), false);
        }

        List<Kunde> kandidaten = kundeRepository.findePotenzielleDuplikate(
                emailNorm, telDigits, mobilDigits, nameNorm, plzNorm, strasseNorm);

        Map<Long, KundeDuplikatTrefferDto> trefferProKunde = new LinkedHashMap<>();
        boolean harterTreffer = false;

        for (Kunde kunde : kandidaten) {
            EnumSet<KundeDuplikatGrund> gruende = ermittleGruende(
                    kunde, emailNorm, telDigits, mobilDigits, nameNorm, plzNorm, strasseNorm);
            if (gruende.isEmpty()) {
                continue;
            }
            KundeDuplikatTrefferDto treffer = toTreffer(kunde, gruende);
            trefferProKunde.put(kunde.getId(), treffer);
            if (gruende.stream().anyMatch(KundeDuplikatGrund::isHart)) {
                harterTreffer = true;
            }
        }

        List<KundeDuplikatTrefferDto> sortiert = new ArrayList<>(trefferProKunde.values());
        sortiert.sort(Comparator.comparingInt(KundeDuplikatTrefferDto::getScore).reversed());
        return new KundeDuplikatResponseDto(sortiert, harterTreffer);
    }

    private EnumSet<KundeDuplikatGrund> ermittleGruende(Kunde k,
                                                        String emailNorm,
                                                        String telDigits,
                                                        String mobilDigits,
                                                        String nameNorm,
                                                        String plz,
                                                        String strasse) {
        EnumSet<KundeDuplikatGrund> gruende = EnumSet.noneOf(KundeDuplikatGrund.class);

        if (emailNorm != null && k.getKundenEmails() != null) {
            for (String e : k.getKundenEmails()) {
                if (e != null && e.trim().toLowerCase(Locale.GERMAN).equals(emailNorm)) {
                    gruende.add(KundeDuplikatGrund.EMAIL_GLEICH);
                    break;
                }
            }
        }
        if (telDigits != null && telDigits.equals(normalisiereTelefon(k.getTelefon()))) {
            gruende.add(KundeDuplikatGrund.TELEFON_GLEICH);
        }
        if (mobilDigits != null && mobilDigits.equals(normalisiereTelefon(k.getMobiltelefon()))) {
            gruende.add(KundeDuplikatGrund.MOBILTELEFON_GLEICH);
        }
        if (nameNorm != null && nameNorm.equals(normalisiereName(k.getName()))) {
            if (plz != null && plz.equals(k.getPlz())) {
                gruende.add(KundeDuplikatGrund.NAME_PLZ_GLEICH);
            }
            if (strasse != null && strasse.equals(normalisiereStrasse(k.getStrasse()))) {
                gruende.add(KundeDuplikatGrund.NAME_STRASSE_GLEICH);
            }
        }
        return gruende;
    }

    private KundeDuplikatTrefferDto toTreffer(Kunde k, Set<KundeDuplikatGrund> gruende) {
        KundeDuplikatTrefferDto dto = new KundeDuplikatTrefferDto();
        dto.setId(k.getId());
        dto.setKundennummer(k.getKundennummer());
        dto.setName(k.getName());
        dto.setAnsprechspartner(k.getAnsprechspartner());
        dto.setStrasse(k.getStrasse());
        dto.setPlz(k.getPlz());
        dto.setOrt(k.getOrt());
        dto.setTelefon(k.getTelefon());
        dto.setMobiltelefon(k.getMobiltelefon());
        dto.setKundenEmails(k.getKundenEmails() == null ? List.of() : new ArrayList<>(k.getKundenEmails()));
        List<KundeDuplikatGrund> sorted = new ArrayList<>(gruende);
        Collections.sort(sorted);
        dto.setGruende(sorted);
        dto.setScore(sorted.stream().mapToInt(KundeDuplikatGrund::getScore).sum());
        return dto;
    }

    static String normalisiereEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase(Locale.GERMAN);
    }

    /**
     * Reduziert eine Telefonnummer auf die reinen Ziffern. Internationale Präfixe
     * (+49 / 0049) werden durch eine führende 0 ersetzt, so dass "+49 30 1234"
     * und "030 1234" denselben Schlüssel ergeben.
     */
    static String normalisiereTelefon(String telefon) {
        if (!StringUtils.hasText(telefon)) {
            return null;
        }
        String t = telefon.trim();
        // +49 / 0049 → 0 (deutsche Nummern unifizieren)
        if (t.startsWith("+49")) {
            t = "0" + t.substring(3);
        } else if (t.startsWith("0049")) {
            t = "0" + t.substring(4);
        }
        String digits = t.replaceAll("\\D", "");
        // Nur reine Ortsvorwahl (< 7 Ziffern) → nicht für Duplikat-Prüfung nutzen.
        // Z.B. "0931 " (auto-befüllt) würde sonst alle Würzburger Kunden matchen.
        if (digits.length() < 7) {
            return null;
        }
        return digits;
    }

    /**
     * Normalisiert einen Firmen-/Personennamen für den Vergleich:
     * lowercase, trim, mehrfache Leerzeichen reduziert. Rechtsformen (GmbH etc.)
     * bleiben erhalten – sie gehören zum Namen, sonst kollidieren z.B.
     * "Müller GmbH" und "Müller GbR".
     */
    static String normalisiereName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.trim().toLowerCase(Locale.GERMAN).replaceAll("\\s+", " ");
    }

    /**
     * Normalisiert eine Straßen-Angabe: lowercase, mehrfache Leerzeichen reduziert,
     * "straße"/"str." vereinheitlicht. So matcht "Hauptstraße 12" mit "Hauptstr. 12".
     */
    static String normalisiereStrasse(String strasse) {
        if (!StringUtils.hasText(strasse)) {
            return null;
        }
        String s = strasse.trim().toLowerCase(Locale.GERMAN).replaceAll("\\s+", " ");
        s = s.replace("straße", "str.").replace("strasse", "str.");
        return s;
    }
}

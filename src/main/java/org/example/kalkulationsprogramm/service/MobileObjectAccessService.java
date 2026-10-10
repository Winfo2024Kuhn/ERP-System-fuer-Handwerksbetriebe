package org.example.kalkulationsprogramm.service;

import java.util.EnumSet;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageNotizBildRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizBildRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mobile Dateirechte stammen aus dem konkreten Fachobjekt, niemals aus dessen Dateinamen. */
@Service
@RequiredArgsConstructor
public class MobileObjectAccessService {
    public enum Speicher { PROJEKT, ANFRAGE, BILD }
    public record FreigegebeneDatei(String dateiname, Speicher speicher) {}

    private final ProjektDokumentRepository projektDokumente;
    private final AnfrageDokumentRepository anfrageDokumente;
    private final ProjektNotizBildRepository projektNotizBilder;
    private final AnfrageNotizBildRepository anfrageNotizBilder;

    /** Fotos, die die mobile App hochladen darf. SVG ist bewusst nicht dabei (Skript im Bild). */
    private static final java.util.Set<String> MOBILE_BILD_TYPEN = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif");
    /** Lieferschein-Scans aus der App: Fotos oder PDF. */
    private static final java.util.Set<String> MOBILE_SCAN_TYPEN = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif", "application/pdf");

    /** Darf die App diese Datei als Foto hochladen? Leere Dateien und unbekannte Typen nicht. */
    public static boolean istErlaubtesMobilBild(org.springframework.web.multipart.MultipartFile datei) {
        return hatTyp(datei, MOBILE_BILD_TYPEN);
    }

    /** Darf die App diese Datei als Lieferschein-Scan hochladen? */
    public static boolean istErlaubterMobilScan(org.springframework.web.multipart.MultipartFile datei) {
        return hatTyp(datei, MOBILE_SCAN_TYPEN);
    }

    /** Dateiendung passend zum geprüften Bildtyp, damit kein .html/.svg-Name auf der Platte landet. */
    public static String endungFuerBild(String contentType) {
        return switch (normalisiereTyp(contentType)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            case "image/heif" -> ".heif";
            default -> throw new IllegalArgumentException("Ungültiger Bildtyp.");
        };
    }

    private static boolean hatTyp(org.springframework.web.multipart.MultipartFile datei, java.util.Set<String> erlaubt) {
        return datei != null && !datei.isEmpty() && erlaubt.contains(normalisiereTyp(datei.getContentType()));
    }

    private static String normalisiereTyp(String contentType) {
        if (contentType == null) return "";
        int semikolon = contentType.indexOf(';');
        return (semikolon < 0 ? contentType : contentType.substring(0, semikolon)).trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static boolean canReadProjektDokument(ProjektDokument dokument) {
        return dokument != null && !(dokument instanceof ProjektGeschaeftsdokument)
                && dokument.getDokumentGruppe() == DokumentGruppe.BILDER
                && dokument.getProjekt() != null && dokument.getProjekt().getId() != null;
    }

    public static boolean canReadAnfrageDokument(AnfrageDokument dokument) {
        return dokument != null && !(dokument instanceof AnfrageGeschaeftsdokument)
                && dokument.getDokumentGruppe() == DokumentGruppe.BILDER
                && dokument.getAnfrage() != null && dokument.getAnfrage().getId() != null;
    }

    public static boolean canReadProjektNotiz(ProjektNotiz notiz, Long mitarbeiterId) {
        return mitarbeiterId != null && notiz != null && notiz.isMobileSichtbar()
                && notiz.getProjekt() != null && notiz.getProjekt().getId() != null
                && (!notiz.isNurFuerErsteller() || (notiz.getMitarbeiter() != null
                    && Objects.equals(notiz.getMitarbeiter().getId(), mitarbeiterId)));
    }

    public static boolean canReadAnfrageNotiz(AnfrageNotiz notiz, Long mitarbeiterId) {
        return mitarbeiterId != null && notiz != null && notiz.isMobileSichtbar()
                && notiz.getAnfrage() != null && notiz.getAnfrage().getId() != null
                && (!notiz.isNurFuerErsteller() || (notiz.getMitarbeiter() != null
                    && Objects.equals(notiz.getMitarbeiter().getId(), mitarbeiterId)));
    }

    @Transactional(readOnly = true)
    public FreigegebeneDatei requireReadableFile(String dateiname, Long mitarbeiterId) {
        if (mitarbeiterId == null || dateiname == null || dateiname.isBlank()
                || dateiname.indexOf('/') >= 0 || dateiname.indexOf('\\') >= 0
                || dateiname.equals(".") || dateiname.equals("..")
                || dateiname.chars().anyMatch(Character::isISOControl)) {
            throw nichtGefunden();
        }

        EnumSet<Speicher> speicher = EnumSet.noneOf(Speicher.class);
        projektDokumente.findByGespeicherterDateiname(dateiname).ifPresent(dokument -> {
            // MySQL kann den Vergleich ohne Beachtung der Großschreibung ausführen.
            if (!dateiname.equals(dokument.getGespeicherterDateiname()) || !canReadProjektDokument(dokument)) {
                throw nichtGefunden();
            }
            speicher.add(Speicher.PROJEKT);
        });
        anfrageDokumente.findByGespeicherterDateiname(dateiname).ifPresent(dokument -> {
            if (!dateiname.equals(dokument.getGespeicherterDateiname()) || !canReadAnfrageDokument(dokument)) {
                throw nichtGefunden();
            }
            speicher.add(Speicher.ANFRAGE);
        });
        for (var bild : projektNotizBilder.findByGespeicherterDateiname(dateiname)) {
            if (!dateiname.equals(bild.getGespeicherterDateiname())
                    || !canReadProjektNotiz(bild.getNotiz(), mitarbeiterId)) {
                throw nichtGefunden();
            }
            speicher.add(Speicher.PROJEKT);
        }
        for (var bild : anfrageNotizBilder.findByGespeicherterDateiname(dateiname)) {
            if (!dateiname.equals(bild.getGespeicherterDateiname())
                    || !canReadAnfrageNotiz(bild.getNotiz(), mitarbeiterId)) {
                throw nichtGefunden();
            }
            speicher.add(Speicher.BILD);
        }
        // Unbekannte oder mehrdeutige Dateien erhalten keinen Dateisystem-Fallback.
        if (speicher.size() != 1) {
            throw nichtGefunden();
        }
        return new FreigegebeneDatei(dateiname, speicher.iterator().next());
    }

    private static NotFoundException nichtGefunden() {
        return new NotFoundException("Datei nicht gefunden.");
    }
}

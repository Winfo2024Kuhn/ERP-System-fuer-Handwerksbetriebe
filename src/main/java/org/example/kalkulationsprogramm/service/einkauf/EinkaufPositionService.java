package org.example.kalkulationsprogramm.service.einkauf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.DokumentSoll;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EinkaufPositionService {
    private static final BigDecimal TAUSEND = new BigDecimal("1000");
    private static final BigDecimal MAX_DECIMAL = new BigDecimal("9999999999999.999999");
    private static final int MAX_TEXT = 500;
    private static final Comparator<DokumentSoll> DOKUMENT_REIHENFOLGE = Comparator
            .comparing((DokumentSoll d) -> d.art() == null ? "" : d.art().name())
            .thenComparing(d -> leer(d.grundlage()))
            .thenComparing(d -> leer(d.grundlageVersion()))
            .thenComparing(DokumentSoll::fachlichBestaetigt);

    private final ArtikelRepository artikelRepository;

    public EinkaufPositionService(ArtikelRepository artikelRepository) {
        this.artikelRepository = artikelRepository;
    }

    @Transactional(readOnly = true)
    public PositionSnapshot validiere(PositionSnapshot input, Long projektId) {
        if (input == null) {
            throw new IllegalArgumentException("Die Einkaufsposition fehlt.");
        }
        if (input.art() == null) {
            throw new IllegalArgumentException("Die Positionsart fehlt.");
        }
        pruefeText(input.interneReferenz(), "Interne Referenz", 128, false);
        pruefeText(input.zeichnungsnummer(), "Zeichnungsnummer", 128, false);
        pruefeText(input.zeichnungsrevision(), "Zeichnungsrevision", 128, false);
        pruefeText(input.bezeichnung(), "Bezeichnung", 255, false);
        pruefeText(input.werkstoff(), "Werkstoff", 128, false);
        pruefeText(input.abmessung(), "Abmessung", 255, false);
        pruefeText(input.schnittForm(), "Schnittform", 80, false);
        pruefeWinkel(input.winkelLinks(), "Linker Winkel");
        pruefeWinkel(input.winkelRechts(), "Rechter Winkel");
        pruefeText(input.bearbeitung(), "Bearbeitung", MAX_TEXT, false);
        pruefeText(input.oberflaeche(), "Oberfläche", 128, false);

        Mengenbasis basis = pruefeMengenbasis(input.basis());
        List<DokumentSoll> dokumente = pruefeDokumente(input.dokumente());
        List<Long> anlagen = pruefeAnlagen(input.anlageVersionIds());
        PositionSnapshot snapshot = input;

        if (input.art() == Positionsart.ARTIKEL) {
            if (input.artikelId() == null || input.artikelId() <= 0) {
                throw new IllegalArgumentException("Bitte wählen Sie einen Artikel aus dem Katalog.");
            }
            Artikel artikel = artikelRepository.findById(input.artikelId())
                    .orElseThrow(() -> new NotFoundException("Der ausgewählte Artikel wurde nicht gefunden."));
            pruefeText(artikel.getProduktname(), "Bezeichnung", 255, true);
            // Nur die betriebsinterne Nummer ist eine Identität. Lieferantennummern
            // sind Preisquellen und dürfen sie nicht ersetzen.
            snapshot = new PositionSnapshot(input.art(), artikel.getId(), artikel.getArtikelnummer(),
                    input.zeichnungsnummer(), input.zeichnungsrevision(), artikel.getProduktname(),
                    artikel.getWerkstoff() == null ? input.werkstoff() : artikel.getWerkstoff().getName(),
                    input.abmessung(), basis, input.schnittForm(), input.winkelLinks(), input.winkelRechts(),
                    input.bearbeitung(), input.oberflaeche(), dokumente, anlagen);
        } else {
            pruefeText(input.bezeichnung(), "Bezeichnung", 255, true);
            if (projektId == null || projektId <= 0) {
                throw new IllegalArgumentException("Ein Zeichnungsteil braucht ein Projekt.");
            }
            if (leer(input.interneReferenz()).isBlank()) {
                throw new IllegalArgumentException("Ein Zeichnungsteil braucht eine eindeutige Projektkennung.");
            }
            if (leer(input.zeichnungsnummer()).isBlank() || leer(input.zeichnungsrevision()).isBlank()) {
                throw new IllegalArgumentException("Zeichnungsnummer und Revision sind für ein Zeichnungsteil erforderlich.");
            }
            if (anlagen.isEmpty()) {
                throw new IllegalArgumentException("Für ein Zeichnungsteil muss eine Zeichnungsanlage hinterlegt sein.");
            }
            snapshot = new PositionSnapshot(input.art(), null, input.interneReferenz().trim(),
                    input.zeichnungsnummer().trim(), input.zeichnungsrevision().trim(), input.bezeichnung(),
                    input.werkstoff(), input.abmessung(), basis, input.schnittForm(), input.winkelLinks(),
                    input.winkelRechts(), input.bearbeitung(), input.oberflaeche(), dokumente, anlagen);
        }
        return snapshot;
    }

    public String buendelSchluessel(PositionSnapshot position, Liefergruppe liefergruppe) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(liefergruppe, "liefergruppe");
        List<String> teile = new ArrayList<>();
        add(teile, position.art());
        add(teile, position.artikelId());
        add(teile, position.interneReferenz());
        add(teile, position.zeichnungsnummer());
        add(teile, position.zeichnungsrevision());
        add(teile, position.bezeichnung());
        add(teile, position.werkstoff());
        add(teile, position.abmessung());
        Mengenbasis basis = position.basis();
        add(teile, basis == null ? null : basis.einheit());
        add(teile, basis == null ? null : basis.einzelLaengeMm());
        add(teile, basis == null ? null : basis.kgJeMeter());
        add(teile, basis == null ? null : basis.faktorQuelle());
        add(teile, position.schnittForm());
        add(teile, position.winkelLinks());
        add(teile, position.winkelRechts());
        add(teile, position.bearbeitung());
        add(teile, position.oberflaeche());
        (position.dokumente() == null ? List.<DokumentSoll>of() : position.dokumente()).stream()
                .sorted(DOKUMENT_REIHENFOLGE)
                .forEach(d -> {
                    add(teile, d.art());
                    add(teile, d.grundlage());
                    add(teile, d.grundlageVersion());
                    add(teile, d.fachlichBestaetigt());
                });
        (position.anlageVersionIds() == null ? List.<Long>of() : position.anlageVersionIds()).stream()
                .sorted().forEach(id -> add(teile, id));
        add(teile, liefergruppe.lieferadresse());
        add(teile, liefergruppe.bedarfstermin());
        add(teile, liefergruppe.projektId());
        add(teile, liefergruppe.lagerzweck());
        return sha256(String.join("", teile));
    }

    public PositionSnapshot mitTeilmenge(PositionSnapshot position, BigDecimal menge) {
        Objects.requireNonNull(position, "position");
        if (position.basis() == null) {
            throw new IllegalArgumentException("Die Mengenbasis fehlt.");
        }
        pruefeMenge(menge, "Teilmenge");
        Mengenbasis alt = position.basis();
        if (menge.compareTo(alt.menge()) > 0) {
            throw new IllegalArgumentException("Die Teilmenge darf den offenen Bedarf nicht überschreiten.");
        }
        BigDecimal stueckzahl = alt.stueckzahl();
        if (alt.einheit() == Einheit.STUECK) {
            pruefeGanzzahlig(menge, "Stückzahl");
            stueckzahl = menge;
        } else if (alt.einzelLaengeMm() != null && stueckzahl != null) {
            BigDecimal meterJeStueck = alt.einzelLaengeMm().divide(TAUSEND, 6, RoundingMode.UNNECESSARY);
            BigDecimal neueStueckzahl = menge.divide(meterJeStueck, 6, RoundingMode.UNNECESSARY);
            pruefeGanzzahlig(neueStueckzahl, "Profilstückzahl");
            stueckzahl = neueStueckzahl;
        }
        Mengenbasis teilbasis = new Mengenbasis(menge.setScale(6, RoundingMode.HALF_UP), alt.einheit(),
                stueckzahl, alt.einzelLaengeMm(), alt.kgJeMeter(), alt.faktorQuelle());
        return new PositionSnapshot(position.art(), position.artikelId(), position.interneReferenz(),
                position.zeichnungsnummer(), position.zeichnungsrevision(), position.bezeichnung(),
                position.werkstoff(), position.abmessung(), teilbasis, position.schnittForm(),
                position.winkelLinks(), position.winkelRechts(), position.bearbeitung(), position.oberflaeche(),
                position.dokumente(), position.anlageVersionIds());
    }

    private static Mengenbasis pruefeMengenbasis(Mengenbasis basis) {
        if (basis == null || basis.einheit() == null) {
            throw new IllegalArgumentException("Bitte geben Sie Menge und Einheit an.");
        }
        BigDecimal stueckzahl = basis.stueckzahl();
        BigDecimal einzelLaenge = basis.einzelLaengeMm();
        if (stueckzahl != null) {
            pruefeMenge(stueckzahl, "Profilstückzahl");
            pruefeGanzzahlig(stueckzahl, "Profilstückzahl");
        }
        if (einzelLaenge != null) pruefeMenge(einzelLaenge, "Einzellänge");
        if (basis.kgJeMeter() != null) pruefeMenge(basis.kgJeMeter(), "Kilogramm je Meter");
        pruefeText(basis.faktorQuelle(), "Quelle des Umrechnungsfaktors", 255, false);
        BigDecimal menge = basis.menge();
        if (basis.einheit() == Einheit.METER && stueckzahl != null && einzelLaenge != null) {
            menge = stueckzahl.multiply(einzelLaenge).divide(TAUSEND, 6, RoundingMode.HALF_UP);
        }
        pruefeMenge(menge, "Menge");
        if (basis.einheit() == Einheit.STUECK) {
            pruefeGanzzahlig(menge, "Menge");
            if (stueckzahl != null && stueckzahl.compareTo(menge) != 0) {
                throw new IllegalArgumentException("Stückzahl und Stückmenge müssen übereinstimmen.");
            }
        }
        return new Mengenbasis(menge.setScale(6, RoundingMode.HALF_UP), basis.einheit(), stueckzahl,
                einzelLaenge, basis.kgJeMeter(), basis.faktorQuelle());
    }

    private static List<DokumentSoll> pruefeDokumente(List<DokumentSoll> dokumente) {
        if (dokumente == null) return List.of();
        List<DokumentSoll> result = new ArrayList<>(dokumente.size());
        for (DokumentSoll dokument : dokumente) {
            if (dokument == null || dokument.art() == null) {
                throw new IllegalArgumentException("Jeder Dokumentnachweis braucht eine Dokumentart.");
            }
            pruefeText(dokument.grundlage(), "Grundlage des Dokumentnachweises", 500, true);
            pruefeText(dokument.grundlageVersion(), "Version der Dokumentgrundlage", 128, true);
            result.add(dokument);
        }
        return result.stream().sorted(DOKUMENT_REIHENFOLGE).toList();
    }

    private static List<Long> pruefeAnlagen(List<Long> ids) {
        if (ids == null) return List.of();
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Die Anlagenreferenz ist ungültig.");
        }
        return ids.stream().distinct().sorted().toList();
    }

    private static void pruefeMenge(BigDecimal wert, String feld) {
        if (wert == null || wert.signum() <= 0 || wert.abs().compareTo(MAX_DECIMAL) > 0
                || Math.max(0, wert.scale()) > 6) {
            throw new IllegalArgumentException(feld + " muss größer als 0 sein und darf höchstens 6 Nachkommastellen haben.");
        }
    }

    private static void pruefeGanzzahlig(BigDecimal wert, String feld) {
        if (wert.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(feld + " muss eine ganze Zahl sein.");
        }
    }

    private static void pruefeText(String wert, String feld, int max, boolean erforderlich) {
        if (erforderlich && (wert == null || wert.isBlank())) {
            throw new IllegalArgumentException(feld + " darf nicht leer sein.");
        }
        if (wert != null && (wert.length() > max || wert.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException(feld + " ist ungültig oder zu lang.");
        }
    }

    private static void pruefeWinkel(String wert, String feld) {
        pruefeText(wert, feld, 16, false);
        if (wert != null && !wert.isBlank() && !wert.matches("[+-]?\\d{1,3}(?:[.,]\\d{1,2})?°?")) {
            throw new IllegalArgumentException(feld + " muss als Winkel zwischen 0 und 360 Grad angegeben werden.");
        }
        if (wert != null && !wert.isBlank()) {
            String number = wert.replace("°", "").replace(',', '.');
            BigDecimal angle = new BigDecimal(number).abs();
            if (angle.compareTo(new BigDecimal("360")) > 0) {
                throw new IllegalArgumentException(feld + " darf höchstens 360 Grad betragen.");
            }
        }
    }

    private static String leer(String text) {
        return text == null ? "" : text;
    }

    private static void add(List<String> parts, Object value) {
        String string;
        if (value == null) {
            string = "";
        } else if (value instanceof BigDecimal decimal) {
            string = decimal.stripTrailingZeros().toPlainString();
        } else {
            string = value.toString();
        }
        parts.add(string.length() + ":" + string);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar.", impossible);
        }
    }
}

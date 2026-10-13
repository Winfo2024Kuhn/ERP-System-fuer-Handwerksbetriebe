package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Profilform;

/**
 * Baut den Suchtext eines Artikels: alle Schreibweisen und Synonyme in Kleinbuchstaben, damit zum Beispiel "rundrohr 42.4 x2" und
 * "42,4 rohr" den Artikel finden. Entspricht dem Aufbau aus V348 und wird nach jeder Änderung der Stammdaten neu erzeugt.
 */
final class ArtikelSuchtext {

    private static final Map<Profilform, String> SYNONYME = new EnumMap<>(Profilform.class);

    static {
        SYNONYME.put(Profilform.RUNDROHR, "rundrohr rohr rund");
        SYNONYME.put(Profilform.QUADRATROHR, "quadratrohr vierkantrohr rohr quadrat hohlprofil");
        SYNONYME.put(Profilform.RECHTECKROHR, "rechteckrohr rohr rechteck hohlprofil");
        SYNONYME.put(Profilform.RUNDSTAB, "rundstab rundstahl rundmaterial stab rund");
        SYNONYME.put(Profilform.VIERKANTSTAB, "vierkantstab vierkant quadratstahl stab");
        SYNONYME.put(Profilform.SECHSKANTSTAB, "sechskantstab sechskant stab");
        SYNONYME.put(Profilform.FLACHSTAB, "flachstahl flach flachmaterial bandstahl");
        SYNONYME.put(Profilform.BREITFLACHSTAHL, "breitflachstahl flach breit");
        SYNONYME.put(Profilform.WINKEL_GLEICHSCHENKLIG, "winkel winkelstahl l-profil gleichschenklig");
        SYNONYME.put(Profilform.WINKEL_UNGLEICHSCHENKLIG, "winkel winkelstahl l-profil ungleichschenklig");
        SYNONYME.put(Profilform.U_PROFIL, "u-profil uprofil u profil");
        SYNONYME.put(Profilform.UPE_PROFIL, "upe-profil upe u profil");
        SYNONYME.put(Profilform.UAP_PROFIL, "uap-profil uap u profil");
        SYNONYME.put(Profilform.IPE_PROFIL, "ipe traeger doppel-t i-profil");
        SYNONYME.put(Profilform.I_PROFIL, "i-profil traeger doppel-t");
        SYNONYME.put(Profilform.HEA_PROFIL, "hea traeger breitflansch");
        SYNONYME.put(Profilform.HEB_PROFIL, "heb traeger breitflansch");
        SYNONYME.put(Profilform.HEM_PROFIL, "hem traeger breitflansch");
        SYNONYME.put(Profilform.T_PROFIL, "t-profil tprofil t profil");
        SYNONYME.put(Profilform.Z_PROFIL, "z-profil zprofil z profil");
        SYNONYME.put(Profilform.BLECH, "blech glattblech tafel platte");
        SYNONYME.put(Profilform.RIFFELBLECH, "riffelblech traenenblech blech rutschhemmend");
        SYNONYME.put(Profilform.LOCHBLECH, "lochblech blech lochung");
    }

    private ArtikelSuchtext() {
    }

    static String aufbauen(Artikel a) {
        List<String> teile = new ArrayList<>();
        if (a.getProfilform() != null) {
            teile.add(SYNONYME.getOrDefault(a.getProfilform(), ""));
        }
        teile.add(a.getArtikelnummer());
        teile.add(a.getEan());
        teile.add(a.getProduktname());
        if (a.getProduktname() != null) {
            teile.add(a.getProduktname().replace('.', ','));
            teile.add(a.getProduktname().replace(',', '.'));
        }
        teile.add(a.getProdukttext());
        teile.add(a.getProduktlinie());
        if (a.getKategorie() != null) {
            teile.add(a.getKategorie().getBeschreibung());
        }
        if (a instanceof ArtikelWerkstoffe aw) {
            for (BigDecimal mass : new BigDecimal[] {aw.getDurchmesser(), aw.getHoehe(), aw.getBreite(), aw.getWandstaerke()}) {
                String kurz = kurz(mass);
                if (kurz != null) {
                    teile.add(kurz);
                    teile.add(kurz.replace('.', ','));
                }
            }
            if (aw.isGeschliffen()) {
                teile.add("geschliffen k240");
            }
        }
        if (a.getWerkstoff() != null) {
            teile.add(a.getWerkstoff().getName());
            teile.add(a.getWerkstoff().getAnzeigename());
        }
        teile.add(a.getMassnorm());
        if (a.getHerstellverfahren() != null) {
            teile.add(a.getHerstellverfahren().name().replace('_', ' '));
        }
        if (a.getFertigungszustand() != null) {
            teile.add(a.getFertigungszustand().name().replace('_', ' '));
        }
        return teile.stream().filter(t -> t != null && !t.isBlank()).map(String::strip).reduce((x, y) -> x + " " + y)
                .orElse("").toLowerCase(Locale.GERMAN);
    }

    /** Maß ohne überflüssige Nullen: 42.40 wird 42.4, 2.00 wird 2. */
    private static String kurz(BigDecimal wert) {
        return wert == null ? null : wert.stripTrailingZeros().toPlainString();
    }
}

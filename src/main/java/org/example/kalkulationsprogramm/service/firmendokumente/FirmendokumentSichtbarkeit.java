package org.example.kalkulationsprogramm.service.firmendokumente;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentOrdner;
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe;

/**
 * Was der Angemeldete im (geladenen) Ordnerbaum sehen und ändern darf – einmal im Speicher berechnet für Baum,
 * Suche und Fällige. Rechte hängen am obersten Ordner, Unterordner erben. Reine Berechnung ohne Datenbank.
 */
public final class FirmendokumentSichtbarkeit {

    private final List<FirmendokumentOrdner> ordner;
    private final Map<Long, FirmendokumentZugriffsStufe> stufen;
    private final Set<Long> eingeschraenkt;
    private final boolean allesSichtbar;

    private FirmendokumentSichtbarkeit(List<FirmendokumentOrdner> ordner, Map<Long, FirmendokumentZugriffsStufe> stufen,
            Set<Long> eingeschraenkt, boolean allesSichtbar) {
        this.ordner = ordner;
        this.stufen = stufen;
        this.eingeschraenkt = eingeschraenkt;
        this.allesSichtbar = allesSichtbar;
    }

    /** Kein Ordner ist eingeschränkt: alle sehen und ändern alles (Stand vor den Ordnerrechten). */
    static FirmendokumentSichtbarkeit alles(List<FirmendokumentOrdner> ordner) {
        return new FirmendokumentSichtbarkeit(List.copyOf(ordner), Map.of(), Set.of(), true);
    }

    /**
     * @param ordner         geladene Ordner (sortiert, wie sie angezeigt werden)
     * @param admin          Admins sehen und ändern alles
     * @param stufeJeWurzel  höchste Stufe der eigenen Abteilungen je oberstem Ordner
     * @param vollstaendig   {@code false}, wenn die Ordnerliste an der Obergrenze abgeschnitten wurde – dann gilt
     *                       nie „alles sichtbar“, weil nicht geladene Ordner unbekannt sind
     */
    static FirmendokumentSichtbarkeit berechne(List<FirmendokumentOrdner> ordner, boolean admin,
            Map<Long, FirmendokumentZugriffsStufe> stufeJeWurzel, boolean vollstaendig) {
        Map<Long, FirmendokumentOrdner> jeId = new HashMap<>();
        ordner.forEach(o -> jeId.put(o.getId(), o));
        Map<Long, FirmendokumentZugriffsStufe> stufen = new HashMap<>();
        Set<Long> eingeschraenkt = new HashSet<>();
        boolean alleSichtbar = vollstaendig || admin;
        for (FirmendokumentOrdner o : ordner) {
            FirmendokumentOrdner wurzel = wurzel(o, jeId);
            boolean zu = wurzel != null && wurzel.isRechteEingeschraenkt();
            if (zu) {
                eingeschraenkt.add(o.getId());
            }
            FirmendokumentZugriffsStufe stufe = stufe(wurzel, zu, admin, stufeJeWurzel);
            stufen.put(o.getId(), stufe);
            alleSichtbar &= stufe.darfAnsehen();
        }
        return new FirmendokumentSichtbarkeit(List.copyOf(ordner), stufen, eingeschraenkt, alleSichtbar);
    }

    private static FirmendokumentZugriffsStufe stufe(FirmendokumentOrdner wurzel, boolean eingeschraenkt, boolean admin,
            Map<Long, FirmendokumentZugriffsStufe> stufeJeWurzel) {
        if (admin) {
            return FirmendokumentZugriffsStufe.AENDERN;
        }
        if (wurzel == null) {
            return FirmendokumentZugriffsStufe.KEINE;
        }
        if (!eingeschraenkt) {
            return FirmendokumentZugriffsStufe.AENDERN;
        }
        return stufeJeWurzel.getOrDefault(wurzel.getId(), FirmendokumentZugriffsStufe.KEINE);
    }

    /** Oberster Ordner über die geladenen Ordner; {@code null} bei Zyklus oder nicht geladenem Elternordner. */
    private static FirmendokumentOrdner wurzel(FirmendokumentOrdner ordner, Map<Long, FirmendokumentOrdner> jeId) {
        Set<Long> gesehen = new HashSet<>();
        FirmendokumentOrdner o = ordner;
        while (gesehen.add(o.getId())) {
            if (o.getParent() == null) {
                return o;
            }
            o = jeId.get(o.getParent().getId());
            if (o == null) {
                return null;
            }
        }
        return null;
    }

    /** Ist jeder Ordner sichtbar? Dann brauchen Suche und Fällige keinen Ordner-Filter. */
    public boolean allesSichtbar() {
        return allesSichtbar;
    }

    /** Stufe eines Ordners; unbekannte Ordner gelten nur bei „alles sichtbar“ als änderbar, sonst als unsichtbar. */
    public FirmendokumentZugriffsStufe stufe(Long ordnerId) {
        FirmendokumentZugriffsStufe stufe = stufen.get(ordnerId);
        if (stufe != null) {
            return stufe;
        }
        return allesSichtbar ? FirmendokumentZugriffsStufe.AENDERN : FirmendokumentZugriffsStufe.KEINE;
    }

    /** Ist der oberste Ordner dieses Zweigs eingeschränkt (Schloss-Symbol)? */
    public boolean eingeschraenkt(Long ordnerId) {
        return eingeschraenkt.contains(ordnerId);
    }

    /** Sichtbare Ordner in der geladenen Reihenfolge. */
    public List<FirmendokumentOrdner> sichtbareOrdner() {
        return ordner.stream().filter(o -> stufe(o.getId()).darfAnsehen()).toList();
    }

    public Set<Long> sichtbareOrdnerIds() {
        Set<Long> ids = new LinkedHashSet<>();
        sichtbareOrdner().forEach(o -> ids.add(o.getId()));
        return ids;
    }
}

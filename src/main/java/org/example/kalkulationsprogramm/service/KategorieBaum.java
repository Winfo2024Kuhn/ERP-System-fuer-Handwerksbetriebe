package org.example.kalkulationsprogramm.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Produktkategorie;

/**
 * Der Ordnerbaum der Produktkategorien im Speicher, aufgebaut aus einer einzigen Abfrage
 * ({@code findAllWithParent}). Erspart das Nachladen der Unterordner Ebene für Ebene.
 */
final class KategorieBaum {

    private final Map<Long, Produktkategorie> nachId = new LinkedHashMap<>();
    private final Map<Long, List<Long>> kinder = new HashMap<>();
    private final List<Long> hauptordner = new ArrayList<>();

    private KategorieBaum(Collection<Produktkategorie> alle) {
        for (Produktkategorie k : alle) {
            nachId.put(k.getId(), k);
        }
        for (Produktkategorie k : alle) {
            Long parentId = parentId(k);
            if (parentId == null || !nachId.containsKey(parentId)) {
                hauptordner.add(k.getId());
            } else {
                kinder.computeIfAbsent(parentId, x -> new ArrayList<>()).add(k.getId());
            }
        }
    }

    static KategorieBaum aus(Collection<Produktkategorie> alle) {
        return new KategorieBaum(alle);
    }

    static Long parentId(Produktkategorie k) {
        return k.getUebergeordneteKategorie() != null ? k.getUebergeordneteKategorie().getId() : null;
    }

    Collection<Long> ids() {
        return nachId.keySet();
    }

    Produktkategorie get(Long id) {
        return nachId.get(id);
    }

    List<Long> hauptordner() {
        return hauptordner;
    }

    List<Long> kinder(Long id) {
        return kinder.getOrDefault(id, List.of());
    }

    boolean hatKinder(Long id) {
        return !kinder(id).isEmpty();
    }

    /**
     * Der Ordner selbst und alle Unterordner auf allen Ebenen (Breitensuche, Ordner zuerst).
     * Jeder Ordner kommt höchstens einmal vor – auch bei kaputten Altdaten mit Ring.
     */
    List<Long> mitUnterordnern(Long id) {
        Set<Long> ergebnis = new LinkedHashSet<>();
        Deque<Long> offen = new ArrayDeque<>();
        offen.add(id);
        while (!offen.isEmpty()) {
            Long aktuell = offen.poll();
            if (ergebnis.add(aktuell)) {
                offen.addAll(kinder(aktuell));
            }
        }
        return new ArrayList<>(ergebnis);
    }

    String pfad(Long id) {
        Deque<String> namen = new ArrayDeque<>();
        Produktkategorie aktuell = nachId.get(id);
        // Schutz gegen Zyklen in Altdaten: höchstens so viele Schritte, wie es Ordner gibt.
        for (int i = 0; aktuell != null && i <= nachId.size(); i++) {
            namen.addFirst(aktuell.getBezeichnung());
            Long parentId = parentId(aktuell);
            aktuell = parentId != null ? nachId.get(parentId) : null;
        }
        return String.join(" > ", namen);
    }
}

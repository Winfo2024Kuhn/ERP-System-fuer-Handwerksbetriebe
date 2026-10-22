package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Telefonanlage im Speicher für Tests: Telefonbücher mit Einträgen, Aufrufe werden mitgeschrieben. */
class FakeTelefonbuchAnlage implements TelefonbuchAnlage {

    final Map<String, String> buecher = new LinkedHashMap<>();
    final Map<String, Map<String, TelefonbuchEintrag>> eintraege = new LinkedHashMap<>();
    final List<String> aufrufe = new ArrayList<>();
    final Set<String> abgelehnteNamen = new HashSet<>();
    TelefonAnlageException fehler;
    CountDownLatch angehalten;
    CountDownLatch weiter;
    private int naechsteId = 1;
    private int naechsteUid = 100;

    @Override
    public synchronized Telefonbuch stelleTelefonbuchBereit(TelefonZugang zugang, String name, String bekannteId) {
        warteFallsAngehalten();
        if (fehler != null) {
            throw fehler;
        }
        aufrufe.add("bereit");
        if (bekannteId != null && name.equals(buecher.get(bekannteId))) {
            return new Telefonbuch(bekannteId, false);
        }
        for (Map.Entry<String, String> b : buecher.entrySet()) {
            if (b.getValue().equals(name)) {
                return new Telefonbuch(b.getKey(), true);
            }
        }
        String id = String.valueOf(naechsteId++);
        buecher.put(id, name);
        eintraege.put(id, new LinkedHashMap<>());
        aufrufe.add("anlegen:" + id);
        return new Telefonbuch(id, true);
    }

    @Override
    public synchronized Set<String> ladeEintragsIds(TelefonZugang zugang, String telefonbuchId) {
        aufrufe.add("lesen");
        return new HashSet<>(eintraege.get(telefonbuchId).keySet());
    }

    @Override
    public synchronized String speichereEintrag(TelefonZugang zugang, String telefonbuchId, TelefonbuchEintrag eintrag,
                                                String eintragsId) {
        if (abgelehnteNamen.contains(eintrag.name())) {
            throw new TelefonAnlageException(TelefonAnlageException.Grund.UNERWARTETE_ANTWORT);
        }
        if (fehler != null) {
            throw fehler;
        }
        Map<String, TelefonbuchEintrag> buch = eintraege.get(telefonbuchId);
        String uid = eintragsId != null && buch.containsKey(eintragsId) ? eintragsId : String.valueOf(naechsteUid++);
        aufrufe.add((eintragsId == null ? "neu:" : "aendern:") + uid);
        buch.put(uid, eintrag);
        return uid;
    }

    @Override
    public synchronized void loescheEintrag(TelefonZugang zugang, String telefonbuchId, String eintragsId) {
        aufrufe.add("loeschen:" + eintragsId);
        eintraege.get(telefonbuchId).remove(eintragsId);
    }

    @Override
    public synchronized boolean loescheTelefonbuch(TelefonZugang zugang, String name, String bekannteId) {
        if (fehler != null) {
            throw fehler;
        }
        String ziel = bekannteId != null && name.equals(buecher.get(bekannteId)) ? bekannteId : null;
        if (ziel == null) {
            ziel = buecher.entrySet().stream().filter(b -> b.getValue().equals(name)).map(Map.Entry::getKey)
                    .findFirst().orElse(null);
        }
        if (ziel == null || "0".equals(ziel)) {
            return false;
        }
        aufrufe.add("telefonbuch-loeschen:" + ziel);
        buecher.remove(ziel);
        eintraege.remove(ziel);
        return true;
    }

    /** Namen der Einträge im Telefonbuch {@code id}. */
    synchronized List<String> namen(String id) {
        return eintraege.get(id).values().stream().map(TelefonbuchEintrag::name).sorted().toList();
    }

    private void warteFallsAngehalten() {
        if (angehalten == null) {
            return;
        }
        angehalten.countDown();
        try {
            weiter.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

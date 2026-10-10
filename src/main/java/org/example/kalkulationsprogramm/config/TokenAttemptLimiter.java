package org.example.kalkulationsprogramm.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Progressive Fehlversuchssperre eines einzelnen Serverprozesses, ohne wartende Threads.
 *
 * <p>Einträge gibt es nur, solange ein Versuch läuft oder Fehlversuche gezählt werden: Erfolgreiche
 * Anmeldungen belegen keinen Platz. IPv6-Adressen zählen je /64-Netz, weil ein einzelner Anschluss
 * meist ein ganzes /64 bekommt – sonst ließe sich die Tabelle mit Adressen aus einem Netz füllen.</p>
 */
public final class TokenAttemptLimiter {
    private static final long RESET_MILLIS = 30 * 60_000L;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final LongSupplier clock;
    private final int capacity;

    public TokenAttemptLimiter() { this(() -> System.nanoTime() / 1_000_000, 10_000); }
    TokenAttemptLimiter(LongSupplier clock, int capacity) {
        this.clock = clock;
        this.capacity = capacity;
    }

    public record Attempt<T>(Optional<T> value, long retryAfterSeconds) {}

    public <T> Attempt<T> attempt(String client, Supplier<Optional<T>> verification) {
        String key = schluessel(client);
        Bucket bucket;
        synchronized (buckets) {
            long now = clock.getAsLong();
            bucket = buckets.get(key);
            if (bucket == null) {
                if (buckets.size() >= capacity) raeumeAuf(now);
                if (buckets.size() >= capacity) return new Attempt<>(Optional.empty(), 300);
                bucket = new Bucket(now);
                buckets.put(key, bucket);
            }
            bucket.users++;
            bucket.lastTouched = now;
        }
        try {
            synchronized (bucket) {
                long now = clock.getAsLong();
                if (bucket.failures > 0 && now - bucket.lastFailure >= RESET_MILLIS) {
                    bucket.failures = 0;
                    bucket.blockedUntil = 0;
                }
                if (now < bucket.blockedUntil) {
                    return new Attempt<>(Optional.empty(), (bucket.blockedUntil - now + 999) / 1000);
                }
                Optional<T> value = verification.get();
                if (value.isPresent()) return new Attempt<>(value, 0);
                bucket.failures = Math.min(12, bucket.failures + 1);
                long wait = bucket.failures < 3 ? 0 : Math.min(300, 2L << (bucket.failures - 3));
                bucket.lastFailure = clock.getAsLong();
                bucket.blockedUntil = bucket.lastFailure + wait * 1000;
                return new Attempt<>(Optional.empty(), wait);
            }
        } finally {
            synchronized (buckets) {
                bucket.users--;
                bucket.lastTouched = clock.getAsLong();
                // Ohne Fehlversuche nichts merken: Erfolgreiche Geräte belegen keinen Platz.
                if (bucket.users == 0 && bucket.failures == 0) buckets.remove(key, bucket);
            }
        }
    }

    int eintraege() {
        synchronized (buckets) { return buckets.size(); }
    }

    /**
     * Platz schaffen: zuerst verfallene Einträge, dann nicht gesperrte. Eine laufende Sperre wird nie
     * verdrängt – sonst könnte ein Angreifer seine eigene Sperre durch neue Adressen aufheben.
     */
    private void raeumeAuf(long now) {
        buckets.values().removeIf(b -> b.users == 0 && now - b.lastTouched >= RESET_MILLIS);
        if (buckets.size() >= capacity) {
            buckets.values().removeIf(b -> b.users == 0 && now >= b.blockedUntil);
        }
    }

    /** IPv4 je Adresse, IPv6 je /64-Netz. Unlesbare Werte bleiben, wie sie sind. */
    static String schluessel(String client) {
        if (client == null || client.indexOf(':') < 0) return client;
        try {
            byte[] adresse = InetAddress.getByName(client).getAddress();
            if (adresse.length != 16) return client;
            return HexFormat.of().formatHex(adresse, 0, 8) + "::/64";
        } catch (UnknownHostException | IllegalArgumentException e) {
            return client;
        }
    }

    private static final class Bucket {
        int users;
        int failures;
        long lastTouched;
        long lastFailure;
        long blockedUntil;
        Bucket(long now) { lastTouched = now; }
    }
}

package org.example.kalkulationsprogramm.service.openfile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EinmalLinksTest {

    private static final String HOST = "erp.firma-mustermann.de";

    private final AtomicLong uhr = new AtomicLong(1_000_000L);
    private EinmalLinks links;

    @BeforeEach
    void setUp() {
        links = new EinmalLinks();
        links.jetzt = uhr::get;
    }

    @Test
    void schluesselIst43ZeichenUrlSicherUndJedesMalNeu() {
        Set<String> schluessel = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String neu = links.neu("skript", HOST);
            assertThat(neu).matches("[A-Za-z0-9_-]{43}");
            schluessel.add(neu);
        }
        assertThat(schluessel).hasSize(10);
    }

    @Test
    void linkLaesstSichGenauEinmalEinloesen() {
        String schluessel = links.neu("#!/bin/bash\necho Max Mustermann\n", HOST);

        assertThat(links.einloesen(schluessel, HOST)).contains("#!/bin/bash\necho Max Mustermann\n");
        assertThat(links.einloesen(schluessel, HOST)).isEmpty();
    }

    @Test
    void rechnernameOhneRuecksichtAufGrossschreibung() {
        String schluessel = links.neu("skript", "ERP.Firma-Mustermann.de");

        assertThat(links.einloesen(schluessel, HOST)).contains("skript");
    }

    @Test
    void andererRechnernameBekommtNichtsUndVerbrauchtDenLinkNicht() {
        String schluessel = links.neu("skript", HOST);

        assertThat(links.einloesen(schluessel, "boese.example.com")).isEmpty();
        assertThat(links.einloesen(schluessel, HOST)).contains("skript");
    }

    @Test
    void nachZehnMinutenIstDerLinkAbgelaufen() {
        String frisch = links.neu("frisch", HOST);
        String alt = links.neu("alt", HOST);

        uhr.addAndGet(EinmalLinks.GUELTIG.toMillis() - 1);
        assertThat(links.einloesen(frisch, HOST)).contains("frisch");

        uhr.addAndGet(1);
        assertThat(links.einloesen(alt, HOST)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "kurz", "../../etc/passwd", "'; DROP TABLE x; --", "<script>alert(1)</script>",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" })
    void ungueltigerSchluesselBekommtNichts(String schluessel) {
        links.neu("skript", HOST);

        assertThat(links.einloesen(schluessel, HOST)).isEmpty();
    }

    @Test
    void ohneRechnernameGibtEsNichts() {
        String schluessel = links.neu("skript", HOST);

        assertThat(links.einloesen(schluessel, null)).isEmpty();
    }

    @Test
    void ueberlangerSchluesselBekommtNichts() {
        assertThat(links.einloesen("A".repeat(10_001), HOST)).isEmpty();
    }

    @Test
    void beiZuVielenOffenenLinksFaelltDerAeltesteWeg() {
        String aeltester = links.neu("0", HOST);
        List<String> weitere = new ArrayList<>();
        for (int i = 1; i <= EinmalLinks.MAX_OFFEN; i++) {
            uhr.incrementAndGet();
            weitere.add(links.neu(String.valueOf(i), HOST));
        }

        assertThat(links.einloesen(aeltester, HOST)).isEmpty();
        assertThat(links.einloesen(weitere.get(0), HOST)).contains("1");
        assertThat(links.einloesen(weitere.get(weitere.size() - 1), HOST)).contains(String.valueOf(EinmalLinks.MAX_OFFEN));
    }

    @Test
    void abgelaufeneLinksZaehlenNichtZurObergrenze() {
        for (int i = 0; i < EinmalLinks.MAX_OFFEN; i++) {
            links.neu("alt", HOST);
        }
        uhr.addAndGet(EinmalLinks.GUELTIG.toMillis());
        String neu = links.neu("neu", HOST);

        assertThat(links.einloesen(neu, HOST)).contains("neu");
    }

    @Test
    void gleichzeitigeAbrufeBekommenDenInhaltNurEinmal() throws Exception {
        String schluessel = links.neu("skript", HOST);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Optional<String>>> abrufe = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                abrufe.add(() -> links.einloesen(schluessel, HOST));
            }
            long treffer = 0;
            for (Future<Optional<String>> f : pool.invokeAll(abrufe)) {
                if (f.get().isPresent()) {
                    treffer++;
                }
            }
            assertThat(treffer).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}

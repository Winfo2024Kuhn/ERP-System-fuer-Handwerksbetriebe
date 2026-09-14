package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProjektDokumentRepositoryLockTest {
    @Autowired ProjektDokumentRepository dokumente;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void zweiteZahlungWartetAufCommitUndLiestDanachDenBezahltenStatus() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Long id = transaction.execute(status -> {
            var rechnung = new ProjektGeschaeftsdokument();
            rechnung.setDokumentid("R-MUSTER-LOCK");
            rechnung.setGeschaeftsdokumentart("Rechnung");
            rechnung.setOriginalDateiname("musterrechnung.pdf");
            rechnung.setGespeicherterDateiname("musterrechnung-lock.pdf");
            return dokumente.saveAndFlush(rechnung).getId();
        });
        CountDownLatch gesperrt = new CountDownLatch(1);
        CountDownLatch freigabe = new CountDownLatch(1);
        CountDownLatch zweiterLeserGestartet = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ersteZahlung = executor.submit(() -> transaction.execute(status -> {
                var rechnung = dokumente.findGeschaeftsdokumentByIdForUpdate(id).orElseThrow();
                assertThat(rechnung.isBezahlt()).isFalse();
                gesperrt.countDown();
                warte(freigabe);
                rechnung.setBezahlt(true);
                dokumente.save(rechnung);
                return true;
            }));
            try {
                warte(gesperrt);
                var zweiteZahlung = executor.submit(() -> transaction.execute(status -> {
                    zweiterLeserGestartet.countDown();
                    var rechnung = dokumente.findGeschaeftsdokumentByIdForUpdate(id).orElseThrow();
                    return rechnung.isBezahlt();
                }));
                warte(zweiterLeserGestartet);
                try {
                    assertThatThrownBy(() -> zweiteZahlung.get(250, TimeUnit.MILLISECONDS))
                            .isInstanceOf(TimeoutException.class);
                } finally {
                    freigabe.countDown();
                }
                assertThat(ersteZahlung.get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(zweiteZahlung.get(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                freigabe.countDown();
            }
        } finally {
            transaction.executeWithoutResult(status -> dokumente.deleteById(id));
        }
    }

    private static void warte(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}

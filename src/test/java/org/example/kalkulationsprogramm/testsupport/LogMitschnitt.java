package org.example.kalkulationsprogramm.testsupport;

import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;

/** Schneidet mit, was eine Klasse beim Ausführen ins Log schreibt – etwa für DSGVO-Prüfungen der Logzeilen. */
public final class LogMitschnitt {

    private LogMitschnitt() {
    }

    /**
     * Ein Element je Log-Eintrag: die fertige Meldung, bei mitgeloggtem Fehler in den Folgezeilen
     * der ganze Stacktrace (samt Meldungen der Ursachen). Schneidet alle Stufen bis DEBUG mit, auch
     * wenn die Log-Konfiguration sonst erst ab INFO schreibt.
     */
    public static List<String> von(Class<?> quelle, Runnable aktion) {
        Logger logger = (Logger) LoggerFactory.getLogger(quelle);
        Level vorherigeStufe = logger.getLevel();
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        logger.addAppender(log);
        logger.setLevel(Level.DEBUG);
        try {
            aktion.run();
        } finally {
            logger.setLevel(vorherigeStufe);
            logger.detachAppender(log);
        }
        return log.list.stream()
                .map(eintrag -> eintrag.getThrowableProxy() == null ? eintrag.getFormattedMessage()
                        : eintrag.getFormattedMessage() + "\n" + ThrowableProxyUtil.asString(eintrag.getThrowableProxy()))
                .toList();
    }
}

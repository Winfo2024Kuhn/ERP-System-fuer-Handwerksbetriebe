package org.example.kalkulationsprogramm.service.einkauf;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EinkaufAnalyseWorkerTest {
    @Mock private EinkaufAngebotsAnalyseService analyse;
    @InjectMocks private EinkaufAnalyseWorker worker;

    @Test
    void jederEventStartetNurSeinenPersistiertenJob() {
        worker.nachCommit(new EinkaufAnalyseWorker.AnalyseAngefordert(71L));
        worker.nachCommit(new EinkaufAnalyseWorker.AnalyseAngefordert(72L));

        verify(analyse).analysiere(71L);
        verify(analyse).analysiere(72L);
    }

    @Test
    void fehlerhafterEventStartetKeinenJob() {
        worker.nachCommit(new EinkaufAnalyseWorker.AnalyseAngefordert(null));
        verifyNoInteractions(analyse);
    }
}

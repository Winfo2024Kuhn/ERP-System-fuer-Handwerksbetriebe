import { useMemo, useRef, useState } from 'react';
import { ZuordnungModal } from '../../components/ZuordnungModal';
import { useToast } from '../../components/ui/toast';
import { RechnungsLeiste } from './RechnungsLeiste';
import {
    mitLokalZugeordnet,
    naechsteOffeneRechnungId,
    rechnungenDerKette,
    startRechnungId,
    type LeistenDokument,
} from './rechnungsLeisteLogik';

export interface KettenZuordnungDokument extends LeistenDokument {
    pdfUrl: string | null;
}

interface KettenZuordnungProps {
    kette: { lieferantName: string | null; dokumente: KettenZuordnungDokument[] };
    /** `gespeichert` = mindestens eine Rechnung wurde gespeichert, die Seite sollte neu laden. */
    onSchliessen: (gespeichert: boolean) => void;
}

/**
 * „Projekten zuordnen“ für eine ganze Bestellung: Hat sie mehrere Rechnungen,
 * steht oben im Dialog die Rechnungs-Leiste. Nach dem Speichern springt der
 * Dialog zur nächsten offenen Rechnung und schließt erst, wenn keine mehr offen ist.
 */
export function KettenZuordnung({ kette, onSchliessen }: KettenZuordnungProps) {
    const toast = useToast();
    const alleRechnungen = useMemo(() => rechnungenDerKette(kette.dokumente), [kette.dokumente]);
    const [lokalZugeordnet, setLokalZugeordnet] = useState<ReadonlySet<number>>(() => new Set());
    const [aktuelleId, setAktuelleId] = useState(() => startRechnungId(alleRechnungen));
    const [leisteFokussieren, setLeisteFokussieren] = useState(false);
    // Ref statt State: onClose läuft im selben Durchgang wie onSuccess.
    const gespeichertRef = useRef(false);

    const rechnungen = useMemo(() => mitLokalZugeordnet(alleRechnungen, lokalZugeordnet), [alleRechnungen, lokalZugeordnet]);
    const aktuelle = rechnungen.find(r => r.id === aktuelleId) ?? rechnungen[0];
    if (!aktuelle) return null;

    const gespeichert = (): 'offen-lassen' | void => {
        gespeichertRef.current = true;
        const neu = new Set(lokalZugeordnet).add(aktuelle.id);
        setLokalZugeordnet(neu);
        const naechsteId = naechsteOffeneRechnungId(mitLokalZugeordnet(rechnungen, neu), aktuelle.id);
        if (naechsteId == null) return;
        const naechste = rechnungen.find(r => r.id === naechsteId);
        toast.success(`Rechnung ${aktuelle.dokumentNummer ?? aktuelle.dateiname} zugeordnet – weiter mit ${naechste?.dokumentNummer ?? naechste?.dateiname ?? 'der nächsten'}.`);
        setLeisteFokussieren(false);
        setAktuelleId(naechsteId);
        return 'offen-lassen';
    };

    return (
        <ZuordnungModal
            // Neue Rechnung = Dialog lädt deren Daten frisch
            key={aktuelle.id}
            geschaeftsdokumentId={aktuelle.id}
            dokumentNummer={aktuelle.dokumentNummer}
            lieferantName={kette.lieferantName}
            pdfUrl={aktuelle.pdfUrl}
            rechnungsLeiste={rechnungen.length >= 2 ? (
                <RechnungsLeiste
                    rechnungen={rechnungen}
                    aktivId={aktuelle.id}
                    autoFokus={leisteFokussieren}
                    onWaehlen={id => {
                        setLeisteFokussieren(true);
                        setAktuelleId(id);
                    }}
                />
            ) : undefined}
            onClose={() => onSchliessen(gespeichertRef.current)}
            onSuccess={gespeichert}
        />
    );
}

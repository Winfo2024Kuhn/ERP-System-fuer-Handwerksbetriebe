import { BarChart3, FolderCog, List } from 'lucide-react';
import { Reiter, ReiterPanel } from '../../components/ui/reiter';
import type { OrdnerAenderung } from './api';
import { AuswertungReiter } from './AuswertungReiter';
import { LeistungenReiter, type LeistungenReiterProps } from './LeistungenReiter';
import { OrdnerKopf } from './OrdnerKopf';
import { OrdnerReiter } from './OrdnerReiter';
import { hatUnterordner, type Baum, type Zaehler } from './ordnerLogik';
import type { Leistung, Ordner, ReiterId } from './typen';

export interface OrdnerDetailProps {
    ordner: Ordner;
    baum: Baum;
    zaehler: Map<number, Zaehler>;
    leistungen: Leistung[];
    reiter: ReiterId;
    waehle: (id: number | null, reiter?: ReiterId) => void;

    /** Aktionen im Kopf des Ordners. */
    onUnterordner: () => void;
    onUmbenennen: () => void;
    onVerschieben: () => void;
    onLoeschen: () => void;

    /** Reiter „Leistungen“. */
    suchtext: string;
    onSucheLeeren: () => void;
    oeffneLeistung: LeistungenReiterProps['oeffnen'];
    onLeistungSpeichern: LeistungenReiterProps['onSpeichern'];
    onLeistungLoeschen: (leistung: Leistung) => void;
    onLeistungVerschieben: (leistung: Leistung) => void;
    onZiehenStart: LeistungenReiterProps['onZiehenStart'];
    onZiehenEnde: () => void;
    onUngespeichertLeistung: (ungespeichert: boolean) => void;

    /** Reiter „Ordner“. */
    onOrdnerSpeichern: (aenderung: OrdnerAenderung) => Promise<void>;
    ungespeichertOrdner: boolean;
    onUngespeichertOrdner: (ungespeichert: boolean) => void;

    /** Reiter „Auswertung“. */
    jahr: string;
    onJahr: (jahr: string) => void;
}

/**
 * Rechte Karte mit dem gewählten Ordner: Kopf mit Pfad und Aktionen, darunter
 * die Reiter Leistungen, Ordner und Auswertung.
 */
export function OrdnerDetail(props: OrdnerDetailProps) {
    const { ordner, baum, zaehler, reiter, waehle } = props;
    const reiterListe = [
        { id: 'leistungen' as const, label: 'Leistungen', icon: <List className="h-4 w-4" aria-hidden="true" />, anzahl: zaehler.get(ordner.id)?.gesamt ?? 0 },
        { id: 'ordner' as const, label: 'Ordner', icon: <FolderCog className="h-4 w-4" aria-hidden="true" />,
            kennzeichen: props.ungespeichertOrdner ? { art: 'punkt' as const, text: 'Nicht gespeichert' } : undefined },
        { id: 'auswertung' as const, label: 'Auswertung', icon: <BarChart3 className="h-4 w-4" aria-hidden="true" /> },
    ];

    return (
        <>
            <OrdnerKopf
                ordner={ordner}
                baum={baum}
                onWaehlen={(id) => waehle(id)}
                onUnterordner={props.onUnterordner}
                onUmbenennen={props.onUmbenennen}
                onVerschieben={props.onVerschieben}
                onLoeschen={props.onLoeschen}
            />
            <div>
                <Reiter reiter={reiterListe} aktiv={reiter} onWechsel={(r) => waehle(ordner.id, r)}
                    ariaLabel={`Bereiche von „${ordner.name}“`} idPrefix="leistungen-seite" />
                {/* Leistungen und Ordner bleiben beim Reiterwechsel aufgebaut – angefangene Eingaben gehen nicht verloren. */}
                <ReiterPanel idPrefix="leistungen-seite" id="leistungen" hidden={reiter !== 'leistungen'} className="pt-5">
                    <LeistungenReiter
                        key={ordner.id}
                        ordner={ordner}
                        baum={baum}
                        leistungen={props.leistungen}
                        suchtext={props.suchtext}
                        onSucheLeeren={props.onSucheLeeren}
                        oeffnen={props.oeffneLeistung}
                        onSpeichern={props.onLeistungSpeichern}
                        onLoeschen={props.onLeistungLoeschen}
                        onVerschieben={props.onLeistungVerschieben}
                        onZiehenStart={props.onZiehenStart}
                        onZiehenEnde={props.onZiehenEnde}
                        onUngespeichert={props.onUngespeichertLeistung}
                    />
                </ReiterPanel>
                <ReiterPanel idPrefix="leistungen-seite" id="ordner" hidden={reiter !== 'ordner'} className="pt-5">
                    <OrdnerReiter
                        key={ordner.id}
                        ordner={ordner}
                        onSpeichern={props.onOrdnerSpeichern}
                        onUngespeichert={props.onUngespeichertOrdner}
                    />
                </ReiterPanel>
                {reiter === 'auswertung' && (
                    <ReiterPanel idPrefix="leistungen-seite" id="auswertung" className="pt-5">
                        <AuswertungReiter
                            key={ordner.id}
                            ordner={ordner}
                            hatUnterordner={hatUnterordner(baum, ordner.id)}
                            jahr={props.jahr}
                            onJahr={props.onJahr}
                            onWaehlen={(id) => waehle(id, 'auswertung')}
                        />
                    </ReiterPanel>
                )}
            </div>
        </>
    );
}

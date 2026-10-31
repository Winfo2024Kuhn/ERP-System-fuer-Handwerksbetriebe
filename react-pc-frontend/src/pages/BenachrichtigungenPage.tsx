import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AlertTriangle, ArrowRight, Bell, Check, CheckCheck, CheckCircle2, LayoutList, RefreshCw } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { InfoAbschnitt, InfoButton } from '../components/ui/info-button';
import { useToast } from '../components/ui/toast';
import { cn } from '../lib/utils';
import { refreshEmails, refreshNotifications } from '../lib/notificationRefresh';
import {
    MAX_IDS_PRO_ANFRAGE, enthaeltEmailKategorie, hatZiel, ohneEintrag, ohneSpalte, summaryUrl,
    type NotificationSummary, type RecentItemDto,
} from '../components/layout/notification-helpers';
import {
    EINTRAG_FARBEN, EINTRAG_ICONS, GRUPPE_BETRIEB, gruppeZuId, gruppiere, themaAusAdresse,
    type BenachrichtigungsGruppe, type GefuellteGruppe, type Rest,
} from '../features/benachrichtigungen/gruppen';
import { formatZeitpunkt } from '../features/benachrichtigungen/zeitpunkt';

/**
 * Seite „Benachrichtigungen“: alle offenen Meldungen nach Themen sortiert.
 * Die Glocke oben rechts führt hierher und zeigt selbst nur noch die Zahl.
 *
 * Was erledigt ist, merkt sich der Server pro Benutzer. Die Seite blendet
 * Erledigtes sofort aus (optimistisch), meldet es dem Server und lädt danach
 * den echten Stand – schlägt das Melden fehl, taucht die Meldung wieder auf.
 */

type Status = 'laden' | 'bereit' | 'fehler';

const zahl = (n: number) => n.toLocaleString('de-DE');

function postJson(url: string, body: unknown): Promise<Response> {
    // Den CSRF-Header setzt der globale Interceptor in main.tsx.
    return fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
}

/** Unvollständige Antworten (fehlende Listen) nicht bis in die Anzeige durchreichen. */
function normalisiere(roh: Partial<NotificationSummary> | null | undefined): NotificationSummary {
    const categories = Array.isArray(roh?.categories) ? roh.categories : [];
    const recentItems = Array.isArray(roh?.recentItems) ? roh.recentItems : [];
    const totalCount = Number.isFinite(roh?.totalCount)
        ? Number(roh?.totalCount)
        : categories.reduce((summe, c) => summe + (Number(c.count) || 0), 0);
    return { totalCount, categories, recentItems };
}

function inStuecken<T>(liste: T[], groesse: number): T[][] {
    const stuecke: T[][] = [];
    for (let i = 0; i < liste.length; i += groesse) stuecke.push(liste.slice(i, i + groesse));
    return stuecke;
}

function untertitel(status: Status, total: number): string {
    if (status !== 'bereit') return 'Alles, was neu ist oder auf Sie wartet – nach Themen sortiert.';
    if (total === 0) return 'Keine neuen Meldungen.';
    return `${zahl(total)} ${total === 1 ? 'neue Meldung' : 'neue Meldungen'} – nach Themen sortiert.`;
}

export default function BenachrichtigungenPage() {
    const toast = useToast();
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const [data, setData] = useState<NotificationSummary | null>(null);
    const [status, setStatus] = useState<Status>('laden');
    const [neuLaden, setNeuLaden] = useState(false);

    const abortRef = useRef<AbortController | null>(null);
    // Solange noch „Erledigt“-Meldungen unterwegs sind, kein Zwischenstand vom
    // Server übernehmen – sonst blitzt gerade Erledigtes kurz wieder auf.
    const laufendeMeldungen = useRef(0);
    const aktivRef = useRef(true);
    // Unser eigenes notifications:refresh (für die Glocke) nicht selbst noch einmal beantworten.
    const eigenesEreignis = useRef(false);

    // Ergebnisse erst in den Promise-Callbacks übernehmen, nicht synchron im Effekt.
    // Den Ladehinweis für den Knopf schaltet `ladenVomNutzer` vorab an.
    const laden = useCallback((vomNutzer = false): Promise<void> => {
        if (laufendeMeldungen.current > 0) return Promise.resolve();
        abortRef.current?.abort();
        const controller = new AbortController();
        abortRef.current = controller;
        return fetch(summaryUrl(true), { signal: controller.signal })
            .then(async (res) => {
                if (!res.ok) throw new Error(`Status ${res.status}`);
                const summary = normalisiere(await res.json());
                if (controller.signal.aborted) return;
                setData(summary);
                setStatus('bereit');
            })
            .catch((err: unknown) => {
                if (controller.signal.aborted || (err as Error).name === 'AbortError') return;
                // Ein gescheitertes Nachladen im Hintergrund lässt die bisherige Liste stehen.
                setStatus(vorher => (vorher === 'bereit' ? vorher : 'fehler'));
                if (vomNutzer) toast.error('Benachrichtigungen konnten nicht geladen werden.');
            })
            .finally(() => {
                if (abortRef.current === controller) abortRef.current = null;
                if (vomNutzer) setNeuLaden(false);
            });
    }, [toast]);

    /** „Neu laden“ per Knopf: mit Ladehinweis und Fehler-Toast. */
    const ladenVomNutzer = () => {
        if (laufendeMeldungen.current > 0) return;
        setNeuLaden(true);
        void laden(true);
    };

    useEffect(() => {
        aktivRef.current = true;
        void laden();
        const intervall = setInterval(() => { void laden(); }, 60_000);
        const beiSichtbarkeit = () => {
            if (document.visibilityState === 'visible') void laden();
        };
        // Andere Stellen (E-Mail-Center, Glocke …) melden so, dass sich etwas geändert hat.
        const vonAussen = () => {
            if (!eigenesEreignis.current) void laden();
        };
        document.addEventListener('visibilitychange', beiSichtbarkeit);
        window.addEventListener('notifications:refresh', vonAussen);
        return () => {
            aktivRef.current = false;
            abortRef.current?.abort();
            clearInterval(intervall);
            document.removeEventListener('visibilitychange', beiSichtbarkeit);
            window.removeEventListener('notifications:refresh', vonAussen);
        };
    }, [laden]);

    /**
     * Meldet dem Server, was erledigt ist, lädt danach den echten Stand und
     * sagt der Glocke Bescheid, damit ihre Zahl sofort stimmt.
     */
    const meldeErledigt = useCallback(async (
        anfragen: { url: string; body: unknown }[],
        fehlertext: string,
    ): Promise<boolean> => {
        abortRef.current?.abort();
        laufendeMeldungen.current += 1;
        let ok = true;
        try {
            for (const anfrage of anfragen) {
                if (!(await postJson(anfrage.url, anfrage.body)).ok) {
                    ok = false;
                    break;
                }
            }
        } catch {
            ok = false;
        } finally {
            laufendeMeldungen.current -= 1;
        }
        if (!ok) toast.error(fehlertext);
        if (aktivRef.current) await laden();
        eigenesEreignis.current = true;
        try {
            refreshNotifications();
        } finally {
            eigenesEreignis.current = false;
        }
        return ok;
    }, [laden, toast]);

    const erledigeEintrag = useCallback(async (item: RecentItemDto) => {
        setData(vorher => (vorher ? ohneEintrag(vorher, item) : vorher));
        const ok = await meldeErledigt(
            [{ url: '/api/notifications/erledigt', body: { ids: [item.id] } }],
            'Die Meldung konnte nicht als erledigt markiert werden.',
        );
        // Bei E-Mails setzt der Server die Mail dabei auf gelesen.
        if (ok && item.type === 'EMAIL') refreshEmails();
    }, [meldeErledigt]);

    const oeffneEintrag = useCallback((item: RecentItemDto) => {
        if (!hatZiel(item.link)) return;
        void erledigeEintrag(item);
        navigate(item.link);
    }, [erledigeEintrag, navigate]);

    /**
     * „Alle erledigt“ eines Themas: Der Server merkt sich ALLE aktuellen
     * Meldungen der Kategorien als erledigt, nicht nur die angezeigten. Bei
     * E-Mail-Ordnern liest er die Mails wirklich – das E-Mail-Center lädt neu.
     */
    const erledigeGruppe = useCallback(async (g: GefuellteGruppe) => {
        const kategorien = g.kategorien.map(k => k.type);
        setData(vorher => (vorher ? ohneSpalte(vorher, kategorien, g.gruppe.recentTypes) : vorher));
        const anfragen = kategorien.length > 0
            ? [{ url: '/api/notifications/erledigt/kategorien', body: { kategorien } }]
            : inStuecken(g.eintraege.map(e => e.id), MAX_IDS_PRO_ANFRAGE)
                .map(ids => ({ url: '/api/notifications/erledigt', body: { ids } }));
        const ok = await meldeErledigt(anfragen, 'Die Meldungen konnten nicht als erledigt markiert werden.');
        if (ok && (enthaeltEmailKategorie(kategorien) || g.eintraege.some(e => e.type === 'EMAIL'))) {
            refreshEmails();
        }
    }, [meldeErledigt]);

    const thema = themaAusAdresse(searchParams.get('thema'));
    const gruppen = useMemo(() => (data ? gruppiere(data) : []), [data]);
    const sichtbareGruppen = thema ? gruppen.filter(g => g.gruppe.id === thema) : gruppen;
    const total = data?.totalCount ?? 0;

    const waehleThema = useCallback((id: string | null) => {
        setSearchParams(vorher => {
            const neu = new URLSearchParams(vorher);
            if (id) neu.set('thema', id);
            else neu.delete('thema');
            return neu;
        }, { replace: true });
    }, [setSearchParams]);

    const themen = useMemo<ThemaEintrag[]>(() => {
        const liste: ThemaEintrag[] = [
            { id: null, label: 'Alle', icon: LayoutList, anzahl: total },
            ...gruppen.map(g => ({ id: g.gruppe.id, label: g.gruppe.label, icon: g.gruppe.icon, anzahl: g.anzahl })),
        ];
        // Ein gewähltes Thema ohne Meldungen bleibt sichtbar, damit klar ist, wo man steht.
        const gewaehlt = thema ? gruppeZuId(thema) : undefined;
        if (gewaehlt && !gruppen.some(g => g.gruppe.id === gewaehlt.id)) {
            liste.push({ id: gewaehlt.id, label: gewaehlt.label, icon: gewaehlt.icon, anzahl: 0 });
        }
        return liste;
    }, [gruppen, thema, total]);

    const zeigeThemenleiste = status === 'bereit' && (gruppen.length > 0 || thema !== null);

    return (
        <PageLayout
            className="max-w-6xl"
            ribbonCategory="Übersicht"
            title="Benachrichtigungen"
            subtitle={untertitel(status, total)}
            actions={(
                <Button
                    variant="outline"
                    size="sm"
                    onClick={ladenVomNutzer}
                    disabled={neuLaden || status === 'laden'}
                    title={neuLaden || status === 'laden' ? 'Wird gerade geladen …' : 'Stand vom Server holen'}
                >
                    <RefreshCw aria-hidden="true" className={cn('h-4 w-4', neuLaden && 'motion-safe:animate-spin')} />
                    Neu laden
                </Button>
            )}
        >
            {status === 'laden' && <Ladezustand />}

            {status === 'fehler' && (
                <Fehlerzustand onNeuLaden={() => { setStatus('laden'); ladenVomNutzer(); }} />
            )}

            {status === 'bereit' && !zeigeThemenleiste && <Leerzustand />}

            {zeigeThemenleiste && (
                <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
                    <Themenleiste themen={themen} aktiv={thema} onWechsel={waehleThema} />
                    <div className="min-w-0 flex-1 space-y-6">
                        {sichtbareGruppen.length === 0 ? (
                            <LeeresThema onAlleZeigen={() => waehleThema(null)} />
                        ) : (
                            sichtbareGruppen.map(g => (
                                <GruppenAbschnitt
                                    key={g.gruppe.id}
                                    eintrag={g}
                                    onAlleErledigt={() => { void erledigeGruppe(g); }}
                                    onErledigt={item => { void erledigeEintrag(item); }}
                                    onOeffnen={oeffneEintrag}
                                    onZurUebersicht={link => navigate(link)}
                                />
                            ))
                        )}
                    </div>
                </div>
            )}
        </PageLayout>
    );
}

// ── Themenleiste ─────────────────────────────────────────────────────────

interface ThemaEintrag {
    id: string | null;
    label: string;
    icon: BenachrichtigungsGruppe['icon'];
    anzahl: number;
}

/**
 * Filter nach Thema. Auf breiten Bildschirmen eine Liste links, auf schmalen
 * Chips oben – dieselben Knöpfe, nur anders angeordnet. Das gewählte Thema
 * steht in der Adresse (`?thema=betrieb`), damit Links direkt darauf zeigen.
 */
function Themenleiste({ themen, aktiv, onWechsel }: {
    themen: ThemaEintrag[];
    aktiv: string | null;
    onWechsel: (id: string | null) => void;
}) {
    return (
        <nav aria-label="Themen" className="w-full lg:sticky lg:top-0 lg:w-64 lg:shrink-0">
            <p className="mb-2 hidden px-1 text-xs font-semibold uppercase tracking-wide text-slate-500 lg:block">Themen</p>
            <div
                role="group"
                aria-label="Nach Thema filtern"
                className="flex flex-wrap gap-2 lg:flex-col lg:gap-1 lg:rounded-lg lg:border lg:border-slate-200 lg:bg-white lg:p-2 lg:shadow-sm"
            >
                {themen.map(thema => {
                    const gewaehlt = thema.id === aktiv;
                    const Icon = thema.icon;
                    return (
                        <button
                            key={thema.id ?? 'alle'}
                            type="button"
                            aria-pressed={gewaehlt}
                            onClick={() => onWechsel(thema.id)}
                            className={cn(
                                'inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm transition-colors',
                                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                                'lg:w-full lg:rounded-md lg:border-transparent lg:px-3 lg:py-2',
                                gewaehlt
                                    ? 'border-rose-300 bg-rose-50 font-semibold text-rose-700'
                                    : 'border-slate-200 bg-white font-medium text-slate-600 hover:border-rose-200 hover:text-rose-700 lg:hover:bg-slate-50',
                            )}
                        >
                            <Icon aria-hidden="true" className="h-4 w-4 shrink-0" />
                            <span className="text-left lg:flex-1">{thema.label}</span>
                            <span
                                className={cn(
                                    'min-w-[1.5rem] rounded-full px-1.5 py-0.5 text-center text-xs font-bold tabular-nums',
                                    gewaehlt ? 'bg-rose-100 text-rose-700'
                                        // Gescheiterte Sicherung fällt auch in der Leiste auf.
                                        : thema.id === GRUPPE_BETRIEB && thema.anzahl > 0 ? 'bg-red-100 text-red-700'
                                            : 'bg-slate-100 text-slate-600',
                                )}
                            >
                                {zahl(thema.anzahl)}
                            </span>
                        </button>
                    );
                })}
            </div>
        </nav>
    );
}

// ── Ein Thema mit seinen Meldungen ───────────────────────────────────────

function GruppenAbschnitt({ eintrag, onAlleErledigt, onErledigt, onOeffnen, onZurUebersicht }: {
    eintrag: GefuellteGruppe;
    onAlleErledigt: () => void;
    onErledigt: (item: RecentItemDto) => void;
    onOeffnen: (item: RecentItemDto) => void;
    onZurUebersicht: (link: string) => void;
}) {
    const { gruppe, eintraege, anzahl, reste } = eintrag;
    const betrieb = gruppe.id === GRUPPE_BETRIEB;
    const Icon = gruppe.icon;
    const ueberschriftId = `thema-${gruppe.id}`;

    return (
        <section
            aria-labelledby={ueberschriftId}
            className={cn('rounded-lg border bg-white shadow-sm', betrieb ? 'border-red-200' : 'border-slate-200')}
        >
            <div
                className={cn(
                    'flex flex-wrap items-center gap-x-3 gap-y-2 rounded-t-lg border-b px-5 py-3',
                    betrieb ? 'border-red-100 bg-red-50/70' : 'border-slate-100 bg-slate-50/60',
                )}
            >
                <span className={cn('flex h-8 w-8 shrink-0 items-center justify-center rounded-lg', gruppe.accentBg)}>
                    <Icon aria-hidden="true" className={cn('h-4 w-4', gruppe.accentText)} />
                </span>
                <div className="flex items-center gap-1">
                    <h2 id={ueberschriftId} className="text-base font-semibold text-slate-900">{gruppe.label}</h2>
                    {betrieb && <SicherungInfo />}
                </div>
                <span className={cn('rounded-full px-2 py-0.5 text-xs font-bold tabular-nums', gruppe.accentBg, gruppe.accentText)}>
                    {zahl(anzahl)} {anzahl === 1 ? 'Meldung' : 'Meldungen'}
                </span>
                <Button
                    variant="ghost"
                    size="sm"
                    className="ml-auto"
                    onClick={onAlleErledigt}
                    aria-label={`${gruppe.label}: alle erledigt`}
                    title="Alle Meldungen dieses Themas als erledigt markieren"
                >
                    <CheckCheck aria-hidden="true" className="h-4 w-4" />
                    Alle erledigt
                </Button>
            </div>

            {eintraege.length > 0 && (
                <ul className="divide-y divide-slate-100">
                    {eintraege.map(item => (
                        <EintragZeile
                            key={item.id}
                            item={item}
                            hervorheben={betrieb}
                            onErledigt={() => onErledigt(item)}
                            onOeffnen={() => onOeffnen(item)}
                        />
                    ))}
                </ul>
            )}

            {reste.map(rest => (
                <RestZeile key={rest.kategorie.type} rest={rest} onZurUebersicht={onZurUebersicht} />
            ))}
        </section>
    );
}

/** Info-Knopf direkt neben „Sicherung & Updates“. */
function SicherungInfo() {
    return (
        <InfoButton titel="Sicherung & Updates">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Jede Nacht (standardmäßig um 2 Uhr) sichert das ERP die komplette Datenbank und alle hochgeladenen
                    Dateien – auf diesem Rechner und zusätzlich auf die externe Festplatte und in den Cloud-Ordner
                    (z. B. OneDrive), sofern sie eingerichtet sind. Danach (standardmäßig um 3 Uhr) schaut das ERP nach
                    einer neuen Version. Vor jedem Update wird noch einmal gesichert; klappt das Update nicht, läuft
                    automatisch wieder die alte Version.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Hier erscheint nur etwas, wenn dabei etwas schiefgegangen ist – oder wenn seit über einem Tag keine
                    Sicherung gelaufen ist, z. B. weil der Rechner nachts aus war. Klappt der nächste Lauf, verschwindet
                    die Meldung von selbst. „Erledigt“ blendet nur die Meldung aus, das Problem bleibt; schlägt die
                    nächste Sicherung wieder fehl, kommt eine neue Meldung. Diese Meldungen sehen nur Admins.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

function EintragZeile({ item, hervorheben, onErledigt, onOeffnen }: {
    item: RecentItemDto;
    /** Sicherung & Updates: roter Rand und vollständiger, mehrzeiliger Text. */
    hervorheben: boolean;
    onErledigt: () => void;
    onOeffnen: () => void;
}) {
    const Icon = EINTRAG_ICONS[item.type] ?? Bell;
    const farbe = EINTRAG_FARBEN[item.type] ?? 'text-slate-400';
    const zeit = formatZeitpunkt(item.timestamp);
    const titel = item.title || 'Meldung';

    return (
        <li
            className={cn(
                'flex items-start gap-3 py-3.5 pr-5',
                // „!“: die Trennlinien der Liste (divide-*) färben sonst ab der
                // zweiten Zeile auch den linken Rand grau.
                hervorheben ? 'border-l-4 !border-l-red-500 bg-red-50/40 pl-4' : 'pl-5',
            )}
        >
            <span
                className={cn(
                    'mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg',
                    hervorheben ? 'bg-white' : 'bg-slate-50',
                    farbe,
                )}
            >
                <Icon aria-hidden="true" className="h-4 w-4" />
            </span>
            <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-baseline gap-x-3 gap-y-0.5">
                    <p className="break-words text-sm font-semibold text-slate-900">{titel}</p>
                    {zeit && (
                        <time dateTime={item.timestamp} className="text-xs tabular-nums text-slate-500">{zeit}</time>
                    )}
                </div>
                {item.subtitle && (hervorheben ? (
                    // Mehrzeilig und vollständig: Zeilenumbrüche des Servers bleiben stehen.
                    // React setzt den Text als Text – kein HTML wird ausgeführt.
                    <p className="mt-1 whitespace-pre-line break-words text-sm text-slate-700">{item.subtitle}</p>
                ) : (
                    <p
                        className="line-clamp-2 mt-0.5 break-words text-sm text-slate-500"
                        title={item.subtitle}
                        data-kuerzung-erlaubt="Untertitel steht vollständig im Tooltip und auf der Zielseite"
                    >
                        {item.subtitle}
                    </p>
                ))}
            </div>
            <div className="flex shrink-0 items-center gap-2 self-center">
                {hatZiel(item.link) && (
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={onOeffnen}
                        aria-label={`Öffnen: ${titel}`}
                        title="Öffnen und als erledigt markieren"
                    >
                        <ArrowRight aria-hidden="true" className="h-4 w-4" />
                        Öffnen
                    </Button>
                )}
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={onErledigt}
                    aria-label={`Erledigt: ${titel}`}
                    title="Meldung ausblenden"
                >
                    <Check aria-hidden="true" className="h-4 w-4" />
                    Erledigt
                </Button>
            </div>
        </li>
    );
}

/**
 * Der Server meldet mehr, als er Einträge mitschickt (z. B. 1.234 ungelesene
 * E-Mails): Hinweis „und X weitere“ mit Weg zur passenden Übersicht.
 */
function RestZeile({ rest, onZurUebersicht }: { rest: Rest; onZurUebersicht: (link: string) => void }) {
    const { kategorie, angezeigt, weitere } = rest;
    return (
        <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-t border-slate-100 px-5 py-2.5 text-sm text-slate-600">
            <p>
                {angezeigt > 0 ? (
                    <>und <span className="font-semibold tabular-nums">{zahl(weitere)}</span> weitere</>
                ) : (
                    <span className="font-semibold tabular-nums">{zahl(weitere)}</span>
                )}
                <span className="text-slate-500">{angezeigt > 0 ? ' · ' : ' '}{kategorie.label}</span>
            </p>
            {hatZiel(kategorie.link) && (
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onZurUebersicht(kategorie.link)}
                    aria-label={`${kategorie.label}: zur Übersicht`}
                >
                    Zur Übersicht
                    <ArrowRight aria-hidden="true" className="h-4 w-4" />
                </Button>
            )}
        </div>
    );
}

// ── Zustände ─────────────────────────────────────────────────────────────

function Ladezustand() {
    return (
        <div role="status" aria-label="Benachrichtigungen werden geladen" className="space-y-4">
            {[0, 1, 2].map(i => (
                <div key={i} className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="h-4 w-48 rounded bg-slate-200 motion-safe:animate-pulse" />
                    <div className="mt-4 h-3 w-full rounded bg-slate-100 motion-safe:animate-pulse" />
                    <div className="mt-2 h-3 w-2/3 rounded bg-slate-100 motion-safe:animate-pulse" />
                </div>
            ))}
            <span className="sr-only">Benachrichtigungen werden geladen …</span>
        </div>
    );
}

function Fehlerzustand({ onNeuLaden }: { onNeuLaden: () => void }) {
    return (
        <div role="alert" className="flex flex-col items-center rounded-lg border border-rose-200 bg-white px-6 py-12 text-center shadow-sm">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-rose-50">
                <AlertTriangle aria-hidden="true" className="h-6 w-6 text-rose-600" />
            </span>
            <p className="mt-4 font-semibold text-slate-900">Benachrichtigungen konnten nicht geladen werden.</p>
            <p className="mt-1 text-sm text-slate-500">Bitte prüfen Sie die Verbindung und versuchen Sie es noch einmal.</p>
            <Button size="sm" className="mt-5" onClick={onNeuLaden}>
                <RefreshCw aria-hidden="true" className="h-4 w-4" />
                Neu laden
            </Button>
        </div>
    );
}

function Leerzustand() {
    return (
        <div className="flex flex-col items-center rounded-lg border border-slate-200 bg-white px-6 py-16 text-center shadow-sm">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50">
                <CheckCircle2 aria-hidden="true" className="h-6 w-6 text-emerald-600" />
            </span>
            <p className="mt-4 font-semibold text-slate-900">Keine neuen Benachrichtigungen – alles erledigt.</p>
            <p className="mt-1 text-sm text-slate-500">Sobald etwas Neues ansteht, zeigt die Glocke oben rechts eine Zahl.</p>
        </div>
    );
}

function LeeresThema({ onAlleZeigen }: { onAlleZeigen: () => void }) {
    return (
        <div className="flex flex-col items-center rounded-lg border border-slate-200 bg-white px-6 py-12 text-center shadow-sm">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50">
                <CheckCircle2 aria-hidden="true" className="h-6 w-6 text-emerald-600" />
            </span>
            <p className="mt-4 font-semibold text-slate-900">In diesem Thema ist alles erledigt.</p>
            <Button variant="outline" size="sm" className="mt-5" onClick={onAlleZeigen}>
                Alle Themen zeigen
            </Button>
        </div>
    );
}

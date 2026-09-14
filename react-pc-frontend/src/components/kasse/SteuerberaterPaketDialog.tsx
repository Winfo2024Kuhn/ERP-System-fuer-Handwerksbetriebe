import { useEffect, useState } from "react";
import {
  AlertCircle,
  CheckCircle2,
  Download,
  Loader2,
  Mail,
  X,
} from "lucide-react";
import { Button } from "../ui/button";
import { Select } from "../ui/select-custom";
import { useToast } from "../ui/toast";

interface OffenerPunkt {
  belegId: number;
  belegDatum: string | null;
  bezeichnung: string | null;
  wasFehlt: string;
}
interface Vorpruefung {
  anzahlBelege: number;
  offenePunkte: OffenerPunkt[];
  beraternummerFehlt: boolean;
  mandantennummerFehlt: boolean;
}
export interface SteuerberaterPaketAnhang {
  dateiname: string;
  blob: Blob;
  jahr: number;
  monat: number;
}

export function SteuerberaterPaketDialog({
  offen,
  onClose,
  onEmailPaket,
}: {
  offen: boolean;
  onClose: () => void;
  onEmailPaket: (anhang: SteuerberaterPaketAnhang) => void;
}) {
  const toast = useToast();
  const heute = new Date();
  const [jahr, setJahr] = useState(
    heute.getMonth() === 0 ? heute.getFullYear() - 1 : heute.getFullYear(),
  );
  const [monat, setMonat] = useState(
    heute.getMonth() === 0 ? 12 : heute.getMonth(),
  );
  const [pruefung, setPruefung] = useState<Vorpruefung | null>(null);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState<"download" | "mail" | null>(null);
  const monate = [
    "Januar",
    "Februar",
    "März",
    "April",
    "Mai",
    "Juni",
    "Juli",
    "August",
    "September",
    "Oktober",
    "November",
    "Dezember",
  ];
  const jahre = Array.from(
    { length: 7 },
    (_, index) => heute.getFullYear() + 1 - index,
  );
  useEffect(() => {
    if (!offen) return;
    let cancelled = false;
    setLoading(true);
    setPruefung(null);
    fetch(
      `/api/buchhaltung/steuerberater/vorpruefung?jahr=${jahr}&monat=${monat}`,
    )
      .then(async (response) => {
        if (!response.ok)
          throw new Error("Vorprüfung konnte nicht geladen werden.");
        return response.json() as Promise<Vorpruefung>;
      })
      .then((data) => {
        if (!cancelled) setPruefung(data);
      })
      .catch((error) => {
        if (!cancelled) {
          setPruefung(null);
          toast.error(
            error instanceof Error
              ? error.message
              : "Vorprüfung konnte nicht geladen werden.",
          );
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [offen, jahr, monat, toast]);
  const ladePaket = async (trotzdem: boolean, ziel: "download" | "mail") => {
    setCreating(ziel);
    try {
      const response = await fetch(
        `/api/buchhaltung/steuerberater/paket?jahr=${jahr}&monat=${monat}&trotzdem=${trotzdem}`,
      );
      if (response.status === 409) {
        setPruefung((await response.json()) as Vorpruefung);
        toast.error(
          "Die Vorprüfung hat sich geändert. Bitte prüfe die offenen Punkte.",
        );
        return;
      }
      if (!response.ok) throw new Error("Paket konnte nicht erstellt werden.");
      const header = response.headers.get("content-disposition") || "";
      const dateiname =
        /filename="?([^";]+)"?/i.exec(header)?.[1] ||
        `${jahr}-${String(monat).padStart(2, "0")}_Kasse.zip`;
      const anhang = { dateiname, blob: await response.blob(), jahr, monat };
      if (ziel === "mail") {
        onEmailPaket(anhang);
        onClose();
        return;
      }
      const url = URL.createObjectURL(anhang.blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = dateiname;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      toast.error(
        error instanceof Error
          ? error.message
          : "Paket konnte nicht erstellt werden.",
      );
    } finally {
      setCreating(null);
    }
  };
  if (!offen) return null;
  const offene = pruefung?.offenePunkte ?? [];
  const trotzdem = offene.length > 0;
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
      data-app-overlay-container
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="steuerberater-paket-titel"
        className="flex max-h-[calc(100vh-var(--pc-toast-height,0px)-2rem)] w-full max-w-2xl flex-col overflow-hidden rounded-xl bg-white shadow-2xl"
      >
        <div className="flex items-start justify-between border-b border-slate-200 bg-rose-50 px-6 py-4">
          <div>
            <h2
              id="steuerberater-paket-titel"
              className="text-lg font-semibold text-slate-900"
            >
              Für den Steuerberater
            </h2>
            <p className="text-sm text-slate-600">
              Kassenunterlagen als ZIP-Datei zusammenstellen
            </p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={onClose}
            aria-label="Dialog schließen"
          >
            <X className="h-5 w-5" />
          </Button>
        </div>
        <div className="flex-1 space-y-4 overflow-y-auto p-6">
          <div className="grid max-w-md grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase text-slate-500">
                Monat
              </label>
              <Select
                value={String(monat)}
                onChange={(value) => setMonat(Number(value))}
                options={monate.map((label, index) => ({
                  value: String(index + 1),
                  label,
                }))}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase text-slate-500">
                Jahr
              </label>
              <Select
                value={String(jahr)}
                onChange={(value) => setJahr(Number(value))}
                options={jahre.map((value) => ({
                  value: String(value),
                  label: String(value),
                }))}
              />
            </div>
          </div>
          {loading ? (
            <div className="flex items-center gap-2 py-8 text-slate-500">
              <Loader2 className="h-5 w-5 animate-spin" />
              Vorprüfung läuft…
            </div>
          ) : (
            pruefung && (
              <>
                {(pruefung.beraternummerFehlt ||
                  pruefung.mandantennummerFehlt) && (
                  <div className="rounded-lg border border-indigo-200 bg-indigo-50 p-3 text-sm text-indigo-900">
                    Beraternummer fehlt – der Export geht trotzdem. Nachtragen
                    kannst du sie in den Kassen-Einstellungen.
                  </div>
                )}
                {!trotzdem ? (
                  <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
                    <CheckCircle2 className="h-5 w-5" />
                    Alle {pruefung.anzahlBelege} Belege sind fertig geprüft.
                  </div>
                ) : (
                  <div className="space-y-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                    <div className="flex gap-2 font-medium">
                      <AlertCircle className="h-5 w-5 shrink-0" />
                      {offene.length} Belege sind noch nicht fertig geprüft.
                    </div>
                    <div className="space-y-2">
                      {offene.map((punkt) => (
                        <a
                          key={punkt.belegId}
                          href={`/belege-kasse?belegId=${punkt.belegId}`}
                          className="block min-w-0 rounded border border-amber-200 bg-white px-3 py-2 hover:border-rose-300 hover:bg-rose-50"
                        >
                          <span className="font-medium">
                            {punkt.belegDatum
                              ? new Date(punkt.belegDatum).toLocaleDateString(
                                  "de-DE",
                                )
                              : "Ohne Datum"}{" "}
                            · {punkt.bezeichnung || `Beleg ${punkt.belegId}`}
                          </span>
                          <span className="block text-amber-800">
                            {punkt.wasFehlt}
                          </span>
                        </a>
                      ))}
                    </div>
                    <p>
                      In der DATEV-Datei steht dann PRÜFEN: vor dem Buchungstext
                      und das Konto bleibt leer.
                    </p>
                  </div>
                )}
              </>
            )
          )}
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-200 bg-slate-50 p-4">
          <Button variant="outline" onClick={onClose}>
            Abbrechen
          </Button>
          <div className="flex flex-wrap gap-2">
            {trotzdem && (
              <Button variant="outline" onClick={onClose}>
                Erst prüfen
              </Button>
            )}
            <Button
              variant="outline"
              disabled={loading || !pruefung || creating !== null}
              onClick={() => ladePaket(trotzdem, "mail")}
            >
              <Mail className="mr-2 h-4 w-4" />
              {creating === "mail"
                ? "Paket wird geladen…"
                : "Per E-Mail an den Steuerberater"}
            </Button>
            <Button
              disabled={loading || !pruefung || creating !== null}
              onClick={() => ladePaket(trotzdem, "download")}
              className="bg-rose-600 text-white hover:bg-rose-700"
            >
              <Download className="mr-2 h-4 w-4" />
              {creating === "download"
                ? "Paket wird erstellt…"
                : trotzdem
                  ? "Trotzdem erstellen"
                  : "Paket erstellen"}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

import { useRef, useState } from "react";
import { Mail, Package, Wallet, X } from "lucide-react";
import { AddressAutocomplete } from "../../components/AddressAutocomplete";
import { KostenstelleSelectModal } from "../../components/KostenstelleSelectModal";
import { PhoneInput } from "../../components/PhoneInput";
import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { Label } from "../../components/ui/label";
import { useToast } from "../../components/ui/toast";
import { ohneReineVorwahl } from "../../lib/phoneUtils";
import { cn } from "../../lib/utils";
import { LIEFERANT_ROLLEN, type Lieferant, type LieferantRolle } from "../../types";
import { mitInfoAdresse } from "./lieferantHilfen";

/** Dialog „Neuer Lieferant“ bzw. „Lieferant bearbeiten“ (Stammdaten). */
export function LieferantModal({ lieferant, onClose, onSave }: { lieferant: Lieferant; onClose: () => void; onSave: (l: Lieferant) => void }) {
    const [formData, setFormData] = useState<Lieferant>({ ...lieferant });
    const [newEmail, setNewEmail] = useState("");
    const [showKostenstelleModal, setShowKostenstelleModal] = useState(false);
    const [nameFehler, setNameFehler] = useState<string | null>(null);
    const nameRef = useRef<HTMLInputElement>(null);
    const toast = useToast();

    const handleChange = (field: keyof Lieferant, value: Lieferant[keyof Lieferant]) => {
        setFormData(prev => ({ ...prev, [field]: value }));
        if (field === "lieferantenname" && nameFehler) setNameFehler(null);
    };

    const addEmail = () => {
        if (!newEmail.trim()) return;
        setFormData(prev => ({ ...prev, kundenEmails: mitInfoAdresse(prev.kundenEmails, newEmail) }));
        setNewEmail("");
    };

    const removeEmail = (email: string) => {
        setFormData(prev => ({
            ...prev,
            kundenEmails: (prev.kundenEmails || []).filter(e => e !== email)
        }));
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 animate-in fade-in duration-200">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto flex flex-col" onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between p-6 border-b border-slate-100">
                    <h3 className="text-xl font-semibold text-slate-900">
                        {lieferant.id ? "Lieferant bearbeiten" : "Neuer Lieferant"}
                    </h3>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                <div className="p-6 space-y-4 overflow-y-auto flex-1">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="space-y-1.5">
                            <Label htmlFor="lieferant-name">Name *</Label>
                            <Input
                                id="lieferant-name"
                                ref={nameRef}
                                value={formData.lieferantenname || ""}
                                onChange={(e) => handleChange("lieferantenname", e.target.value)}
                                required
                                aria-invalid={nameFehler ? true : undefined}
                                aria-describedby={nameFehler ? "lieferant-name-fehler" : undefined}
                                className={cn(nameFehler && "border-red-500 focus-visible:ring-red-500")}
                            />
                            {nameFehler && (
                                <p id="lieferant-name-fehler" className="text-sm text-red-600">{nameFehler}</p>
                            )}
                        </div>
                        <div className="space-y-1.5">
                            <Label htmlFor="lieferant-alias">Zweiter Name (optional)</Label>
                            <Input
                                id="lieferant-alias"
                                value={formData.aliasName || ""}
                                onChange={(e) => handleChange("aliasName", e.target.value)}
                                placeholder="z.B. Kfz Meier"
                            />
                            <p className="text-xs text-slate-400">
                                Wie die Firma bei euch im Betrieb genannt wird. Damit wird sie auch gefunden.
                            </p>
                        </div>
                    </div>

                    <div className="space-y-1.5 border-t border-slate-100 pt-4">
                        <Label>Rollen (was liefert dieser Lieferant?)</Label>
                        <p className="text-xs text-slate-400 -mt-1">
                            Steuert, bei welchen Artikel-Kategorien dieser Lieferant beim Preis-Eintragen vorgeschlagen wird.
                        </p>
                        <div className="flex flex-wrap gap-2">
                            {LIEFERANT_ROLLEN.map(rolle => {
                                const active = (formData.rollen || []).includes(rolle.value);
                                return (
                                    <button
                                        key={rolle.value}
                                        type="button"
                                        onClick={() => {
                                            const current = formData.rollen || [];
                                            const next: LieferantRolle[] = active
                                                ? current.filter(r => r !== rolle.value)
                                                : [...current, rolle.value];
                                            handleChange("rollen", next);
                                        }}
                                        className={cn(
                                            "px-3 py-1.5 rounded-full text-sm font-medium border transition-colors",
                                            active
                                                ? "bg-rose-600 text-white border-rose-600"
                                                : "border-rose-300 text-rose-700 hover:bg-rose-50"
                                        )}
                                    >
                                        {rolle.label}
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="md:col-span-2 space-y-1.5">
                            <Label>Eigene Kundennummer (beim Lieferanten)</Label>
                            <Input
                                value={formData.eigeneKundennummer || ""}
                                onChange={(e) => handleChange("eigeneKundennummer", e.target.value)}
                                placeholder="z.B. K-123456"
                            />
                        </div>
                        <div className="md:col-span-2 space-y-1.5">
                            <Label>Standard-Kostenstelle</Label>
                            <p className="text-xs text-slate-400 -mt-1">
                                Wird bei neuen Bestellungen / Eingangsrechnungen automatisch vorgeschlagen.
                            </p>
                            <div className="flex items-center gap-2">
                                <div className="flex-1 px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-700 flex items-center gap-2">
                                    <Package className="w-4 h-4 text-slate-400 shrink-0" />
                                    {formData.standardKostenstelleName ? (
                                        <span>{formData.standardKostenstelleName}</span>
                                    ) : (
                                        <span className="text-slate-400">Keine zugewiesen</span>
                                    )}
                                </div>
                                <Button type="button" variant="outline" onClick={() => setShowKostenstelleModal(true)}>
                                    {formData.standardKostenstelleName ? "Ändern" : "Auswählen"}
                                </Button>
                                {formData.standardKostenstelleId != null && (
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        onClick={() => setFormData(prev => ({ ...prev, standardKostenstelleId: null, standardKostenstelleName: undefined }))}
                                        title="Kostenstelle entfernen"
                                    >
                                        <X className="w-4 h-4" />
                                    </Button>
                                )}
                            </div>
                        </div>
                    </div>

                    <div className="border-t border-slate-100 pt-4">
                        <label className="flex items-start gap-3 cursor-pointer p-3 rounded-lg hover:bg-slate-50 transition-colors">
                            <input
                                type="checkbox"
                                checked={!!formData.vorauskasse}
                                onChange={(e) => handleChange("vorauskasse", e.target.checked)}
                                className="w-5 h-5 mt-0.5 shrink-0 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                            />
                            <div>
                                <span className="flex items-center gap-2 text-sm font-medium text-slate-800">
                                    <Wallet className="w-4 h-4 text-slate-400" />
                                    Wird immer im Voraus bezahlt
                                </span>
                                <p className="text-xs text-slate-500 mt-1">
                                    Anhaken, wenn hier per Vorkasse, Lastschrift oder Kreditkarte gezahlt wird.
                                    Rechnungen dieser Firma sind dann schon beglichen, wenn sie per E-Mail
                                    ankommen — sie werden automatisch als bezahlt eingetragen und stehen nicht
                                    unter „Offene Posten“. So wird nichts versehentlich doppelt überwiesen.
                                </p>
                            </div>
                        </label>
                    </div>

                    <AddressAutocomplete
                        value={{
                            strasse: formData.strasse || "",
                            plz: formData.plz || "",
                            ort: formData.ort || ""
                        }}
                        onChange={(next) => setFormData(prev => ({
                            ...prev,
                            strasse: next.strasse,
                            plz: next.plz,
                            ort: next.ort
                        }))}
                    />

                    <div className="space-y-1.5">
                        <Label>Lieferzeit Ø (Tage)</Label>
                        <div className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-slate-600">
                            {formData.lieferzeit || "—"}
                        </div>
                        <p className="text-xs text-slate-400">Wird automatisch aus Auftragsbestätigungen berechnet</p>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 border-t border-slate-100 pt-4">
                        <div className="space-y-1.5">
                            <Label>Telefon</Label>
                            <PhoneInput
                                value={formData.telefon || ""}
                                onChange={(v) => handleChange("telefon", v)}
                                variant="festnetz"
                                autoPrefillAreaCode
                                plz={formData.plz}
                                ort={formData.ort}
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label>Mobil</Label>
                            <PhoneInput
                                value={formData.mobiltelefon || ""}
                                onChange={(v) => handleChange("mobiltelefon", v)}
                                variant="mobil"
                            />
                        </div>
                    </div>

                    <div className="space-y-2 border-t border-slate-100 pt-4">
                        <Label>E-Mail-Adressen</Label>
                        <div className="flex gap-2">
                            <Input
                                value={newEmail}
                                onChange={e => setNewEmail(e.target.value)}
                                placeholder="neue@adresse.de"
                                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addEmail(); } }}
                            />
                            <Button type="button" onClick={addEmail} variant="outline">Hinzufügen</Button>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {(formData.kundenEmails || []).map(email => (
                                <span key={email} className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-700 text-sm border border-slate-200">
                                    <Mail className="w-3 h-3 text-slate-400" />
                                    {email}
                                    <button type="button" onClick={() => removeEmail(email)} className="ml-1 text-slate-400 hover:text-red-600">
                                        <X className="w-3 h-3" />
                                    </button>
                                </span>
                            ))}
                        </div>
                    </div>
                </div>

                <div className="p-6 border-t border-slate-100 bg-slate-50 flex justify-end gap-3 rounded-b-xl">
                    <Button variant="ghost" onClick={onClose}>Abbrechen</Button>
                    <Button onClick={() => {
                        // Ohne Namen lässt sich der Lieferant später nicht wiederfinden –
                        // gar nicht erst ans Backend schicken.
                        if (!formData.lieferantenname?.trim()) {
                            const meldung = "Bitte einen Namen für den Lieferanten eingeben.";
                            setNameFehler(meldung);
                            toast.error(meldung);
                            nameRef.current?.focus();
                            return;
                        }
                        // Wenn noch Text im E-Mail-Feld steht, diesen automatisch hinzufügen
                        const finalData = {
                            ...formData,
                            // Die vorgeschlagene Ortsvorwahl allein ist keine Telefonnummer.
                            telefon: ohneReineVorwahl(formData.telefon),
                            mobiltelefon: ohneReineVorwahl(formData.mobiltelefon),
                        };
                        if (newEmail.trim()) {
                            finalData.kundenEmails = mitInfoAdresse(finalData.kundenEmails, newEmail);
                        }
                        onSave(finalData);
                    }} className="bg-rose-600 hover:bg-rose-700 text-white">Speichern</Button>
                </div>
            </div>
            {showKostenstelleModal && (
                <KostenstelleSelectModal
                    onClose={() => setShowKostenstelleModal(false)}
                    onSelect={(k) => {
                        setFormData(prev => ({
                            ...prev,
                            standardKostenstelleId: k.id,
                            standardKostenstelleName: k.bezeichnung,
                        }));
                        setShowKostenstelleModal(false);
                    }}
                />
            )}
        </div>
    );
}

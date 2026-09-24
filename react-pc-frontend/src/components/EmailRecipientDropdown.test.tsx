import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { EmailRecipientDropdown, parseRecipientList } from "./EmailRecipientDropdown";

const mockToast = {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
};

vi.mock("../components/ui/toast", () => ({
    useToast: () => mockToast,
}));

describe("EmailRecipientDropdown", () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe("parseRecipientList", () => {
        it("parst einfache E-Mail-Adressen", () => {
            const list = parseRecipientList("a@b.de, c@d.de");
            expect(list).toHaveLength(2);
            expect(list[0].email).toBe("a@b.de");
            expect(list[1].email).toBe("c@d.de");
        });

        it("parst Namen in Anführungszeichen mit Kommas", () => {
            const list = parseRecipientList('"Kiesel, IBC" <ibc@kiesel.de>, info@tfm.de');
            expect(list).toHaveLength(2);
            expect(list[0].displayName).toBe("Kiesel, IBC");
            expect(list[0].email).toBe("ibc@kiesel.de");
            expect(list[1].email).toBe("info@tfm.de");
        });

        it('parst Namen mit Apostroph ("O\'Connor" <o@example.com>) korrekt', () => {
            const list = parseRecipientList('"O\'Connor" <oconnor@example.com>');
            expect(list).toHaveLength(1);
            expect(list[0].email).toBe("oconnor@example.com");
            expect(list[0].displayName).toBe("O'Connor");
        });

        it("gibt leeres Array bei leerem String zurück", () => {
            expect(parseRecipientList("")).toEqual([]);
            expect(parseRecipientList(undefined)).toEqual([]);
        });
    });

    it('rendert 1-2 Empfänger direkt inline ohne "+ X weitere"', () => {
        render(<EmailRecipientDropdown recipients="kunde@example.com" />);
        expect(screen.getByText("kunde@example.com")).toBeInTheDocument();
        expect(screen.queryByText(/weitere/i)).not.toBeInTheDocument();
    });

    it('rendert Badge "+ 12 weitere" bei einer Rundmail mit 14 Empfängern', () => {
        const recipients = [
            "r1@domain.de", "r2@domain.de", "r3@domain.de", "r4@domain.de",
            "r5@domain.de", "r6@domain.de", "r7@domain.de", "r8@domain.de",
            "r9@domain.de", "r10@domain.de", "r11@domain.de", "r12@domain.de",
            "r13@domain.de", "r14@domain.de"
        ].join(", ");

        render(<EmailRecipientDropdown recipients={recipients} maxInline={2} />);
        expect(screen.getByText("r1@domain.de")).toBeInTheDocument();
        expect(screen.getByText("r2@domain.de")).toBeInTheDocument();
        expect(screen.getByText("+12 weitere")).toBeInTheDocument();

        // Klick auf Badge öffnet Dropdown
        fireEvent.click(screen.getByText("+12 weitere"));
        expect(screen.getByText("Alle Empfänger (14)")).toBeInTheDocument();
        expect(screen.getByText("r14@domain.de")).toBeInTheDocument();
    });

    it("kopiert alle Empfänger in die Zwischenablage bei Klick auf Alle kopieren", async () => {
        const writeTextMock = vi.fn().mockResolvedValue(undefined);
        Object.assign(navigator, {
            clipboard: {
                writeText: writeTextMock,
            },
        });

        render(<EmailRecipientDropdown recipients="a@b.de, c@d.de, e@f.de" maxInline={1} />);
        fireEvent.click(screen.getByText("+2 weitere"));
        const copyBtn = screen.getByTitle("Alle E-Mail-Adressen in die Zwischenablage kopieren");
        fireEvent.click(copyBtn);

        await waitFor(() => {
            expect(writeTextMock).toHaveBeenCalledWith("a@b.de, c@d.de, e@f.de");
            expect(screen.getByText("Kopiert!")).toBeInTheDocument();
        });
    });

    it("zeigt Toast-Fehler wenn das Kopieren aller Empfänger fehlschlägt", async () => {
        const writeTextMock = vi.fn().mockRejectedValue(new Error("Clipboard blocked"));
        Object.assign(navigator, {
            clipboard: {
                writeText: writeTextMock,
            },
        });

        render(<EmailRecipientDropdown recipients="a@b.de, c@d.de, e@f.de" maxInline={1} />);
        fireEvent.click(screen.getByText("+2 weitere"));
        const copyBtn = screen.getByTitle("Alle E-Mail-Adressen in die Zwischenablage kopieren");
        fireEvent.click(copyBtn);

        await waitFor(() => {
            expect(mockToast.error).toHaveBeenCalledWith("E-Mail-Adressen konnten nicht kopiert werden.");
            expect(screen.queryByText("Kopiert!")).not.toBeInTheDocument();
        });
    });

    it("kopiert bei Namen mit Apostroph nur die reine E-Mail-Adresse", async () => {
        const writeTextMock = vi.fn().mockResolvedValue(undefined);
        Object.assign(navigator, {
            clipboard: {
                writeText: writeTextMock,
            },
        });

        render(<EmailRecipientDropdown recipients={`"O'Connor" <oconnor@example.com>, a@b.de`} maxInline={1} />);
        fireEvent.click(screen.getByText("+1 weitere"));
        const copyBtns = screen.getAllByTitle("Adresse kopieren");
        fireEvent.click(copyBtns[0]);

        await waitFor(() => {
            expect(writeTextMock).toHaveBeenCalledWith("oconnor@example.com");
        });
    });

    it("zeigt Toast-Fehler wenn das Kopieren einer einzelnen Adresse fehlschlägt", async () => {
        const writeTextMock = vi.fn().mockRejectedValue(new Error("Clipboard blocked"));
        Object.assign(navigator, {
            clipboard: {
                writeText: writeTextMock,
            },
        });

        render(<EmailRecipientDropdown recipients="kunde@example.com, a@b.de" maxInline={1} />);
        fireEvent.click(screen.getByText("+1 weitere"));
        const copyBtns = screen.getAllByTitle("Adresse kopieren");
        fireEvent.click(copyBtns[0]);

        await waitFor(() => {
            expect(mockToast.error).toHaveBeenCalledWith("E-Mail-Adresse konnte nicht kopiert werden.");
        });
    });

    it("stellt Tastatur-Fokussierbarkeit für Einzel-Kopierbuttons sicher", () => {
        render(<EmailRecipientDropdown recipients="kunde@example.com, a@b.de" maxInline={1} />);
        fireEvent.click(screen.getByText("+1 weitere"));
        const copyBtns = screen.getAllByTitle("Adresse kopieren");
        expect(copyBtns[0]).toHaveClass("focus-visible:opacity-100");
        expect(copyBtns[0]).toHaveClass("focus-visible:ring-2");
        expect(copyBtns[0]).toHaveAttribute("aria-label", "kunde@example.com kopieren");
    });
});

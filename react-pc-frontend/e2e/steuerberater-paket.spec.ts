import type { Page, Route } from "@playwright/test";
import { test, expect } from "./hilfen/test";
import { designPruefung } from "./hilfen/design";

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

async function stub(
  page: Page,
  offenePunkte: unknown[] = [],
  beraternummerFehlt = false,
) {
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/auth/me")
      return json(route, {
        id: 1,
        displayName: "Max Mustermann",
        roles: ["ADMIN"],
        admin: true,
      });
    if (url.pathname === "/api/notifications/summary")
      return json(route, { totalCount: 0, categories: [], recentItems: [] });
    if (url.pathname === "/api/buchhaltung/belege") return json(route, []);
    if (
      url.pathname === "/api/buchhaltung/sachkonten" ||
      url.pathname === "/api/buchhaltung/zahlungsarten"
    )
      return json(route, []);
    if (url.pathname === "/api/buchhaltung/steuerberater/vorpruefung") {
      return json(route, {
        anzahlBelege: 4,
        offenePunkte,
        beraternummerFehlt,
        mandantennummerFehlt: false,
      });
    }
    if (url.pathname === "/api/buchhaltung/steuerberater/paket") {
      return route.fulfill({
        status: 200,
        contentType: "application/zip",
        body: "zip",
      });
    }
    if (url.pathname === "/api/firma/steuerberater")
      return json(route, [
        {
          id: 1,
          name: "Musterkanzlei",
          email: "muster@example.com",
          weitereEmails: [],
          ansprechpartnerListe: [],
        },
      ]);
    if (url.pathname === "/api/firma")
      return json(route, { firmenname: "Musterbetrieb GmbH" });
    if (url.pathname === "/api/email/signatures/default")
      return route.fulfill({ status: 204, body: "" });
    return json(route, []);
  });
}

test("Steuerberater-Paket zeigt fertige Belege und versendet die ZIP mit kurzem Mailtext", async ({
  page,
}, info) => {
  await stub(page, [], true);
  await page.goto("/belege-kasse");
  await page.getByRole("button", { name: "Für den Steuerberater" }).click();
  await expect(
    page.getByText("Alle 4 Belege sind fertig geprüft."),
  ).toBeVisible();
  await expect(
    page.getByText("Beraternummer fehlt – der Export geht trotzdem."),
  ).toBeVisible();
  const paket = page.getByRole("button", { name: "Paket erstellen" });
  await expect(paket).toBeEnabled();
  await designPruefung(page, info, "steuerberater-paket-fertig", {
    primaerAktion: paket,
  });
  await page
    .getByRole("button", { name: "Per E-Mail an den Steuerberater" })
    .click();
  await expect(page.getByText(/anbei die Kassenunterlagen für/)).toBeVisible();
  await expect(page.locator("[contenteditable=true] table")).toHaveCount(0);
});

test("Steuerberater-Paket erlaubt Export trotz offener Punkte", async ({
  page,
}, info) => {
  const urls: string[] = [];
  await stub(page, [
    {
      belegId: 31,
      belegDatum: "2026-08-02",
      bezeichnung: "Werkzeugkauf",
      wasFehlt: "Konto fehlt",
    },
    {
      belegId: 32,
      belegDatum: "2026-08-03",
      bezeichnung: "Material",
      wasFehlt: "Zahlungsart fehlt",
    },
    {
      belegId: 33,
      belegDatum: "2026-08-04",
      bezeichnung: "Fahrt",
      wasFehlt: "Belegdatum fehlt",
    },
  ]);
  await page.route("**/api/buchhaltung/steuerberater/paket**", (route) => {
    urls.push(route.request().url());
    return route.fulfill({
      status: 200,
      contentType: "application/zip",
      body: "zip",
    });
  });
  await page.goto("/belege-kasse");
  await page.getByRole("button", { name: "Für den Steuerberater" }).click();
  await expect(
    page.getByText("3 Belege sind noch nicht fertig geprüft."),
  ).toBeVisible();
  await expect(page.getByText("Konto fehlt")).toBeVisible();
  await expect(page.getByText("Zahlungsart fehlt")).toBeVisible();
  const trotzdem = page.getByRole("button", { name: "Trotzdem erstellen" });
  await designPruefung(page, info, "steuerberater-paket-offen", {
    primaerAktion: trotzdem,
  });
  await trotzdem.click();
  await expect.poll(() => urls).toHaveLength(1);
  expect(urls[0]).toContain("trotzdem=true");
});

import { expect, test } from "@playwright/test";
import type { Route } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { mockReservationBootstrap } from "./support/mock-api";
import { reservationBase, stableIsoTimestamp, todayIsoDate } from "./support/test-data";

test("reservierung: PENDING -> CONFIRMED/REJECTED -> CHECKED_IN", async ({ page }) => {
  const today = todayIsoDate();

  const reservations = [
    reservationBase({ id: 301, guestName: "Anna Pending", reservationDate: today, reservationTime: "19:00", qrCodeToken: "anna-token" }),
    reservationBase({ id: 302, guestName: "Ben Pending", reservationDate: today, reservationTime: "20:00", guestCount: 4, qrCodeToken: "ben-token" }),
  ];

  await mockAuthenticatedSession(page);

  await mockReservationBootstrap(page, reservations);

  await page.route("**/api/reservations/301/confirm", async (route: Route) => {
    reservations[0].status = "CONFIRMED";
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(reservations[0]) });
  });

  await page.route("**/api/reservations/301/check-in", async (route: Route) => {
    reservations[0].status = "CHECKED_IN";
    (reservations[0] as { checkedInAt: string | null }).checkedInAt = stableIsoTimestamp();
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(reservations[0]) });
  });

  await page.route("**/api/reservations/302/cancel", async (route: Route) => {
    reservations[1].status = "REJECTED";
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(reservations[1]) });
  });

  await page.goto("/bookings");

  await expect(page.getByRole("heading", { name: "Reservierungen Dashboard" })).toBeVisible();
  await expect(page.getByText("Anna Pending").first()).toBeVisible();

  const annaCard = page.locator("div.rounded-lg").filter({ hasText: "Anna Pending" }).first();
  const benCard = page.locator("div.rounded-lg").filter({ hasText: "Ben Pending" }).first();

  await annaCard.getByRole("button", { name: "Bestätigen" }).click();
  await expect(annaCard.getByText("CONFIRMED")).toBeVisible();

  await annaCard.getByRole("button", { name: "Check-in" }).click();
  await expect(annaCard.getByRole("button", { name: "Check-in" })).toHaveCount(0);
  await expect(annaCard.getByRole("button", { name: "Abschliessen" })).toHaveCount(0);
  await expect(annaCard.getByText("Tische bleiben bis zur Bezahlung belegt.")).toBeVisible();

  await benCard.getByRole("button", { name: "Ablehnen" }).click();
  await page.locator("textarea").fill("Slot intern nicht verfügbar");
  await page.getByRole("button", { name: "Ablehnen" }).last().click();

  await expect(page.getByRole("main").getByText("Reservierung abgelehnt.")).toBeVisible();
  await expect(page.getByText("Ben Pending").first()).toBeVisible();
});



test("QR navigation does not check in; staff explicitly confirms", async ({ page }) => {
  await mockAuthenticatedSession(page);
  let scans = 0;
  let checkIns = 0;
  await page.route("**/api/reservations/scan/group-token", async route => {
    expect(route.request().method()).toBe("POST");
    scans++;
    await route.fulfill({ json: { reservationId: 301, guestName: "Group of six", reservationDate: todayIsoDate(), reservationTime: "18:00", status: "CONFIRMED", checkInAllowed: true } });
  });
  await page.route("**/api/reservations/301/check-in", async route => {
    expect(route.request().method()).toBe("POST");
    checkIns++;
    await route.fulfill({ json: { status: "CHECKED_IN" } });
  });
  await page.goto("/bookings/scan/group-token");
  await expect(page.getByRole("button", { name: "Reservierung pruefen" })).toBeVisible();
  expect(scans).toBe(0);
  expect(checkIns).toBe(0);
  await page.getByRole("button", { name: "Reservierung pruefen" }).click();
  await expect(page.getByText(/Group of six/)).toBeVisible();
  expect(checkIns).toBe(0);
  await page.getByRole("button", { name: "Check-in bestaetigen" }).click();
  await expect(page.getByRole("status")).toHaveText("Check-in erfolgreich.");
  expect(checkIns).toBe(1);
});

test("QR scan authorization failure reveals no reservation", async ({ page }) => {
  await mockAuthenticatedSession(page);
  await page.route("**/api/reservations/scan/denied-token", route => route.fulfill({ status: 403, json: {} }));
  await page.goto("/bookings/scan/denied-token");
  await page.getByRole("button", { name: "Reservierung pruefen" }).click();
  await expect(page.getByRole("status")).toContainText("Bitte als Teammitglied anmelden");
  await expect(page.getByRole("button", { name: "Check-in bestaetigen" })).toHaveCount(0);
});

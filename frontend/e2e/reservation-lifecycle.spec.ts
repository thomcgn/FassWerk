import { expect, test } from "@playwright/test";
import { mockAuthenticatedSession } from "./support/mock-auth";
import { reservationBase, todayIsoDate } from "./support/test-data";

test("reservierung: PENDING -> CONFIRMED/REJECTED -> CHECKED_IN", async ({ page }) => {
  const today = todayIsoDate();

  const reservations = [
    reservationBase({ id: 301, guestName: "Anna Pending", reservationDate: today, reservationTime: "19:00", qrCodeToken: "anna-token" }),
    reservationBase({ id: 302, guestName: "Ben Pending", reservationDate: today, reservationTime: "20:00", guestCount: 4, qrCodeToken: "ben-token" }),
  ];

  await mockAuthenticatedSession(page);

  await page.route("**/api/inventory", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([]) });
  });

  await page.route("**/api/reservations?date=**", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(reservations) });
  });

  await page.route("**/api/reservations/301/confirm", async (route) => {
    reservations[0].status = "CONFIRMED";
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(reservations[0]) });
  });

  await page.route("**/api/reservations/301/check-in", async (route) => {
    reservations[0].status = "CHECKED_IN";
    reservations[0].checkedInAt = new Date().toISOString();
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(reservations[0]) });
  });

  await page.route("**/api/reservations/302/cancel", async (route) => {
    reservations[1].status = "REJECTED";
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(reservations[1]) });
  });

  await page.goto("/bookings");

  await expect(page.getByRole("heading", { name: "Reservierungen Dashboard" })).toBeVisible();
  await expect(page.getByText("Anna Pending").first()).toBeVisible();

  await page.getByRole("button", { name: /Best.*tigen/ }).first().click();
  await expect(page.getByText("CONFIRMED")).toBeVisible();

  await page.getByRole("button", { name: "Check-in" }).first().click();
  await expect(page.getByRole("button", { name: "Check-in" })).toHaveCount(0);

  await page.getByRole("button", { name: "Ablehnen" }).first().click();
  await page.locator("textarea").fill("Slot intern nicht verfügbar");
  await page.getByRole("button", { name: "Ablehnen" }).last().click();

  await expect(page.getByText("Abgelehnt")).toBeVisible();
  await expect(page.getByText("Ben Pending").first()).toBeVisible();
});



export function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

export function reservationBase(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    guestName: "Testgast",
    contactEmail: "gast@example.test",
    contactPhone: "+490000000",
    reservationDate: todayIsoDate(),
    reservationTime: "19:00",
    guestCount: 2,
    status: "PENDING",
    expiresAt: null,
    checkedInAt: null,
    qrCodeToken: "qr-token",
    qrScanUrl: "https://example.test/qr-token",
    ...overrides,
  };
}

export function tableBillingMeta() {
  return {
    tables: [{ id: 1, name: "T1", area: "INSIDE", status: "FREE", active: true }],
    categories: [{ id: 11, name: "Bier", sortOrder: 10, active: true }],
    drinks: [{ id: 21, categoryId: 11, categoryName: "Bier", name: "Helles", description: null, imageUrl: null, active: true }],
    variants: [{ id: 31, drinkId: 21, drinkName: "Helles", displayVolumeName: "0,5 l", volumeMl: 500, price: "5.90", useStandardPrice: true, sku: null, active: true }],
  };
}


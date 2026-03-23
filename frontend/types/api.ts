export type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  accessExpiresInSeconds: number;
  refreshExpiresInSeconds: number;
  role: string;
  displayName: string;
};

export type InventoryItem = {
  id: number;
  name: string;
  linkedDrinkId: number | null;
  linkedDrinkVariantId: number | null;
  packageType: string;
  packagesInStock: string;
  contentPerPackage: string;
  contentUnit: string;
  totalStockAmount: string;
  reorderThreshold: string;
  minimumStock: string;
  recommendedReorderAmount: string;
  supplier: string | null;
  active: boolean;
};

export type SessionResponse = {
  id: number;
  tokenId: string;
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string | null;
  userAgent: string | null;
  ipAddress: string | null;
  current: boolean;
};

export type ReservationStatus = "PENDING" | "CONFIRMED" | "CHECKED_IN" | "CANCELLED" | "NO_SHOW";

export type Reservation = {
  id: number;
  guestName: string;
  contactEmail: string | null;
  contactPhone: string | null;
  reservationDate: string;
  reservationTime: string;
  guestCount: number;
  status: ReservationStatus;
  expiresAt: string | null;
  checkedInAt: string | null;
  qrCodeToken: string;
  qrScanUrl: string;
};

export type CreateReservationRequest = {
  guestName: string;
  contactEmail?: string;
  contactPhone?: string;
  reservationDate: string;
  reservationTime: string;
  guestCount: number;
};

export type TableStatus = "AVAILABLE" | "OCCUPIED" | "RESERVED" | "OUT_OF_SERVICE";

export type Table = {
  id: number;
  name: string;
  capacity: number;
  area: string | null;
  status: TableStatus;
  active: boolean;
};

export type DrinkVariant = {
  id: number;
  drinkId: number;
  drinkName: string;
  displayVolumeName: string;
  volumeMl: number;
  price: string;
  sku: string | null;
  active: boolean;
};

export type TableOrderStatus = "OPEN" | "CLOSED";

export type TableOrderItem = {
  id: number;
  drinkVariantId: number;
  drinkLabel: string;
  quantity: number;
  unitPrice: string;
  totalPrice: string;
  deductedVolumeMl: string;
};

export type TableOrder = {
  id: number;
  tableId: number;
  tableName: string;
  reservationId: number | null;
  status: TableOrderStatus;
  openedAt: string;
  closedAt: string | null;
  total: string;
  items: TableOrderItem[];
};


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

export type InventoryItemUpsertRequest = {
  name: string;
  linkedDrinkId: number | null;
  linkedDrinkVariantId: number | null;
  packageType: "BARREL" | "CRATE" | "BOTTLE" | "BOX" | "SINGLE_BOTTLE";
  packagesInStock: number;
  contentPerPackage: number;
  contentUnit: "MILLILITER" | "LITER" | "PIECE";
  reorderThreshold: number;
  minimumStock: number;
  recommendedReorderAmount?: number;
  reorderThresholdPackages?: number;
  minimumStockPackages?: number;
  recommendedReorderPackages?: number;
  supplier: string | null;
  active: boolean;
};

export type InventoryPackageDefaults = {
  packageType: InventoryItemUpsertRequest["packageType"];
  reorderThresholdPackages: number;
  minimumStockPackages: number;
  recommendedReorderPackages: number;
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

export type ReservationStatus = "PENDING" | "CONFIRMED" | "CHECKED_IN" | "REJECTED" | "CANCELLED" | "NO_SHOW";

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

export type TableStatus = "FREE" | "OCCUPIED" | "RESERVED" | "READY_FOR_PAYMENT";

export type Table = {
  id: number;
  name: string;
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
  useStandardPrice: boolean;
  sku: string | null;
  active: boolean;
};

export type VolumePrice = {
  id: number;
  volumeMl: number;
  price: string;
};

export type DrinkCategory = {
  id: number;
  name: string;
  sortOrder: number;
  active: boolean;
};

export type Drink = {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
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
  paid: boolean;
  openedAt: string;
  closedAt: string | null;
  total: string;
  items: TableOrderItem[];
};

export type SplitPaymentItemRequest = {
  itemId: number;
  quantity: number;
};

export type SplitPaymentResponse = {
  openOrder: TableOrder;
  paidOrder: TableOrder;
};

export type RevenuePoint = {
  label: string;
  revenue: string;
};

export type RevenueOverview = {
  dayRevenue: string;
  weekRevenue: string;
  monthRevenue: string;
  dayConsumedMl: string;
  weekConsumedMl: string;
  monthConsumedMl: string;
  strongestWeekday: string;
  weekPoints: RevenuePoint[];
  monthPoints: RevenuePoint[];
};

export type ShiftWorkerEntry = {
  id: number | null;
  employeeName: string;
  shiftStart: string;
  shiftEnd: string;
  hourlyWage: string;
  workedHours: string;
  wageCost: string;
};

export type ShiftSettlement = {
  id: number | null;
  settlementDate: string;
  openingCash: string;
  otherExpenses: string;
  dailyRevenue: string;
  totalWages: string;
  expectedClosingCash: string;
  entries: ShiftWorkerEntry[];
};

export type ShiftWorkerEntryUpsert = {
  employeeName: string;
  shiftStart: string;
  shiftEnd: string;
  hourlyWage: number;
};

export type ShiftSettlementUpsertRequest = {
  openingCash: number;
  otherExpenses: number;
  entries: ShiftWorkerEntryUpsert[];
};

// Sales Tracking Types
export type DrinkSalesDaily = {
  id: number;
  drinkId: number;
  drinkName: string;
  drinkVariantId: number | null;
  drinkVariantName: string | null;
  saleDate: string;
  quantitySold: string;
  volumeSoldMl: string;
};

export type DrinkSalesWeekly = {
  id: number;
  drinkId: number;
  drinkName: string;
  drinkVariantId: number | null;
  drinkVariantName: string | null;
  weekStartDate: string;
  quantitySold: string;
  volumeSoldMl: string;
  averageDailyQuantity: string;
  averageDailyVolumeMl: string;
};

export type ReorderCalculation = {
  id: number;
  inventoryItemId: number;
  inventoryItemName: string;
  calculationDate: string;
  currentStockAmount: string;
  weeklyAverageConsumption: string;
  recommendedReorderAmount: string;
  isBelowThreshold: boolean;
  weeksUntilStockout: string | null;
};

export type ConsumptionMetadata = {
  id: number;
  inventoryItemId: number;
  leadTimeDays: number;
  safetyStockFactor: string;
  weeksLookback: number;
};

export type ConsumptionMetadataRequest = {
  leadTimeDays?: number;
  safetyStockFactor?: number;
  weeksLookback?: number;
};

export type SalesConfiguration = {
  weeksLookback: number;
  defaultSafetyFactor: number;
  defaultLeadTimeDays: number;
  businessTimezone: string;
  businessDayEndsAt: string;
  manualBusinessDate: string | null;
  effectiveBusinessDate: string;
};


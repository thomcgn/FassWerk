import type { components } from "./generated/api";

type ApiSchema<Name extends keyof components["schemas"]> = components["schemas"][Name];

export type LoginResponse = ApiSchema<"LoginResponse">;
export type SessionResponse = ApiSchema<"SessionResponse">;

export type InventoryItem = ApiSchema<"InventoryItemResponse">;
export type InventoryItemUpsertRequest = ApiSchema<"InventoryItemRequest">;
export type InventoryPackageDefaults = ApiSchema<"InventoryPackageDefaultsResponse">;
export type InventoryMovement = ApiSchema<"InventoryMovementResponse">;
export type InventoryAdjustmentRequest = ApiSchema<"InventoryAdjustmentRequest">;

export type Reservation = ApiSchema<"ReservationResponse">;
export type ReservationStatus = Reservation["status"];
export type CreateReservationRequest = ApiSchema<"CreateReservationRequest">;
export type ReservationSettings = ApiSchema<"ReservationSettingsResponse">;

export type Table = ApiSchema<"TableResponse">;
export type TableStatus = Table["status"];

export type DrinkVariant = ApiSchema<"DrinkVariantResponse">;
export type VolumePrice = ApiSchema<"VolumePriceResponse">;
export type DrinkCategory = ApiSchema<"DrinkCategoryResponse">;
export type Drink = ApiSchema<"DrinkResponse">;

export type TableOrder = ApiSchema<"TableOrderResponse">;
export type TableOrderStatus = TableOrder["status"];
export type TableOrderItem = ApiSchema<"TableOrderItemResponse">;
export type SplitPaymentItemRequest = ApiSchema<"SplitTableOrderItemRequest">;
export type SplitPaymentResponse = ApiSchema<"SplitTableOrderPaymentResponse">;

export type RevenuePoint = ApiSchema<"RevenueDayPointResponse">;
export type RevenueOverview = ApiSchema<"RevenueOverviewResponse">;

export type ShiftWorkerEntry = ApiSchema<"ShiftWorkerEntryResponse">;
export type ShiftSettlement = ApiSchema<"ShiftSettlementResponse">;
export type ShiftWorkerEntryUpsert = ApiSchema<"ShiftWorkerEntryRequest">;
export type ShiftSettlementUpsertRequest = ApiSchema<"ShiftSettlementRequest">;

export type DrinkSalesDaily = ApiSchema<"DrinkSalesDailyResponse">;
export type DrinkSalesWeekly = ApiSchema<"DrinkSalesWeeklyResponse">;
export type ReorderCalculation = ApiSchema<"ReorderCalculationResponse">;
export type ReorderSuggestion = ApiSchema<"ReorderSuggestionResponse">;
export type ConsumptionMetadata = ApiSchema<"ConsumptionMetadataResponse">;
export type ConsumptionMetadataRequest = ApiSchema<"ConsumptionMetadataRequest">;
export type ManualDayCloseRequest = ApiSchema<"ManualDayCloseRequest">;
export type SalesConfiguration = ApiSchema<"SalesConfigurationResponse">;

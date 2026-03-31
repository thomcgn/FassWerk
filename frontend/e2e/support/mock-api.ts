import type { Page, Route } from "@playwright/test";
import { stableIsoTimestamp } from "./test-data";

type Table = { id: number; name: string; area: string; status: "FREE" | "OCCUPIED" | "RESERVED" | "READY_FOR_PAYMENT"; active: boolean };
type Category = { id: number; name: string; sortOrder: number; active: boolean };
type Drink = { id: number; categoryId: number; categoryName: string; name: string; description: string | null; imageUrl: string | null; active: boolean };
type Variant = { id: number; drinkId: number; drinkName: string; displayVolumeName: string; volumeMl: number; price: string; useStandardPrice: boolean; sku: string | null; active: boolean };
type Inventory = {
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
type TableOrderItem = { id: number; drinkVariantId: number; drinkLabel: string; quantity: number; unitPrice: string; totalPrice: string; deductedVolumeMl: string };
type TableOrder = {
  id: number;
  tableId: number;
  tableName: string;
  reservationId: number | null;
  status: "OPEN" | "CLOSED";
  paid: boolean;
  openedAt: string;
  closedAt: string | null;
  total: string;
  items: TableOrderItem[];
};

type FlowState = {
  tables: Table[];
  categories: Category[];
  drinks: Drink[];
  variants: Variant[];
  volumePrices: Array<{ id: number; volumeMl: number; price: string }>;
  inventory: Inventory[];
  defaults: Array<{ packageType: string; reorderThresholdPackages: number; minimumStockPackages: number; recommendedReorderPackages: number }>;
  orders: TableOrder[];
  counters: { categoryId: number; drinkId: number; variantId: number; inventoryId: number; orderId: number; orderItemId: number };
};

type SetupOptions = { tables?: Table[]; defaults?: FlowState["defaults"] };

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
}

function toAmountString(value: number): string {
  if (!Number.isFinite(value)) return "0";
  return Number(value.toFixed(2)).toString();
}

function cloneOrder(order: TableOrder): TableOrder {
  return {
    ...order,
    items: order.items.map((item) => ({ ...item })),
  };
}

function recalcOrderTotal(order: TableOrder) {
  const total = order.items.reduce((sum, item) => sum + Number(item.totalPrice), 0);
  order.total = total.toFixed(2);
}

function findOpenOrder(state: FlowState, tableId: number): TableOrder | undefined {
  return state.orders.find((entry) => entry.tableId === tableId && entry.status === "OPEN");
}

function setTableStatus(state: FlowState, tableId: number, status: Table["status"]) {
  const table = state.tables.find((entry) => entry.id === tableId);
  if (table) {
    table.status = status;
  }
}

export function createFlowState(options: SetupOptions = {}): FlowState {
  return {
    tables: options.tables ?? [{ id: 1, name: "T1", area: "INSIDE", status: "OCCUPIED", active: true }],
    categories: [],
    drinks: [],
    variants: [],
    volumePrices: [
      { id: 1, volumeMl: 200, price: "3.90" },
      { id: 2, volumeMl: 330, price: "4.50" },
      { id: 3, volumeMl: 500, price: "5.90" },
    ],
    inventory: [],
    defaults: options.defaults ?? [],
    orders: [],
    counters: {
      categoryId: 100,
      drinkId: 200,
      variantId: 300,
      inventoryId: 400,
      orderId: 900,
      orderItemId: 1,
    },
  };
}

export async function mockReservationBootstrap(page: Page, reservations: unknown[]) {
  await page.route("**/api/inventory", async (route: Route) => {
    await json(route, []);
  });

  await page.route("**/api/reservations**", async (route: Route) => {
    if (route.request().method() === "GET") {
      await json(route, reservations);
      return;
    }
    await route.fallback();
  });
}

export async function mockFlowApis(page: Page, state: FlowState) {
  await page.route("**/api/drink-categories", async (route: Route) => {
    const method = route.request().method();
    if (method === "GET") {
      await json(route, state.categories);
      return;
    }
    if (method === "POST") {
      const body = (await route.request().postDataJSON()) as { name: string; sortOrder: number; active?: boolean };
      const created: Category = {
        id: state.counters.categoryId++,
        name: body.name,
        sortOrder: body.sortOrder,
        active: body.active ?? true,
      };
      state.categories.push(created);
      await json(route, created, 201);
      return;
    }
    await route.fallback();
  });

  await page.route("**/api/drinks", async (route: Route) => {
    const method = route.request().method();
    if (method === "GET") {
      await json(route, state.drinks);
      return;
    }
    if (method === "POST") {
      const body = (await route.request().postDataJSON()) as { categoryId: number; name: string; description?: string | null; imageUrl?: string | null; active?: boolean };
      const category = state.categories.find((entry) => entry.id === body.categoryId);
      const created: Drink = {
        id: state.counters.drinkId++,
        categoryId: body.categoryId,
        categoryName: category?.name ?? "Kategorie",
        name: body.name,
        description: body.description ?? null,
        imageUrl: body.imageUrl ?? null,
        active: body.active ?? true,
      };
      state.drinks.push(created);
      await json(route, created, 201);
      return;
    }
    await route.fallback();
  });

  await page.route("**/api/drink-variants", async (route: Route) => {
    const method = route.request().method();
    if (method === "GET") {
      await json(route, state.variants);
      return;
    }
    if (method === "POST") {
      const body = (await route.request().postDataJSON()) as {
        drinkId: number;
        displayVolumeName: string;
        volumeMl: number;
        useStandardPrice: boolean;
        price: string | number | null;
        sku?: string | null;
        active?: boolean;
      };
      const drink = state.drinks.find((entry) => entry.id === body.drinkId);
      const priceFromVolume = state.volumePrices.find((entry) => entry.volumeMl === body.volumeMl)?.price ?? "5.90";
      const created: Variant = {
        id: state.counters.variantId++,
        drinkId: body.drinkId,
        drinkName: drink?.name ?? "Drink",
        displayVolumeName: body.displayVolumeName,
        volumeMl: body.volumeMl,
        price: body.useStandardPrice ? priceFromVolume : String(body.price ?? priceFromVolume),
        useStandardPrice: body.useStandardPrice,
        sku: body.sku ?? null,
        active: body.active ?? true,
      };
      state.variants.push(created);
      await json(route, created, 201);
      return;
    }
    await route.fallback();
  });

  await page.route("**/api/volume-prices", async (route: Route) => {
    await json(route, state.volumePrices);
  });

  await page.route("**/api/inventory/defaults", async (route: Route) => {
    await json(route, state.defaults);
  });

  await page.route("**/api/inventory", async (route: Route) => {
    const method = route.request().method();
    if (method === "GET") {
      await json(route, state.inventory);
      return;
    }
    if (method === "POST") {
      const body = (await route.request().postDataJSON()) as {
        name: string;
        linkedDrinkId: number | null;
        linkedDrinkVariantId: number | null;
        packageType: string;
        packagesInStock: number;
        contentPerPackage: number;
        contentUnit: string;
        reorderThreshold: number;
        minimumStock: number;
        recommendedReorderAmount?: number;
        supplier: string | null;
        active?: boolean;
      };
      const totalStockAmount = body.packagesInStock * body.contentPerPackage;
      const created: Inventory = {
        id: state.counters.inventoryId++,
        name: body.name,
        linkedDrinkId: body.linkedDrinkId,
        linkedDrinkVariantId: body.linkedDrinkVariantId,
        packageType: body.packageType,
        packagesInStock: String(body.packagesInStock),
        contentPerPackage: String(body.contentPerPackage),
        contentUnit: body.contentUnit,
        totalStockAmount: toAmountString(totalStockAmount),
        reorderThreshold: String(body.reorderThreshold),
        minimumStock: String(body.minimumStock),
        recommendedReorderAmount: String(body.recommendedReorderAmount ?? 0),
        supplier: body.supplier,
        active: body.active ?? true,
      };
      state.inventory.push(created);
      await json(route, created, 201);
      return;
    }
    await route.fallback();
  });

  await page.route("**/api/tables", async (route: Route) => {
    const method = route.request().method();
    if (method === "GET") {
      await json(route, state.tables);
      return;
    }
    if (method === "POST") {
      const body = (await route.request().postDataJSON()) as { name: string; area: string; status: Table["status"]; active?: boolean };
      const created: Table = {
        id: Math.max(0, ...state.tables.map((entry) => entry.id)) + 1,
        name: body.name,
        area: body.area,
        status: body.status,
        active: body.active ?? true,
      };
      state.tables.push(created);
      await json(route, created, 201);
      return;
    }
    await route.fallback();
  });

  await page.route(/.*\/api\/table-orders\/open\/table\/(\d+)$/, async (route: Route) => {
    if (route.request().method() !== "GET") {
      await route.fallback();
      return;
    }
    const tableId = Number(route.request().url().split("/").pop());
    const openOrder = findOpenOrder(state, tableId);
    if (!openOrder) {
      await json(route, { message: "not-found" }, 404);
      return;
    }
    await json(route, cloneOrder(openOrder));
  });

  await page.route("**/api/table-orders/open", async (route: Route) => {
    if (route.request().method() !== "POST") {
      await route.fallback();
      return;
    }
    const body = (await route.request().postDataJSON()) as { tableId: number; reservationId: number | null };
    const table = state.tables.find((entry) => entry.id === body.tableId);
    const created: TableOrder = {
      id: state.counters.orderId++,
      tableId: body.tableId,
      tableName: table?.name ?? `T${body.tableId}`,
      reservationId: body.reservationId,
      status: "OPEN",
      paid: false,
      openedAt: stableIsoTimestamp(),
      closedAt: null,
      total: "0.00",
      items: [],
    };
    state.orders.push(created);
    setTableStatus(state, body.tableId, "OCCUPIED");
    await json(route, cloneOrder(created));
  });

  await page.route(/.*\/api\/table-orders\/(\d+)$/, async (route: Route) => {
    if (route.request().method() !== "GET") {
      await route.fallback();
      return;
    }
    const orderId = Number(route.request().url().split("/").pop());
    const order = state.orders.find((entry) => entry.id === orderId);
    if (!order) {
      await json(route, { message: "not-found" }, 404);
      return;
    }
    await json(route, cloneOrder(order));
  });

  await page.route("**/api/table-orders/archive**", async (route: Route) => {
    if (route.request().method() !== "GET") {
      await route.fallback();
      return;
    }

    const url = new URL(route.request().url());
    const payment = (url.searchParams.get("payment") ?? "ALL").toUpperCase();
    const date = url.searchParams.get("date");

    const filtered = state.orders
      .filter((entry) => entry.status === "CLOSED")
      .filter((entry) => {
        if (payment === "UNPAID") return true;
        if (!date) return true;
        if (!entry.closedAt) return false;
        return entry.closedAt.slice(0, 10) === date;
      })
      .filter((entry) => {
        if (payment === "PAID") return entry.paid;
        if (payment === "UNPAID") return !entry.paid;
        return true;
      })
      .map(cloneOrder);

    await json(route, filtered);
  });

  await page.route(/.*\/api\/table-orders\/(\d+)\/items$/, async (route: Route) => {
    if (route.request().method() !== "POST") {
      await route.fallback();
      return;
    }
    const segments = route.request().url().split("/");
    const orderId = Number(segments[segments.length - 2]);
    const order = state.orders.find((entry) => entry.id === orderId);
    if (!order) {
      await json(route, { message: "order-not-found" }, 404);
      return;
    }
    const body = (await route.request().postDataJSON()) as { drinkVariantId: number; quantity: number };
    const variant = state.variants.find((entry) => entry.id === body.drinkVariantId);
    if (!variant) {
      await json(route, { message: "variant-not-found" }, 404);
      return;
    }
    const existing = order.items.find((item) => item.drinkVariantId === variant.id);
    if (existing) {
      existing.quantity += body.quantity;
      existing.totalPrice = (Number(existing.unitPrice) * existing.quantity).toFixed(2);
      existing.deductedVolumeMl = String(variant.volumeMl * existing.quantity);
    } else {
      const item: TableOrderItem = {
        id: state.counters.orderItemId++,
        drinkVariantId: variant.id,
        drinkLabel: `${variant.drinkName} · ${variant.displayVolumeName}`,
        quantity: body.quantity,
        unitPrice: variant.price,
        totalPrice: (Number(variant.price) * body.quantity).toFixed(2),
        deductedVolumeMl: String(variant.volumeMl * body.quantity),
      };
      order.items.push(item);
    }
    recalcOrderTotal(order);
    await json(route, cloneOrder(order));
  });

  await page.route(/.*\/api\/table-orders\/(\d+)\/items\/(\d+)$/, async (route: Route) => {
    if (route.request().method() !== "DELETE") {
      await route.fallback();
      return;
    }

    const segments = route.request().url().split("/");
    const itemId = Number(segments[segments.length - 1]);
    const orderId = Number(segments[segments.length - 3]);
    const order = state.orders.find((entry) => entry.id === orderId);
    if (!order) {
      await json(route, { message: "order-not-found" }, 404);
      return;
    }

    const item = order.items.find((entry) => entry.id === itemId);
    if (!item) {
      await json(route, { message: "item-not-found" }, 404);
      return;
    }

    if (item.quantity <= 1) {
      order.items = order.items.filter((entry) => entry.id !== itemId);
    } else {
      const unitVolumeMl = Number(item.deductedVolumeMl) / item.quantity;
      item.quantity -= 1;
      item.totalPrice = (Number(item.unitPrice) * item.quantity).toFixed(2);
      item.deductedVolumeMl = String(unitVolumeMl * item.quantity);
    }

    recalcOrderTotal(order);
    await json(route, cloneOrder(order));
  });

  await page.route(/.*\/api\/table-orders\/(\d+)\/split-payment$/, async (route: Route) => {
    if (route.request().method() !== "POST") {
      await route.fallback();
      return;
    }
    const segments = route.request().url().split("/");
    const orderId = Number(segments[segments.length - 2]);
    const order = state.orders.find((entry) => entry.id === orderId);
    if (!order) {
      await json(route, { message: "order-not-found" }, 404);
      return;
    }
    const body = (await route.request().postDataJSON()) as { items: Array<{ itemId: number; quantity: number }> };
    const paidItems: TableOrderItem[] = [];

    for (const selected of body.items) {
      const openItem = order.items.find((item) => item.id === selected.itemId);
      if (!openItem || selected.quantity < 1 || selected.quantity > openItem.quantity) {
        await json(route, { message: "invalid-split" }, 400);
        return;
      }
      paidItems.push({
        ...openItem,
        id: state.counters.orderItemId++,
        quantity: selected.quantity,
        totalPrice: (Number(openItem.unitPrice) * selected.quantity).toFixed(2),
        deductedVolumeMl: String((Number(openItem.deductedVolumeMl) / openItem.quantity) * selected.quantity),
      });
      openItem.quantity -= selected.quantity;
      openItem.totalPrice = (Number(openItem.unitPrice) * openItem.quantity).toFixed(2);
      openItem.deductedVolumeMl = String((Number(openItem.deductedVolumeMl) / (openItem.quantity + selected.quantity)) * openItem.quantity);
    }

    order.items = order.items.filter((item) => item.quantity > 0);
    recalcOrderTotal(order);

    if (order.items.length === 0) {
      order.status = "CLOSED";
      order.paid = true;
      order.closedAt = stableIsoTimestamp();
      setTableStatus(state, order.tableId, "FREE");
    } else {
      setTableStatus(state, order.tableId, "OCCUPIED");
    }

    const paidOrder: TableOrder = {
      id: state.counters.orderId++,
      tableId: order.tableId,
      tableName: order.tableName,
      reservationId: order.reservationId,
      status: "CLOSED",
      paid: true,
      openedAt: order.openedAt,
      closedAt: stableIsoTimestamp(),
      total: paidItems.reduce((sum, item) => sum + Number(item.totalPrice), 0).toFixed(2),
      items: paidItems,
    };
    state.orders.push(paidOrder);

    await json(route, { openOrder: cloneOrder(order), paidOrder: cloneOrder(paidOrder) });
  });

  await page.route(/.*\/api\/table-orders\/(\d+)\/close$/, async (route: Route) => {
    if (route.request().method() !== "POST") {
      await route.fallback();
      return;
    }
    const segments = route.request().url().split("/");
    const orderId = Number(segments[segments.length - 2]);
    const order = state.orders.find((entry) => entry.id === orderId);
    if (!order) {
      await json(route, { message: "order-not-found" }, 404);
      return;
    }

    for (const item of order.items) {
      const variant = state.variants.find((entry) => entry.id === item.drinkVariantId);
      if (!variant) continue;
      const target = state.inventory.find((entry) => entry.linkedDrinkVariantId === variant.id)
        ?? state.inventory.find((entry) => entry.linkedDrinkId === variant.drinkId);
      if (!target) continue;
      const nextAmount = Math.max(0, Number(target.totalStockAmount) - Number(item.deductedVolumeMl) / 1000);
      target.totalStockAmount = toAmountString(nextAmount);
    }

    order.status = "CLOSED";
    order.paid = true;
    order.closedAt = stableIsoTimestamp();
    setTableStatus(state, order.tableId, "FREE");
    await json(route, cloneOrder(order));
  });

  await page.route(/.*\/api\/table-orders\/(\d+)\/mark-unpaid$/, async (route: Route) => {
    if (route.request().method() !== "POST") {
      await route.fallback();
      return;
    }

    const segments = route.request().url().split("/");
    const orderId = Number(segments[segments.length - 2]);
    const order = state.orders.find((entry) => entry.id === orderId);
    if (!order) {
      await json(route, { message: "order-not-found" }, 404);
      return;
    }

    order.status = "CLOSED";
    order.paid = false;
    order.closedAt = stableIsoTimestamp();
    setTableStatus(state, order.tableId, "FREE");
    await json(route, cloneOrder(order));
  });

  await page.route(/.*\/api\/table-orders\/(\d+)\/reopen-unpaid$/, async (route: Route) => {
    if (route.request().method() !== "POST") {
      await route.fallback();
      return;
    }

    const segments = route.request().url().split("/");
    const orderId = Number(segments[segments.length - 2]);
    const order = state.orders.find((entry) => entry.id === orderId);
    if (!order) {
      await json(route, { message: "order-not-found" }, 404);
      return;
    }
    if (order.status !== "CLOSED" || order.paid) {
      await json(route, { message: "invalid-order-state" }, 409);
      return;
    }

    const existingOpen = findOpenOrder(state, order.tableId);
    if (existingOpen) {
      await json(route, { message: "table-already-open" }, 409);
      return;
    }

    order.status = "OPEN";
    order.paid = false;
    order.closedAt = null;
    setTableStatus(state, order.tableId, "OCCUPIED");
    await json(route, cloneOrder(order));
  });
}


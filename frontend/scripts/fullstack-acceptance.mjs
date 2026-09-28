// Real browser/BFF/backend/PostgreSQL acceptance. No fabricated API responses.
import { chromium, expect } from '@playwright/test';
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';

const left = process.env.ACCEPTANCE_BFF_LEFT;
const right = process.env.ACCEPTANCE_BFF_RIGHT;
const directory = process.env.ACCEPTANCE_DIRECTORY;
const phase = process.argv[2];
assert.ok(left && right && directory);
const browser = await chromium.launch();
try {
  const context = await browser.newContext(phase === 'after'
    ? { storageState: `${directory}/browser.json` } : {});
  const page = await context.newPage();
  const rightPage = await context.newPage();
  await rightPage.goto(`${right}/login`);
  await page.goto(`${left}/login`);
  async function api(path, method = 'GET', data, key, origin = left) {
    const targetPage = origin === right ? rightPage : page;
    const result = await targetPage.evaluate(async ({ path, method, data, key }) => {
      const response = await fetch(path, { method,
        headers: { 'Content-Type': 'application/json', ...(key ? { 'Idempotency-Key': key } : {}) },
        ...(data !== undefined ? { body: JSON.stringify(data) } : {}),
      });
      return { status: response.status, text: await response.text() };
    }, { path, method, data, key });
    assert.ok(result.status >= 200 && result.status < 300, `${method} ${path}: HTTP ${result.status}`);
    return result.status === 204 ? null : JSON.parse(result.text);
  }
  async function login(device, origin) {
    await device.goto(`${origin}/login`);
    await device.locator('input[type=email]').fill(process.env.BOOTSTRAP_ADMIN_EMAIL);
    await device.locator('input[type=password]').fill(process.env.BOOTSTRAP_ADMIN_PASSWORD);
    await device.locator('button[type=submit]').click();
    await device.waitForURL('**/inventory');
  }
  async function verifyRecovery(state) {
    // A new device has neither cookies nor local/session storage from the original browser.
    const fresh = await browser.newContext();
    try {
      const device = await fresh.newPage();
      await device.goto(`${right}/login`);
      assert.deepEqual(await device.evaluate(() => [localStorage.length, sessionStorage.length]), [0, 0]);
      await login(device, right);
      await device.goto(`${right}/table-billing`);
      const archived = device.getByRole('button', { name: `Bon #${state.unpaidOrderId} laden`, exact: true });
      await expect(archived).toBeVisible();
      const table = device.getByRole('button', { name: /Audit table.*OCCUPIED/ });
      await expect(table).toBeVisible();
      await table.click();
      const dialog = device.getByRole('dialog', { name: 'Tischdetail' });
      await expect(dialog).toContainText(`Bon #${state.orderId}`);
      await expect(dialog.getByText('1 × 3,00 €', { exact: true })).toBeVisible();
      await expect(dialog.getByRole('button', { name: 'Bezahlen', exact: true })).toBeEnabled();
      // Discard every browser cache and rebuild the same open bill after reload.
      await device.evaluate(() => { localStorage.clear(); sessionStorage.clear(); });
      await device.reload();
      await table.click();
      await expect(dialog).toContainText(`Bon #${state.orderId}`);
      await expect(dialog.getByText('1 × 3,00 €', { exact: true })).toBeVisible();
      await device.goto(`${right}/table-billing`);
      await archived.click();
      await expect(dialog).toContainText(`Bon #${state.unpaidOrderId}`);
      await expect(dialog.getByRole('button', { name: 'Wieder oeffnen', exact: true })).toBeEnabled();
      await expect(dialog.getByText('1 × 3,00 €', { exact: true })).toBeVisible();
      await device.goto(`${right}/table-billing`);
      await device.getByPlaceholder('Order ID').fill(String(state.paidOrderId));
      await device.getByRole('button', { name: 'Laden', exact: true }).click();
      await expect(dialog).toContainText(`Bon #${state.paidOrderId}`);
      await expect(dialog.getByRole('button', { name: 'Bezahlen', exact: true })).toBeDisabled();
      await device.goto(`${right}/shift-settlement`);
      await device.locator('#settlement-date').fill('2038-06-01');
      await expect(device.locator('#opening-cash')).toHaveValue('100');
      await expect(device.locator('#other-expenses')).toHaveValue('5');
      await expect(device.getByPlaceholder('Mitarbeitername')).toHaveValue('Recovery worker');
      await expect(device.locator('input[type=time]').first()).toHaveValue('18:00');
      await expect(device.locator('input[type=time]').nth(1)).toHaveValue('22:00');
      // Authenticated server reads verify the associated reservation and exact stock too.
      const recovered = await device.evaluate(async ({ state }) => {
        const read = async path => { const r = await fetch(path); if (!r.ok) throw new Error(`Recovery GET: ${r.status}`); return r.json(); };
        return { reservation: (await read('/api/reservations')).find(r => r.id === state.reservationId),
          stock: (await read('/api/inventory')).find(i => i.id === state.inventoryId),
          open: await read(`/api/table-orders/${state.orderId}`) };
      }, { state });
      assert.equal(recovered.reservation.status, 'CHECKED_IN');
      assert.equal(recovered.open.status, 'OPEN');
      assert.equal(recovered.open.items[0].quantity, 1);
      assert.equal(Number(recovered.stock.totalStockAmount), 9.25);
      console.log(`PASS: ${phase} restart — fresh-device login, cache-free reload, open/unpaid/paid split receipts, occupancy and saved shift`);
    } finally { await fresh.close(); }
  }
  if (phase === 'before') {
    await page.goto(`${left}/login`);
    await page.locator('input[type=email]').fill(process.env.BOOTSTRAP_ADMIN_EMAIL);
    await page.locator('input[type=password]').fill(process.env.BOOTSTRAP_ADMIN_PASSWORD);
    await page.locator('button[type=submit]').click();
    await page.waitForURL(url => !url.pathname.startsWith('/login'));
    const original = (await context.cookies()).find(c => c.name === 'fw_refresh_token');
    assert.ok(original?.value.startsWith('fw_'));
    await context.clearCookies({ name: 'fw_access_token' });
    await Promise.all([api('/api/auth/refresh', 'POST'), api('/api/auth/refresh', 'POST', undefined, undefined, right)]);
    assert.ok((await context.cookies()).find(c => c.name === 'fw_refresh_token')?.value === original.value, 'Browser refresh credential stays stable');
    assert.equal((await api('/api/auth/status')).authenticated, true);
    assert.equal((await api('/api/auth/status', 'GET', undefined, undefined, right)).authenticated, true);

    const category = await api('/api/drink-categories', 'POST', { name: 'Audit acceptance', sortOrder: 0, active: true });
    const drink = await api('/api/drinks', 'POST', { categoryId: category.id, name: 'Audit beer', active: true });
    const variant = await api('/api/drink-variants', 'POST', {
      drinkId: drink.id, displayVolumeName: '250 ml', volumeMl: 250, price: 3, useStandardPrice: false, active: true,
    });
    const inventory = await api('/api/inventory', 'POST', {
      name: 'Audit stock', linkedDrinkId: drink.id, linkedDrinkVariantId: variant.id,
      packageType: 'BARREL', packagesInStock: 1, contentPerPackage: 10, contentUnit: 'LITER',
      reorderThreshold: 1, minimumStock: 1, recommendedReorderAmount: 10, active: true,
    });
    await api('/api/tables', 'POST', { name: 'Audit table', seats: 4, status: 'FREE', active: true });
    const arrival = new Date(Date.now() + 120000).toISOString();
    const reservation = await api('/api/reservations', 'POST', {
      guestName: 'Acceptance guest', reservationDate: arrival.slice(0, 10), reservationTime: arrival.slice(11, 16), guestCount: 2,
    });
    await api(`/api/reservations/${reservation.id}/confirm`, 'POST', {});
    await api(`/api/reservations/${reservation.id}/check-in`, 'POST');
    const order = await api(`/api/table-orders/open/table/${reservation.assignedTableIds[0]}`);
    // A stock form read before another device books a drink must not restore stock.
    await page.goto(`${left}/inventory`);
    await expect(page.getByRole('heading', { name: 'Lagerbestand kompakt' })).toBeVisible();
    const showConfig = page.getByRole('button', { name: 'Konfiguration einblenden' });
    if (await showConfig.count()) await showConfig.click();
    await page.getByLabel('Lagerartikel verknüpfen').selectOption(String(inventory.id));
    await page.getByLabel('Drink für Verknüpfung').selectOption(String(drink.id));
    await page.getByLabel('Variante für Verknüpfung').selectOption(String(variant.id));
    const consumed = await api(`/api/table-orders/${order.id}/items`, 'POST',
      { drinkVariantId: variant.id, quantity: 1 }, 'phase2-concurrent-consumption', right);
    const inventoryConflict = page.waitForResponse(response => response.url().endsWith(`/api/inventory/${inventory.id}`) && response.request().method() === 'PUT');
    await page.getByRole('button', { name: 'Verknüpfung speichern', exact: true }).click();
    assert.equal((await inventoryConflict).status(), 409);
    await expect(page.getByText(/Aktueller Bestand wurde neu geladen/).first()).toBeVisible();
    await expect(page.getByLabel('Variante für Verknüpfung')).toHaveValue(String(variant.id));
    assert.equal(Number((await api('/api/inventory')).find(item => item.id === inventory.id).totalStockAmount), 9.75);
    const inventorySaved = page.waitForResponse(response => response.url().endsWith(`/api/inventory/${inventory.id}`) && response.request().method() === 'PUT');
    await page.getByRole('button', { name: 'Verknüpfung speichern', exact: true }).click();
    assert.equal((await inventorySaved).status(), 200);
    assert.equal(Number((await api('/api/inventory')).find(item => item.id === inventory.id).totalStockAmount), 9.75);
    await api(`/api/table-orders/${order.id}/items/${consumed.items[0].id}`, 'DELETE', undefined, 'phase2-restore-consumption', right);

    // Two independent BFFs edit the same shift; the losing browser retains its draft.
    for (const [device, origin] of [[page, left], [rightPage, right]]) {
      await device.goto(`${origin}/shift-settlement`);
      await device.locator('#settlement-date').fill('2038-06-01');
      await expect(device.getByRole('button', { name: 'Schichtabrechnung speichern', exact: true })).toBeEnabled();
      await expect(device.locator('#opening-cash')).toHaveValue('0');
    }
    await page.locator('#opening-cash').fill('100');
    await rightPage.locator('#opening-cash').fill('200');
    await page.getByRole('button', { name: 'Schichtabrechnung speichern', exact: true }).click();
    await expect(page.getByText('Schichtabrechnung wurde gespeichert.', { exact: true }).first()).toBeVisible();
    await rightPage.getByRole('button', { name: 'Schichtabrechnung speichern', exact: true }).click();
    await expect(rightPage.getByText('Konflikt: Dein Entwurf oben wurde nicht überschrieben.', { exact: true })).toBeVisible();
    await expect(rightPage.locator('#opening-cash')).toHaveValue('200');
    await rightPage.getByRole('button', { name: 'Serverstand übernehmen und Entwurf verwerfen', exact: true }).click();
    await expect(rightPage.locator('#opening-cash')).toHaveValue('100');
    await rightPage.locator('#other-expenses').fill('5');
    await rightPage.getByRole('button', { name: 'Schichtabrechnung speichern', exact: true }).click();
    await expect(rightPage.getByText('Schichtabrechnung wurde gespeichert.', { exact: true }).first()).toBeVisible();
    const shift = await api('/api/shift-settlements/2038-06-01');
    assert.equal(shift.revision, 2);
    assert.equal(Number(shift.openingCash), 100);
    assert.equal(Number(shift.otherExpenses), 5);
    console.log('PASS: two-device stock/shift conflicts, preserved draft, explicit resolution and unchanged physical stock');

    // Exercise the actual UI: server commits, response is lost, then reload/retry.
    await page.goto(`${left}/table-billing`);
    await page.getByPlaceholder('Order ID').fill(String(order.id));
    await page.getByRole('button', { name: 'Laden', exact: true }).click();
    async function lostUiAction(path, trigger, reload = false) {
      let key;
      let firstResult;
      let attempts = 0;
      await page.route(`${left}${path}`, async route => {
        const requestKey = route.request().headers()['idempotency-key'];
        assert.ok(requestKey, 'UI sends an operation key');
        if (key) assert.equal(requestKey, key, 'UI retry preserves the original key');
        key = requestKey;
        const upstream = await route.fetch();
        assert.ok(upstream.ok(), `UI operation: HTTP ${upstream.status()}`);
        attempts++;
        if (attempts === 1) {
          firstResult = await upstream.json();
          await route.abort('connectionreset');
        } else { await route.fulfill({ response: upstream }); }
      });
      await trigger();
      await expect(page.getByRole('button', { name: 'Offene Aktion wiederholen', exact: true })).toBeVisible({ timeout: 8000 });
      if (reload === 'login') {
        await context.clearCookies();
        await login(page, left);
        await page.goto(`${left}/table-billing`);
      } else if (reload) await page.reload();
      await page.getByRole('button', { name: 'Offene Aktion wiederholen', exact: true }).click();
      await expect(page.getByRole('button', { name: 'Offene Aktion wiederholen', exact: true })).toHaveCount(0);
      await expect.poll(() => attempts).toBe(2);
      await expect.poll(() => page.evaluate(() => sessionStorage.getItem('billing-pending-command-v1'))).toBeNull();
      await page.unroute(`${left}${path}`);
      return { key, result: firstResult };
    }
    const uiAdd = await lostUiAction(`/api/table-orders/${order.id}/items`,
      () => page.getByRole('button', { name: /Audit beer · 250 ml/ }).click(), true);
    assert.equal((await api(`/api/table-orders/${order.id}`)).items[0].quantity, 1);
    await lostUiAction(`/api/table-orders/${order.id}/items/${uiAdd.result.items[0].id}`,
      () => page.getByRole('button', { name: 'Entfernen', exact: true }).evaluate(button => { button.click(); button.click(); }));
    assert.equal((await api(`/api/table-orders/${order.id}`)).items.length, 0);
    const addPath = `/api/table-orders/${order.id}/items`;
    const addBody = { drinkVariantId: variant.id, quantity: 3 };
    // Let the real request commit, then drop only its response to the browser.
    let committed = false;
    await page.route(`${left}${addPath}`, async route => {
      const upstream = await route.fetch();
      assert.ok(upstream.ok(), `real add: HTTP ${upstream.status()}`);
      committed = true;
      await route.abort('connectionreset');
    });
    const delivered = await page.evaluate(async ({ path, body }) => {
      try {
        await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'audit-lost-add' }, body: JSON.stringify(body) });
        return true;
      } catch { return false; }
    }, { path: addPath, body: addBody });
    assert.equal(committed, true);
    assert.equal(delivered, false);
    await page.unroute(`${left}${addPath}`);
    const replay = await api(addPath, 'POST', addBody, 'audit-lost-add', right);
    assert.equal(replay.items[0].quantity, 3);
    const cancelItemId = replay.items[0].id;
    await api(`${addPath}/${cancelItemId}`, 'DELETE', undefined, 'audit-cancel');
    const cancelled = await api(`${addPath}/${cancelItemId}`, 'DELETE', undefined, 'audit-cancel', right);
    assert.equal(cancelled.items[0].quantity, 2);
    const splitBody = { items: [{ itemId: replay.items[0].id, quantity: 1 }] };
    await page.reload();
    await page.getByPlaceholder('Order ID').fill(String(order.id));
    await page.getByRole('button', { name: 'Laden', exact: true }).click();
    const uiSplit = await lostUiAction(`/api/table-orders/${order.id}/split-payment`,
      () => page.getByRole('button', { name: 'Teilzahlung', exact: true }).click(), 'login');
    const split = await api(`/api/table-orders/${order.id}/split-payment`, 'POST', splitBody, uiSplit.key, right);
    assert.equal(Number(split.paidOrder.total), 3);
    assert.equal(split.openOrder.items[0].quantity, 1);
    const stock = (await api('/api/inventory')).find(item => item.id === inventory.id);
    assert.equal(Number(stock.totalStockAmount), 9.5);
    const debtTable = await api('/api/tables', 'POST', { name: 'Recovery debt', seats: 2, status: 'FREE', active: true });
    const debtOrder = await api('/api/table-orders/open', 'POST', { tableId: debtTable.id, reservationId: null });
    await api(`/api/table-orders/${debtOrder.id}/items`, 'POST', { drinkVariantId: variant.id, quantity: 1 }, 'phase5-debt-add');
    await api(`/api/table-orders/${debtOrder.id}/mark-unpaid`, 'POST', undefined, 'phase5-debt-archive');
    await api('/api/shift-settlements/2038-06-01', 'PUT', { expectedRevision: shift.revision,
      openingCash: 100, otherExpenses: 5,
      entries: [{ employeeName: 'Recovery worker', shiftStart: '18:00', shiftEnd: '22:00', hourlyWage: 12.5 }] });
    const recoveryState = { reservationId: reservation.id, orderId: order.id,
      inventoryId: inventory.id, addBody, splitBody, cancelItemId, splitKey: uiSplit.key,
      paidOrderId: split.paidOrder.id, unpaidOrderId: debtOrder.id };
    await verifyRecovery(recoveryState);
    await writeFile(`${directory}/state.json`, JSON.stringify(recoveryState), { mode: 0o600 });
    await context.storageState({ path: `${directory}/browser.json` });
    console.log('PASS: browser login, two BFF refreshes, reservation/check-in, lost response, add retry, split and stock');
  } else {
    const state = JSON.parse(await readFile(`${directory}/state.json`, 'utf8'));
    const persistedShift = await api('/api/shift-settlements/2038-06-01');
    assert.equal(persistedShift.revision, 3);
    await verifyRecovery(state);
    assert.equal(Number(persistedShift.openingCash), 100);
    // Same credentials, same commands, all application processes restarted.
    await api(`/api/table-orders/${state.orderId}/items`, 'POST', state.addBody, 'audit-lost-add');
    await api(`/api/table-orders/${state.orderId}/items/${state.cancelItemId}`, 'DELETE', undefined, 'audit-cancel');
    const split = await api(`/api/table-orders/${state.orderId}/split-payment`, 'POST', state.splitBody, state.splitKey, right);
    assert.equal(split.paidOrder.id, state.paidOrderId);
    assert.equal(split.openOrder.items[0].quantity, 1);
    assert.equal(Number((await api('/api/inventory')).find(item => item.id === state.inventoryId).totalStockAmount), 9.25);
    const closed = await api(`/api/table-orders/${state.orderId}/close`, 'POST');
    assert.equal(closed.status, 'CLOSED');
    await api(`/api/reservations/${state.reservationId}/complete`, 'POST');
    const reservations = await api('/api/reservations');
    assert.equal(reservations.find(item => item.id === state.reservationId).status, 'COMPLETED');

    // Booked prices survive catalog changes, including after a completed payment.
    const priceTable = await api('/api/tables', 'POST', { name: 'Price binding table', seats: 2, status: 'FREE', active: true });
    const priceOrder = await api('/api/table-orders/open', 'POST', { tableId: priceTable.id, reservationId: null });
    const priceVariant = (await api('/api/drink-variants')).find(v => v.id === state.addBody.drinkVariantId);
    await api(`/api/table-orders/${priceOrder.id}/items`, 'POST', { drinkVariantId: priceVariant.id, quantity: 1 }, 'phase6-old-price');
    await api(`/api/drink-variants/${priceVariant.id}`, 'PUT', { drinkId: priceVariant.drinkId,
      displayVolumeName: priceVariant.displayVolumeName, volumeMl: priceVariant.volumeMl,
      price: 4, useStandardPrice: false, active: true });
    assert.equal(Number((await api(`/api/table-orders/${priceOrder.id}`)).total), 3);
    assert.equal(Number((await api(`/api/table-orders/${state.paidOrderId}`)).total), 3);
    const newPriced = await api(`/api/table-orders/${priceOrder.id}/items`, 'POST', { drinkVariantId: priceVariant.id, quantity: 1 }, 'phase6-new-price');
    assert.equal(newPriced.items.length, 2);
    assert.equal(Number(newPriced.total), 7);
    await page.goto(`${left}/table-billing`);
    await page.getByRole('button', { name: /Price binding table.*OCCUPIED/ }).click();
    const priceDialog = page.getByRole('dialog', { name: 'Tischdetail' });
    await expect(priceDialog.getByText('1 × 3,00 €', { exact: true })).toBeVisible();
    await expect(priceDialog.getByText('1 × 4,00 €', { exact: true })).toBeVisible();
    console.log('PASS: catalog price change preserves open/paid receipt prices; new sale has a separate price position');

    // A direct checkout survives a lost response/reload without another stock or payment effect.
    const tablesBeforeDirect = await api('/api/tables');
    const stockBeforeDirect = Number((await api('/api/inventory')).find(i => i.id === state.inventoryId).totalStockAmount);
    await page.goto(`${left}/direct-sales`);
    await page.getByRole('button', { name: /Audit beer · 250 ml/ }).click();
    await page.getByRole('button', { name: /Audit beer · 250 ml/ }).click();
    await page.getByLabel('Zahlungsart').selectOption('CARD');
    let directKey, directReceipt;
    let directAttempts = 0;
    await page.route(`${left}/api/table-orders/direct`, async route => {
      const key = route.request().headers()['idempotency-key'];
      assert.ok(key);
      if (directKey) assert.equal(key, directKey);
      directKey = key;
      const upstream = await route.fetch();
      assert.ok(upstream.ok(), `Direct sale HTTP ${upstream.status()}`);
      const receipt = await upstream.json();
      if (directReceipt) assert.equal(receipt.id, directReceipt.id);
      directReceipt = receipt;
      if (++directAttempts === 1) await route.abort('connectionreset');
      else await route.fulfill({ response: upstream });
    });
    await page.getByRole('button', { name: 'Zahlung erhalten – abschließen', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Offene Aktion wiederholen' })).toBeVisible();
    await page.reload();
    await page.getByRole('button', { name: 'Offene Aktion wiederholen' }).click();
    await expect(page.getByRole('status')).toContainText('Bereit für den nächsten Verkauf.');
    assert.equal(directAttempts, 2);
    assert.equal(directReceipt.saleType, 'DIRECT');
    assert.equal(directReceipt.paymentMethod, 'CARD');
    assert.equal(directReceipt.tableId, null);
    assert.equal(directReceipt.reservationId, null);
    assert.equal(directReceipt.status, 'CLOSED');
    assert.equal(directReceipt.paid, true);
    assert.equal(Number(directReceipt.total), 8);
    assert.equal(Number((await api('/api/inventory')).find(i => i.id === state.inventoryId).totalStockAmount), stockBeforeDirect - 0.5);
    assert.deepEqual(await api('/api/tables'), tablesBeforeDirect);
    assert.equal(await page.evaluate(() => sessionStorage.getItem('fasswerk-direct-sale-pending-v1')), null);
    await page.unroute(`${left}/api/table-orders/direct`);
    const directArchive = await api('/api/table-orders/archive?query=Barverkauf&payment=PAID');
    assert.ok(directArchive.some(o => o.id === directReceipt.id));
    console.log('PASS: real direct checkout, card receipt, lost-response replay after reload, one stock deduction, no table occupancy');

    // Manual day close through the real UI/BFF: replay after reload cannot advance twice.
    const beforeDate = (await api('/api/inventory/configuration')).effectiveBusinessDate;
    await page.goto(`${left}/sales-configuration`);
    let dayKey;
    let dayAttempts = 0;
    await page.route(`${left}/api/inventory/configuration/manual-day-close`, async route => {
      const key = route.request().headers()['idempotency-key'];
      assert.ok(key);
      if (dayKey) assert.equal(key, dayKey);
      dayKey = key;
      assert.equal(route.request().postDataJSON().expectedBusinessDate, beforeDate);
      const upstream = await route.fetch();
      assert.ok(upstream.ok());
      if (++dayAttempts === 1) await route.abort('connectionreset');
      else await route.fulfill({ response: upstream });
    });
    await page.getByRole('button', { name: 'Tagesabschluss manuell (+1 Tag)', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Offene Aktion wiederholen' })).toBeVisible();
    await page.reload();
    await page.getByRole('button', { name: 'Offene Aktion wiederholen' }).click();
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('manual-day-close-pending-v1'))).toBeNull();
    assert.equal(dayAttempts, 2);
    const nextDate = new Date(`${beforeDate}T12:00:00Z`);
    nextDate.setUTCDate(nextDate.getUTCDate() + 1);
    assert.equal((await api('/api/inventory/configuration')).effectiveBusinessDate, nextDate.toISOString().slice(0, 10));
    await page.unroute(`${left}/api/inventory/configuration/manual-day-close`);
    console.log('PASS: actual UI add/cancel/split/day-close retries, durable keys after reload, exactly one effect');

    // A response completed before logout but delivered afterwards cannot authorize again.
    await api('/api/auth/refresh', 'POST', undefined, undefined, right);
    const stale = await context.cookies();
    await api('/api/auth/logout', 'POST');
    const lateContext = await browser.newContext();
    await lateContext.addCookies(stale);
    const latePage = await lateContext.newPage();
    await latePage.goto(`${right}/login`);
    const denied = await latePage.evaluate(async () => (await fetch('/api/tables')).status);
    assert.equal(denied, 401);
    const refresh = await latePage.evaluate(async () => (await fetch('/api/auth/refresh', { method: 'POST' })).status);
    assert.equal(refresh, 401);
    await lateContext.close();
    console.log('PASS: restart, persisted idempotency, payment/checkout, stock and late-cookie logout rejection');
  }
  await context.close();
} finally { await browser.close(); }

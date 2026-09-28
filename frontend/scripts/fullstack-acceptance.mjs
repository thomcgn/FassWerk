// Real browser/BFF/backend/PostgreSQL acceptance. No fabricated API responses.
import { chromium } from '@playwright/test';
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
    const split = await api(`/api/table-orders/${order.id}/split-payment`, 'POST', splitBody, 'audit-split');
    assert.equal(Number(split.paidOrder.total), 3);
    assert.equal(split.openOrder.items[0].quantity, 1);
    const stock = (await api('/api/inventory')).find(item => item.id === inventory.id);
    assert.equal(Number(stock.totalStockAmount), 9.5);
    await writeFile(`${directory}/state.json`, JSON.stringify({ reservationId: reservation.id, orderId: order.id,
      inventoryId: inventory.id, addBody, splitBody, cancelItemId, paidOrderId: split.paidOrder.id }), { mode: 0o600 });
    await context.storageState({ path: `${directory}/browser.json` });
    console.log('PASS: browser login, two BFF refreshes, reservation/check-in, lost response, add retry, split and stock');
  } else {
    const state = JSON.parse(await readFile(`${directory}/state.json`, 'utf8'));
    // Same credentials, same commands, all application processes restarted.
    await api(`/api/table-orders/${state.orderId}/items`, 'POST', state.addBody, 'audit-lost-add');
    await api(`/api/table-orders/${state.orderId}/items/${state.cancelItemId}`, 'DELETE', undefined, 'audit-cancel');
    const split = await api(`/api/table-orders/${state.orderId}/split-payment`, 'POST', state.splitBody, 'audit-split', right);
    assert.equal(split.paidOrder.id, state.paidOrderId);
    assert.equal(split.openOrder.items[0].quantity, 1);
    assert.equal(Number((await api('/api/inventory')).find(item => item.id === state.inventoryId).totalStockAmount), 9.5);
    const closed = await api(`/api/table-orders/${state.orderId}/close`, 'POST');
    assert.equal(closed.status, 'CLOSED');
    await api(`/api/reservations/${state.reservationId}/complete`, 'POST');
    const reservations = await api('/api/reservations');
    assert.equal(reservations.find(item => item.id === state.reservationId).status, 'COMPLETED');

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

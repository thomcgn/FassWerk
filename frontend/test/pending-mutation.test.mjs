import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PendingMutation } from '../lib/pending-mutation.ts';
const command = { url: '/api/table-orders/1/items', method: 'POST', body: '{"quantity":1}', label: 'Position' };
const consume = response => response.json();
function storage() {
  const values = new Map();
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
}

test('lost response and reload reuse key and payload; confirmed next intent gets a new key', async () => {
  const db = storage();
  let runner = new PendingMutation(db, 'pending');
  const keys = [];
  const committed = new Set();
  let effects = 0;
  const send = async (_url, init) => {
    const key = init.headers['Idempotency-Key'];
    keys.push(key);
    assert.equal(init.body, command.body);
    if (!committed.has(key)) { committed.add(key); effects++; }
    if (keys.length === 1) throw new TypeError('response lost after commit');
    return Response.json({ effects });
  };
  await assert.rejects(runner.run(command, consume, send), /Ausgang.*unklar/);
  runner = new PendingMutation(db, 'pending');
  assert.equal((await runner.run(undefined, consume, send)).value.effects, 1);
  assert.equal(keys[0], keys[1]);
  assert.equal(db.getItem('pending'), null);
  assert.equal((await runner.run(command, consume, send)).value.effects, 2);
  assert.notEqual(keys[1], keys[2]);
});

test('double tap sends only one in-flight request', async () => {
  const runner = new PendingMutation(storage(), 'pending');
  let finish;
  let sent = 0;
  const send = () => { sent++; return new Promise(resolve => { finish = resolve; }); };
  const first = runner.run(command, consume, send);
  assert.equal(await runner.run(command, consume, send), null);
  assert.equal(sent, 1);
  finish(Response.json({ ok: true }));
  await first;
});

test('unknown outcome blocks a different operation instead of silently replacing the key', async () => {
  const runner = new PendingMutation(storage(), 'pending');
  await assert.rejects(runner.run(command, consume, async () => { throw new Error('timeout'); }));
  await assert.rejects(runner.run({ ...command, body: '{"quantity":2}' }, consume), /zuerst die offene Aktion/);
  assert.equal(runner.pending.command.body, command.body);
});

test('server error or malformed successful response keeps the command for retry', async () => {
  for (const response of [new Response('', { status: 502 }), new Response('not json')]) {
    const runner = new PendingMutation(storage(), 'pending');
    await assert.rejects(runner.run(command, consume, async () => response), /Ausgang.*unklar/);
    assert.ok(runner.pending);
  }
});

test('definite first rejection allows correction; later rejection never discards an unknown earlier commit', async () => {
  const runner = new PendingMutation(storage(), 'pending');
  await assert.rejects(runner.run(command, consume, async () => Response.json({ message: 'No stock' }, { status: 409 })), /No stock/);
  assert.equal(runner.pending, null);
  await assert.rejects(runner.run(command, consume, async () => { throw new Error('lost'); }));
  const key = runner.pending.key;
  await assert.rejects(runner.run(undefined, consume, async () => new Response('', { status: 401 })));
  assert.equal(runner.pending.key, key);
});

test('storage failure prevents a financial request from being sent', async () => {
  const db = storage();
  db.setItem = () => { throw new Error('quota'); };
  const runner = new PendingMutation(db, 'pending');
  let sent = false;
  await assert.rejects(runner.run(command, consume, async () => { sent = true; return Response.json({}); }));
  assert.equal(sent, false);
});

test('corrupt pending data is not silently discarded', () => {
  const db = storage();
  db.setItem('pending', '{"command":{"url":"https://elsewhere.test"}}');
  assert.throws(() => new PendingMutation(db, 'pending'));
  assert.notEqual(db.getItem('pending'), null);
});


test('late response from a previous page instance cannot erase a newer pending command', async () => {
  const db = storage();
  const oldPage = new PendingMutation(db, 'pending');
  let deliverOld;
  const oldRequest = oldPage.run(command, consume, () => new Promise(resolve => { deliverOld = resolve; }));
  const newPage = new PendingMutation(db, 'pending');
  await newPage.run(undefined, consume, async () => Response.json({ ok: true }));
  let deliverNew;
  const newRequest = newPage.run(command, consume, () => new Promise(resolve => { deliverNew = resolve; }));
  const newKey = newPage.pending.key;
  deliverOld(Response.json({ ok: true }));
  await oldRequest;
  assert.equal(JSON.parse(db.getItem('pending')).key, newKey);
  deliverNew(Response.json({ ok: true }));
  await newRequest;
  assert.equal(db.getItem('pending'), null);
});

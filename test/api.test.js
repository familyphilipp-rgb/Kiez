const { test, before, after } = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const os = require('node:os');
const fs = require('node:fs');

process.env.DB_FILE = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'kiez-')), 'test.db');
const { server } = require('../server.js');
let base;
before(() => new Promise((r) => server.listen(0, () => { base = `http://localhost:${server.address().port}`; r(); })));
after(() => server.close());

async function call(method, p, token, body) {
  const res = await fetch(base + p, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, data: await res.json() };
}
const newUser = async () => (await call('POST', '/api/register')).data.token;

const BERLIN = { lat: 52.52, lng: 13.405 };
const POTSDAM = { lat: 52.39, lng: 13.065 };   // ~27 km entfernt

test('Posts sind nur im Radius sichtbar', async () => {
  const a = await newUser();
  const r = await call('POST', '/api/posts', a, { text: 'Hallo Kiez', ...BERLIN });
  assert.equal(r.status, 201);
  const near = await call('GET', `/api/posts?lat=52.53&lng=13.41`, a);
  assert.ok(near.data.posts.some((p) => p.text === 'Hallo Kiez'));
  const far = await call('GET', `/api/posts?lat=${POTSDAM.lat}&lng=${POTSDAM.lng}`, a);
  assert.ok(!far.data.posts.some((p) => p.text === 'Hallo Kiez'));
});

test('Votes, eigene Posts nicht votebar, ab -5 ausgeblendet', async () => {
  const owner = await newUser();
  const { data: post } = await call('POST', '/api/posts', owner, { text: 'Downvote mich', ...BERLIN });
  assert.equal((await call('POST', `/api/posts/${post.id}/vote`, owner, { value: 1 })).status, 400);
  for (let i = 0; i < 5; i++) {
    const u = await newUser();
    await call('POST', `/api/posts/${post.id}/vote`, u, { value: -1 });
  }
  const feed = await call('GET', `/api/posts?lat=${BERLIN.lat}&lng=${BERLIN.lng}`, owner);
  assert.ok(!feed.data.posts.some((p) => p.id === post.id));
});

test('Antworten: OJ-Markierung und feste Nummern', async () => {
  const op = await newUser(), x = await newUser(), y = await newUser();
  const { data: post } = await call('POST', '/api/posts', op, { text: 'Frage an alle', ...BERLIN });
  await call('POST', `/api/posts/${post.id}/replies`, x, { text: 'erste' });
  await call('POST', `/api/posts/${post.id}/replies`, op, { text: 'danke' });
  await call('POST', `/api/posts/${post.id}/replies`, y, { text: 'zweite' });
  await call('POST', `/api/posts/${post.id}/replies`, x, { text: 'nochmal' });
  const { data } = await call('GET', `/api/posts/${post.id}?lat=${BERLIN.lat}&lng=${BERLIN.lng}`, op);
  assert.deepEqual(data.replies.map((r) => r.isOP ? 'OJ' : r.authorNo), [1, 'OJ', 2, 1]);
  assert.equal(data.post.commentCount, 4);
});

test('Sortierung und Validierung', async () => {
  const u = await newUser();
  assert.equal((await call('POST', '/api/posts', u, { text: '', ...BERLIN })).status, 400);
  assert.equal((await call('POST', '/api/posts', u, { text: 'x'.repeat(251), ...BERLIN })).status, 400);
  assert.equal((await call('GET', '/api/posts', u)).status, 400);
  assert.equal((await call('GET', '/api/posts?lat=1&lng=1')).status, 401);
  const loud = await call('GET', `/api/posts?lat=${BERLIN.lat}&lng=${BERLIN.lng}&sort=loud`, u);
  assert.equal(loud.data.posts[0].text, 'Frage an alle');
});

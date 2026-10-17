const { test, before, after } = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const os = require('node:os');
const fs = require('node:fs');

const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'kiez-'));
process.env.DB_FILE = path.join(TMP, 'test.db');
process.env.UPLOAD_DIR = path.join(TMP, 'uploads');
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

async function upload(token, type, buf) {
  const res = await fetch(base + '/api/media', { method: 'POST', headers: { 'Content-Type': type, Authorization: `Bearer ${token}` }, body: buf });
  return { status: res.status, data: await res.json() };
}
// 1x1-JPEG
const JPEG = Buffer.from('/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', 'base64');

test('Foto-Post: Upload, Anhängen, Ausliefern, Aufräumen', async () => {
  const u = await newUser();
  const up = await upload(u, 'image/jpeg', JPEG);
  assert.equal(up.status, 201, JSON.stringify(up.data));
  assert.equal(up.data.kind, 'image');
  const { status, data: post } = await call('POST', '/api/posts', u, { mediaId: up.data.id, ...BERLIN });
  assert.equal(status, 201);
  assert.equal(post.media.kind, 'image');
  const file = await fetch(base + post.media.url);
  assert.equal(file.status, 200);
  assert.match(file.headers.get('content-type'), /^image\//);
  // Dasselbe Medium kann nicht zweimal benutzt werden
  assert.equal((await call('POST', '/api/posts', u, { mediaId: up.data.id, ...BERLIN })).status, 400);
  // Fremde Medien kann man nicht anhängen
  const other = await newUser();
  const up2 = await upload(u, 'image/jpeg', JPEG);
  assert.equal((await call('POST', '/api/posts', other, { mediaId: up2.data.id, ...BERLIN })).status, 400);
  // Löschen entfernt auch die Datei
  await call('DELETE', `/api/posts/${post.id}`, u);
  assert.equal((await fetch(base + post.media.url)).status, 404);
});

test('Upload-Prüfungen: falscher Typ, gefälschter Inhalt', async () => {
  const u = await newUser();
  assert.equal((await upload(u, 'text/html', Buffer.from('<h1>x</h1>'))).status, 415);
  assert.equal((await upload(u, 'image/png', Buffer.from('<script>alert(1)</script>'))).status, 415);
});

const { spawnSync } = require('node:child_process');
const hasFfmpeg = spawnSync('ffmpeg', ['-version']).status === 0;
test('Video: wird zu MP4 umgewandelt, bekommt Vorschaubild, Range-Anfragen gehen', { skip: !hasFfmpeg && 'ffmpeg fehlt' }, async () => {
  const src = path.join(TMP, 'clip.mov');
  spawnSync('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'lavfi', '-i', 'testsrc=size=640x360:rate=25', '-f', 'lavfi', '-i', 'sine', '-t', '2',
    '-metadata', 'location=+52.5200+013.4050/', '-c:v', 'libx264', '-c:a', 'aac', src]);
  const u = await newUser();
  const up = await upload(u, 'video/quicktime', fs.readFileSync(src));
  assert.equal(up.status, 201, JSON.stringify(up.data));
  assert.match(up.data.url, /\.mp4$/);
  assert.ok(up.data.poster);
  const r = await fetch(base + up.data.url, { headers: { Range: 'bytes=0-99' } });
  assert.equal(r.status, 206);
  assert.equal((await r.arrayBuffer()).byteLength, 100);
  const meta = spawnSync('ffprobe', ['-v', 'error', '-show_entries', 'format_tags', '-of', 'json', path.join(process.env.UPLOAD_DIR, path.basename(up.data.url))]);
  assert.ok(!String(meta.stdout).includes('52.52'), 'GPS-Metadaten müssen entfernt sein');
});

test('Hashtags als Kanäle, Meine Aktivität und Karma-Stufe', async () => {
  const a = await newUser(), b = await newUser();
  const { data: p1 } = await call('POST', '/api/posts', a, { text: 'Wer kommt mit? #Flohmarkt #kiez', ...BERLIN });
  await call('POST', '/api/posts', b, { text: 'Heute #flohmarkt am Breslauer Platz', ...BERLIN });
  const feed = await call('GET', `/api/posts?lat=${BERLIN.lat}&lng=${BERLIN.lng}&tag=flohmarkt`, a);
  assert.equal(feed.data.posts.length, 2);
  assert.deepEqual(feed.data.trending[0], { tag: 'flohmarkt', count: 2 });
  await call('POST', `/api/posts/${p1.id}/replies`, b, { text: 'Ich!' });
  await call('POST', `/api/posts/${p1.id}/vote`, b, { value: 1 });
  const mine = await call('GET', '/api/me/activity?kind=posts', a);
  assert.deepEqual(mine.data.posts.map((p) => p.id), [p1.id]);
  assert.deepEqual((await call('GET', '/api/me/activity?kind=replies', b)).data.posts.map((p) => p.id), [p1.id]);
  assert.deepEqual((await call('GET', '/api/me/activity?kind=voted', b)).data.posts.map((p) => p.id), [p1.id]);
  const me = await call('GET', '/api/me', a);
  assert.equal(me.data.karma, 1);
  assert.equal(me.data.level.name, 'Neuling');
  assert.equal(me.data.level.next.name, 'Kiezkind');
});

test('Ortsname aus Koordinaten', async () => {
  const u = await newUser();
  assert.equal((await call('GET', '/api/place?lat=52.4707&lng=13.3304', u)).data.name, 'Friedenau');
  assert.equal((await call('GET', '/api/place?lat=0&lng=-30', u)).data.name, null);
});

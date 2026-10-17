// Jodel-Klon: hyperlokaler, anonymer Feed. Keine externen Abhängigkeiten (Node >= 22.5).
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { DatabaseSync } = require('node:sqlite');
const media = require('./media');
const { nearestPlace } = require('./places');

const PORT = Number(process.env.PORT) || 3000;
const RADIUS_KM = Number(process.env.RADIUS_KM) || 10;
const DB_FILE = process.env.DB_FILE || path.join(__dirname, 'jodel.db');
const PUBLIC_DIR = path.join(__dirname, 'public');
const DELETE_AT_SCORE = -5;          // Posts mit diesem Score verschwinden (wie bei Jodel)
const MAX_LEN = 250;
// Akzentfarben aus der Ideenwerft-Palette (Rot, Stahlblau, Bernstein, Grün, Pflaume, Petrol)
const COLORS = ['#CD1818', '#58769D', '#CF8217', '#29A366', '#9B3B6B', '#30878F'];

const db = new DatabaseSync(DB_FILE);
db.exec(`
  PRAGMA journal_mode = WAL;
  PRAGMA foreign_keys = ON;
  CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    token_hash TEXT UNIQUE NOT NULL,
    created_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS posts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL REFERENCES users(id),
    parent_id INTEGER REFERENCES posts(id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    color TEXT NOT NULL,
    lat REAL NOT NULL,
    lng REAL NOT NULL,
    created_at INTEGER NOT NULL
  );
  CREATE INDEX IF NOT EXISTS idx_posts_geo ON posts(lat, lng);
  CREATE INDEX IF NOT EXISTS idx_posts_parent ON posts(parent_id);
  CREATE TABLE IF NOT EXISTS votes (
    post_id INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id),
    value INTEGER NOT NULL CHECK (value IN (-1, 1)),
    PRIMARY KEY (post_id, user_id)
  );
  CREATE TABLE IF NOT EXISTS reports (
    post_id INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id),
    PRIMARY KEY (post_id, user_id)
  );
`);

media.init(db);

const sha = (s) => crypto.createHash('sha256').update(s).digest('hex');

// --- Hilfsfunktionen -------------------------------------------------------
function haversineKm(lat1, lng1, lat2, lng2) {
  const r = (d) => (d * Math.PI) / 180;
  const dLat = r(lat2 - lat1), dLng = r(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(r(lat1)) * Math.cos(r(lat2)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(a));
}

// Standort auf ~100 m runden, damit kein exakter Ort gespeichert wird
const fuzz = (v) => Math.round(v * 1000) / 1000;

function parseCoords(q) {
  if (!q.get('lat') || !q.get('lng')) return null;
  const lat = Number(q.get('lat')), lng = Number(q.get('lng'));
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return null;
  return { lat, lng };
}

function distanceLabel(km) {
  if (km < 1) return 'ganz nah';
  if (km < 2) return 'nah';
  if (km < 5) return `${Math.round(km)} km`;
  return 'weiter weg';
}

// Hashtags wie #mensa oder #wg_suche, klein geschrieben und ohne Doppelte
function hashtags(text) {
  return [...new Set((text.match(/#[\p{L}\p{N}_]{2,30}/gu) || []).map((t) => t.slice(1).toLowerCase()))];
}

// Karma-Stufen
const LEVELS = [[0, 'Neuling'], [10, 'Kiezkind'], [50, 'Stammgast'], [200, 'Kiezlegende'], [1000, 'Kapitän']];
function level(karma) {
  let i = 0;
  while (i + 1 < LEVELS.length && karma >= LEVELS[i + 1][0]) i++;
  const next = LEVELS[i + 1];
  return { name: LEVELS[i][1], next: next ? { name: next[1], at: next[0] } : null, from: LEVELS[i][0] };
}

// Hot-Score: Votes gewichtet mit Alter (ähnlich Hacker News)
function hotScore(p, now) {
  const hours = (now - p.created_at) / 3_600_000;
  return (p.score + p.comment_count * 0.5 + 1) / Math.pow(hours + 2, 1.5);
}

function getUser(req) {
  const auth = req.headers.authorization || '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : '';
  if (!token) return null;
  return db.prepare('SELECT id FROM users WHERE token_hash = ?').get(sha(token)) || null;
}

const POST_COLUMNS = `
  p.id, p.user_id, p.parent_id, p.text, p.color, p.lat, p.lng, p.created_at,
  COALESCE((SELECT SUM(value) FROM votes v WHERE v.post_id = p.id), 0) AS score,
  (SELECT COUNT(*) FROM posts c WHERE c.parent_id = p.id) AS comment_count`;

function serialize(p, userId, origin, opUserId) {
  const myVote = db.prepare('SELECT value FROM votes WHERE post_id = ? AND user_id = ?').get(p.id, userId);
  const out = {
    id: p.id,
    text: p.text,
    color: p.color,
    createdAt: p.created_at,
    score: p.score,
    commentCount: p.comment_count,
    myVote: myVote ? myVote.value : 0,
    mine: p.user_id === userId,
    media: media.forPost(db, p.id),
    distance: origin ? distanceLabel(haversineKm(origin.lat, origin.lng, p.lat, p.lng)) : null,
  };
  if (opUserId !== undefined) out.isOP = p.user_id === opUserId;
  return out;
}

// --- HTTP ------------------------------------------------------------------
function send(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(data);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', (c) => { raw += c; if (raw.length > 10_000) { reject(new Error('too large')); req.destroy(); } });
    req.on('end', () => { try { resolve(raw ? JSON.parse(raw) : {}); } catch { reject(new Error('bad json')); } });
  });
}

// Einfaches Rate-Limit pro Nutzer: max. 10 Posts/Kommentare pro 10 Minuten
const recent = new Map();
function rateLimited(userId) {
  const now = Date.now();
  const list = (recent.get(userId) || []).filter((t) => now - t < 600_000);
  if (list.length >= 10) return true;
  list.push(now);
  recent.set(userId, list);
  return false;
}

function validText(t) {
  return typeof t === 'string' && t.trim().length > 0 && t.trim().length <= MAX_LEN;
}

// Text und/oder Medium: ohne Foto/Video ist Text Pflicht
function checkContent(body, userId) {
  const text = typeof body.text === 'string' ? body.text.trim() : '';
  if (text.length > MAX_LEN) return { error: `Text darf höchstens ${MAX_LEN} Zeichen haben` };
  if (body.mediaId != null) {
    if (typeof body.mediaId !== 'string' || !media.claimable(db, body.mediaId, userId)) return { error: 'Foto/Video nicht gefunden, bitte neu hochladen' };
    return { text, mediaId: body.mediaId };
  }
  if (!validText(text)) return { error: `Text muss 1–${MAX_LEN} Zeichen haben` };
  return { text, mediaId: null };
}

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.webmanifest': 'application/manifest+json', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2', '.webp': 'image/webp' };

function serveStatic(req, res, pathname) {
  const file = path.normalize(path.join(PUBLIC_DIR, pathname === '/' ? 'index.html' : pathname));
  if (!file.startsWith(PUBLIC_DIR)) return send(res, 403, { error: 'forbidden' });
  fs.readFile(file, (err, data) => {
    if (err) {
      // SPA-Fallback
      return fs.readFile(path.join(PUBLIC_DIR, 'index.html'), (e2, html) => {
        if (e2) return send(res, 404, { error: 'not found' });
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(html);
      });
    }
    const type = MIME[path.extname(file)] || 'application/octet-stream';
    res.writeHead(200, { 'Content-Type': type.startsWith('text/') || type.endsWith('json') ? type + '; charset=utf-8' : type });
    res.end(data);
  });
}

async function handleApi(req, res, url) {
  const { pathname, searchParams } = url;
  const m = (re) => pathname.match(re);

  // Anonymes Konto anlegen: liefert ein geheimes Token, das nur im Browser liegt
  if (req.method === 'POST' && pathname === '/api/register') {
    const token = crypto.randomBytes(32).toString('hex');
    const id = crypto.randomUUID();
    db.prepare('INSERT INTO users (id, token_hash, created_at) VALUES (?, ?, ?)').run(id, sha(token), Date.now());
    return send(res, 201, { token });
  }

  const user = getUser(req);
  if (!user) return send(res, 401, { error: 'Nicht angemeldet' });

  // Foto- oder Video-Upload (Rohdaten im Body)
  if (req.method === 'POST' && pathname === '/api/media') {
    if (rateLimited(user.id)) return send(res, 429, { error: 'Langsam! Bitte kurz warten.' });
    try {
      return send(res, 201, await media.handleUpload(db, req, user.id));
    } catch (err) {
      if (err instanceof media.UploadError) return send(res, err.status, { error: err.message });
      throw err;
    }
  }

  // Feed im Umkreis
  if (req.method === 'GET' && pathname === '/api/posts') {
    const origin = parseCoords(searchParams);
    if (!origin) return send(res, 400, { error: 'Standort fehlt' });
    const sort = searchParams.get('sort') || 'new';
    const dLat = RADIUS_KM / 111;
    const dLng = RADIUS_KM / (111 * Math.max(Math.cos((origin.lat * Math.PI) / 180), 0.01));
    let rows = db.prepare(`
      SELECT ${POST_COLUMNS} FROM posts p
      WHERE p.parent_id IS NULL AND p.lat BETWEEN ? AND ? AND p.lng BETWEEN ? AND ?
        AND p.created_at > ?
      ORDER BY p.created_at DESC LIMIT 1000`)
      .all(origin.lat - dLat, origin.lat + dLat, origin.lng - dLng, origin.lng + dLng, Date.now() - 7 * 86_400_000)
      .filter((p) => haversineKm(origin.lat, origin.lng, p.lat, p.lng) <= RADIUS_KM && p.score > DELETE_AT_SCORE);
    // Kanäle: die häufigsten Hashtags in der Umgebung, optional danach filtern
    const tagCounts = new Map();
    for (const p of rows) for (const t of hashtags(p.text)) tagCounts.set(t, (tagCounts.get(t) || 0) + 1);
    const trending = [...tagCounts].sort((a, b) => b[1] - a[1]).slice(0, 12).map(([tag, count]) => ({ tag, count }));
    const tag = (searchParams.get('tag') || '').toLowerCase().replace(/^#/, '');
    if (tag) rows = rows.filter((p) => hashtags(p.text).includes(tag));
    const now = Date.now();
    if (sort === 'popular') rows.sort((a, b) => hotScore(b, now) - hotScore(a, now));
    else if (sort === 'loud') rows.sort((a, b) => b.comment_count - a.comment_count || b.created_at - a.created_at);
    return send(res, 200, { radiusKm: RADIUS_KM, trending, posts: rows.slice(0, 100).map((p) => serialize(p, user.id, origin)) });
  }

  // Neuer Post
  if (req.method === 'POST' && pathname === '/api/posts') {
    const body = await readBody(req);
    const content = checkContent(body, user.id);
    if (content.error) return send(res, 400, { error: content.error });
    const lat = Number(body.lat), lng = Number(body.lng);
    if (body.lat == null || body.lng == null || !Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return send(res, 400, { error: 'Standort fehlt' });
    if (rateLimited(user.id)) return send(res, 429, { error: 'Langsam! Bitte kurz warten.' });
    const color = COLORS[Math.floor(Math.random() * COLORS.length)];
    const r = db.prepare('INSERT INTO posts (user_id, text, color, lat, lng, created_at) VALUES (?, ?, ?, ?, ?, ?)')
      .run(user.id, content.text, color, fuzz(lat), fuzz(lng), Date.now());
    if (content.mediaId) media.attach(db, content.mediaId, user.id, Number(r.lastInsertRowid));
    const p = db.prepare(`SELECT ${POST_COLUMNS} FROM posts p WHERE p.id = ?`).get(r.lastInsertRowid);
    return send(res, 201, serialize(p, user.id, { lat, lng }));
  }

  // Einzelner Post mit Antworten
  let mm = m(/^\/api\/posts\/(\d+)$/);
  if (req.method === 'GET' && mm) {
    const id = Number(mm[1]);
    const origin = parseCoords(searchParams);
    const p = db.prepare(`SELECT ${POST_COLUMNS} FROM posts p WHERE p.id = ? AND p.parent_id IS NULL`).get(id);
    if (!p || p.score <= DELETE_AT_SCORE) return send(res, 404, { error: 'Post nicht gefunden' });
    const replies = db.prepare(`SELECT ${POST_COLUMNS} FROM posts p WHERE p.parent_id = ? ORDER BY p.created_at ASC`).all(id)
      .filter((c) => c.score > DELETE_AT_SCORE);
    // Jeder Antwortende bekommt pro Thread eine feste Nummer (wie "#3" bei Jodel)
    const numbers = new Map();
    const replyOut = replies.map((c) => {
      const out = serialize(c, user.id, origin, p.user_id);
      if (!out.isOP) {
        if (!numbers.has(c.user_id)) numbers.set(c.user_id, numbers.size + 1);
        out.authorNo = numbers.get(c.user_id);
      }
      return out;
    });
    return send(res, 200, { post: serialize(p, user.id, origin), replies: replyOut });
  }

  // Antworten
  mm = m(/^\/api\/posts\/(\d+)\/replies$/);
  if (req.method === 'POST' && mm) {
    const parent = db.prepare('SELECT * FROM posts WHERE id = ? AND parent_id IS NULL').get(Number(mm[1]));
    if (!parent) return send(res, 404, { error: 'Post nicht gefunden' });
    const body = await readBody(req);
    const content = checkContent(body, user.id);
    if (content.error) return send(res, 400, { error: content.error });
    if (rateLimited(user.id)) return send(res, 429, { error: 'Langsam! Bitte kurz warten.' });
    const r = db.prepare('INSERT INTO posts (user_id, parent_id, text, color, lat, lng, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)')
      .run(user.id, parent.id, content.text, parent.color, parent.lat, parent.lng, Date.now());
    if (content.mediaId) media.attach(db, content.mediaId, user.id, Number(r.lastInsertRowid));
    return send(res, 201, { id: Number(r.lastInsertRowid) });
  }

  // Voten (+1, -1 oder 0 zum Zurücknehmen)
  mm = m(/^\/api\/posts\/(\d+)\/vote$/);
  if (req.method === 'POST' && mm) {
    const id = Number(mm[1]);
    const post = db.prepare('SELECT user_id FROM posts WHERE id = ?').get(id);
    if (!post) return send(res, 404, { error: 'Post nicht gefunden' });
    if (post.user_id === user.id) return send(res, 400, { error: 'Eigene Posts kann man nicht voten' });
    const { value } = await readBody(req);
    if (![-1, 0, 1].includes(value)) return send(res, 400, { error: 'Ungültiger Vote' });
    if (value === 0) db.prepare('DELETE FROM votes WHERE post_id = ? AND user_id = ?').run(id, user.id);
    else db.prepare('INSERT INTO votes (post_id, user_id, value) VALUES (?, ?, ?) ON CONFLICT(post_id, user_id) DO UPDATE SET value = excluded.value').run(id, user.id, value);
    const { score } = db.prepare('SELECT COALESCE(SUM(value), 0) AS score FROM votes WHERE post_id = ?').get(id);
    return send(res, 200, { score, myVote: value });
  }

  // Melden: ab 3 Meldungen wird der Post ausgeblendet
  mm = m(/^\/api\/posts\/(\d+)\/report$/);
  if (req.method === 'POST' && mm) {
    const id = Number(mm[1]);
    if (!db.prepare('SELECT 1 FROM posts WHERE id = ?').get(id)) return send(res, 404, { error: 'Post nicht gefunden' });
    db.prepare('INSERT OR IGNORE INTO reports (post_id, user_id) VALUES (?, ?)').run(id, user.id);
    const { n } = db.prepare('SELECT COUNT(*) AS n FROM reports WHERE post_id = ?').get(id);
    if (n >= 3) { db.prepare('DELETE FROM posts WHERE id = ?').run(id); media.cleanup(db); }
    return send(res, 200, { ok: true });
  }

  // Eigenen Post löschen
  if (req.method === 'DELETE' && (mm = m(/^\/api\/posts\/(\d+)$/))) {
    const r = db.prepare('DELETE FROM posts WHERE id = ? AND user_id = ?').run(Number(mm[1]), user.id);
    if (r.changes) media.cleanup(db);
    return r.changes ? send(res, 200, { ok: true }) : send(res, 404, { error: 'Post nicht gefunden' });
  }

  // Ortsname für die Kopfzeile, z. B. „Friedenau“
  if (req.method === 'GET' && pathname === '/api/place') {
    const origin = parseCoords(searchParams);
    if (!origin) return send(res, 400, { error: 'Standort fehlt' });
    return send(res, 200, { name: nearestPlace(origin.lat, origin.lng) });
  }

  // Karma: Summe aller Votes auf eigene Beiträge
  if (req.method === 'GET' && pathname === '/api/me') {
    const { karma } = db.prepare(`SELECT COALESCE(SUM(v.value), 0) AS karma FROM votes v JOIN posts p ON p.id = v.post_id WHERE p.user_id = ?`).get(user.id);
    const { posts } = db.prepare('SELECT COUNT(*) AS posts FROM posts WHERE user_id = ? AND parent_id IS NULL').get(user.id);
    const { replies } = db.prepare('SELECT COUNT(*) AS replies FROM posts WHERE user_id = ? AND parent_id IS NOT NULL').get(user.id);
    const { votes } = db.prepare('SELECT COUNT(*) AS votes FROM votes WHERE user_id = ?').get(user.id);
    return send(res, 200, { karma, posts, replies, votes, level: level(karma) });
  }

  // Meine Aktivität: eigene Posts, Posts mit eigener Antwort, hochgevotete Posts
  if (req.method === 'GET' && pathname === '/api/me/activity') {
    const kind = searchParams.get('kind') || 'posts';
    const where = {
      posts: 'p.user_id = ?',
      replies: 'p.id IN (SELECT parent_id FROM posts WHERE user_id = ? AND parent_id IS NOT NULL)',
      voted: 'p.id IN (SELECT post_id FROM votes WHERE user_id = ? AND value = 1)',
    }[kind];
    if (!where) return send(res, 400, { error: 'Unbekannte Auswahl' });
    const rows = db.prepare(`SELECT ${POST_COLUMNS} FROM posts p WHERE p.parent_id IS NULL AND ${where} ORDER BY p.created_at DESC LIMIT 100`).all(user.id);
    return send(res, 200, { posts: rows.map((p) => serialize(p, user.id, parseCoords(searchParams))) });
  }

  return send(res, 404, { error: 'Unbekannte Route' });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  try {
    if (url.pathname.startsWith('/api/')) await handleApi(req, res, url);
    else if (url.pathname.startsWith('/media/')) media.serve(req, res, url.pathname.slice(7));
    else serveStatic(req, res, url.pathname);
  } catch (err) {
    console.error(err);
    if (!res.headersSent) send(res, 500, { error: 'Serverfehler' });
  }
});

if (require.main === module) {
  setInterval(() => media.cleanup(db), 600_000).unref();
  server.listen(PORT, () => console.log(`Kiez läuft auf http://localhost:${PORT} (Radius ${RADIUS_KM} km, ffmpeg ${media.HAS_FFMPEG ? 'an' : 'aus'})`));
}
module.exports = { server, db };

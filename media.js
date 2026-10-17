// Bild- und Video-Uploads: speichern, mit ffmpeg verkleinern/umwandeln, Metadaten (z. B. GPS) entfernen, ausliefern.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawn, spawnSync } = require('node:child_process');

const UPLOAD_DIR = process.env.UPLOAD_DIR || path.join(__dirname, 'uploads');
const MAX_IMAGE_BYTES = (Number(process.env.MAX_IMAGE_MB) || 15) * 1024 * 1024;
const MAX_VIDEO_BYTES = (Number(process.env.MAX_VIDEO_MB) || 100) * 1024 * 1024;
const MAX_VIDEO_SECONDS = Number(process.env.MAX_VIDEO_SECONDS) || 60;
const HAS_FFMPEG = process.env.DISABLE_FFMPEG ? false : spawnSync('ffmpeg', ['-version']).status === 0;

const TYPES = {
  'image/jpeg': { kind: 'image', ext: 'jpg' },
  'image/png': { kind: 'image', ext: 'png' },
  'image/webp': { kind: 'image', ext: 'webp' },
  'image/gif': { kind: 'image', ext: 'gif' },
  'video/mp4': { kind: 'video', ext: 'mp4' },
  'video/webm': { kind: 'video', ext: 'webm' },
  'video/quicktime': { kind: 'video', ext: 'mov' },
  'video/3gpp': { kind: 'video', ext: '3gp' },
};

// Prüft die ersten Bytes, damit niemand z. B. HTML als Bild hochlädt
function magicOk(kind, ext, head) {
  const hex = head.toString('hex');
  const ascii = head.toString('latin1');
  if (ext === 'jpg') return hex.startsWith('ffd8ff');
  if (ext === 'png') return hex.startsWith('89504e47');
  if (ext === 'gif') return ascii.startsWith('GIF8');
  if (ext === 'webp') return ascii.startsWith('RIFF') && ascii.slice(8, 12) === 'WEBP';
  if (ext === 'webm') return hex.startsWith('1a45dfa3');
  if (kind === 'video') return ascii.slice(4, 8) === 'ftyp';   // mp4, mov, 3gp
  return false;
}

fs.mkdirSync(UPLOAD_DIR, { recursive: true });

function init(db) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS media (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      kind TEXT NOT NULL CHECK (kind IN ('image', 'video')),
      file TEXT NOT NULL,
      poster TEXT,
      width INTEGER,
      height INTEGER,
      post_id INTEGER,
      created_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_media_post ON media(post_id);
  `);
}

// ffmpeg-Aufträge nacheinander abarbeiten, damit der Server nicht überlastet wird
let queue = Promise.resolve();
function ffmpeg(args) {
  const run = () => new Promise((resolve, reject) => {
    const p = spawn('nice', ['-n', '10', 'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', ...args]);
    let err = '';
    p.stderr.on('data', (d) => { err += d; });
    p.on('error', reject);
    p.on('close', (code) => (code === 0 ? resolve() : reject(new Error(err.slice(-500) || `ffmpeg ${code}`))));
  });
  const job = queue.then(run, run);
  queue = job.catch(() => {});
  return job;
}

function probeSize(file) {
  const r = spawnSync('ffprobe', ['-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=width,height', '-of', 'csv=p=0', file]);
  const [w, h] = String(r.stdout).trim().split(',').map(Number);
  return Number.isFinite(w) && Number.isFinite(h) ? { width: w, height: h } : {};
}

class UploadError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

function receive(req, tmpFile, limit) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const out = fs.createWriteStream(tmpFile);
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        req.unpipe(out); out.destroy(); req.resume();
        reject(new UploadError(413, `Datei zu groß (max. ${Math.round(limit / 1024 / 1024)} MB)`));
      }
    });
    req.pipe(out);
    out.on('finish', () => resolve(size));
    out.on('error', reject);
    req.on('error', reject);
  });
}

// POST /api/media, Body = Rohdaten der Datei, Content-Type = MIME-Typ
async function handleUpload(db, req, userId) {
  const mime = String(req.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
  const type = TYPES[mime];
  if (!type) throw new UploadError(415, 'Nur Fotos (JPG, PNG, WebP, GIF) und Videos (MP4, WebM, MOV) sind erlaubt');
  const limit = type.kind === 'image' ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
  if (Number(req.headers['content-length']) > limit) throw new UploadError(413, `Datei zu groß (max. ${Math.round(limit / 1024 / 1024)} MB)`);

  const id = crypto.randomBytes(12).toString('hex');
  const tmp = path.join(UPLOAD_DIR, `${id}.upload`);
  try {
    const size = await receive(req, tmp, limit);
    if (!size) throw new UploadError(400, 'Leere Datei');
    const fd = fs.openSync(tmp, 'r');
    const head = Buffer.alloc(16);
    fs.readSync(fd, head, 0, 16, 0);
    fs.closeSync(fd);
    if (!magicOk(type.kind, type.ext, head)) throw new UploadError(415, 'Die Datei passt nicht zu ihrem Typ');

    let file, poster = null;
    if (HAS_FFMPEG && type.kind === 'image' && type.ext !== 'gif') {
      file = `${id}.jpg`;
      await ffmpeg(['-i', tmp, '-map_metadata', '-1', '-vf', "scale='min(1600,iw)':-2", '-q:v', '4', '-frames:v', '1', path.join(UPLOAD_DIR, file)]);
    } else if (HAS_FFMPEG && type.kind === 'video') {
      file = `${id}.mp4`;
      poster = `${id}.poster.jpg`;
      await ffmpeg(['-i', tmp, '-t', String(MAX_VIDEO_SECONDS), '-map_metadata', '-1',
        '-vf', "scale='if(gt(iw,ih),min(1280,iw),-2)':'if(gt(iw,ih),-2,min(1280,ih))'",
        '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '28', '-pix_fmt', 'yuv420p',
        '-c:a', 'aac', '-b:a', '96k', '-movflags', '+faststart', path.join(UPLOAD_DIR, file)]);
      await ffmpeg(['-ss', '0.5', '-i', path.join(UPLOAD_DIR, file), '-frames:v', '1', '-vf', "scale='min(720,iw)':-2", '-q:v', '5',
        path.join(UPLOAD_DIR, poster)]).catch(() => { poster = null; });
    } else {
      // Ohne ffmpeg: Datei unverändert speichern (die App entfernt bei Fotos die Metadaten schon beim Verkleinern)
      file = `${id}.${type.ext}`;
      fs.renameSync(tmp, path.join(UPLOAD_DIR, file));
    }
    const dims = HAS_FFMPEG ? probeSize(path.join(UPLOAD_DIR, file)) : {};
    db.prepare('INSERT INTO media (id, user_id, kind, file, poster, width, height, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)')
      .run(id, userId, type.kind, file, poster, dims.width ?? null, dims.height ?? null, Date.now());
    return { id, kind: type.kind, url: `/media/${file}`, poster: poster && `/media/${poster}` };
  } catch (err) {
    if (err instanceof UploadError) throw err;
    console.error('Upload fehlgeschlagen:', err.message);
    throw new UploadError(422, 'Die Datei konnte nicht verarbeitet werden');
  } finally {
    fs.rm(tmp, { force: true }, () => {});
  }
}

// Ein hochgeladenes Medium an einen Post hängen (nur eigene, noch unbenutzte)
function attach(db, mediaId, userId, postId) {
  const r = db.prepare('UPDATE media SET post_id = ? WHERE id = ? AND user_id = ? AND post_id IS NULL').run(postId, mediaId, userId);
  return r.changes === 1;
}

function claimable(db, mediaId, userId) {
  return !!db.prepare('SELECT 1 FROM media WHERE id = ? AND user_id = ? AND post_id IS NULL').get(mediaId, userId);
}

function forPost(db, postId) {
  const m = db.prepare('SELECT kind, file, poster, width, height FROM media WHERE post_id = ?').get(postId);
  if (!m) return null;
  return { kind: m.kind, url: `/media/${m.file}`, poster: m.poster ? `/media/${m.poster}` : null, width: m.width, height: m.height };
}

// Verwaiste Dateien löschen: nie benutzte Uploads nach 1 Stunde, Medien gelöschter Posts sofort
function cleanup(db) {
  const rows = db.prepare(`SELECT id, file, poster FROM media
    WHERE (post_id IS NULL AND created_at < ?) OR (post_id IS NOT NULL AND post_id NOT IN (SELECT id FROM posts))`).all(Date.now() - 3_600_000);
  for (const m of rows) {
    for (const f of [m.file, m.poster]) if (f) fs.rm(path.join(UPLOAD_DIR, f), { force: true }, () => {});
    db.prepare('DELETE FROM media WHERE id = ?').run(m.id);
  }
  return rows.length;
}

const MEDIA_MIME = { jpg: 'image/jpeg', png: 'image/png', webp: 'image/webp', gif: 'image/gif', mp4: 'video/mp4', webm: 'video/webm', mov: 'video/quicktime', '3gp': 'video/3gpp' };

// GET /media/<datei>, mit Range-Unterstützung für Videos
function serve(req, res, name) {
  const m = name.match(/^[a-f0-9]{24}(?:\.poster)?\.(jpg|png|webp|gif|mp4|webm|mov|3gp)$/);
  if (!m) { res.writeHead(404); return res.end(); }
  const file = path.join(UPLOAD_DIR, name);
  fs.stat(file, (err, st) => {
    if (err) { res.writeHead(404); return res.end(); }
    const headers = { 'Content-Type': MEDIA_MIME[m[1]], 'Accept-Ranges': 'bytes', 'Cache-Control': 'public, max-age=31536000, immutable',
      'X-Content-Type-Options': 'nosniff' };
    const range = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range || '');
    if (range && (range[1] || range[2])) {
      let start = range[1] ? Number(range[1]) : st.size - Number(range[2]);
      let end = range[1] && range[2] ? Number(range[2]) : st.size - 1;
      start = Math.max(0, start); end = Math.min(end, st.size - 1);
      if (start > end) { res.writeHead(416, { 'Content-Range': `bytes */${st.size}` }); return res.end(); }
      res.writeHead(206, { ...headers, 'Content-Range': `bytes ${start}-${end}/${st.size}`, 'Content-Length': end - start + 1 });
      return req.method === 'HEAD' ? res.end() : fs.createReadStream(file, { start, end }).pipe(res);
    }
    res.writeHead(200, { ...headers, 'Content-Length': st.size });
    return req.method === 'HEAD' ? res.end() : fs.createReadStream(file).pipe(res);
  });
}

module.exports = { init, handleUpload, attach, claimable, forPost, cleanup, serve, UploadError, HAS_FFMPEG, UPLOAD_DIR };
